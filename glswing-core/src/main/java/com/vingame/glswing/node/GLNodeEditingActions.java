package com.vingame.glswing.node;

/** Optional model-aware editing commands exposed by a node-editor host. */
public interface GLNodeEditingActions {
	default boolean canCopy() { return true; }
	default boolean canCut() { return canCopy(); }
	default boolean canPaste() { return true; }
	default boolean canDuplicate() { return canCopy(); }
	default boolean canExtractComposite() { return canCopy(); }
	default boolean canDelete() { return true; }
	default boolean canToggleHidden() { return true; }
	default boolean canToggleMuted() { return true; }
	void copy();
	void cut();
	void paste(float graphX, float graphY);
	void duplicate();
	void extractComposite();
	void delete();
	default void toggleHidden() { }
	default void toggleMuted() { }
	default GLNodePlacement beginPaste(float graphX, float graphY) {
		paste(graphX, graphY);
		return null;
	}
	default GLNodePlacement beginDuplicate(float graphX, float graphY) {
		duplicate();
		return null;
	}
	default GLNodePlacement beginMoveSelection(float graphX, float graphY) {
		return null;
	}
	default void requestNodeCreation(float graphX, float graphY,
			float screenX, float screenY) { }
	default boolean canOpenSelectedComposite() { return false; }
	default void openSelectedComposite() { }
}
