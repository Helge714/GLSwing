package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.DoubleFunction;

import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLPointerController;
import com.vingame.glswing.input.GLPointerCursor;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLCornerRadii;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLRenderParameters;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.rendering.GLTextShadow;

/** A numeric slider with either a bounded progress fill or unbounded arrows. */
public class GLSlider extends GLControl {

	public enum Mode {
		FIXED,
		FREE
	}

	public static final float DEFAULT_WIDTH = 180;
	public static final float DEFAULT_HEIGHT = 22;
	public static final double DEFAULT_DRAG_SENSITIVITY = 0.1;
	public static final double DEFAULT_PRECISE_DRAG_FACTOR = 0.1;
	public static final double DEFAULT_COARSE_DRAG_FACTOR = 10;
	static final float DRAG_THRESHOLD = 2;

	private final Mode mode;
	private final CopyOnWriteArrayList<ChangeListener> changeListeners =
			new CopyOnWriteArrayList<>();
	private final GLRenderParameters backgroundParameters = new GLRenderParameters();
	private final GLRenderParameters progressParameters = new GLRenderParameters();
	private final GLRenderParameters leftArrowBackgroundParameters = new GLRenderParameters();
	private final GLRenderParameters rightArrowBackgroundParameters = new GLRenderParameters();
	private final GLRenderParameters leftArrowParameters = new GLRenderParameters();
	private final GLRenderParameters rightArrowParameters = new GLRenderParameters();
	private final GLRenderParameters textParameters = new GLRenderParameters();
	private final GLRenderParameters borderParameters = new GLRenderParameters();
	private final Timer validationFeedbackTimer;

	private String text;
	private double minimum;
	private double maximum;
	private double value;
	private double defaultValue;
	private double step = 1;
	private double dragSensitivity = DEFAULT_DRAG_SENSITIVITY;
	private double preciseDragFactor = Double.NaN;
	private double coarseDragFactor = Double.NaN;
	private DoubleFunction<String> valueFormatter = GLSlider::defaultValueText;
	private GLSliderStyle localStyle;
	private GLSliderStyle appliedStyle;
	private HitPart hoverPart = HitPart.NONE;
	private HitPart pressedPart = HitPart.NONE;
	private HitPart armedArrow = HitPart.NONE;
	private GLTextEditor valueEditor;
	private GLPopupMenu contextMenu;
	private GLMenuItem resetMenuItem;
	private float dragDistance;
	private boolean dragOccurred;
	private boolean validationFeedbackActive;
	private boolean relativeDragging;
	private Point2D.Float dragCenter;
	private GLSliderGroup sliderGroup;
	private GLPointerController pointerController = GLPointerController.NONE;
	private boolean groupGesturePressed;
	private double gestureStartValue;

	private GLSlider(String text, Mode mode, double minimum,
			double maximum, double value) {
		super(true);
		this.text = requireText(text);
		this.mode = mode;
		this.minimum = minimum;
		this.maximum = maximum;
		this.value = value;
		defaultValue = value;
		validationFeedbackTimer = new Timer(450, event -> {
			validationFeedbackActive = false;
			invalidateRecording();
			updateColors();
		});
		validationFeedbackTimer.setRepeats(false);
		updatePresentation(true);
	}

	public static GLSlider fixed(String text, double minimum,
			double maximum, double value) {
		requireFinite(minimum, "Minimum");
		requireFinite(maximum, "Maximum");
		if (!(maximum > minimum))
			throw new IllegalArgumentException("Slider maximum must exceed minimum");
		requireFinite(value, "Value");
		return new GLSlider(text, Mode.FIXED, minimum, maximum,
				clamp(value, minimum, maximum));
	}

	public static GLSlider free(String text, double value) {
		requireFinite(value, "Value");
		return new GLSlider(text, Mode.FREE,
				Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, value);
	}

	public final Mode getMode() {
		return mode;
	}

	public final String getText() {
		return text;
	}

	@Override
	protected String defaultName() {
		return text;
	}

	public final void setText(String text) {
		text = requireText(text);
		if (this.text.equals(text))
			return;
		this.text = text;
		invalidateRecording();
	}

	public final double getValue() {
		return value;
	}

	public final void setValue(double value) {
		requireFinite(value, "Value");
		double resolved = mode == Mode.FIXED
				? clamp(value, minimum, maximum) : value;
		if (Double.compare(this.value, resolved) == 0)
			return;
		this.value = resolved;
		invalidateRecording();
		fireStateChanged();
	}

	/** Returns the value restored by {@link #reset()} and by right-clicking. */
	public final double getDefaultValue() {
		return defaultValue;
	}

	/** Sets the reset value, clamped to the range of a FIXED slider. */
	public final void setDefaultValue(double defaultValue) {
		requireFinite(defaultValue, "Default value");
		this.defaultValue = mode == Mode.FIXED
				? clamp(defaultValue, minimum, maximum) : defaultValue;
	}

	/** Restores the configured default value. */
	public final void reset() {
		cancelValueEditing();
		setValue(defaultValue);
	}

	/**
	 * Returns this slider's persistent context menu. Additional items and submenus
	 * may be appended directly; the built-in reset item remains the first row.
	 */
	public final GLPopupMenu getContextMenu() {
		ensureContextMenu();
		return contextMenu;
	}

	/** Returns the built-in reset item, for example to disable it temporarily. */
	public final GLMenuItem getResetMenuItem() {
		ensureContextMenu();
		return resetMenuItem;
	}

	public final double getMinimum() {
		requireFixed();
		return minimum;
	}

	public final double getMaximum() {
		requireFixed();
		return maximum;
	}

	public final void setRange(double minimum, double maximum) {
		requireFixed();
		requireFinite(minimum, "Minimum");
		requireFinite(maximum, "Maximum");
		if (!(maximum > minimum))
			throw new IllegalArgumentException("Slider maximum must exceed minimum");
		if (this.minimum == minimum && this.maximum == maximum)
			return;
		if (sliderGroup != null)
			sliderGroup.validateRangeChange(this, minimum, maximum);
		this.minimum = minimum;
		this.maximum = maximum;
		defaultValue = clamp(defaultValue, minimum, maximum);
		setValue(value);
		invalidateRecording();
	}

	public final double getStep() {
		return step;
	}

	public final void setStep(double step) {
		if (!Double.isFinite(step) || step <= 0)
			throw new IllegalArgumentException("Slider step must be finite and positive");
		this.step = step;
	}

	/** Value units added per horizontal logical screen pixel while dragging FREE. */
	public final double getDragSensitivity() {
		return dragSensitivity;
	}

	public final void setDragSensitivity(double dragSensitivity) {
		requirePositive(dragSensitivity, "Slider drag sensitivity");
		this.dragSensitivity = dragSensitivity;
	}

	/** Multiplier applied to the normal FREE drag rate while Shift is held. */
	public final double getPreciseDragFactor() {
		return Double.isNaN(preciseDragFactor)
				? getGLInputMap().getValue(GLInputActions.SLIDER_PRECISE_FACTOR,
						DEFAULT_PRECISE_DRAG_FACTOR) : preciseDragFactor;
	}

	public final void setPreciseDragFactor(double preciseDragFactor) {
		requirePositive(preciseDragFactor, "Precise drag factor");
		this.preciseDragFactor = preciseDragFactor;
	}

	/** Multiplier applied to the normal FREE drag rate while Control is held. */
	public final double getCoarseDragFactor() {
		return Double.isNaN(coarseDragFactor)
				? getGLInputMap().getValue(GLInputActions.SLIDER_COARSE_FACTOR,
						DEFAULT_COARSE_DRAG_FACTOR) : coarseDragFactor;
	}

	public final void setCoarseDragFactor(double coarseDragFactor) {
		requirePositive(coarseDragFactor, "Coarse drag factor");
		this.coarseDragFactor = coarseDragFactor;
	}

	public final void increment() {
		setValue(value + step);
	}

	public final void decrement() {
		setValue(value - step);
	}

	public final DoubleFunction<String> getValueFormatter() {
		return valueFormatter;
	}

	public final void setValueFormatter(DoubleFunction<String> formatter) {
		if (formatter == null)
			throw new IllegalArgumentException("Slider value formatter must not be null");
		valueFormatter = formatter;
		valueText();
		invalidateRecording();
	}

	public final void addChangeListener(ChangeListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("Change listener must not be null");
		changeListeners.addIfAbsent(listener);
	}

	public final void removeChangeListener(ChangeListener listener) {
		changeListeners.remove(listener);
	}

	public final ChangeListener[] getChangeListeners() {
		return changeListeners.toArray(ChangeListener[]::new);
	}

	/** Opens the reusable inline editor over this slider's value field. */
	public final void startValueEditing() {
		if (!isEnabled())
			return;
		if (valueEditor != null) {
			requestGLFocus(valueEditor);
			valueEditor.selectAll();
			return;
		}
		double valueBeforeEditing = value;
		GLTextEditor editor = createValueEditor(valueText(), true);
		if (editor == null)
			return;
		editor.addActionListener(event -> {
			Double parsed = parseNumber(editor.getText());
			if (parsed != null) {
				setValue(parsed);
				recordValueChange(valueBeforeEditing, value,
						"Edit slider value");
				closeValueEditor(editor);
			}
		});
		editor.addCancelListener(event -> closeValueEditor(editor));
		editor.addValidationFailureListener(event -> {
			closeValueEditor(editor);
			showValidationFailure();
		});
		focusValueEditor(editor);
	}

	public final boolean isValueEditing() {
		return valueEditor != null;
	}

	public final void cancelValueEditing() {
		GLTextEditor editor = valueEditor;
		if (editor != null)
			editor.cancel();
	}

	public final boolean isValidationFeedbackActive() {
		return validationFeedbackActive;
	}

	public final void setGLStyle(GLSliderStyle style) {
		if (style == null)
			throw new IllegalArgumentException("Slider style must not be null");
		if (localStyle == style)
			return;
		localStyle = style;
		updatePresentation(true);
	}

	public final void clearGLStyle() {
		if (localStyle == null)
			return;
		localStyle = null;
		updatePresentation(true);
	}

	public final GLSliderStyle getGLStyle() {
		return localStyle != null ? localStyle : getGLTheme().sliderStyle();
	}

	@Override
	protected void record(GLContext2D context) {
		GLSliderStyle style = getGLStyle();
		float width = getWidth();
		float height = getHeight();
		if (width <= 0 || height <= 0)
			return;

		GLCornerRadii radii = sliderGroup == null
				? GLCornerRadii.all(style.cornerRadius())
				: sliderGroup.cornerRadii(this, style.cornerRadius());
		float border = sliderGroup == null ? style.borderWidth() : 0;
		boolean compositeFixed = sliderGroup == null && mode == Mode.FIXED
				&& border > 0;
		if (!compositeFixed) context.withParameters(backgroundParameters, () -> {
			if (border > 0)
				context.fillAndDrawUIRoundRect(0, 0, width, height,
						style.cornerRadius(), border, Color.WHITE,
						style.borderColor(), GLStroke.WidthSpace.SCREEN);
			else
				context.fillUIRoundRect(0, 0, width, height, radii, Color.WHITE);
		});

		if (mode == Mode.FIXED) {
			float progress = (float) ((value - minimum) / (maximum - minimum));
			Color progressDark = style.progressDarkColor();
			Color progressLight = sliderGroup == null
					|| sliderGroup.usesProgressGradient(this)
					? style.progressLightColor() : progressDark;
			if (compositeFixed) {
				GLControlState presentationState = groupGesturePressed && isEnabled()
						? GLControlState.PRESSED : getControlState();
				float progressOpacity = isEnabled() ? 1 : 0.45f;
				Color borderColor = validationFeedbackActive
						? getGLTheme().textEditorStyle().invalidBorderColor()
						: style.borderColor();
				context.fillUISlider(0, 0, width, height, style.cornerRadius(),
						progress, border, style.background(presentationState),
						withOpacity(progressLight, progressOpacity),
						withOpacity(progressDark, progressOpacity), borderColor,
						GLStroke.WidthSpace.SCREEN);
			} else context.withParameters(progressParameters, () -> {
				if (sliderGroup == null) {
					float progressInset = Math.min(border,
							Math.min(width, height) * 0.5f);
					float progressWidth = Math.max(0.0001f,
							width - progressInset * 2);
					float progressHeight = Math.max(0.0001f,
							height - progressInset * 2);
					context.fillUIInnerProgressRoundRect(progressInset, progressInset,
							progressWidth, progressHeight,
							Math.max(0, style.cornerRadius() - progressInset), progress,
							progressLight, progressDark);
				} else
					context.fillUIProgressRoundRect(0, 0, width, height,
							radii.topLeft(), radii.bottomLeft(), progress,
							progressLight, progressDark);
			});
		} else {
			recordArrowButton(context, style, radii, true);
			recordArrowButton(context, style, radii, false);
		}

		if (!compositeFixed && sliderGroup == null && border > 0
				&& validationFeedbackActive)
			context.withParameters(borderParameters, () ->
					context.drawUIRoundRect(0, 0, width, height,
							style.cornerRadius(), style.borderWidth(), Color.WHITE,
							GLStroke.WidthSpace.SCREEN));

		recordText(context, style, width, height);
	}

	private static Color withOpacity(Color color, float opacity) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(),
				Math.round(color.getAlpha() * opacity));
	}

	private void recordArrowButton(GLContext2D context, GLSliderStyle style,
			GLCornerRadii sliderRadii, boolean left) {
		float inset = style.arrowInset();
		float availableHalfWidth = Math.max(1,
				getWidth() * 0.5f - inset);
		float buttonWidth = Math.min(style.arrowButtonWidth(),
				availableHalfWidth);
		float buttonHeight = Math.max(1, getHeight() - inset * 2);
		float effectiveSliderRadius = Math.min(style.cornerRadius(),
				Math.min(getWidth(), getHeight()) * 0.5f);
		float outerRadius = Math.max(0,
				Math.min(buttonHeight * 0.5f, effectiveSliderRadius - inset));
		float innerRadius = Math.min(outerRadius, buttonWidth * 0.25f);
		float topOuterRadius = (left ? sliderRadii.topLeft()
				: sliderRadii.topRight()) > 0 ? outerRadius : 0;
		float bottomOuterRadius = (left ? sliderRadii.bottomLeft()
				: sliderRadii.bottomRight()) > 0 ? outerRadius : 0;
		float backgroundWidth = Math.min(availableHalfWidth,
				Math.max(buttonWidth, outerRadius * 2));
		float backgroundX = left ? inset
				: getWidth() - inset - backgroundWidth;
		GLCornerRadii radii = left
				? new GLCornerRadii(topOuterRadius, innerRadius,
						innerRadius, bottomOuterRadius)
				: new GLCornerRadii(innerRadius, topOuterRadius,
						bottomOuterRadius, innerRadius);
		float iconX = left ? inset : getWidth() - inset - buttonWidth;
		GLRenderParameters background = left
				? leftArrowBackgroundParameters : rightArrowBackgroundParameters;
		GLRenderParameters arrow = left ? leftArrowParameters : rightArrowParameters;
		context.withParameters(background, () ->
				context.fillUIRoundRect(backgroundX, inset, backgroundWidth,
						buttonHeight, radii, Color.WHITE));
		float horizontalIconInset = Math.max(3, buttonWidth * 0.25f);
		float verticalIconInset = Math.max(4, buttonHeight * 0.28f);
		context.withParameters(arrow, () ->
				context.drawUIChevron(iconX + horizontalIconInset,
						inset + verticalIconInset,
						Math.max(1, buttonWidth - horizontalIconInset * 2),
						Math.max(1, buttonHeight - verticalIconInset * 2), !left,
						style.arrowStrokeWidth(), Color.WHITE,
						GLStroke.WidthSpace.SCREEN));
	}

	private void recordText(GLContext2D context, GLSliderStyle style,
			float width, float height) {
		GLFont font = getGLFont(style.font());
		GLInsets padding = style.padding();
		float contentLeft = padding.left();
		float contentRight = width - padding.right();
		if (mode == Mode.FREE) {
			contentLeft += style.arrowInset() + style.arrowButtonWidth();
			contentRight -= style.arrowInset() + style.arrowButtonWidth();
		}
		String renderedValue = valueText();
		float baseline = (height - font.getLineHeight()) * 0.5f + font.getAscent();
		float labelX = contentLeft;
		float contentWidth = Math.max(0, contentRight - contentLeft);
		renderedValue = GLTextFitter.ellipsize(font, renderedValue, contentWidth);
		float valueWidth = font.measureText(renderedValue);
		float valueX = Math.max(contentLeft, contentRight - valueWidth);
		float labelGap = renderedValue.isEmpty() ? 0 : 6;
		String renderedLabel = GLTextFitter.ellipsize(font, text,
				Math.max(0, valueX - labelGap - labelX));
		String finalValue = renderedValue;
		GLTextShadow shadow = getGLTheme().textShadow("slider");
		if (!renderedLabel.isEmpty())
			context.drawUITextShadow(font, renderedLabel, labelX, baseline, shadow);
		context.drawUITextShadow(font, finalValue, valueX, baseline, shadow);
		context.withParameters(textParameters, () -> {
			if (!renderedLabel.isEmpty())
				context.drawUIText(font, renderedLabel, labelX, baseline, Color.WHITE);
			context.drawUIText(font, finalValue, valueX, baseline, Color.WHITE);
		});
	}

	@Override
	protected void controlStateChanged(GLControlState oldState,
			GLControlState newState) {
		if (newState == GLControlState.DISABLED) {
			if (relativeDragging)
				endRelativeDrag(null, false);
			if (sliderGroup != null)
				sliderGroup.pointerReleased(this, false);
			restoreHoverCursor();
			cancelValueEditing();
		}
		updateColors();
	}

	@Override
	protected void controlMouseEntered(GLMouseEvent event) {
		showResizeCursor(event);
		updateHoverPart(event);
	}

	@Override
	protected void controlMouseMoved(GLMouseEvent event) {
		showResizeCursor(event);
		updateHoverPart(event);
	}

	@Override
	protected void controlMouseExited(GLMouseEvent event) {
		hoverPart = HitPart.NONE;
		updateArrowColors();
		if (!relativeDragging)
			restoreHoverCursor();
	}

	@Override
	protected void controlMousePressed(GLMouseEvent event) {
		if (!isEnabled() || event.getButton() != MouseEvent.BUTTON1
				|| !event.hasLocalPosition())
			return;
		pressedPart = hitPart(event.getLocalX());
		gestureStartValue = value;
		armedArrow = HitPart.NONE;
		dragDistance = 0;
		dragOccurred = false;
		if (sliderGroup != null && canStartGroupGesture())
			sliderGroup.pointerPressed(this, event);
		updateArrowColors();
	}

	@Override
	protected void controlMouseDragged(GLMouseEvent event) {
		if (!isEnabled())
			return;
		dragDistance += (float) Math.hypot(event.getDeltaX(), event.getDeltaY());
		boolean groupSliding = sliderGroup == null || !canStartGroupGesture()
				|| sliderGroup.pointerDragged(this, event);
		if (!dragOccurred && dragDistance >= DRAG_THRESHOLD)
			dragOccurred = true;
		if (!dragOccurred || !groupSliding)
			return;
		boolean manipulated = false;
		if (mode == Mode.FIXED && event.hasLocalPosition()) {
			setFixedValue(event.getLocalX());
			manipulated = true;
		} else if (mode == Mode.FREE && pressedPart == HitPart.CENTER) {
			if (!relativeDragging)
				beginRelativeDrag(event);
			if (Math.abs(event.getDeltaX()) > 0.0001f)
				setValue(value + event.getDeltaX() * dragSensitivityFor(event));
			manipulated = true;
			if (dragCenter != null)
				event.requestPointerWarp(dragCenter.x, dragCenter.y);
		}
		if (manipulated && sliderGroup != null)
			sliderGroup.synchronizeGestureValue(this);
		if (event.hasLocalPosition()) {
			hoverPart = hitPart(event.getLocalX());
			updateArrowColors();
		}
	}

	private boolean canStartGroupGesture() {
		return mode == Mode.FIXED || pressedPart == HitPart.CENTER;
	}

	@Override
	protected void controlMouseReleased(GLMouseEvent event) {
		if (mode == Mode.FREE) {
			HitPart releasedPart = event.hasLocalPosition()
					? hitPart(event.getLocalX()) : HitPart.NONE;
			if (!dragOccurred && pressedPart.isArrow()
					&& releasedPart == pressedPart)
				armedArrow = pressedPart;
			if (relativeDragging)
				endRelativeDrag(event, true);
		}
		if (sliderGroup != null)
			sliderGroup.pointerReleased(this, true);
		else
			recordValueChange(gestureStartValue, value, "Drag slider");
		pressedPart = HitPart.NONE;
		updateArrowColors();
	}

	@Override
	protected void controlMouseCaptureLost(GLMouseEvent event) {
		armedArrow = HitPart.NONE;
		dragOccurred = false;
		if (relativeDragging)
			endRelativeDrag(null, false);
		if (sliderGroup != null)
			sliderGroup.pointerReleased(this, false);
		else
			recordValueChange(gestureStartValue, value, "Drag slider");
		pressedPart = HitPart.NONE;
		hoverPart = HitPart.NONE;
		updateArrowColors();
		restoreHoverCursor();
	}

	@Override
	protected void controlMouseClicked(GLMouseEvent event) {
		if (!isEnabled())
			return;
		if (event.getButton() == MouseEvent.BUTTON3) {
			showContextMenu(event);
			return;
		}
		if (event.getButton() != MouseEvent.BUTTON1)
			return;
		HitPart action = armedArrow;
		armedArrow = HitPart.NONE;
		if (mode == Mode.FREE && action == HitPart.LEFT)
			changeValueFromUser(value - step, "Decrease slider");
		else if (mode == Mode.FREE && action == HitPart.RIGHT)
			changeValueFromUser(value + step, "Increase slider");
		else if (!dragOccurred && event.hasLocalPosition()
				&& hitPart(event.getLocalX()) == HitPart.CENTER)
			startValueEditing();
		dragOccurred = false;
	}

	@Override
	protected void controlFocusChanged(boolean focused) {
		if (!focused) {
			if (relativeDragging)
				endRelativeDrag(null, false);
			if (sliderGroup != null)
				sliderGroup.pointerReleased(this, false);
		}
	}

	@Override
	protected void controlKeyPressed(GLKeyEvent event) {
		if (getGLInputMap().matches(GLInputActions.NAVIGATE_LEFT, event)
				|| getGLInputMap().matches(GLInputActions.NAVIGATE_DOWN, event)) {
			changeValueFromUser(value - step, "Decrease slider");
			event.consume();
		} else if (getGLInputMap().matches(GLInputActions.NAVIGATE_RIGHT, event)
				|| getGLInputMap().matches(GLInputActions.NAVIGATE_UP, event)) {
			changeValueFromUser(value + step, "Increase slider");
			event.consume();
		} else if (getGLInputMap().matches(GLInputActions.NAVIGATE_HOME, event)) {
			if (mode == Mode.FIXED) {
				changeValueFromUser(minimum, "Set slider to minimum");
				event.consume();
			}
		} else if (getGLInputMap().matches(GLInputActions.NAVIGATE_END, event)) {
			if (mode == Mode.FIXED) {
				changeValueFromUser(maximum, "Set slider to maximum");
				event.consume();
			}
		}
	}

	@Override
	protected void themeChanged() {
		updateContextMenuText();
		if (localStyle == null && !getGLStyle().equals(appliedStyle))
			updatePresentation(true);
		else
			syncValueEditorStyle();
	}

	@Override
	protected void resized(float width, float height) {
		layoutValueEditor();
	}

	@Override
	protected void detached(GLParent formerParent) {
		super.detached(formerParent);
		if (formerParent == sliderGroup) {
			GLSliderGroup formerGroup = sliderGroup;
			sliderGroup = null;
			formerGroup.sliderDetached(this);
			syncValueEditorStyle();
			invalidateRecording();
		}
	}

	private void updatePresentation(boolean geometryChanged) {
		GLSliderStyle style = getGLStyle();
		appliedStyle = style;
		setIntrinsicPreferredGLSize(new GLSize(style.preferredWidth(), style.height()));
		updateColors();
		if (geometryChanged) {
			syncValueEditorStyle();
			layoutValueEditor();
			invalidateRecording();
		}
		if (sliderGroup != null)
			sliderGroup.sliderPresentationChanged(this);
	}

	private void updateColors() {
		GLSliderStyle style = getGLStyle();
		GLControlState presentationState = groupGesturePressed && isEnabled()
				? GLControlState.PRESSED : getControlState();
		backgroundParameters.setColor(style.background(presentationState));
		textParameters.setColor(style.text(presentationState));
		borderParameters.setColor(validationFeedbackActive
				? getGLTheme().textEditorStyle().invalidBorderColor()
				: style.borderColor());
		progressParameters.setOpacity(isEnabled() ? 1 : 0.45f);
		updateArrowColors();
		invalidateRecording();
		repaintGL();
	}

	private void updateArrowColors() {
		if (mode != Mode.FREE)
			return;
		GLSliderStyle style = getGLStyle();
		leftArrowBackgroundParameters.setColor(arrowBackground(style, HitPart.LEFT));
		rightArrowBackgroundParameters.setColor(arrowBackground(style, HitPart.RIGHT));
		Color icon = isEnabled() ? style.arrowColor() : style.disabledArrowColor();
		leftArrowParameters.setColor(icon);
		rightArrowParameters.setColor(icon);
		float opacity = isEnabled()
				&& (hoverPart != HitPart.NONE || pressedPart != HitPart.NONE
						|| relativeDragging) ? 1 : 0;
		leftArrowBackgroundParameters.setOpacity(opacity);
		rightArrowBackgroundParameters.setOpacity(opacity);
		leftArrowParameters.setOpacity(opacity);
		rightArrowParameters.setOpacity(opacity);
		repaintGL();
	}

	private Color arrowBackground(GLSliderStyle style, HitPart part) {
		if (!isEnabled())
			return style.arrowDisabledBackground();
		if (pressedPart == part && hoverPart == part)
			return style.arrowPressedBackground();
		if (hoverPart == part)
			return style.arrowHoverBackground();
		return style.arrowBackground();
	}

	private void updateHoverPart(GLMouseEvent event) {
		if (mode != Mode.FREE || !event.hasLocalPosition())
			return;
		HitPart next = hitPart(event.getLocalX());
		if (hoverPart == next)
			return;
		hoverPart = next;
		updateArrowColors();
	}

	private HitPart hitPart(float localX) {
		if (mode != Mode.FREE)
			return HitPart.CENTER;
		GLSliderStyle style = getGLStyle();
		float edge = style.arrowInset() + style.arrowButtonWidth();
		if (localX <= edge)
			return HitPart.LEFT;
		if (localX >= getWidth() - edge)
			return HitPart.RIGHT;
		return HitPart.CENTER;
	}

	private void setFixedValue(float localX) {
		float progress = Math.max(0, Math.min(1, localX / Math.max(1, getWidth())));
		setValue(minimum + progress * (maximum - minimum));
	}

	private void layoutValueEditor() {
		GLTextEditor editor = valueEditor;
		if (editor == null)
			return;
		editor.setBounds(0, 0, getWidth(), getHeight());
	}

	private void syncValueEditorStyle() {
		GLTextEditor editor = valueEditor;
		if (editor == null)
			return;
		GLSliderStyle sliderStyle = getGLStyle();
		GLTextEditorStyle editorStyle = getGLTheme().textEditorStyle();
		editor.setGLStyle(editorStyle.withFont(sliderStyle.font())
				.withGeometry(sliderStyle.cornerRadius(),
				sliderStyle.borderWidth(), editorStyle.height(),
				editorStyle.preferredWidth(), sliderStyle.padding()));
		GLCornerRadii radii = sliderGroup == null
				? GLCornerRadii.all(sliderStyle.cornerRadius())
				: sliderGroup.cornerRadii(this, sliderStyle.cornerRadius());
		editor.setContainerShape(radii, sliderGroup == null);
	}

	GLTextEditor createValueEditor(String initialText, boolean interactive) {
		if (!isEnabled() || valueEditor != null)
			return null;
		GLTextEditor editor = interactive ? new GLTextEditor(initialText)
				: new GLTextEditor(initialText) {
					@Override
					protected boolean isMouseTargetEnabled() {
						return false;
					}
				};
		editor.setHistoryEnabled(false);
		editor.setInputFilter(GLTextInputFormats.DECIMAL_NUMBER);
		editor.setInputVerifier(GLSlider::isValidNumber);
		editor.setValidationFailureMode(
				GLTextEditor.ValidationFailureMode.RESTORE_LAST_VALID);
		if (!interactive)
			editor.setFocusable(false);
		valueEditor = editor;
		syncValueEditorStyle();
		addGL(editor);
		layoutValueEditor();
		repaintGL();
		return editor;
	}

	void focusValueEditor(GLTextEditor editor) {
		if (valueEditor != editor)
			return;
		requestGLFocus(editor);
		editor.selectAll();
	}

	String editorValueText() {
		return valueText();
	}

	static Double parseEditorNumber(String text) {
		return parseNumber(text);
	}

	void closeValueEditor(GLTextEditor editor) {
		if (valueEditor != editor)
			return;
		valueEditor = null;
		removeGL(editor);
		repaintGL();
	}

	void showValidationFailure() {
		validationFeedbackActive = true;
		invalidateRecording();
		updateColors();
		validationFeedbackTimer.restart();
	}

	private void ensureContextMenu() {
		if (contextMenu != null)
			return;
		GLPopupMenu menu = new GLPopupMenu();
		GLMenuItem resetItem = menu.addItem(
				getGLLocalizer().text(GLUiKeys.SLIDER_RESET),
				event -> {
					double before = value;
					reset();
					recordValueChange(before, value, "Reset slider");
				});
		contextMenu = menu;
		resetMenuItem = resetItem;
	}

	private void updateContextMenuText() {
		if (resetMenuItem != null)
			resetMenuItem.setText(getGLLocalizer().text(GLUiKeys.SLIDER_RESET));
	}

	@Override
	protected void localizerChanged() {
		updateContextMenuText();
	}

	private void showContextMenu(GLMouseEvent event) {
		GLParent host = rootParent();
		if (host == null)
			return;
		GLPopupMenu menu = getContextMenu();
		resetMenuItem.setEnabled(Double.compare(value, defaultValue) != 0);
		menu.show(host, event.getScreenX(), event.getScreenY());
	}

	private GLParent rootParent() {
		GLParent current = getGLParent();
		while (current instanceof GLContainer container
				&& container.getGLParent() != null)
			current = container.getGLParent();
		return current;
	}

	private void changeValueFromUser(double next, String name) {
		double before = value;
		setValue(next);
		recordValueChange(before, value, name);
	}

	void recordValueChange(double before, double after, String name) {
		if (Double.compare(before, after) == 0)
			return;
		var manager = getGLHistoryManager();
		if (manager != null)
			manager.record(name, () -> setValue(before), () -> setValue(after));
	}

	private void beginRelativeDrag(GLMouseEvent event) {
		if (relativeDragging)
			return;
		relativeDragging = true;
		dragCenter = event.getPickResult().toScreen(getWidth() * 0.5f,
				getHeight() * 0.5f);
		pointerController = event.getPointerController();
		pointerController.setCursor(this, GLPointerCursor.HIDDEN);
	}

	void joinSliderGroup(GLSliderGroup group) {
		if (group == null)
			throw new IllegalArgumentException("Slider group must not be null");
		if (sliderGroup != null && sliderGroup != group)
			throw new IllegalStateException("Slider already belongs to another group");
		sliderGroup = group;
		syncValueEditorStyle();
		invalidateRecording();
	}

	void groupGeometryChanged() {
		syncValueEditorStyle();
		invalidateRecording();
	}

	boolean isValueEditorFocused() {
		return valueEditor != null && valueEditor.isFocused();
	}

	void setGroupGesturePressed(boolean pressed) {
		if (groupGesturePressed == pressed)
			return;
		groupGesturePressed = pressed;
		updateColors();
	}

	private void showResizeCursor(GLMouseEvent event) {
		if (mode != Mode.FREE || !isEnabled() || relativeDragging)
			return;
		pointerController = event.getPointerController();
		pointerController.setCursor(this, GLPointerCursor.HORIZONTAL_RESIZE);
	}

	private void restoreHoverCursor() {
		pointerController.clearCursor(this);
		pointerController = GLPointerController.NONE;
	}

	private double dragSensitivityFor(GLMouseEvent event) {
		int modifiers = event.getModifiersEx();
		if (getGLInputMap().hasModifiers(GLInputActions.SLIDER_PRECISE, modifiers))
			return dragSensitivity * getPreciseDragFactor();
		if (getGLInputMap().hasModifiers(GLInputActions.SLIDER_COARSE, modifiers))
			return dragSensitivity * getCoarseDragFactor();
		return dragSensitivity;
	}

	private void endRelativeDrag(GLMouseEvent event, boolean recenter) {
		Point2D.Float center = dragCenter;
		relativeDragging = false;
		dragCenter = null;
		if (recenter) {
			if (event != null && center != null)
				event.requestPointerWarp(center.x, center.y);
			pointerController.setCursor(this, GLPointerCursor.HORIZONTAL_RESIZE);
		} else {
			restoreHoverCursor();
		}
	}

	private String valueText() {
		String result = valueFormatter.apply(value);
		if (result == null)
			throw new IllegalStateException("Slider value formatter returned null");
		return result;
	}

	private void fireStateChanged() {
		ChangeEvent event = new ChangeEvent(this);
		for (ChangeListener listener : changeListeners)
			listener.stateChanged(event);
	}

	private void requireFixed() {
		if (mode != Mode.FIXED)
			throw new IllegalStateException("FREE sliders do not have a fixed range");
	}

	private static String defaultValueText(double value) {
		return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP)
				.stripTrailingZeros().toPlainString();
	}

	private static String requireText(String text) {
		if (text == null)
			throw new IllegalArgumentException("Slider text must not be null");
		return text;
	}

	private static void requireFinite(double value, String description) {
		if (!Double.isFinite(value))
			throw new IllegalArgumentException(description + " must be finite");
	}

	private static void requirePositive(double value, String description) {
		if (!Double.isFinite(value) || value <= 0)
			throw new IllegalArgumentException(
					description + " must be finite and positive");
	}

	private static double clamp(double value, double minimum, double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static boolean isValidNumber(String text) {
		return parseNumber(text) != null;
	}

	private static Double parseNumber(String text) {
		if (text == null)
			return null;
		try {
			double parsed = Double.parseDouble(text.trim().replace(',', '.'));
			return Double.isFinite(parsed) ? parsed : null;
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private enum HitPart {
		NONE,
		LEFT,
		CENTER,
		RIGHT;

		private boolean isArrow() {
			return this == LEFT || this == RIGHT;
		}
	}
}
