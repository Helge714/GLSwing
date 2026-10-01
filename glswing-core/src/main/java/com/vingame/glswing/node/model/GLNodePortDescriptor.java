package com.vingame.glswing.node.model;

/** Backend-neutral contract of one registered node port. */
public record GLNodePortDescriptor(String id, GLPortDirection direction,
		String valueTypeId, GLPortTiming timing, boolean required,
		int maximumConnections) {
	public GLNodePortDescriptor {
		if (!validPortId(id) || direction == null || !validId(valueTypeId)
				|| timing == null || maximumConnections < 1)
			throw new IllegalArgumentException("Invalid node-port descriptor");
		if (direction == GLPortDirection.OUTPUT && required)
			throw new IllegalArgumentException("Only input ports can be required");
	}

	public static GLNodePortDescriptor input(String id, String valueTypeId,
			boolean required, int maximumConnections) {
		return new GLNodePortDescriptor(id, GLPortDirection.INPUT, valueTypeId,
				GLPortTiming.CURRENT, required, maximumConnections);
	}

	public static GLNodePortDescriptor output(String id, String valueTypeId) {
		return output(id, valueTypeId, GLPortTiming.CURRENT,
				Integer.MAX_VALUE);
	}

	public static GLNodePortDescriptor output(String id, String valueTypeId,
			GLPortTiming timing, int maximumConnections) {
		return new GLNodePortDescriptor(id, GLPortDirection.OUTPUT, valueTypeId,
				timing, false, maximumConnections);
	}

    private static boolean validPortId(String value) {
        return value != null && value.equals(value.strip()) && value.matches("[A-Za-z0-9][A-Za-z0-9._ -]*");
    }

	private static boolean validId(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}
}
