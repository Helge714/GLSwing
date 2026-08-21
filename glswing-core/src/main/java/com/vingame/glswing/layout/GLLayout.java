package com.vingame.glswing.layout;

import com.vingame.glswing.component.GLParent;

/** Measures and arranges the direct children of one GL parent. */
public interface GLLayout {

	GLSize measure(GLParent parent);

	void layout(GLParent parent, float width, float height);
}
