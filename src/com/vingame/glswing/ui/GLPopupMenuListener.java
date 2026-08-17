package com.vingame.glswing.ui;

/** Receives lifetime changes of a root {@link GLPopupMenu} session. */
public interface GLPopupMenuListener {

	default void popupMenuOpened(GLPopupMenu menu) {
	}

	default void popupMenuClosed(GLPopupMenu menu) {
	}
}
