package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;

import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLMouseWheelEvent;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;

/** Lightweight scrollbar without arrow buttons. */
public final class GLScrollBar extends GLControl {
	public enum Orientation { HORIZONTAL, VERTICAL }

	private final Orientation orientation;
	private final CopyOnWriteArrayList<GLScrollListener> listeners =
			new CopyOnWriteArrayList<>();
	private GLScrollBarStyle localStyle;
	private float value;
	private float extent = 1;
	private float maximum = 1;
	private float unitIncrement = 24;
	private float blockIncrement = 100;
	private float dragOffset;

	public GLScrollBar(Orientation orientation) {
		super(false);
		if (orientation == null)
			throw new IllegalArgumentException("Scrollbar orientation must not be null");
		this.orientation = orientation;
		updatePreferredSize();
	}

	public Orientation getOrientation() { return orientation; }
	@Override protected AccessibleRole defaultAccessibleRole() {
		return AccessibleRole.SCROLL_BAR;
	}
	@Override protected void collectAccessibleStates(AccessibleStateSet states) {
		states.add(orientation == Orientation.HORIZONTAL
				? AccessibleState.HORIZONTAL : AccessibleState.VERTICAL);
	}
	@Override protected Number accessibleCurrentValue() { return value; }
	@Override protected boolean setAccessibleCurrentValue(Number next) {
		if (next == null || !Float.isFinite(next.floatValue())) return false;
		setValue(next.floatValue());
		return true;
	}
	@Override protected Number accessibleMinimumValue() { return 0f; }
	@Override protected Number accessibleMaximumValue() { return getMaximumValue(); }
	public float getValue() { return value; }
	public float getExtent() { return extent; }
	public float getMaximum() { return maximum; }
	public float getMaximumValue() { return Math.max(0, maximum - extent); }

	public void setValues(float value, float extent, float maximum) {
		if (!Float.isFinite(value) || !Float.isFinite(extent)
				|| !Float.isFinite(maximum) || extent < 0 || maximum < 0)
			throw new IllegalArgumentException(
					"Scrollbar values must be finite and non-negative");
		float oldValue = this.value;
		float oldExtent = this.extent;
		float oldMaximum = this.maximum;
		this.extent = Math.min(extent, maximum);
		this.maximum = maximum;
		this.value = clamp(value, 0, getMaximumValue());
		if (Float.compare(oldValue, this.value) == 0
				&& Float.compare(oldExtent, this.extent) == 0
				&& Float.compare(oldMaximum, this.maximum) == 0)
			return;
		invalidateRecording();
		if (Float.compare(oldValue, this.value) != 0) {
			fireAccessibleValueChange(oldValue, this.value);
			fireChanged(oldValue);
		}
	}

	public void setValue(float value) {
		if (!Float.isFinite(value))
			throw new IllegalArgumentException("Scrollbar value must be finite");
		float next = clamp(value, 0, getMaximumValue());
		if (Float.compare(this.value, next) == 0)
			return;
		float old = this.value;
		this.value = next;
		invalidateRecording();
		fireAccessibleValueChange(old, next);
		fireChanged(old);
	}

	public float getUnitIncrement() { return unitIncrement; }
	public void setUnitIncrement(float increment) {
		if (!Float.isFinite(increment) || increment <= 0)
			throw new IllegalArgumentException("Unit increment must be positive");
		unitIncrement = increment;
	}

	public float getBlockIncrement() { return blockIncrement; }
	public void setBlockIncrement(float increment) {
		if (!Float.isFinite(increment) || increment <= 0)
			throw new IllegalArgumentException("Block increment must be positive");
		blockIncrement = increment;
	}

	public void addScrollListener(GLScrollListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("Scroll listener must not be null");
		listeners.addIfAbsent(listener);
	}

	public void removeScrollListener(GLScrollListener listener) {
		listeners.remove(listener);
	}

	public GLScrollBarStyle getGLStyle() {
		return localStyle != null ? localStyle
				: GLScrollBarStyle.fromTheme(getGLTheme());
	}

	public void setGLStyle(GLScrollBarStyle style) {
		if (style == null)
			throw new IllegalArgumentException("Scrollbar style must not be null");
		localStyle = style;
		styleChanged();
	}

	public void clearGLStyle() {
		if (localStyle == null)
			return;
		localStyle = null;
		styleChanged();
	}

	@Override
	protected void record(GLContext2D context) {
		float width = getWidth();
		float height = getHeight();
		if (width <= 0 || height <= 0)
			return;
		GLScrollBarStyle style = getGLStyle();
		context.addUIHitArea(0, 0, width, height);
		float padding = Math.min(style.trackPadding(),
				Math.min(width, height) * 0.5f);
		float trackX = padding;
		float trackY = padding;
		float trackWidth = Math.max(0, width - padding * 2);
		float trackHeight = Math.max(0, height - padding * 2);
		float trackRadius = Math.min(layoutCornerRadius(style.cornerRadius()),
				Math.min(trackWidth, trackHeight) * 0.5f);
		context.fillUIRoundRect(trackX, trackY, trackWidth, trackHeight,
				trackRadius, style.track());
		float thumbLength = thumbLength(style);
		float thumbPosition = thumbPosition(thumbLength);
		Color thumbColor = switch (getControlState()) {
		case HOVERED -> style.thumbHover();
		case PRESSED -> style.thumbPressed();
		case DISABLED -> style.thumbDisabled();
		case NORMAL -> style.thumb();
		};
		if (orientation == Orientation.VERTICAL)
			context.fillUIRoundRect(trackX, trackY + thumbPosition,
					trackWidth, thumbLength, trackRadius, thumbColor);
		else
			context.fillUIRoundRect(trackX + thumbPosition, trackY,
					thumbLength, trackHeight, trackRadius, thumbColor);
	}

	@Override
	protected void controlStateChanged(GLControlState oldState,
			GLControlState newState) {
		invalidateRecording();
	}

	@Override
	protected void controlMousePressed(GLMouseEvent event) {
		if (event.getButton() != MouseEvent.BUTTON1 || !event.hasLocalPosition())
			return;
		float length = thumbLength(getGLStyle());
		float position = getGLStyle().trackPadding() + thumbPosition(length);
		float coordinate = coordinate(event);
		if (coordinate >= position && coordinate <= position + length)
			dragOffset = coordinate - position;
		else {
			dragOffset = length * 0.5f;
			setValue(value + (coordinate < position
					? -blockIncrement : blockIncrement));
		}
	}

	@Override
	protected void controlMouseDragged(GLMouseEvent event) {
		if (!event.hasLocalPosition())
			return;
		float length = thumbLength(getGLStyle());
		float trackLength = usableTrackLength();
		float travel = Math.max(0, trackLength - length);
		float position = clamp(coordinate(event) - getGLStyle().trackPadding()
				- dragOffset, 0, travel);
		setValue(travel <= 0 ? 0 : getMaximumValue() * position / travel);
	}

	@Override
	public void mouseWheelMoved(GLMouseWheelEvent event) {
		float rotation = (float) event.getPreciseWheelRotation();
		if (rotation == 0 || getMaximumValue() <= 0)
			return;
		float distance = event.getScrollType()
				== java.awt.event.MouseWheelEvent.WHEEL_BLOCK_SCROLL
						? rotation * blockIncrement
						: rotation * Math.max(1, event.getScrollAmount())
								* unitIncrement;
		float old = value;
		setValue(value + distance);
		if (Float.compare(old, value) != 0)
			event.consume();
	}

	@Override
	protected void themeChanged() {
		if (localStyle == null)
			styleChanged();
	}

	private float coordinate(GLMouseEvent event) {
		return orientation == Orientation.VERTICAL
				? event.getLocalY() : event.getLocalX();
	}

	private float usableTrackLength() {
		float length = orientation == Orientation.VERTICAL ? getHeight() : getWidth();
		return Math.max(0, length - getGLStyle().trackPadding() * 2);
	}

	private float thumbLength(GLScrollBarStyle style) {
		float trackLength = usableTrackLength();
		if (maximum <= 0)
			return trackLength;
		return Math.min(trackLength, Math.max(style.minimumThumbLength(),
				trackLength * extent / maximum));
	}

	private float thumbPosition(float thumbLength) {
		float travel = Math.max(0, usableTrackLength() - thumbLength);
		return getMaximumValue() <= 0 ? 0 : travel * value / getMaximumValue();
	}

	private void fireChanged(float oldValue) {
		for (GLScrollListener listener : listeners)
			listener.scrollChanged(this, oldValue, value);
	}

	private void styleChanged() {
		updatePreferredSize();
		invalidateRecording();
	}

	private void updatePreferredSize() {
		float thickness = getGLStyle().thickness();
		setIntrinsicPreferredGLSize(orientation == Orientation.VERTICAL
				? new GLSize(thickness, 80) : new GLSize(80, thickness));
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
