package com.vingame.glswing.node.model;

/** Maps one input directly to one output while a node is muted. */
public record GLNodeMuteBypass(String inputPortId, String outputPortId) {
	public GLNodeMuteBypass {
		if (inputPortId == null || inputPortId.isBlank() || outputPortId == null
				|| outputPortId.isBlank())
			throw new IllegalArgumentException("Mute bypass port IDs required");
	}
}
