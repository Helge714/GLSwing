package com.vingame.glswing.node.model;

/** Portable built-in node-state properties understood by editors and compilers. */
public final class GLNodeState {
	public static final String HIDDEN = "glswing.hidden";
	public static final String MUTED = "glswing.muted";

	private GLNodeState() { }

	public static boolean isHidden(GLGraphNode node) {
		return flag(node, HIDDEN);
	}

	public static boolean isMuted(GLGraphNode node) {
		return flag(node, MUTED);
	}

	private static boolean flag(GLGraphNode node, String key) {
		return node != null && node.properties().get(key)
				instanceof GLNodeValue.BooleanValue value && value.value();
	}
}
