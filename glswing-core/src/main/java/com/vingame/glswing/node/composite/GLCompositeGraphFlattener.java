package com.vingame.glswing.node.composite;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.vingame.glswing.node.document.GLNodeGraphAsset;
import com.vingame.glswing.node.model.GLGraphConnection;
import com.vingame.glswing.node.model.GLGraphDiagnostic;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeConnectionId;
import com.vingame.glswing.node.model.GLNodeEndpoint;
import com.vingame.glswing.node.model.GLNodeGraphSnapshot;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodeValue;

/** Deterministically replaces composite instances with their leaf graph contents. */
public final class GLCompositeGraphFlattener {
	private GLCompositeGraphFlattener() { }

	public static GLCompositeFlattenResult flatten(GLNodeGraphAsset root,
			GLCompositeGraphRegistry registry, GLCompositeExpansionLimits limits) {
		GLCompositeExpansionResult estimate = GLCompositeExpansionAnalyzer.analyze(
				root, registry, limits);
		if (!estimate.isValid()) return failed(estimate.diagnostics());
		root=GLCompositeForwarding.materialize(root);
        Context context = new Context(root);
		expand(root, registry, List.of(), true, context);
		try{var collapsed=GLCompositeForwarding.collapse(context.connections,context.forwarding,limits.maximumConnections());context.connections.clear();context.connections.addAll(collapsed);}
        catch(IllegalArgumentException e){context.error("composite.limit",e.getMessage(),null);}
        if(context.connections.size()>limits.maximumConnections()) context.error("composite.limit", "Expanded fanout exceeds the connection limit", null);
        if (!context.diagnostics.isEmpty()) return failed(context.diagnostics);
		GLNodeGraphSnapshot graph = new GLNodeGraphSnapshot(root.graph().graphId(),
				root.graph().revision(), context.nodes, context.connections);
		return new GLCompositeFlattenResult(Optional.of(graph), context.origins, List.of());
	}

	private static ExpandedGraph expand(GLNodeGraphAsset asset,
			GLCompositeGraphRegistry registry, List<GLCompositeOrigin.Instance> path,
			boolean root, Context context) {
		Map<GLNodeId, EndpointResolver> endpoints = new LinkedHashMap<>();
		for (GLGraphNode node : asset.graph().nodes()) {
			GLCompositeNodeDescriptor descriptor = registry.findComposite(node.typeId())
					.orElse(null);
			if (descriptor == null) {
				GLNodeId id = root ? node.id() : derivedNodeId(path, asset.id(), node.id());
				GLGraphNode leaf = new GLGraphNode(id, node.typeId(), node.typeVersion(),
						node.properties());
				if(node.typeId().equals(GLCompositeForwarding.TYPE))context.forwarding.add(id);else context.nodes.add(leaf);
				if(!node.typeId().equals(GLCompositeForwarding.TYPE))context.origins.put(id, new GLCompositeOrigin(path, asset.id(), node.id()));
				endpoints.put(node.id(), port -> List.of(new GLNodeEndpoint(id, port)));
				continue;
			}
			GLNodeGraphAsset nested = GLCompositeForwarding.materialize(registry.findAsset(descriptor.assetId()).orElseThrow());
            descriptor=nested.compositeDescriptor();
			GLNodeGraphAsset configured = applyProperties(node, descriptor, nested, context);
			List<GLCompositeOrigin.Instance> nestedPath = new ArrayList<>(path);
			nestedPath.add(new GLCompositeOrigin.Instance(node.id(), node.typeId(), nested.id()));
			ExpandedGraph expanded = expand(configured, registry, List.copyOf(nestedPath),
					false, context);
			Map<String, List<GLNodeEndpoint>> publicEndpoints = new LinkedHashMap<>();
			for (GLCompositePortMapping mapping : descriptor.portMappings()) {
				if (!mapping.isBound()) { publicEndpoints.put(mapping.publicPort().id(), List.of()); continue; }
                List<GLNodeEndpoint> resolved = new ArrayList<>();
                for(var endpoint : mapping.internalEndpoints()) { var targets = expanded.resolve(endpoint); if (targets == null) context.error("composite.port-target", "Cannot resolve internal target for public port " + mapping.publicPort().id(), node.id()); else resolved.addAll(targets); }
                // An empty nested mapping is a valid, disconnected public port.
                publicEndpoints.put(mapping.publicPort().id(), List.copyOf(resolved));
			}
			endpoints.put(node.id(), publicEndpoints::get);
		}
		for (GLGraphConnection connection : asset.graph().connections()) {
			List<GLNodeEndpoint> output = resolve(endpoints, connection.output());
			List<GLNodeEndpoint> input = resolve(endpoints, connection.input());
			if (output == null || input == null) {
				GLNodeId origin = output == null ? connection.output().nodeId()
						: connection.input().nodeId();
				context.error("composite.unmapped-port",
						"A connected composite port has no internal mapping", origin);
				continue;
			}
			GLNodeConnectionId id = root ? connection.id()
					: derivedConnectionId(path, asset.id(), connection.id());
			for (var out : output) for (var in : input) {
                var wireId = output.size()==1 && input.size()==1 ? id : new GLNodeConnectionId(UUID.nameUUIDFromBytes((id+"/"+out+"/"+in).getBytes(StandardCharsets.UTF_8)));
                context.connections.add(new GLGraphConnection(wireId, out, in));
            }
		}
		return new ExpandedGraph(endpoints);
	}

	private static GLNodeGraphAsset applyProperties(GLGraphNode instance,
			GLCompositeNodeDescriptor descriptor, GLNodeGraphAsset nested,
			Context context) {
		Map<GLNodeId, GLGraphNode> nodes = new LinkedHashMap<>(nested.graph().nodesById());
		for (String property : instance.properties().keySet())
			if (!property.startsWith(GLGraphNode.AUTOMATIC_PREFIX) && !property.startsWith(GLGraphNode.AUTOMATIC_DEFAULT_PREFIX) && descriptor.findProperty(property).isEmpty())
				context.error("composite.property-unknown", "Unknown composite property: "
						+ property, instance.id());
		for (GLCompositePropertyDescriptor property : descriptor.properties()) {
			GLNodeValue value = instance.properties().getOrDefault(property.id(),
					property.defaultValue());
			if (!property.accepts(value)) {
				context.error("composite.property-value", "Invalid value for composite property "
						+ property.id(), instance.id());
				continue;
			}
			for (GLCompositePropertyMapping mapping : descriptor.propertyMappings()) {
				if (!mapping.publicPropertyId().equals(property.id())) continue;
				GLGraphNode target = nodes.get(mapping.internalNodeId());
				if (target == null) {
					context.error("composite.property-node", "Property " + property.id()
							+ " maps to a missing internal node", instance.id());
					continue;
				}
				var mapped=mapping.mappedValue(value);
                if(mapped==null){context.error("composite.property-choice","No mapped value for "+property.id(),instance.id());continue;}
                nodes.put(target.id(), target.withProperty(mapping.internalPropertyId(), mapped));
			}
		}
		GLNodeGraphSnapshot graph = new GLNodeGraphSnapshot(nested.graph().graphId(),
				nested.graph().revision(), List.copyOf(nodes.values()), nested.graph().connections());
		return new GLNodeGraphAsset(nested.id(), nested.version(), nested.kind(), graph,
				nested.viewState(), nested.metadata(), nested.compositeDescriptor());
	}

	private static List<GLNodeEndpoint> resolve(Map<GLNodeId, EndpointResolver> endpoints,
			GLNodeEndpoint endpoint) {
		EndpointResolver resolver = endpoints.get(endpoint.nodeId());
		var result = resolver == null ? null : resolver.resolve(endpoint.portId());
        return result; // null means unknown; an empty list means intentionally disconnected.
	}
	private static GLNodeId derivedNodeId(List<GLCompositeOrigin.Instance> path,
			String assetId, GLNodeId source) {
		return new GLNodeId(derived("node", path, assetId, source.value().toString()));
	}
	private static GLNodeConnectionId derivedConnectionId(
			List<GLCompositeOrigin.Instance> path, String assetId,
			GLNodeConnectionId source) {
		return new GLNodeConnectionId(derived("connection", path, assetId,
				source.value().toString()));
	}
	private static UUID derived(String kind, List<GLCompositeOrigin.Instance> path,
			String assetId, String source) {
		StringBuilder key = new StringBuilder("glswing:").append(kind);
		for (GLCompositeOrigin.Instance instance : path)
			key.append('/').append(instance.nodeId()).append('@').append(instance.assetId());
		key.append('/').append(assetId).append('/').append(source);
		return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));
	}
	private static GLCompositeFlattenResult failed(List<GLGraphDiagnostic> diagnostics) {
		return new GLCompositeFlattenResult(Optional.empty(), Map.of(), diagnostics);
	}

	@FunctionalInterface private interface EndpointResolver {
		List<GLNodeEndpoint> resolve(String portId);
	}
	private record ExpandedGraph(Map<GLNodeId, EndpointResolver> endpoints) {
		private List<GLNodeEndpoint> resolve(GLNodeEndpoint endpoint) {
			return GLCompositeGraphFlattener.resolve(endpoints, endpoint);
		}
	}
	private static final class Context {
		private final List<GLGraphNode> nodes = new ArrayList<>();
        private final Set<GLNodeId> forwarding=new java.util.HashSet<>();
		private final List<GLGraphConnection> connections = new ArrayList<>();
		private final Map<GLNodeId, GLCompositeOrigin> origins = new LinkedHashMap<>();
		private final List<GLGraphDiagnostic> diagnostics = new ArrayList<>();
		private Context(GLNodeGraphAsset root) { }
		private void error(String code, String message, GLNodeId node) {
			diagnostics.add(new GLGraphDiagnostic(GLGraphDiagnostic.Severity.ERROR,
					code, message, node == null ? Set.of() : Set.of(node), Set.of()));
		}
	}
}
