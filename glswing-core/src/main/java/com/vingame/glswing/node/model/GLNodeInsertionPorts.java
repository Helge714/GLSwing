package com.vingame.glswing.node.model;

/** Explicit ports used when an unconnected node is dropped onto a connection. */
public record GLNodeInsertionPorts(String inputPortId, String outputPortId) {
    public GLNodeInsertionPorts {
        if (inputPortId == null || inputPortId.isBlank() || outputPortId == null || outputPortId.isBlank())
            throw new IllegalArgumentException("Insertion ports required");
    }
}
