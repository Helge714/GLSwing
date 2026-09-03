package com.vingame.glswing.ui;

import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.input.GLMouseWheelEvent;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLCornerRadii;
import com.vingame.glswing.rendering.GLStroke;

/**
 * General clipped viewport with optional horizontal and vertical scrollbars.
 * Child controls remain individually pickable inside the visible rectangle.
 */
public final class GLScrollPane extends GLContainer {
	public enum ScrollBarPolicy { AS_NEEDED, ALWAYS, NEVER }

	private final Viewport viewport = new Viewport();
	private final GLScrollBar verticalBar =
			new GLScrollBar(GLScrollBar.Orientation.VERTICAL);
	private final GLScrollBar horizontalBar =
			new GLScrollBar(GLScrollBar.Orientation.HORIZONTAL);
	private GLContainer content;
	private GLScrollPaneStyle localStyle;
	private ScrollBarPolicy verticalPolicy = ScrollBarPolicy.AS_NEEDED;
	private ScrollBarPolicy horizontalPolicy = ScrollBarPolicy.AS_NEEDED;
	private float scrollUnit = 28;
	private float viewportWidth;
	private float viewportHeight;
	private boolean layingOut;

	public GLScrollPane() {
		this(null);
	}

	public GLScrollPane(GLContainer content) {
		addGL(viewport);
		addGL(verticalBar);
		addGL(horizontalBar);
		verticalBar.addScrollListener((source, oldValue, newValue) ->
				applyContentPosition());
		horizontalBar.addScrollListener((source, oldValue, newValue) ->
				applyContentPosition());
		setIntrinsicPreferredGLSize(new GLSize(280, 190));
		setContent(content);
	}

	public GLContainer getContent() { return content; }

	public void setContent(GLContainer content) {
		if (this.content == content)
			return;
		if (this.content != null)
			viewport.removeGL(this.content);
		this.content = content;
		if (content != null)
			viewport.addGL(content);
		verticalBar.setValue(0);
		horizontalBar.setValue(0);
		layoutPane();
	}

	public GLScrollBar getVerticalScrollBar() { return verticalBar; }
	public GLScrollBar getHorizontalScrollBar() { return horizontalBar; }
	public float getHorizontalScrollOffset() { return horizontalBar.getValue(); }
	public float getVerticalScrollOffset() { return verticalBar.getValue(); }

	public void setScrollOffset(float x, float y) {
		horizontalBar.setValue(x);
		verticalBar.setValue(y);
		applyContentPosition();
	}

	public ScrollBarPolicy getVerticalScrollBarPolicy() { return verticalPolicy; }
	public void setVerticalScrollBarPolicy(ScrollBarPolicy policy) {
		if (policy == null)
			throw new IllegalArgumentException("Scrollbar policy must not be null");
		if (verticalPolicy == policy)
			return;
		verticalPolicy = policy;
		layoutPane();
	}

	public ScrollBarPolicy getHorizontalScrollBarPolicy() { return horizontalPolicy; }
	public void setHorizontalScrollBarPolicy(ScrollBarPolicy policy) {
		if (policy == null)
			throw new IllegalArgumentException("Scrollbar policy must not be null");
		if (horizontalPolicy == policy)
			return;
		horizontalPolicy = policy;
		layoutPane();
	}

	public float getScrollUnit() { return scrollUnit; }
	public void setScrollUnit(float scrollUnit) {
		if (!Float.isFinite(scrollUnit) || scrollUnit <= 0)
			throw new IllegalArgumentException("Scroll unit must be positive");
		this.scrollUnit = scrollUnit;
	}

	public GLScrollPaneStyle getGLStyle() {
		return localStyle != null ? localStyle
				: GLScrollPaneStyle.fromTheme(getGLTheme());
	}

	public void setGLStyle(GLScrollPaneStyle style) {
		if (style == null)
			throw new IllegalArgumentException("Scroll-pane style must not be null");
		localStyle = style;
		styleChanged();
	}

	public void clearGLStyle() {
		if (localStyle == null)
			return;
		localStyle = null;
		styleChanged();
	}

	/** Moves the smallest possible distance to reveal a content-space rectangle. */
	public void scrollRectToVisible(float x, float y, float width, float height) {
		if (!Float.isFinite(x) || !Float.isFinite(y)
				|| !Float.isFinite(width) || !Float.isFinite(height)
				|| width < 0 || height < 0)
			throw new IllegalArgumentException("Visible rectangle must be finite");
		float nextX = reveal(horizontalBar.getValue(), viewportWidth, x, width);
		float nextY = reveal(verticalBar.getValue(), viewportHeight, y, height);
		setScrollOffset(nextX, nextY);
	}

	@Override
	public void mouseWheelMoved(GLMouseWheelEvent event) {
		float rotation = (float) event.getPreciseWheelRotation();
		if (rotation == 0)
			return;
		boolean horizontal = getGLInputMap().hasModifiers(
				GLInputActions.SCROLL_HORIZONTAL, event.getModifiersEx());
		GLScrollBar bar = horizontal ? horizontalBar : verticalBar;
		if (!bar.isVisible() || bar.getMaximumValue() <= 0) {
			bar = horizontal ? verticalBar : horizontalBar;
			if (!bar.isVisible() || bar.getMaximumValue() <= 0)
				return;
		}
		float distance = event.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL
				? rotation * Math.max(scrollUnit, bar.getExtent() * 0.8f)
				: rotation * Math.max(1, event.getScrollAmount()) * scrollUnit;
		float old = bar.getValue();
		bar.setValue(old + distance);
		if (Float.compare(old, bar.getValue()) != 0)
			event.consume();
	}

	@Override
	protected void record(GLContext2D context) {
		float width = getWidth();
		float height = getHeight();
		if (width <= 0 || height <= 0)
			return;
		GLScrollPaneStyle style = getGLStyle();
		context.fillAndDrawUIRoundRect(0, 0, width, height,
				style.cornerRadius(), style.borderWidth(), style.background(),
				style.border(), GLStroke.WidthSpace.SCREEN);
	}

	@Override
	protected void render(GLContext2D context) {
		layoutPane();
		super.render(context);
	}

	@Override
	protected void resized(float width, float height) {
		layoutPane();
	}

	@Override
	protected void themeChanged() {
		if (localStyle == null)
			styleChanged();
	}

	private void styleChanged() {
		invalidateRecording();
		layoutPane();
	}

	private void layoutPane() {
		if (layingOut)
			return;
		layingOut = true;
		try {
			GLScrollPaneStyle style = getGLStyle();
			GLInsets padding = contentInsets(style);
			float availableWidth = Math.max(0, getWidth() - padding.horizontal());
			float availableHeight = Math.max(0, getHeight() - padding.vertical());
			GLSize preferred = content == null ? GLSize.ZERO
					: content.getPreferredGLSize();
			float verticalThickness = verticalBar.getGLStyle().thickness();
			float horizontalThickness = horizontalBar.getGLStyle().thickness();
			float gap = style.scrollBarGap();
			boolean vertical = verticalPolicy == ScrollBarPolicy.ALWAYS;
			boolean horizontal = horizontalPolicy == ScrollBarPolicy.ALWAYS;
			for (int pass = 0; pass < 2; pass++) {
				float candidateWidth = Math.max(0, availableWidth
						- (vertical ? verticalThickness + gap : 0));
				float candidateHeight = Math.max(0, availableHeight
						- (horizontal ? horizontalThickness + gap : 0));
				if (verticalPolicy == ScrollBarPolicy.AS_NEEDED)
					vertical = preferred.height() > candidateHeight + 0.01f;
				if (horizontalPolicy == ScrollBarPolicy.AS_NEEDED)
					horizontal = preferred.width() > candidateWidth + 0.01f;
			}
			viewportWidth = Math.max(0, availableWidth
					- (vertical ? verticalThickness + gap : 0));
			viewportHeight = Math.max(0, availableHeight
					- (horizontal ? horizontalThickness + gap : 0));
			viewport.setBounds(padding.left(), padding.top(),
					viewportWidth, viewportHeight);
			verticalBar.setVisible(vertical);
			horizontalBar.setVisible(horizontal);
			if (vertical)
				verticalBar.setBounds(padding.left() + viewportWidth + gap,
						padding.top(), verticalThickness, viewportHeight);
			if (horizontal)
				horizontalBar.setBounds(padding.left(),
						padding.top() + viewportHeight + gap,
						viewportWidth, horizontalThickness);
			float contentWidth = Math.max(viewportWidth, preferred.width());
			float contentHeight = Math.max(viewportHeight, preferred.height());
			verticalBar.setValues(verticalBar.getValue(), viewportHeight,
					contentHeight);
			horizontalBar.setValues(horizontalBar.getValue(), viewportWidth,
					contentWidth);
			verticalBar.setBlockIncrement(Math.max(scrollUnit, viewportHeight * 0.8f));
			horizontalBar.setBlockIncrement(Math.max(scrollUnit, viewportWidth * 0.8f));
			applyContentBounds(contentWidth, contentHeight);
		} finally {
			layingOut = false;
		}
	}

	private static GLInsets contentInsets(GLScrollPaneStyle style) {
		GLInsets padding = style.padding();
		float border = style.borderWidth();
		return new GLInsets(Math.max(padding.top(), border),
				Math.max(padding.left(), border),
				Math.max(padding.bottom(), border),
				Math.max(padding.right(), border));
	}

	private static float innerCornerRadius(float radius,
			float horizontalInset, float verticalInset) {
		if (radius <= 0 || horizontalInset >= radius || verticalInset >= radius)
			return 0;
		return Math.max(0, radius - Math.min(horizontalInset, verticalInset));
	}

	GLCornerRadii viewportContentCornerRadii() {
		float border = Math.min(getGLStyle().viewportBorderWidth(),
				Math.min(viewport.getWidth(), viewport.getHeight()) * 0.5f);
		return inset(viewportBorderCornerRadii(), border);
	}

	private GLCornerRadii viewportBorderCornerRadii() {
		float radius = getGLStyle().cornerRadius();
		float leftInset = viewport.getX();
		float topInset = viewport.getY();
		float rightInset = Math.max(0, getWidth()
				- viewport.getX() - viewport.getWidth());
		float bottomInset = Math.max(0, getHeight()
				- viewport.getY() - viewport.getHeight());
		return new GLCornerRadii(
				innerCornerRadius(radius, leftInset, topInset),
				innerCornerRadius(radius, rightInset, topInset),
				innerCornerRadius(radius, rightInset, bottomInset),
				innerCornerRadius(radius, leftInset, bottomInset));
	}

	private static GLCornerRadii inset(GLCornerRadii radii, float amount) {
		return new GLCornerRadii(
				Math.max(0, radii.topLeft() - amount),
				Math.max(0, radii.topRight() - amount),
				Math.max(0, radii.bottomRight() - amount),
				Math.max(0, radii.bottomLeft() - amount));
	}

	private void applyContentPosition() {
		if (content == null)
			return;
		content.setLocation(-horizontalBar.getValue(), -verticalBar.getValue());
		notifyVirtualViewport();
	}

	private void applyContentBounds(float width, float height) {
		if (content == null)
			return;
		content.setBounds(-horizontalBar.getValue(), -verticalBar.getValue(),
				width, height);
		notifyVirtualViewport();
	}

	private void notifyVirtualViewport() {
		if (content instanceof GLVirtualViewportContent virtualContent)
			virtualContent.viewportChanged(horizontalBar.getValue(),
					verticalBar.getValue(), viewportWidth, viewportHeight);
	}

	private static float reveal(float offset, float extent,
			float start, float length) {
		if (start < offset)
			return start;
		if (start + length > offset + extent)
			return start + length - extent;
		return offset;
	}

	private final class Viewport extends GLContainer {
		@Override
		protected void record(GLContext2D context) {
			context.addUIHitArea(0, 0, getWidth(), getHeight());
		}

		@Override
		protected void renderChildren(GLContext2D context) {
			if (getWidth() <= 0 || getHeight() <= 0)
				return;
			GLScrollPaneStyle style = GLScrollPane.this.getGLStyle();
			GLCornerRadii borderRadii = viewportBorderCornerRadii();
			float border = Math.min(style.viewportBorderWidth(),
					Math.min(getWidth(), getHeight()) * 0.5f);
			float contentWidth = Math.max(0, getWidth() - border * 2);
			float contentHeight = Math.max(0, getHeight() - border * 2);
			if (contentWidth > 0 && contentHeight > 0) {
				context.beginUIRoundedClip(border, border,
						contentWidth, contentHeight, inset(borderRadii, border));
				try {
					super.renderChildren(context);
				} finally {
					context.endClip();
				}
			}
			if (border > 0)
				context.drawUIRoundRect(0, 0, getWidth(), getHeight(), borderRadii,
						border, style.viewportBorder(), GLStroke.WidthSpace.SCREEN);
		}

	}
}
