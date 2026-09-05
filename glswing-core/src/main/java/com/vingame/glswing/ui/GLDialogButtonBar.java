package com.vingame.glswing.ui;

import java.util.List;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLLayout;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;

/**
 * A horizontal row for dialog actions that derives each button width from its
 * complete localized presentation. Buttons shrink only when the available row
 * is narrower than their combined preferred width.
 */
public final class GLDialogButtonBar extends GLContainer {
	/** Placement of the complete button group within the available row. */
	public enum Alignment { LEFT, CENTER, RIGHT }

	private float gap = 10;
	private float minimumButtonWidth = 72;
	private Alignment alignment = Alignment.RIGHT;

	/** Creates a right-aligned button row with default spacing and minimum width. */
	public GLDialogButtonBar() {
		setGLLayout(new ButtonLayout());
	}

	/** Adds an action button and returns this bar for chaining. */
	public GLDialogButtonBar addButton(GLButton button) {
		if (button == null) throw new IllegalArgumentException("Button required");
		addGL(button);
		return this;
	}

	/** Removes a button if it belongs to this bar. */
	public boolean removeButton(GLButton button) {
		return button != null && removeGL(button);
	}

	/** Returns an immutable snapshot of the buttons in display order. */
	public List<GLButton> buttons() {
		return getGLChildren().stream().filter(GLButton.class::isInstance)
				.map(GLButton.class::cast).toList();
	}

	/** Returns the logical-pixel gap between adjacent buttons. */
	public float gap() { return gap; }
	/** Sets the non-negative logical-pixel gap between adjacent buttons. */
	public void setGap(float gap) {
		if (!Float.isFinite(gap) || gap < 0)
			throw new IllegalArgumentException("Button gap must be finite and non-negative");
		if (this.gap == gap) return;
		this.gap = gap;
		invalidateGLLayout();
	}

	/** Returns the minimum preferred width assigned to each button. */
	public float minimumButtonWidth() { return minimumButtonWidth; }
	/** Sets the non-negative minimum preferred width assigned to each button. */
	public void setMinimumButtonWidth(float width) {
		if (!Float.isFinite(width) || width < 0)
			throw new IllegalArgumentException(
					"Minimum button width must be finite and non-negative");
		if (minimumButtonWidth == width) return;
		minimumButtonWidth = width;
		invalidateGLLayout();
	}

	/** Returns the placement of the complete button group. */
	public Alignment alignment() { return alignment; }
	/** Sets the placement of the complete button group. */
	public void setAlignment(Alignment alignment) {
		if (alignment == null) throw new IllegalArgumentException("Alignment required");
		if (this.alignment == alignment) return;
		this.alignment = alignment;
		invalidateGLLayout();
	}

	@Override protected void record(GLContext2D context) { }

	private final class ButtonLayout implements GLLayout {
		@Override public GLSize measure(GLParent parent) {
			List<GLButton> buttons = buttons();
			GLInsets padding = getGLPadding();
			float width = padding.horizontal()
					+ Math.max(0, buttons.size() - 1) * gap;
			float height = 0;
			for (GLButton button : buttons) {
				GLSize preferred = button.getPreferredGLSize();
				width += Math.max(minimumButtonWidth, preferred.width());
				height = Math.max(height, preferred.height());
			}
			return new GLSize(width, height + padding.vertical());
		}

		@Override public void layout(GLParent parent, float width, float height) {
			List<GLButton> buttons = buttons();
			if (buttons.isEmpty()) return;
			GLInsets padding = getGLPadding();
			float availableWidth = Math.max(0, width - padding.horizontal());
			float availableHeight = Math.max(0, height - padding.vertical());
			float gaps = Math.min(availableWidth,
					Math.max(0, buttons.size() - 1) * gap);
			float[] widths = new float[buttons.size()];
			float preferredTotal = 0;
			for (int index = 0; index < buttons.size(); index++) {
				widths[index] = Math.max(minimumButtonWidth,
						buttons.get(index).getPreferredGLSize().width());
				preferredTotal += widths[index];
			}
			float contentWidth = preferredTotal + gaps;
			if (contentWidth > availableWidth && preferredTotal > 0) {
				float scale = Math.max(0, availableWidth - gaps) / preferredTotal;
				for (int index = 0; index < widths.length; index++) widths[index] *= scale;
				contentWidth = availableWidth;
			}
			float x = padding.left() + switch (alignment) {
				case LEFT -> 0;
				case CENTER -> (availableWidth - contentWidth) * .5f;
				case RIGHT -> availableWidth - contentWidth;
			};
			for (int index = 0; index < buttons.size(); index++) {
				GLButton button = buttons.get(index);
				float buttonHeight = availableHeight;
				button.setBounds(x, padding.top() + (availableHeight - buttonHeight) * .5f,
						widths[index], buttonHeight);
				x += widths[index] + (index + 1 < buttons.size() ? gap : 0);
			}
		}
	}
}
