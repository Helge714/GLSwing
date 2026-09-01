package com.vingame.glswing.ui;

import java.awt.Color;

/** Supplies transient presentation colors without modifying document styles. */
@FunctionalInterface
public interface GLTextColorProvider {
	Color colorAt(GLTextDocument document, int offset, Color defaultColor);
}
