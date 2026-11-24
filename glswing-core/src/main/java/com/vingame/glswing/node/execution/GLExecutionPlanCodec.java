package com.vingame.glswing.node.execution;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.vingame.glswing.node.composite.GLCompositeOrigin;
import com.vingame.glswing.node.model.GLNodeGraphId;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodeValue;
import com.vingame.glswing.node.model.GLPortTiming;

/** Versioned, checksummed binary exchange format for immutable execution plans. */
public final class GLExecutionPlanCodec {
	private static final int MAGIC = 0x474c5850; // GLXP
	private static final int MAX_BODY_BYTES = 256 * 1024 * 1024;
	private static final int MAX_ELEMENTS = 1_000_000;
	private static final int MAX_STRING_BYTES = 16 * 1024 * 1024;
	private static final int MAX_VALUE_DEPTH = 256;

	private GLExecutionPlanCodec() { }

	public static byte[] encode(GLExecutionPlan plan) throws IOException {
		if (plan == null) throw new IllegalArgumentException("Plan required");
		ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
		try (DataOutputStream body = new DataOutputStream(bodyBytes)) {
			writeBody(body, plan);
		}
		byte[] payload = bodyBytes.toByteArray();
		if (payload.length > MAX_BODY_BYTES)
			throw new IOException("Execution plan exceeds the exchange size limit");
		ByteArrayOutputStream encoded = new ByteArrayOutputStream(
				Integer.BYTES + payload.length + 32);
		try (DataOutputStream output = new DataOutputStream(encoded)) {
			output.writeInt(payload.length);
			output.write(payload);
			output.write(digest(payload));
		}
		return encoded.toByteArray();
	}

	public static GLExecutionPlan decode(byte[] encoded) throws IOException {
		if (encoded == null) throw new IllegalArgumentException("Encoded plan required");
		return read(new ByteArrayInputStream(encoded));
	}

	public static void write(GLExecutionPlan plan, OutputStream target)
			throws IOException {
		if (target == null) throw new IllegalArgumentException("Target required");
		target.write(encode(plan));
	}

	public static GLExecutionPlan read(InputStream source) throws IOException {
		if (source == null) throw new IllegalArgumentException("Source required");
		DataInputStream input = new DataInputStream(source);
		int size = input.readInt();
		if (size < 0 || size > MAX_BODY_BYTES)
			throw new IOException("Invalid execution-plan payload size: " + size);
		byte[] body = input.readNBytes(size);
		if (body.length != size) throw new IOException("Truncated execution-plan payload");
		byte[] expected = input.readNBytes(32);
		if (expected.length != 32 || !MessageDigest.isEqual(expected, digest(body)))
			throw new IOException("Execution-plan checksum does not match");
		try (DataInputStream payload = new DataInputStream(new ByteArrayInputStream(body))) {
			GLExecutionPlan plan = readBody(payload);
			if (payload.available() != 0)
				throw new IOException("Unexpected data after execution plan");
			return plan;
		} catch (IllegalArgumentException failure) {
			throw new IOException("Invalid execution-plan data", failure);
		}
	}

	private static void writeBody(DataOutputStream output, GLExecutionPlan plan)
			throws IOException {
		output.writeInt(MAGIC);
		output.writeInt(GLExecutionPlan.FORMAT_VERSION);
		writeUuid(output, plan.sourceGraphId().value());
		output.writeLong(plan.sourceRevision());
		writeString(output, plan.latencyUnitId());
		writeString(output, plan.hash());
		GLExecutionPlanStatistics statistics = plan.statistics();
		output.writeInt(statistics.sourceNodes());
		output.writeInt(statistics.retainedNodes());
		output.writeInt(statistics.foldedOperations());
		output.writeInt(statistics.scheduledOperations());
		output.writeLong(statistics.estimatedOperationCost());
		output.writeInt(statistics.temporarySlots());
		output.writeInt(statistics.stateSlots());
		output.writeLong(statistics.maximumLatency());

		output.writeInt(plan.values().size());
		for (GLExecutionValue value : plan.values()) {
			output.writeInt(value.ref().index());
			writeString(output, value.valueTypeId());
			output.writeByte(value.storage().ordinal());
			output.writeInt(value.storageSlot());
			output.writeBoolean(value.constant().isPresent());
			if (value.constant().isPresent()) writeValue(output, value.constant().get());
			writeUuid(output, value.producerNodeId().value());
			writeString(output, value.producerPortId());
			output.writeLong(value.latency());
		}

		output.writeInt(plan.operations().size());
		for (GLExecutionOperation operation : plan.operations()) {
			output.writeInt(operation.index());
			writeUuid(output, operation.nodeId().value());
			writeString(output, operation.nodeTypeId());
			writeString(output, operation.operationTypeId());
			output.writeByte(operation.kind().ordinal());
			writeValueMap(output, operation.properties());
			output.writeInt(operation.inputs().size());
			for (GLExecutionInput input : operation.inputs()) {
				writeString(output, input.portId());
				output.writeInt(input.value().index());
				output.writeByte(input.timing().ordinal());
				output.writeLong(input.compensationLatency());
			}
			output.writeInt(operation.outputs().size());
			for (Map.Entry<String, GLExecutionValueRef> entry
					: operation.outputs().entrySet()) {
				writeString(output, entry.getKey());
				output.writeInt(entry.getValue().index());
			}
			output.writeLong(operation.estimatedCost());
			output.writeLong(operation.processingLatency());
			output.writeLong(operation.outputLatency());
			output.writeBoolean(operation.origin().isPresent());
			if (operation.origin().isPresent()) writeOrigin(output, operation.origin().get());
		}
	}

	private static GLExecutionPlan readBody(DataInputStream input) throws IOException {
		if (input.readInt() != MAGIC) throw new IOException("Not an execution plan");
		int version = input.readInt();
		if (version != GLExecutionPlan.FORMAT_VERSION)
			throw new IOException("Unsupported execution-plan version: " + version);
		GLNodeGraphId graphId = new GLNodeGraphId(readUuid(input));
		long revision = input.readLong();
		String latencyUnit = readString(input);
		String hash = readString(input);
		GLExecutionPlanStatistics statistics = new GLExecutionPlanStatistics(
				input.readInt(), input.readInt(), input.readInt(), input.readInt(),
				input.readLong(), input.readInt(), input.readInt(), input.readLong());

		int valueCount = readCount(input, "value");
		List<GLExecutionValue> values = new ArrayList<>(valueCount);
		for (int index = 0; index < valueCount; index++) {
			GLExecutionValueRef ref = new GLExecutionValueRef(input.readInt());
			String valueType = readString(input);
			GLExecutionStorage storage = readEnum(input, GLExecutionStorage.values(),
					"storage");
			int slot = input.readInt();
			Optional<GLNodeValue> constant = input.readBoolean()
					? Optional.of(readValue(input)) : Optional.empty();
			GLNodeId producer = new GLNodeId(readUuid(input));
			String port = readString(input);
			long latency = input.readLong();
			values.add(new GLExecutionValue(ref, valueType, storage, slot, constant,
					producer, port, latency));
		}

		int operationCount = readCount(input, "operation");
		List<GLExecutionOperation> operations = new ArrayList<>(operationCount);
		for (int index = 0; index < operationCount; index++) {
			int operationIndex = input.readInt();
			GLNodeId nodeId = new GLNodeId(readUuid(input));
			String nodeType = readString(input);
			String operationType = readString(input);
			GLNodeImplementationKind kind = readEnum(input,
					GLNodeImplementationKind.values(), "implementation kind");
			Map<String, GLNodeValue> properties = readValueMap(input);
			int inputCount = readCount(input, "input");
			List<GLExecutionInput> inputs = new ArrayList<>(inputCount);
			for (int item = 0; item < inputCount; item++)
				inputs.add(new GLExecutionInput(readString(input),
						new GLExecutionValueRef(input.readInt()),
						readEnum(input, GLPortTiming.values(), "port timing"),
						input.readLong()));
			int outputCount = readCount(input, "output");
			Map<String, GLExecutionValueRef> outputs = new LinkedHashMap<>();
			for (int item = 0; item < outputCount; item++)
				if (outputs.put(readString(input),
						new GLExecutionValueRef(input.readInt())) != null)
					throw new IOException("Duplicate operation output");
			long cost = input.readLong();
			long processingLatency = input.readLong();
			long outputLatency = input.readLong();
			Optional<GLCompositeOrigin> origin = input.readBoolean()
					? Optional.of(readOrigin(input)) : Optional.empty();
			operations.add(new GLExecutionOperation(operationIndex, nodeId, nodeType,
					operationType, kind, properties, inputs, outputs, cost,
					processingLatency, outputLatency, origin));
		}
		return new GLExecutionPlan(graphId, revision, operations, values, latencyUnit,
				hash, statistics);
	}

	private static void writeOrigin(DataOutputStream output, GLCompositeOrigin origin)
			throws IOException {
		output.writeInt(origin.instances().size());
		for (GLCompositeOrigin.Instance instance : origin.instances()) {
			writeUuid(output, instance.nodeId().value());
			writeString(output, instance.nodeTypeId());
			writeString(output, instance.assetId());
		}
		writeString(output, origin.sourceAssetId());
		writeUuid(output, origin.sourceNodeId().value());
	}

	private static GLCompositeOrigin readOrigin(DataInputStream input) throws IOException {
		int count = readCount(input, "origin instance");
		List<GLCompositeOrigin.Instance> instances = new ArrayList<>(count);
		for (int index = 0; index < count; index++)
			instances.add(new GLCompositeOrigin.Instance(new GLNodeId(readUuid(input)),
					readString(input), readString(input)));
		return new GLCompositeOrigin(instances, readString(input),
				new GLNodeId(readUuid(input)));
	}

	private static void writeValueMap(DataOutputStream output,
			Map<String, GLNodeValue> values) throws IOException {
		writeValueMap(output, values, 0);
	}
	private static void writeValueMap(DataOutputStream output,
			Map<String, GLNodeValue> values, int depth) throws IOException {
		checkDepth(depth);
		output.writeInt(values.size());
		for (Map.Entry<String, GLNodeValue> entry : values.entrySet()) {
			writeString(output, entry.getKey());
			writeValue(output, entry.getValue(), depth + 1);
		}
	}

	private static Map<String, GLNodeValue> readValueMap(DataInputStream input)
			throws IOException {
		return readValueMap(input, 0);
	}
	private static Map<String, GLNodeValue> readValueMap(DataInputStream input,
			int depth) throws IOException {
		checkDepth(depth);
		int count = readCount(input, "map entry");
		Map<String, GLNodeValue> result = new LinkedHashMap<>();
		for (int index = 0; index < count; index++)
			if (result.put(readString(input), readValue(input, depth + 1)) != null)
				throw new IOException("Duplicate map key");
		return result;
	}

	private static void writeValue(DataOutputStream output, GLNodeValue value)
			throws IOException {
		writeValue(output, value, 0);
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
			output.writeByte(5); output.writeInt(item.values().size());
			for (GLNodeValue child : item.values()) writeValue(output, child, depth + 1);
		} else {
			output.writeByte(6);
			writeValueMap(output, ((GLNodeValue.MapValue) value).values(), depth + 1);
		}
	}

	private static GLNodeValue readValue(DataInputStream input) throws IOException {
		return readValue(input, 0);
	}
	private static GLNodeValue readValue(DataInputStream input, int depth)
			throws IOException {
		checkDepth(depth);
		return switch (input.readUnsignedByte()) {
		case 0 -> GLNodeValue.NullValue.INSTANCE;
		case 1 -> GLNodeValue.of(input.readBoolean());
		case 2 -> GLNodeValue.of(input.readLong());
		case 3 -> GLNodeValue.of(input.readDouble());
		case 4 -> GLNodeValue.of(readString(input));
		case 5 -> {
			int count = readCount(input, "list value");
			List<GLNodeValue> values = new ArrayList<>(count);
			for (int index = 0; index < count; index++)
				values.add(readValue(input, depth + 1));
			yield new GLNodeValue.ListValue(values);
		}
		case 6 -> new GLNodeValue.MapValue(readValueMap(input, depth + 1));
		default -> throw new IOException("Unknown node-value tag");
		};
	}

	private static void writeString(DataOutputStream output, String value)
			throws IOException {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		if (bytes.length > MAX_STRING_BYTES) throw new IOException("String is too large");
		output.writeInt(bytes.length);
		output.write(bytes);
	}
	private static String readString(DataInputStream input) throws IOException {
		int size = input.readInt();
		if (size < 0 || size > MAX_STRING_BYTES) throw new IOException("Invalid string size");
		byte[] bytes = input.readNBytes(size);
		if (bytes.length != size) throw new IOException("Truncated string");
		return new String(bytes, StandardCharsets.UTF_8);
	}
	private static int readCount(DataInputStream input, String subject) throws IOException {
		int count = input.readInt();
		if (count < 0 || count > MAX_ELEMENTS)
			throw new IOException("Invalid " + subject + " count: " + count);
		return count;
	}
	private static void checkDepth(int depth) throws IOException {
		if (depth > MAX_VALUE_DEPTH)
			throw new IOException("Node value exceeds the nesting limit");
	}
	private static <E> E readEnum(DataInputStream input, E[] values, String subject)
			throws IOException {
		int ordinal = input.readUnsignedByte();
		if (ordinal >= values.length) throw new IOException("Unknown " + subject);
		return values[ordinal];
	}
	private static void writeUuid(DataOutputStream output, UUID value)
			throws IOException {
		output.writeLong(value.getMostSignificantBits());
		output.writeLong(value.getLeastSignificantBits());
	}
	private static UUID readUuid(DataInputStream input) throws IOException {
		return new UUID(input.readLong(), input.readLong());
	}
	private static byte[] digest(byte[] bytes) {
		try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}
}
