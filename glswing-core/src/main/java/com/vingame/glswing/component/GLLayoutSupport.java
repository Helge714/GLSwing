package com.vingame.glswing.component;

import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLLayout;
import com.vingame.glswing.layout.GLLayouts;
import com.vingame.glswing.layout.GLSize;

/** Shared dirty layout state for panels and regular GL containers. */
final class GLLayoutSupport {

	private final GLParent owner;
	private volatile GLLayout layout = GLLayouts.FREE;
	private volatile GLInsets padding = GLInsets.ZERO;
	private volatile boolean dirty = true;

	GLLayoutSupport(GLParent owner) {
		this.owner = owner;
	}

	GLLayout getLayout() {
		return layout;
	}

	void setLayout(GLLayout layout) {
		if (layout == null)
			throw new IllegalArgumentException("GL layout must not be null");
		if (this.layout == layout)
			return;
		this.layout = layout;
		invalidate();
	}

	GLInsets getPadding() {
		return padding;
	}

	void setPadding(GLInsets padding) {
		if (padding == null)
			throw new IllegalArgumentException("GL padding must not be null");
		if (this.padding.equals(padding))
			return;
		this.padding = padding;
		invalidate();
	}

	void invalidate() {
		dirty = true;
		GLParent parent = owner instanceof GLContainer container
				? container.getGLParent() : null;
		if (parent != null)
			parent.invalidateGLLayout();
		else
			owner.repaintGL();
	}

	void invalidateLocal() {
		dirty = true;
		owner.repaintGL();
	}

	/** Marks only this layout dirty when the backend already guarantees a frame. */
	void markDirty() {
		dirty = true;
	}

	boolean isDirty() {
		return dirty;
	}

	void ensure(float width, float height) {
		if (!dirty)
			return;
		dirty = false;
		try {
			layout.layout(owner, Math.max(0, width), Math.max(0, height));
		} catch (RuntimeException | Error error) {
			dirty = true;
			throw error;
		}
	}

	GLSize measure() {
		GLSize measured = layout.measure(owner);
		if (measured == null)
			throw new IllegalStateException("GL layout returned no measured size");
		if (!Float.isFinite(measured.width()) || !Float.isFinite(measured.height()))
			throw new IllegalStateException("GL layout returned a non-finite measured size");
		return measured;
	}
}
