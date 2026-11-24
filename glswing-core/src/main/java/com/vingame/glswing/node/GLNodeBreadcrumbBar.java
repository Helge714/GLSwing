package com.vingame.glswing.node;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.IntConsumer;

import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLPointerCursor;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.ui.GLControl;
import com.vingame.glswing.ui.GLTextFitter;

/** Compact clickable path for navigating nested node graphs. */
public final class GLNodeBreadcrumbBar extends GLControl {
	private static final float HORIZONTAL_PADDING = 7;
	private static final float SEPARATOR_WIDTH = 13;
	private List<String> segments = List.of();
	private float[] starts = new float[0];
	private float[] ends = new float[0];
	private IntConsumer navigation = index -> { };
	private int hovered = -1;

	public GLNodeBreadcrumbBar() { super(false); }

	public void setNavigationListener(IntConsumer listener) {
		navigation = listener == null ? index -> { } : listener;
	}

	public void setSegments(List<String> names) {
		if (names == null || names.stream().anyMatch(name ->
				name == null || name.isBlank()))
			throw new IllegalArgumentException("Breadcrumb names required");
		segments = List.copyOf(names);
		starts = new float[segments.size()];
		ends = new float[segments.size()];
		hovered = -1;
		invalidateRecording();
	}

	@Override protected void record(GLContext2D context) {
		if (segments.isEmpty()) return;
		// The path is visually transparent in its normal state. Keep its complete
		// bounds in the picking pass so hover does not depend on glyph coverage.
		context.addUIHitArea(0, 0, getWidth(), getHeight());
		GLFont font = getGLFont(getGLTheme().labelStyle().font());
		float baseline = (getHeight() - font.getLineHeight()) * .5f + font.getAscent();
		float available = Math.max(0, getWidth()
				- (segments.size() - 1) * SEPARATOR_WIDTH);
		float maximumSegment = available / segments.size();
		float x = 0;
		Color text = getGLTheme().labelStyle().textColor();
		Color hover = getGLTheme().values().getColor("palette.hover");
		for (int index = 0; index < segments.size(); index++) {
			String shown = GLTextFitter.ellipsize(font, segments.get(index),
					Math.max(0, maximumSegment - HORIZONTAL_PADDING * 2));
			float width = Math.min(maximumSegment,
					font.measureText(shown) + HORIZONTAL_PADDING * 2);
			starts[index] = x; ends[index] = x + width;
			if (hovered == index)
				context.fillUIRoundRect(x, 2, width, Math.max(0, getHeight() - 4),
						4, hover);
			context.drawUIText(font, shown, x + HORIZONTAL_PADDING, baseline,
					hovered == index ? getGLTheme().values().getColor("nodeBreadcrumb.hoverText") : text);
			x += width;
			if (index + 1 < segments.size()) {
				context.drawUIText(font, "/", x + 4, baseline, text);
				x += SEPARATOR_WIDTH;
			}
		}
	}

	@Override protected void controlMouseMoved(GLMouseEvent event) {
		setHovered(segmentAt(event));
		if (hovered >= 0)
			event.getPointerController().setCursor(this, GLPointerCursor.HAND);
		else event.getPointerController().clearCursor(this);
	}

	@Override protected void controlMouseEntered(GLMouseEvent event) {
		controlMouseMoved(event);
	}

	@Override protected void controlMouseExited(GLMouseEvent event) {
		setHovered(-1);
		event.getPointerController().clearCursor(this);
	}

	@Override protected void controlMouseClicked(GLMouseEvent event) {
		if (event.getButton() != MouseEvent.BUTTON1) return;
		int index = segmentAt(event);
		if (index >= 0) { navigation.accept(index); event.consume(); }
	}

	@Override protected String toolTipTextAt(GLMouseEvent event) {
		int index = segmentAt(event);
		return index < 0 ? null : segments.get(index);
	}

	private int segmentAt(GLMouseEvent event) {
		if (!event.hasLocalPosition()) return -1;
		float x = event.getLocalX();
		for (int index = 0; index < starts.length; index++)
			if (x >= starts[index] && x <= ends[index]) return index;
		return -1;
	}

	private void setHovered(int index) {
		if (hovered == index) return;
		hovered = index;
		invalidateRecording();
	}
}
