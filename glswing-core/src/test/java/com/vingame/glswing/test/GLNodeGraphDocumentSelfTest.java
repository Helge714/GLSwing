package com.vingame.glswing.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.vingame.glswing.document.GLDocumentCodecRegistry;
import com.vingame.glswing.document.GLDocumentManager;
import com.vingame.glswing.node.binding.GLNodeEditorViewState;
import com.vingame.glswing.node.binding.GLNodeViewState;
import com.vingame.glswing.node.composite.GLCompositeNodeDescriptor;
import com.vingame.glswing.node.composite.GLCompositePortMapping;
import com.vingame.glswing.node.composite.GLCompositePortPresentation;
import com.vingame.glswing.node.composite.GLNodeInputEditorRegistry;
import com.vingame.glswing.node.composite.GLCompositePropertyDescriptor;
import com.vingame.glswing.node.composite.GLCompositePropertyMapping;
import com.vingame.glswing.node.composite.GLCompositeValueKind;
import com.vingame.glswing.node.document.GLNodeGraphAsset;
import com.vingame.glswing.node.document.GLNodeGraphAssetKind;
import com.vingame.glswing.node.document.GLNodeGraphAssetReference;
import com.vingame.glswing.node.document.GLNodeGraphDocument;
import com.vingame.glswing.node.document.GLNodeGraphDocumentCodec;
import com.vingame.glswing.node.document.GLNodeGraphMigrationException;
import com.vingame.glswing.node.document.GLNodeGraphMigrationRegistry;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeGraph;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodeEndpoint;
import com.vingame.glswing.node.model.GLNodePortDescriptor;
import com.vingame.glswing.node.model.GLNodeValue;

/** Round-trip checks for portable project and composite graph assets. */
public final class GLNodeGraphDocumentSelfTest {
	private GLNodeGraphDocumentSelfTest() { }

	public static void main(String[] args) throws Exception {
		migrationChecks();
		Path directory = Files.createTempDirectory("glswing-node-graphs-");
		Path projectFile = directory.resolve("project.glgraph");
		Path compositeFile = directory.resolve("reusable-filter.glgraph");
		try {
			GLNodeGraphAsset project = asset("example.project",
					GLNodeGraphAssetKind.PROJECT, "application.unknown-root");
			GLNodeGraphAsset compositeBase = asset("example.filter", 
					GLNodeGraphAssetKind.COMPOSITE, "plugin.missing-filter");
			GLNodeId internal = compositeBase.graph().nodes().get(0).id();
			GLNodeGraphAsset composite = new GLNodeGraphAsset(compositeBase.id(),
					compositeBase.version(), compositeBase.kind(), compositeBase.graph(),
					compositeBase.viewState(), compositeBase.metadata(),
					new GLCompositeNodeDescriptor("example.filter-node",
							compositeBase.id(), 1,
							List.of(new GLCompositePortMapping(
									GLNodePortDescriptor.input("input", "audio", true, 1),
									new GLNodeEndpoint(internal, "input"),
									new GLCompositePortPresentation(
											GLNodeInputEditorRegistry.SLIDER, true,
											Map.of("minimum", GLNodeValue.of(0.0),
													"maximum", GLNodeValue.of(2.0),
													"numericType", GLNodeValue.of("float")))),
									new GLCompositePortMapping(
											GLNodePortDescriptor.input("tint", "color", false, 1),
											null, new GLCompositePortPresentation(
													"example.color", false, Map.of()))),
							List.of(new GLCompositePropertyDescriptor("cutoff", "frequency",
									GLCompositeValueKind.DECIMAL, GLNodeValue.of(1000.0),
									20.0, 20000.0)),
							List.of(new GLCompositePropertyMapping("cutoff", internal,
									"cutoffHz"))));
			GLNodeGraphDocument document = new GLNodeGraphDocument(project,
					Map.of(composite.id(), composite), List.of(
							new GLNodeGraphAssetReference("shared-envelope",
									"example.envelope", 2,
									"assets/envelope.glgraph")));
			GLNodeGraphDocument standalone = document.exportAsset(composite.id());
			check(standalone.root().equals(composite)
					&& !standalone.embeddedAssets().containsKey(project.id()),
					"Composite export did not create a self-contained asset document");

			GLNodeGraphDocumentCodec codec = new GLNodeGraphDocumentCodec();
			GLDocumentCodecRegistry codecs = new GLDocumentCodecRegistry();
			codecs.register(codec);
			try (GLDocumentManager manager = new GLDocumentManager(codecs)) {
				var projectDocument = manager.create(document, "Project", codec);
				manager.saveAs(projectDocument, projectFile).toCompletableFuture()
						.get(5, TimeUnit.SECONDS);
				var reopened = manager.open(projectFile, GLNodeGraphDocument.class)
						.toCompletableFuture().get(5, TimeUnit.SECONDS);
				check(reopened.content().equals(document),
						"Project graph document did not round-trip");

				var compositeDocument = manager.create(standalone, "Filter", codec);
				manager.saveAs(compositeDocument, compositeFile).toCompletableFuture()
						.get(5, TimeUnit.SECONDS);
				var imported = manager.open(compositeFile, GLNodeGraphDocument.class)
						.toCompletableFuture().get(5, TimeUnit.SECONDS).content();
				check(imported.root().kind() == GLNodeGraphAssetKind.COMPOSITE
						&& imported.root().graph().nodes().get(0).typeId()
								.equals("plugin.missing-filter"),
						"Standalone composite lost its kind or unknown node type");
			}
			check(document.externalAssets().get(0).resolveAgainst(directory)
					.equals(directory.resolve("assets/envelope.glgraph")),
					"Portable external asset path resolved incorrectly");
			System.out.println("GL node-graph document self-test passed.");
		} finally {
			Files.deleteIfExists(projectFile);
			Files.deleteIfExists(compositeFile);
			Files.deleteIfExists(directory);
		}
	}

	private static void migrationChecks() throws Exception {
		GLNodeGraphMigrationRegistry migrations = new GLNodeGraphMigrationRegistry();
		migrations.registerNodeType("legacy.gain", 2);
		migrations.addNodeMigration("legacy.gain", 1, node ->
				new GLGraphNode(node.id(), "current.gain", node.properties()));
		migrations.registerNodeType("current.gain", 2);
		migrations.registerAsset("legacy.asset", 2);
		migrations.addAssetMigration("legacy.asset", 1, asset ->
				new GLNodeGraphAsset(asset.id(), asset.version(), asset.kind(),
						asset.graph(), asset.viewState(), Map.of("migrated",
								GLNodeValue.of(true))));
		GLNodeGraphDocumentCodec codec = new GLNodeGraphDocumentCodec(migrations);
		java.util.concurrent.atomic.AtomicReference<com.vingame.glswing.node.document.GLNodeGraphMigrationReport>
				report = new java.util.concurrent.atomic.AtomicReference<>();
		codec.setMigrationListener(report::set);
		var decoded = codec.readResult(new ByteArrayInputStream(legacyV1Document()),
				codec.currentSchemaVersion());
		check(decoded.migrated() && decoded.storedSchemaVersion() == 1,
				"Legacy schema migration was not reported");
		GLNodeGraphAsset root = decoded.content().root();
		check(root.version() == 2 && root.metadata().containsKey("migrated"),
				"Asset migration did not run");
		check(root.graph().nodes().get(0).typeId().equals("current.gain")
				&& root.graph().nodes().get(0).typeVersion() == 2,
				"Node-type migration did not run");
		check(report.get() != null && report.get().steps().size() == 2,
				"Migration audit report is incomplete");
		Path legacy = Files.createTempFile("glswing-node-v1-", ".glgraph");
		try {
			Files.write(legacy, legacyV1Document());
			GLDocumentCodecRegistry registry = new GLDocumentCodecRegistry();
			registry.register(codec);
			try (GLDocumentManager manager = new GLDocumentManager(registry)) {
				var opened = manager.open(legacy, GLNodeGraphDocument.class)
						.toCompletableFuture().get(5, TimeUnit.SECONDS);
				check(opened.isModified()
						&& manager.migratedFromSchema(opened).orElseThrow() == 1,
						"Manager did not retain the migrated document state");
				manager.save(opened).toCompletableFuture().get(5, TimeUnit.SECONDS);
				check(!opened.isModified() && manager.migratedFromSchema(opened).isEmpty(),
						"Explicit save did not establish the current-schema checkpoint");
			}
			byte[] current = Files.readAllBytes(legacy);
			int versionOffset = "GLSWING-NODE-GRAPH\n".length();
			check(java.nio.ByteBuffer.wrap(current, versionOffset, Integer.BYTES).getInt()
					== codec.currentSchemaVersion(),
					"Explicit save did not write the current schema");
		} finally { Files.deleteIfExists(legacy); }

		GLNodeGraphMigrationRegistry incomplete = new GLNodeGraphMigrationRegistry();
		incomplete.registerNodeType("legacy.gain", 2);
		try {
			GLNodeGraphDocumentCodec incompleteCodec =
					new GLNodeGraphDocumentCodec(incomplete);
			incompleteCodec.readResult(new ByteArrayInputStream(legacyV1Document()),
					incompleteCodec.currentSchemaVersion());
			throw new AssertionError("Missing node migration was accepted");
		} catch (GLNodeGraphMigrationException expected) {
			check(expected.subjectId().equals("legacy.gain")
					&& expected.sourceVersion() == 1,
					"Migration failure lacks structured context");
		}
	}

	/** Minimal authentic schema-v1 payload retained as a compatibility fixture. */
	private static byte[] legacyV1Document() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream data = new DataOutputStream(bytes);
		data.write("GLSWING-NODE-GRAPH\n".getBytes(StandardCharsets.US_ASCII));
		data.writeInt(1);
		writeString(data, "legacy.asset"); data.writeInt(1); data.writeByte(0);
		java.util.UUID graph = new java.util.UUID(1, 2);
		data.writeLong(graph.getMostSignificantBits()); data.writeLong(graph.getLeastSignificantBits());
		data.writeLong(0); data.writeInt(1);
		java.util.UUID node = new java.util.UUID(3, 4);
		data.writeLong(node.getMostSignificantBits()); data.writeLong(node.getLeastSignificantBits());
		writeString(data, "legacy.gain"); data.writeInt(0);
		data.writeInt(0); // connections
		data.writeInt(0); // view states
		data.writeInt(0); // metadata
		data.writeInt(0); // embedded assets
		data.writeInt(0); // external assets
		data.flush(); return bytes.toByteArray();
	}

	private static void writeString(DataOutputStream output, String value) throws Exception {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		output.writeInt(bytes.length); output.write(bytes);
	}

	private static GLNodeGraphAsset asset(String id, GLNodeGraphAssetKind kind,
			String nodeType) {
		GLNodeId nodeId = GLNodeId.random();
		GLNodeGraph graph = new GLNodeGraph();
		graph.edit("Create asset", edit -> edit.addNode(new GLGraphNode(nodeId,
				nodeType, Map.of("source", GLNodeValue.of("return input;")))));
		return new GLNodeGraphAsset(id, 1, kind, graph.snapshot(),
				new GLNodeEditorViewState(Map.of(nodeId,
						new GLNodeViewState(32, 48, 220, 140)),
						Map.of("composite.boundary.input",
								new GLNodeViewState(12, 24, 180, 96),
								"composite.boundary.output",
								new GLNodeViewState(640, 24, 180, 96))),
				Map.of("display-name", GLNodeValue.of(id)));
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
