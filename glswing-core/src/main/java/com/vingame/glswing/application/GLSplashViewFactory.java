package com.vingame.glswing.application;

import javax.swing.JComponent;

/**
 * Creates the content of a startup window on the AWT event-dispatch thread.
 */
@FunctionalInterface
public interface GLSplashViewFactory {
	/**
	 * Creates one splash root. Components obtained from {@code context} remain
	 * bound to startup progress and may each be added to one Swing parent.
	 */
	JComponent create(GLSplashViewContext context);
}
