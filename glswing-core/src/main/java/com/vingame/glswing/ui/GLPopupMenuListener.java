package com.vingame.glswing.ui;

/** Receives lifetime and pointer-presence changes of a root popup session. */
public interface GLPopupMenuListener {

	default void popupMenuOpened(GLPopupMenu menu) {
	}

	default void popupMenuClosed(GLPopupMenu menu) {
	}

	/** Called when the pointer enters an enabled item in this popup tree. */
	default void popupMenuPointerEntered(GLPopupMenu menu) {
	}

	/** Called when the pointer leaves an item in this popup tree. */
	default void popupMenuPointerExited(GLPopupMenu menu) {
	}
}
