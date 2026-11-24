package com.vingame.glswing.ui;

/** Receives selected-index changes from a {@link GLTabbedPane}. */
@FunctionalInterface
public interface GLTabSelectionListener {
	void selectionChanged(GLTabbedPane source, int oldIndex, int newIndex);
}
