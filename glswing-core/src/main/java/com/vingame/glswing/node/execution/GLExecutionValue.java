package com.vingame.glswing.node.execution;

import java.util.Optional;

import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodeValue;

/** One typed output value and its compiler-assigned storage location. */
public record GLExecutionValue(GLExecutionValueRef ref, String valueTypeId,
		GLExecutionStorage storage, int storageSlot, Optional<GLNodeValue> constant,
		GLNodeId producerNodeId, String producerPortId, long latency) {
	public GLExecutionValue {
		if (ref == null || !valid(valueTypeId) || storage == null || constant == null
				|| producerNodeId == null || !valid(producerPortId) || latency < 0)
			throw new IllegalArgumentException("Invalid execution value");
		if (storage == GLExecutionStorage.CONSTANT) {
			if (storageSlot != -1 || constant.isEmpty())
				throw new IllegalArgumentException("A constant requires a value and no slot");
		} else if (storageSlot < 0 || constant.isPresent()) {
			throw new IllegalArgumentException("Runtime values require a storage slot");
		}
	}
	public GLExecutionValue(GLExecutionValueRef ref, String valueTypeId,
			GLExecutionStorage storage, int storageSlot,
			Optional<GLNodeValue> constant, GLNodeId producerNodeId,
			String producerPortId) {
		this(ref, valueTypeId, storage, storageSlot, constant, producerNodeId,
				producerPortId, 0);
	}
	private static boolean valid(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}
}
