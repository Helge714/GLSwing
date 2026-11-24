package com.vingame.glswing.node.document;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.vingame.glswing.document.GLDocumentCodec;
import com.vingame.glswing.document.GLDocumentFormatException;
import com.vingame.glswing.document.GLDocumentReadResult;
import com.vingame.glswing.node.binding.GLNodeEditorViewState;
import com.vingame.glswing.node.binding.GLNodeViewState;
import com.vingame.glswing.node.composite.GLCompositeNodeDescriptor;
import com.vingame.glswing.node.composite.GLCompositePortMapping;
import com.vingame.glswing.node.composite.GLCompositePortPresentation;
import com.vingame.glswing.node.composite.GLCompositePropertyDescriptor;
import com.vingame.glswing.node.composite.GLCompositePropertyMapping;
import com.vingame.glswing.node.composite.GLCompositeValueKind;
import com.vingame.glswing.node.model.GLGraphConnection;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeConnectionId;
import com.vingame.glswing.node.model.GLNodeEndpoint;
import com.vingame.glswing.node.model.GLNodeGraphId;
import com.vingame.glswing.node.model.GLNodeGraphSnapshot;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodeValue;
import com.vingame.glswing.node.model.GLNodePortDescriptor;
import com.vingame.glswing.node.model.GLPortDirection;
import com.vingame.glswing.node.model.GLPortTiming;

/** Deterministic, versioned codec for standalone and project graph assets. */
public final class GLNodeGraphDocumentCodec
		implements GLDocumentCodec<GLNodeGraphDocument> {
	public static final String TYPE_ID = "glswing.node-graph";
	public static final String CODEC_ID = "binary";
	private static final byte[] MAGIC = "GLSWING-NODE-GRAPH\n"
			.getBytes(StandardCharsets.US_ASCII);
	private static final int MAX_COUNT = 1_000_000;
	private static final int MAX_STRING_BYTES = 16 * 1024 * 1024;
	private static final int MAX_VALUE_DEPTH = 64;
	private final GLNodeGraphMigrationRegistry migrations;
	private volatile java.util.function.Consumer<GLNodeGraphMigrationReport> migrationListener =
			ignored -> { };

	public GLNodeGraphDocumentCodec() {
		this(new GLNodeGraphMigrationRegistry());
	}

	public GLNodeGraphDocumentCodec(GLNodeGraphMigrationRegistry migrations) {
		if (migrations == null) throw new IllegalArgumentException("Migration registry required");
		this.migrations = migrations;
	}

	/** Receives the immutable report produced for every successfully decoded document. */
	public void setMigrationListener(
			java.util.function.Consumer<GLNodeGraphMigrationReport> listener) {
		migrationListener = listener == null ? ignored -> { } : listener;
	}

	@Override public String typeId() { return TYPE_ID; }
	@Override public String codecId() { return CODEC_ID; }
	@Override public Class<GLNodeGraphDocument> contentType() {
		return GLNodeGraphDocument.class;
	}
	@Override public int currentSchemaVersion() { return 10; }
	@Override public Set<String> fileExtensions() { return Set.of("glgraph"); }
	@Override public boolean supportsSchemaVersion(int version) {
		return version >= 1 && version <= currentSchemaVersion();
	}

	@Override public GLNodeGraphDocument read(InputStream input, int schemaVersion)
			throws IOException {
		return readResult(input, schemaVersion).content();
	}

	@Override public GLDocumentReadResult<GLNodeGraphDocument> readResult(InputStream input,
			int schemaVersion) throws IOException {
		try {
			DataInputStream data = new DataInputStream(input);
			byte[] magic = data.readNBytes(MAGIC.length);
			if (!java.util.Arrays.equals(magic, MAGIC))
				throw new GLDocumentFormatException("Invalid node-graph signature");
			int storedVersion = data.readInt();
			if (!supportsSchemaVersion(storedVersion))
				throw new GLDocumentFormatException(
						"Unsupported node-graph schema version: " + storedVersion);
			if (schemaVersion != storedVersion && schemaVersion != currentSchemaVersion())
				throw new GLDocumentFormatException("Node-graph schema metadata says "
						+ schemaVersion + " but the file contains " + storedVersion);
			GLNodeGraphAsset root = readAsset(data, storedVersion);
			int embeddedCount = readCount(data, "embedded asset");
			Map<String, GLNodeGraphAsset> embedded = new LinkedHashMap<>();
			for (int index = 0; index < embeddedCount; index++) {
				GLNodeGraphAsset asset = readAsset(data, storedVersion);
				if (embedded.putIfAbsent(asset.id(), asset) != null)
					throw new GLDocumentFormatException(
							"Duplicate embedded asset: " + asset.id());
			}
			int externalCount = readCount(data, "external asset");
			List<GLNodeGraphAssetReference> external =
					new ArrayList<>(externalCount);
			for (int index = 0; index < externalCount; index++)
				external.add(new GLNodeGraphAssetReference(readString(data),
						readString(data), positive(data.readInt(), "asset version"),
						readString(data)));
			GLNodeGraphMigrationReport report = migrations.migrate(
					new GLNodeGraphDocument(root, embedded, external));
			migrationListener.accept(report);
			return new GLDocumentReadResult<>(report.document(), storedVersion,
					storedVersion != currentSchemaVersion() || report.migrated());
		} catch (EOFException failure) {
			throw new GLDocumentFormatException("Truncated node-graph document", failure);
		} catch (IllegalArgumentException failure) {
			throw new GLDocumentFormatException("Invalid node-graph document", failure);
		}
	}

	@Override public void write(GLNodeGraphDocument content, OutputStream output)
			throws IOException {
		if (content == null || output == null)
			throw new IllegalArgumentException("Content and output required");
		DataOutputStream data = new DataOutputStream(output);
		data.write(MAGIC);
		data.writeInt(currentSchemaVersion());
		writeAsset(data, content.root());
		writeCount(data, content.embeddedAssets().size(), "embedded asset");
		for (GLNodeGraphAsset asset : content.embeddedAssets().values())
			writeAsset(data, asset);
		writeCount(data, content.externalAssets().size(), "external asset");
		for (GLNodeGraphAssetReference reference : content.externalAssets()) {
			writeString(data, reference.alias());
			writeString(data, reference.assetId());
			data.writeInt(reference.minimumVersion());
			writeString(data, reference.location());
		}
		data.flush();
	}

	private static void writeAsset(DataOutputStream output,
			GLNodeGraphAsset asset) throws IOException {
		writeString(output, asset.id());
		output.writeInt(asset.version());
		output.writeByte(asset.kind().ordinal());
		writeGraph(output, asset.graph());
		writeViewState(output, asset.viewState());
		writeCount(output, asset.metadata().size(), "metadata");
		for (Map.Entry<String, GLNodeValue> entry : asset.metadata().entrySet()) {
			writeString(output, entry.getKey());
			writeValue(output, entry.getValue(), 0);
		}
		output.writeBoolean(asset.compositeDescriptor() != null);
		if (asset.compositeDescriptor() != null)
			writeComposite(output, asset.compositeDescriptor());
	}

	private static GLNodeGraphAsset readAsset(DataInputStream input,
			int schemaVersion)
			throws IOException {
		String id = readString(input);
		int version = positive(input.readInt(), "asset version");
		int kind = input.readUnsignedByte();
		if (kind >= GLNodeGraphAssetKind.values().length)
			throw new GLDocumentFormatException("Unknown graph-asset kind: " + kind);
		GLNodeGraphSnapshot graph = readGraph(input, schemaVersion >= 2);
		GLNodeEditorViewState view = readViewState(input, schemaVersion);
		int metadataCount = readCount(input, "metadata");
		Map<String, GLNodeValue> metadata = new LinkedHashMap<>();
		for (int index = 0; index < metadataCount; index++) {
			String key = readString(input);
			if (metadata.putIfAbsent(key, readValue(input, 0)) != null)
				throw new GLDocumentFormatException("Duplicate metadata key: " + key);
		}
		GLCompositeNodeDescriptor composite = schemaVersion >= 3 && input.readBoolean()
				? readComposite(input, schemaVersion) : null;
		return new GLNodeGraphAsset(id, version,
				GLNodeGraphAssetKind.values()[kind], graph, view, metadata, composite);
	}

	private static void writeComposite(DataOutputStream output,
			GLCompositeNodeDescriptor descriptor) throws IOException {
		writeString(output, descriptor.nodeTypeId());
		writeString(output, descriptor.assetId());
		output.writeInt(descriptor.minimumAssetVersion());
		writeCount(output, descriptor.portMappings().size(), "composite port");
		for (GLCompositePortMapping mapping : descriptor.portMappings()) {
			GLNodePortDescriptor port = mapping.publicPort();
			writeString(output, port.id()); output.writeByte(port.direction().ordinal());
			writeString(output, port.valueTypeId()); output.writeByte(port.timing().ordinal());
			output.writeBoolean(port.required()); output.writeInt(port.maximumConnections());
			output.writeBoolean(mapping.isBound());
			if (mapping.isBound()) writeEndpoint(output, mapping.internalEndpoint());
            writeCount(output, mapping.additionalEndpoints().size(), "additional port target");
            for (var endpoint : mapping.additionalEndpoints()) writeEndpoint(output, endpoint);
			writeString(output, mapping.presentation().inputEditorId());
			output.writeBoolean(mapping.presentation().socketVisible());
			writeCount(output, mapping.presentation().editorOptions().size(),
					"input editor option");
			for (Map.Entry<String, GLNodeValue> option
					: mapping.presentation().editorOptions().entrySet()) {
				writeString(output, option.getKey());
				writeValue(output, option.getValue(), 0);
			}
		}
		writeCount(output, descriptor.properties().size(), "composite property");
		for (GLCompositePropertyDescriptor property : descriptor.properties()) {
			writeString(output, property.id()); writeString(output, property.valueTypeId());
			output.writeByte(property.valueKind().ordinal());
			writeValue(output, property.defaultValue(), 0);
			output.writeBoolean(property.minimum() != null);
			if (property.minimum() != null) output.writeDouble(property.minimum());
			output.writeBoolean(property.maximum() != null);
			if (property.maximum() != null) output.writeDouble(property.maximum());
		}
		writeCount(output, descriptor.propertyMappings().size(),
				"composite property mapping");
		for (GLCompositePropertyMapping mapping : descriptor.propertyMappings()) {
			writeString(output, mapping.publicPropertyId());
			writeUuid(output, mapping.internalNodeId().value());
			writeString(output, mapping.internalPropertyId());
            writeCount(output,mapping.values().size(),"property choice mapping");
            for(var entry:new java.util.TreeMap<>(mapping.values()).entrySet()){writeString(output,entry.getKey());writeValue(output,entry.getValue(),0);}
		}
	}

	private static GLCompositeNodeDescriptor readComposite(DataInputStream input,
			int schemaVersion)
			throws IOException {
		String typeId = readString(input); String assetId = readString(input);
		int minimumVersion = positive(input.readInt(), "minimum asset version");
		int portCount = readCount(input, "composite port");
		List<GLCompositePortMapping> ports = new ArrayList<>(portCount);
		for (int index = 0; index < portCount; index++) {
			String id = readString(input);
			GLPortDirection direction = enumValue(GLPortDirection.values(),
					input.readUnsignedByte(), "port direction");
			String valueType = readString(input);
			GLPortTiming timing = enumValue(GLPortTiming.values(),
					input.readUnsignedByte(), "port timing");
			boolean required = input.readBoolean();
			int maximumConnections = positive(input.readInt(), "connection limit");
			GLNodeEndpoint endpoint = schemaVersion >= 5
					? (input.readBoolean() ? readEndpoint(input) : null)
					: readEndpoint(input);
			List<GLNodeEndpoint> additional = new ArrayList<>();
            if (schemaVersion >= 7) { int count=readCount(input,"additional port target"); for(int n=0;n<count;n++) additional.add(readEndpoint(input)); }
            GLCompositePortPresentation presentation =
					GLCompositePortPresentation.socketOnly();
			if (schemaVersion >= 4) {
				String editorId = readString(input);
				boolean socketVisible = input.readBoolean();
				int optionCount = readCount(input, "input editor option");
				Map<String, GLNodeValue> options = new LinkedHashMap<>();
				for (int option = 0; option < optionCount; option++) {
					String key = readString(input);
					if (options.putIfAbsent(key, readValue(input, 0)) != null)
						throw new GLDocumentFormatException(
								"Duplicate input editor option: " + key);
				}
				presentation = new GLCompositePortPresentation(editorId,
						socketVisible, options);
			}
			ports.add(new GLCompositePortMapping(new GLNodePortDescriptor(id, direction,
					valueType, timing, required, maximumConnections), endpoint,
					presentation, additional));
		}
		int propertyCount = readCount(input, "composite property");
		List<GLCompositePropertyDescriptor> properties = new ArrayList<>(propertyCount);
		for (int index = 0; index < propertyCount; index++) {
			String id = readString(input); String valueType = readString(input);
			GLCompositeValueKind kind = enumValue(GLCompositeValueKind.values(),
					input.readUnsignedByte(), "property kind");
			GLNodeValue defaultValue = readValue(input, 0);
			Double minimum = input.readBoolean() ? input.readDouble() : null;
			Double maximum = input.readBoolean() ? input.readDouble() : null;
			properties.add(new GLCompositePropertyDescriptor(id, valueType, kind,
					defaultValue, minimum, maximum));
		}
		int mappingCount = readCount(input, "composite property mapping");
		List<GLCompositePropertyMapping> mappings = new ArrayList<>(mappingCount);
		for (int index = 0; index < mappingCount; index++){
            String property=readString(input);var node=new GLNodeId(readUuid(input));String target=readString(input);
            Map<String,GLNodeValue> values=new LinkedHashMap<>();
            if(schemaVersion>=8){int count=readCount(input,"property choice mapping");for(int n=0;n<count;n++){String key=readString(input);if(values.putIfAbsent(key,readValue(input,0))!=null)throw new GLDocumentFormatException("Duplicate choice mapping");}}
            mappings.add(new GLCompositePropertyMapping(property,node,target,values));
        }
		return new GLCompositeNodeDescriptor(typeId, assetId, minimumVersion,
				ports, properties, mappings);
	}

	private static <E> E enumValue(E[] values, int ordinal, String name)
			throws GLDocumentFormatException {
		if (ordinal < 0 || ordinal >= values.length)
			throw new GLDocumentFormatException("Unknown " + name + ": " + ordinal);
		return values[ordinal];
	}

	private static void writeGraph(DataOutputStream output,
			GLNodeGraphSnapshot graph) throws IOException {
		writeUuid(output, graph.graphId().value());
		output.writeLong(graph.revision());
		writeCount(output, graph.nodes().size(), "node");
		for (GLGraphNode node : graph.nodes()) {
			writeUuid(output, node.id().value());
			writeString(output, node.typeId());
			output.writeInt(node.typeVersion());
			writeCount(output, node.properties().size(), "node property");
			for (Map.Entry<String, GLNodeValue> property : node.properties().entrySet()) {
				writeString(output, property.getKey());
				writeValue(output, property.getValue(), 0);
			}
		}
		writeCount(output, graph.connections().size(), "connection");
		for (GLGraphConnection connection : graph.connections()) {
			writeUuid(output, connection.id().value());
			writeEndpoint(output, connection.output());
			writeEndpoint(output, connection.input());
		}
	}

	private static GLNodeGraphSnapshot readGraph(DataInputStream input,
			boolean versionedNodes)
			throws IOException {
		GLNodeGraphId graphId = new GLNodeGraphId(readUuid(input));
		long revision = input.readLong();
		if (revision < 0)
			throw new GLDocumentFormatException("Negative graph revision");
		int nodeCount = readCount(input, "node");
		List<GLGraphNode> nodes = new ArrayList<>(nodeCount);
		for (int index = 0; index < nodeCount; index++) {
			GLNodeId id = new GLNodeId(readUuid(input));
			String type = readString(input);
			int typeVersion = versionedNodes
					? positive(input.readInt(), "node type version") : 1;
			int propertyCount = readCount(input, "node property");
			Map<String, GLNodeValue> properties = new LinkedHashMap<>();
			for (int property = 0; property < propertyCount; property++) {
				String key = readString(input);
				if (properties.putIfAbsent(key, readValue(input, 0)) != null)
					throw new GLDocumentFormatException(
							"Duplicate node property: " + key);
			}
			nodes.add(new GLGraphNode(id, type, typeVersion, properties));
		}
		int connectionCount = readCount(input, "connection");
		List<GLGraphConnection> connections = new ArrayList<>(connectionCount);
		for (int index = 0; index < connectionCount; index++)
			connections.add(new GLGraphConnection(
					new GLNodeConnectionId(readUuid(input)), readEndpoint(input),
					readEndpoint(input)));
		return new GLNodeGraphSnapshot(graphId, revision, nodes, connections);
	}

	private static void writeViewState(DataOutputStream output,
			GLNodeEditorViewState state) throws IOException {
		writeCount(output, state.nodes().size(), "node view");
		for (Map.Entry<GLNodeId, GLNodeViewState> entry : state.nodes().entrySet()) {
			writeUuid(output, entry.getKey().value());
			GLNodeViewState value = entry.getValue();
			output.writeFloat(value.x()); output.writeFloat(value.y());
			output.writeFloat(value.width()); output.writeFloat(value.height());
            output.writeBoolean(value.customTitle()!=null);
            if(value.customTitle()!=null)writeString(output,value.customTitle());
		}
		writeCount(output, state.auxiliaryNodes().size(), "auxiliary node view");
		for (Map.Entry<String, GLNodeViewState> entry
				: state.auxiliaryNodes().entrySet()) {
			writeString(output, entry.getKey());
			GLNodeViewState value = entry.getValue();
			output.writeFloat(value.x()); output.writeFloat(value.y());
			output.writeFloat(value.width()); output.writeFloat(value.height());
            output.writeBoolean(value.customTitle()!=null);
            if(value.customTitle()!=null)writeString(output,value.customTitle());
		}
        output.writeBoolean(state.camera()!=null);
        if(state.camera()!=null){output.writeFloat(state.camera().x());output.writeFloat(state.camera().y());}
	}

	private static GLNodeEditorViewState readViewState(DataInputStream input,
			int schemaVersion)
			throws IOException {
		int count = readCount(input, "node view");
		Map<GLNodeId, GLNodeViewState> values = new LinkedHashMap<>();
		for (int index = 0; index < count; index++) {
			GLNodeId id = new GLNodeId(readUuid(input));
			GLNodeViewState state = new GLNodeViewState(input.readFloat(),
					input.readFloat(), input.readFloat(), input.readFloat(),
                    schemaVersion>=9&&input.readBoolean()?readString(input):null);
			if (values.putIfAbsent(id, state) != null)
				throw new GLDocumentFormatException("Duplicate node view: " + id);
		}
		Map<String, GLNodeViewState> auxiliary = new LinkedHashMap<>();
		if (schemaVersion >= 6) {
			int auxiliaryCount = readCount(input, "auxiliary node view");
			for (int index = 0; index < auxiliaryCount; index++) {
				String id = readString(input);
				GLNodeViewState state = new GLNodeViewState(input.readFloat(),
						input.readFloat(), input.readFloat(), input.readFloat(),
                    schemaVersion>=9&&input.readBoolean()?readString(input):null);
				if (auxiliary.putIfAbsent(id, state) != null)
					throw new GLDocumentFormatException(
							"Duplicate auxiliary node view: " + id);
			}
		}
		return new GLNodeEditorViewState(values, auxiliary,schemaVersion>=10&&input.readBoolean()?new com.vingame.glswing.node.binding.GLNodeCameraState(input.readFloat(),input.readFloat()):null);
	}

	private static void writeEndpoint(DataOutputStream output,
			GLNodeEndpoint endpoint) throws IOException {
		writeUuid(output, endpoint.nodeId().value());
		writeString(output, endpoint.portId());
	}

	private static GLNodeEndpoint readEndpoint(DataInputStream input)
			throws IOException {
		return new GLNodeEndpoint(new GLNodeId(readUuid(input)), readString(input));
	}

	private static void writeValue(DataOutputStream output, GLNodeValue value,
			int depth) throws IOException {
		checkDepth(depth);
		if (value == GLNodeValue.NullValue.INSTANCE) output.writeByte(0);
		else if (value instanceof GLNodeValue.BooleanValue item) {
			output.writeByte(1); output.writeBoolean(item.value());
		} else if (value instanceof GLNodeValue.IntegerValue item) {
			output.writeByte(2); output.writeLong(item.value());
		} else if (value instanceof GLNodeValue.DecimalValue item) {
			output.writeByte(3); output.writeDouble(item.value());
		} else if (value instanceof GLNodeValue.TextValue item) {
			output.writeByte(4); writeString(output, item.value());
		} else if (value instanceof GLNodeValue.ListValue item) {
			output.writeByte(5); writeCount(output, item.values().size(), "list value");
			for (GLNodeValue child : item.values()) writeValue(output, child, depth + 1);
		} else {
			GLNodeValue.MapValue item = (GLNodeValue.MapValue) value;
			output.writeByte(6); writeCount(output, item.values().size(), "map value");
			for (Map.Entry<String, GLNodeValue> entry : item.values().entrySet()) {
				writeString(output, entry.getKey());
				writeValue(output, entry.getValue(), depth + 1);
			}
		}
	}

	private static GLNodeValue readValue(DataInputStream input, int depth)
			throws IOException {
		checkDepth(depth);
		return switch (input.readUnsignedByte()) {
			case 0 -> GLNodeValue.NullValue.INSTANCE;
			case 1 -> new GLNodeValue.BooleanValue(input.readBoolean());
			case 2 -> new GLNodeValue.IntegerValue(input.readLong());
			case 3 -> new GLNodeValue.DecimalValue(input.readDouble());
			case 4 -> new GLNodeValue.TextValue(readString(input));
			case 5 -> {
				int count = readCount(input, "list value");
				List<GLNodeValue> values = new ArrayList<>(count);
				for (int index = 0; index < count; index++)
					values.add(readValue(input, depth + 1));
				yield new GLNodeValue.ListValue(values);
			}
			case 6 -> {
				int count = readCount(input, "map value");
				Map<String, GLNodeValue> values = new LinkedHashMap<>();
				for (int index = 0; index < count; index++) {
					String key = readString(input);
					if (values.putIfAbsent(key, readValue(input, depth + 1)) != null)
						throw new GLDocumentFormatException("Duplicate map key: " + key);
				}
				yield new GLNodeValue.MapValue(values);
			}
			default -> throw new GLDocumentFormatException("Unknown graph value tag");
		};
	}

	private static void writeString(DataOutputStream output, String value)
			throws IOException {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		if (bytes.length > MAX_STRING_BYTES)
			throw new IOException("Node-graph string exceeds " + MAX_STRING_BYTES + " bytes");
		output.writeInt(bytes.length); output.write(bytes);
	}

	private static String readString(DataInputStream input) throws IOException {
		int length = input.readInt();
		if (length < 0 || length > MAX_STRING_BYTES)
			throw new GLDocumentFormatException("Invalid node-graph string length");
		byte[] bytes = input.readNBytes(length);
		if (bytes.length != length) throw new EOFException();
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static void writeUuid(DataOutputStream output, UUID value)
			throws IOException {
		output.writeLong(value.getMostSignificantBits());
		output.writeLong(value.getLeastSignificantBits());
	}

	private static UUID readUuid(DataInputStream input) throws IOException {
		return new UUID(input.readLong(), input.readLong());
	}

	private static void writeCount(DataOutputStream output, int count, String name)
			throws IOException {
		if (count < 0 || count > MAX_COUNT)
			throw new IOException("Too many " + name + " entries: " + count);
		output.writeInt(count);
	}

	private static int readCount(DataInputStream input, String name)
			throws IOException {
		int count = input.readInt();
		if (count < 0 || count > MAX_COUNT)
			throw new GLDocumentFormatException("Invalid " + name + " count");
		return count;
	}

	private static int positive(int value, String name)
			throws GLDocumentFormatException {
		if (value < 1) throw new GLDocumentFormatException("Invalid " + name);
		return value;
	}

	private static void checkDepth(int depth) throws IOException {
		if (depth > MAX_VALUE_DEPTH)
			throw new GLDocumentFormatException("Graph value nesting is too deep");
	}
}
