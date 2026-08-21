package com.vingame.glswing.node;

/** Input/output pair returned by a convenience input-output row. */
public record GLNodePorts(GLNodePort input, GLNodePort output) {
	public GLNodePorts {
		if (input == null || output == null)
			throw new IllegalArgumentException("Node ports must not be null");
		if (input.getDirection() != GLNodePort.Direction.INPUT
				|| output.getDirection() != GLNodePort.Direction.OUTPUT)
			throw new IllegalArgumentException(
					"Node port pair requires one input and one output");
	}
}
