package com.vingame.glswing.component;

import java.util.List;

import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLLayout;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.ui.GLFontSpec;
import com.vingame.glswing.ui.GLLocalizer;
import com.vingame.glswing.ui.GLTheme;
import com.vingame.glswing.input.GLKeyTarget;
import com.vingame.glswing.input.GLInputMap;

/** A GLSwing object that can directly own and render {@link GLContainer}s. */
public interface GLParent {

	void addGL(GLContainer child);

	void addGL(GLContainer child, GLConstraints constraints);

	boolean removeGL(GLContainer child);

	void removeAllGL();

	/** Returns an immutable snapshot in stable layout/insertion order. */
	List<GLContainer> getGLChildren();

	/** Returns direct children in back-to-front paint and picking order. */
	List<GLContainer> getGLChildrenInPaintOrder();

	/** Zero is the backmost position; the highest index is frontmost. */
	int getGLZOrder(GLContainer child);

	/** Changes paint and picking order without changing layout order. */
	void setGLZOrder(GLContainer child, int zOrder);

	default void bringGLToFront(GLContainer child) {
		setGLZOrder(child, getGLChildren().size() - 1);
	}

	default void sendGLToBack(GLContainer child) {
		setGLZOrder(child, 0);
	}

	default void moveGLForward(GLContainer child) {
		setGLZOrder(child, Math.min(getGLChildren().size() - 1,
				getGLZOrder(child) + 1));
	}

	default void moveGLBackward(GLContainer child) {
		setGLZOrder(child, Math.max(0, getGLZOrder(child) - 1));
	}

	void setGLConstraints(GLContainer child, GLConstraints constraints);

	GLConstraints getGLConstraints(GLContainer child);

	void setGLLayout(GLLayout layout);

	GLLayout getGLLayout();

	void setGLPadding(GLInsets padding);

	GLInsets getGLPadding();

	void invalidateGLLayout();

	boolean isGLLayoutDirty();

	void layoutGL();

	GLSize getPreferredGLSize();

	void packGL();

	void setGLTheme(GLTheme theme);

	GLTheme getGLTheme();

	void setGLLocalizer(GLLocalizer localizer);

	GLLocalizer getGLLocalizer();

	GLFont getGLFont(GLFontSpec specification);

	/** Nearest undo/redo history inherited by children without a local scope. */
	GLHistoryManager getGLHistoryManager();

	/** Nearest semantic input map inherited by children. */
	GLInputMap getGLInputMap();

	void requestGLFocus(GLKeyTarget target);

	void clearGLFocus(GLContainer subtree);

	/** Requests a repaint from the Swing render surface owning this subtree. */
	void repaintGL();

	/** Requests repainting for one retained subtree. */
	default void repaintGL(GLContainer source) {
		repaintGL();
	}

	/** Marks both color and picking data of one retained subtree as changed. */
	default void repaintAndRebuildPicking(GLContainer source) {
		repaintGL(source);
	}
}
