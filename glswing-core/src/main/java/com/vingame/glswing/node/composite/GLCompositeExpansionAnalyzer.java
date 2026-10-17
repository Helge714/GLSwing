package com.vingame.glswing.node.composite;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.vingame.glswing.node.document.GLNodeGraphAsset;
import com.vingame.glswing.node.model.GLGraphDiagnostic;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeId;

/** Performs bounded, non-materializing analysis before composite expansion. */
public final class GLCompositeExpansionAnalyzer {
	private GLCompositeExpansionAnalyzer() { }

	public static GLCompositeExpansionResult analyze(GLNodeGraphAsset root,
			GLCompositeGraphRegistry registry, GLCompositeExpansionLimits limits) {
		if (root == null || registry == null || limits == null)
			throw new IllegalArgumentException("Root asset, registry, and limits required");
		State state = new State(limits);
		visit(root, registry, 0, new LinkedHashSet<>(), null, state);
		return new GLCompositeExpansionResult(state.nodes, state.connections,
				state.operations, state.maximumDepth, state.diagnostics);
	}

	private static void visit(GLNodeGraphAsset asset, GLCompositeGraphRegistry registry,
			int depth, LinkedHashSet<String> path, GLNodeId origin, State state) {
		if (state.limitReached) return;
		state.maximumDepth = Math.max(state.maximumDepth, depth);
		if (depth > state.limits.maximumDepth()) {
			state.error("composite.depth-limit", "Composite nesting depth " + depth
					+ " exceeds the configured limit of " + state.limits.maximumDepth(), origin);
			state.limitReached = true; return;
		}
		if (!path.add(asset.id())) {
			state.error("composite.recursive", "Recursive composite dependency: "
					+ String.join(" -> ", path) + " -> " + asset.id(), origin);
			return;
		}
		state.addConnections(asset.graph().connections().size(), origin);
		for (GLGraphNode node : asset.graph().nodes()) {
			if (state.limitReached) break;
			GLCompositeNodeDescriptor composite = registry.findComposite(node.typeId())
					.orElse(null);
			if (composite == null) {
				state.addNodes(1, node.id()); state.addOperations(1, node.id()); continue;
			}
			GLNodeGraphAsset nested = registry.findAsset(composite.assetId()).orElse(null);
			if (nested == null) {
				state.error("composite.missing-asset", "Composite asset is not loaded: "
						+ composite.assetId(), node.id());
				continue;
			}
			if (nested.version() < composite.minimumAssetVersion()) {
				state.error("composite.asset-version", "Composite asset " + nested.id()
						+ " has version " + nested.version() + ", but version "
						+ composite.minimumAssetVersion() + " is required", node.id());
				continue;
			}
			if (nested.kind() != com.vingame.glswing.node.document.GLNodeGraphAssetKind.COMPOSITE) {
				state.error("composite.asset-kind", "Referenced asset is not a composite: "
						+ nested.id(), node.id());
				continue;
			}
			validateMappings(composite, nested, node.id(), state);
			visit(nested, registry, depth + 1, path, node.id(), state);
		}
		path.remove(asset.id());
	}

	private static void validateMappings(GLCompositeNodeDescriptor descriptor,
			GLNodeGraphAsset asset, GLNodeId origin, State state) {
		for (GLCompositePortMapping mapping : descriptor.portMappings()) {
			if (!mapping.isBound()) continue;
			for(var endpoint : mapping.internalEndpoints()) {
            if(GLCompositeForwarding.isInput(endpoint)){
                if(!GLCompositeForwarding.valid(mapping,descriptor.portMappings()))state.error("composite.forward-input","Unknown public input forwarding target",origin);
                continue;
            }
            GLGraphNode internal = asset.graph().findNode(endpoint.nodeId())
					.orElse(null);
			if (internal == null)
				state.error("composite.port-node", "Public port "
						+ mapping.publicPort().id() + " maps to a missing internal node", origin);
            }
		}
	}

	private static final class State {
		private final GLCompositeExpansionLimits limits;
		private final List<GLGraphDiagnostic> diagnostics = new ArrayList<>();
		private long nodes, connections, operations;
		private int maximumDepth;
		private boolean limitReached;
		private State(GLCompositeExpansionLimits limits) { this.limits = limits; }
		private void addNodes(long count, GLNodeId origin) {
			nodes = saturatedAdd(nodes, count);
			if (nodes > limits.maximumNodes()) limit("node", nodes, limits.maximumNodes(), origin);
		}
		private void addConnections(long count, GLNodeId origin) {
			connections = saturatedAdd(connections, count);
			if (connections > limits.maximumConnections())
				limit("connection", connections, limits.maximumConnections(), origin);
		}
		private void addOperations(long count, GLNodeId origin) {
			operations = saturatedAdd(operations, count);
			if (operations > limits.maximumOperations())
				limit("operation", operations, limits.maximumOperations(), origin);
		}
		private void limit(String subject, long actual, long maximum, GLNodeId origin) {
			error("composite." + subject + "-limit", "Composite expansion would produce "
					+ actual + " " + subject + "s and exceeds the configured limit of "
					+ maximum, origin);
			limitReached = true;
		}
		private void error(String code, String message, GLNodeId origin) {
			diagnostics.add(new GLGraphDiagnostic(GLGraphDiagnostic.Severity.ERROR,
					code, message, origin == null ? Set.of() : Set.of(origin), Set.of()));
		}
		private static long saturatedAdd(long left, long right) {
			return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
		}
	}
}
