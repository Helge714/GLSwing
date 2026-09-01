package com.vingame.glswing.ui;

import com.vingame.glswing.component.GLContainer;

@FunctionalInterface
public interface GLTabCloseListener {
	void tabClosed(GLTabbedPane source, int formerIndex, String title, GLContainer content);
}
