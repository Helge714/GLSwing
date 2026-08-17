package com.vingame.glswing.layout;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;

/** Built-in lightweight GL layout policies. */
public final class GLLayouts {

	/** Preserves manually assigned bounds and only measures their union. */
	public static final GLLayout FREE = new GLLayout() {
		@Override
		public GLSize measure(GLParent parent) {
			GLInsets padding = parent.getGLPadding();
			float requiredWidth = padding.left();
			float requiredHeight = padding.top();
			for (GLContainer child : parent.getGLChildren()) {
				if (!child.isVisible())
					continue;
				requiredWidth = Math.max(requiredWidth,
						child.getX() + child.getWidth());
				requiredHeight = Math.max(requiredHeight,
						child.getY() + child.getHeight());
			}
			return new GLSize(requiredWidth + padding.right(),
					requiredHeight + padding.bottom());
		}

		@Override
		public void layout(GLParent parent, float width, float height) {
			// Bounds remain fully user controlled.
		}

		@Override
		public String toString() {
			return "GLLayouts.FREE";
		}
	};

	private GLLayouts() {
	}
}
