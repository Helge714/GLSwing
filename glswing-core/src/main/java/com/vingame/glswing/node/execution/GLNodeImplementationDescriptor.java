package com.vingame.glswing.node.execution;

/**
 * Backend-neutral link between a graph node type and an executable operation.
 * The operation type is interpreted by the selected backend.
 */
public record GLNodeImplementationDescriptor(String nodeTypeId,
		String operationTypeId, GLNodeImplementationKind kind,
		boolean observable, long estimatedCost, long fixedLatency) {
	public GLNodeImplementationDescriptor {
		if (!validId(nodeTypeId) || !validId(operationTypeId) || kind == null
				|| estimatedCost < 0 || fixedLatency < 0)
			throw new IllegalArgumentException("Invalid node implementation descriptor");
		if (kind == GLNodeImplementationKind.EFFECT) observable = true;
	}

	public GLNodeImplementationDescriptor(String nodeTypeId, String operationTypeId,
			GLNodeImplementationKind kind, boolean observable, long estimatedCost) {
		this(nodeTypeId, operationTypeId, kind, observable, estimatedCost, 0);
	}

	public GLNodeImplementationDescriptor(String nodeTypeId, String operationTypeId,
			GLNodeImplementationKind kind) {
		this(nodeTypeId, operationTypeId, kind,
				kind == GLNodeImplementationKind.EFFECT, 1, 0);
	}

	private static boolean validId(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}
}
