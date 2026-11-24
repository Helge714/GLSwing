package com.vingame.glswing.node.execution;

import com.vingame.glswing.node.model.GLPortTiming;

/** One connected operation input and the step from which it reads. */
public record GLExecutionInput(String portId, GLExecutionValueRef value,
		GLPortTiming timing, long compensationLatency) {
	public GLExecutionInput {
		if (portId == null || portId.isBlank() || value == null || timing == null
				|| compensationLatency < 0)
			throw new IllegalArgumentException("Invalid execution input");
		if (timing == GLPortTiming.PREVIOUS_STEP && compensationLatency != 0)
			throw new IllegalArgumentException(
					"Previous-step inputs cannot use current-step compensation");
	}
	public GLExecutionInput(String portId, GLExecutionValueRef value,
			GLPortTiming timing) {
		this(portId, value, timing, 0);
	}
}
