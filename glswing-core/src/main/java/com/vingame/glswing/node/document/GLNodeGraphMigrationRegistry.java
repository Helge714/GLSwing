package com.vingame.glswing.node.document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeGraphSnapshot;

/**
 * Registry of deterministic, consecutive application migrations for graph assets
 * and node types. Unknown subjects are preserved verbatim; registered subjects
 * must provide a complete chain to their declared current version.
 */
public final class GLNodeGraphMigrationRegistry {
	private record Key(String id, int version) { }
	private final Map<String, Integer> nodeVersions = new LinkedHashMap<>();
	private final Map<Key, GLNodeMigration> nodeMigrations = new LinkedHashMap<>();
	private final Map<String, Integer> assetVersions = new LinkedHashMap<>();
	private final Map<Key, GLNodeAssetMigration> assetMigrations = new LinkedHashMap<>();

	public synchronized void registerNodeType(String typeId, int currentVersion) {
		registerVersion(nodeVersions, typeId, currentVersion, "Node type");
	}
	public synchronized void addNodeMigration(String typeId, int fromVersion,
			GLNodeMigration migration) {
		add(nodeVersions, nodeMigrations, typeId, fromVersion, migration, "node");
	}
	public synchronized void registerAsset(String assetId, int currentVersion) {
		registerVersion(assetVersions, assetId, currentVersion, "Asset");
	}
	public synchronized void addAssetMigration(String assetId, int fromVersion,
			GLNodeAssetMigration migration) {
		add(assetVersions, assetMigrations, assetId, fromVersion, migration, "asset");
	}

	/** Migrates a detached immutable document; the supplied instance is never changed. */
	public synchronized GLNodeGraphMigrationReport migrate(GLNodeGraphDocument source)
			throws GLNodeGraphMigrationException {
		if (source == null) throw new IllegalArgumentException("Graph document required");
		List<GLNodeGraphMigrationReport.Step> steps = new ArrayList<>();
		GLNodeGraphAsset root = migrateAsset(source.root(), steps);
		Map<String, GLNodeGraphAsset> embedded = new LinkedHashMap<>();
		for (GLNodeGraphAsset asset : source.embeddedAssets().values()) {
			GLNodeGraphAsset migrated = migrateAsset(asset, steps);
			embedded.put(migrated.id(), migrated);
		}
		return new GLNodeGraphMigrationReport(new GLNodeGraphDocument(root, embedded,
				source.externalAssets()), steps);
	}

	private GLNodeGraphAsset migrateAsset(GLNodeGraphAsset original,
			List<GLNodeGraphMigrationReport.Step> steps)
			throws GLNodeGraphMigrationException {
		GLNodeGraphAsset asset = original;
		Integer target = assetVersions.get(asset.id());
		if (target == null) return migrateNodes(asset, steps);
		if (asset.version() > target)
			throw newer("asset", asset.id(), asset.version(), target);
		while (asset.version() < target) {
			int from = asset.version();
			GLNodeAssetMigration migration = assetMigrations.get(new Key(asset.id(), from));
			if (migration == null) throw missing("asset", asset.id(), from);
			try {
				GLNodeGraphAsset converted = migration.migrate(asset);
				if (converted == null || !converted.id().equals(asset.id()))
					throw new IllegalStateException("Asset migration changed its identity");
				asset = withVersion(converted, from + 1);
				steps.add(new GLNodeGraphMigrationReport.Step(
						GLNodeGraphMigrationReport.Scope.ASSET, asset.id(), from, from + 1));
			} catch (Exception failure) {
				if (failure instanceof GLNodeGraphMigrationException migrationFailure)
					throw migrationFailure;
				throw failed("asset", asset.id(), from, failure);
			}
		}
		return migrateNodes(asset, steps);
	}

	private GLNodeGraphAsset migrateNodes(GLNodeGraphAsset asset,
			List<GLNodeGraphMigrationReport.Step> steps)
			throws GLNodeGraphMigrationException {
		List<GLGraphNode> nodes = new ArrayList<>(asset.graph().nodes().size());
		boolean changed = false;
		for (GLGraphNode original : asset.graph().nodes()) {
			GLGraphNode node = migrateNode(original, steps);
			nodes.add(node); changed |= node != original;
		}
		if (!changed) return asset;
		GLNodeGraphSnapshot graph = new GLNodeGraphSnapshot(asset.graph().graphId(),
				asset.graph().revision(), nodes, asset.graph().connections());
		return new GLNodeGraphAsset(asset.id(), asset.version(), asset.kind(), graph,
				asset.viewState(), asset.metadata(), asset.compositeDescriptor());
	}

	private GLGraphNode migrateNode(GLGraphNode original,
			List<GLNodeGraphMigrationReport.Step> steps)
			throws GLNodeGraphMigrationException {
		GLGraphNode node = original;
		Integer target = nodeVersions.get(node.typeId());
		if (target == null) return node;
		if (node.typeVersion() > target)
			throw newer("node type", node.typeId(), node.typeVersion(), target);
		while (node.typeVersion() < target) {
			String id = node.typeId(); int from = node.typeVersion();
			GLNodeMigration migration = nodeMigrations.get(new Key(id, from));
			if (migration == null) throw missing("node type", id, from);
			try {
				GLGraphNode converted = migration.migrate(node);
				if (converted == null || !converted.id().equals(node.id()))
					throw new IllegalStateException("Node migration changed its instance ID");
				node = new GLGraphNode(converted.id(), converted.typeId(), from + 1,
						converted.properties());
				steps.add(new GLNodeGraphMigrationReport.Step(
						GLNodeGraphMigrationReport.Scope.NODE, id, from, from + 1));
				target = nodeVersions.get(node.typeId());
				if (target == null) return node;
			} catch (Exception failure) {
				throw failed("node type", id, from, failure);
			}
		}
		return node;
	}

	private static GLNodeGraphAsset withVersion(GLNodeGraphAsset asset, int version) {
		return new GLNodeGraphAsset(asset.id(), version, asset.kind(), asset.graph(),
				asset.viewState(), asset.metadata(), asset.compositeDescriptor());
	}
	private static void registerVersion(Map<String, Integer> versions, String id,
			int version, String label) {
		if (!validId(id) || version < 1)
			throw new IllegalArgumentException(label + " ID and version are invalid");
		if (versions.putIfAbsent(id, version) != null)
			throw new IllegalArgumentException(label + " is already registered: " + id);
	}
	private static <M> void add(Map<String, Integer> versions, Map<Key, M> migrations,
			String id, int from, M migration, String label) {
		Integer current = versions.get(id);
		if (current == null || from < 1 || from >= current || migration == null)
			throw new IllegalArgumentException("Invalid " + label + " migration: " + id
					+ " v" + from);
		if (migrations.putIfAbsent(new Key(id, from), migration) != null)
			throw new IllegalArgumentException("Migration already registered: " + id
					+ " v" + from);
	}
	private static boolean validId(String id) {
		return id != null && id.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}
	private static GLNodeGraphMigrationException missing(String scope, String id,
			int version) {
		return new GLNodeGraphMigrationException("Missing " + scope + " migration for "
				+ id + " from version " + version + " to " + (version + 1), id, version,
				com.vingame.glswing.document.GLDocumentMigrationException.Reason.MISSING_STEP);
	}
	private static GLNodeGraphMigrationException newer(String scope, String id,
			int version, int supported) {
		return new GLNodeGraphMigrationException("The " + scope + " " + id + " uses version "
				+ version + ", but this application supports up to " + supported, id, version,
				com.vingame.glswing.document.GLDocumentMigrationException.Reason.NEWER_VERSION);
	}
	private static GLNodeGraphMigrationException failed(String scope, String id,
			int version, Throwable failure) {
		return new GLNodeGraphMigrationException("Cannot migrate " + scope + " " + id
				+ " from version " + version, id, version,
				com.vingame.glswing.document.GLDocumentMigrationException.Reason.CONVERSION_FAILED,
				failure);
	}
}
