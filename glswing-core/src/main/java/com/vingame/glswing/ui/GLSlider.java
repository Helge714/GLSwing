package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.DoubleFunction;

import javax.accessibility.AccessibleRole;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

/** A numeric slider with independent value constraints and interaction style. */
public class GLSlider extends GLControl {
	private static final Pattern DECORATED_NUMBER = Pattern.compile(
			"^[^\\d+\\-.,]*([+\\-]?(?:(?:\\d+(?:[.,]\\d*)?)|(?:[.,]\\d+))"
			+ "(?:[eE][+\\-]?\\d+)?)[^\\d]*$");
	private static final Pattern PARTIAL_DECORATED_NUMBER = Pattern.compile(
			"^[^\\d+\\-.,]*[+\\-]?(?:(?:\\d+(?:[.,]\\d*)?)|(?:[.,]\\d+))"
			+ "(?:[eE][+\\-]?\\d*)?[^\\d]*$");

	public enum Mode {
		FIXED,
		FREE
	}
	/** Visual and pointer interaction presented independently of {@link Mode}. */
	public enum Presentation {
		/** An absolute position represented by a progress fill. */
		BAR,
		/** Increment/decrement buttons with relative dragging in the center. */
		BUTTONS
	}

	public static final float DEFAULT_WIDTH = 180;
	public static final float DEFAULT_HEIGHT = 22;
	public static final double DEFAULT_DRAG_SENSITIVITY = 0.1;
	public static final double DEFAULT_PRECISE_DRAG_FACTOR = 0.1;
	public static final double DEFAULT_COARSE_DRAG_FACTOR = 10;
	static final float DRAG_THRESHOLD = 2;

	private final Mode mode;
	private Presentation presentation;
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
	private final Timer arrowRepeat = new Timer(70, event -> repeatArrow());
	private boolean arrowRepeated;
	private void repeatArrow() {
		if (!isEnabled() || !pressedPart.isArrow()) { arrowRepeat.stop(); return; }
		arrowRepeated=true;
		setValue(value + (pressedPart == HitPart.LEFT ? -step : step));
	}

	private String text;
	private double minimum;
	private double maximum;
	private double displayMinimum;
	private double displayMaximum;
	private double visibleMinimum;
	private double visibleMaximum;
	private double value;
	private double defaultValue;
	private double step = 1;
	private boolean integerValuesOnly;
	private double dragSensitivity = DEFAULT_DRAG_SENSITIVITY;
	private double preciseDragFactor = Double.NaN;
	private double coarseDragFactor = Double.NaN;
	private DoubleFunction<String> valueFormatter = GLSlider::defaultValueText;
	private String unit = "";

	/** Non-editable suffix, painted after the numeric value and outside its document. */
	public final String getUnit() { return unit; }
	public final void setUnit(String unit) {
		if (unit == null || unit.indexOf('\n') >= 0 || unit.indexOf('\r') >= 0)
			throw new IllegalArgumentException("A single-line slider unit is required");
		if (this.unit.equals(unit)) return;
		this.unit = unit;
		syncValueEditorStyle(); layoutValueEditor(); invalidateRecording();
	}
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

	protected GLSlider(String text, Mode mode, Presentation presentation,
			double minimum, double maximum, double displayMinimum,
			double displayMaximum, double value) {
		super(true);
		this.text = requireText(text);
		this.mode = mode;
		this.presentation = java.util.Objects.requireNonNull(presentation);
		this.minimum = minimum;
		this.maximum = maximum;
		this.displayMinimum = displayMinimum;
		this.displayMaximum = displayMaximum;
		visibleMinimum = displayMinimum;
		visibleMaximum = displayMaximum;
		this.value = value;
		updateVisibleRange();
		defaultValue = value;
		validationFeedbackTimer = new Timer(450, event -> {
			validationFeedbackActive = false;
			invalidateRecording();
			updateColors();
		});
		validationFeedbackTimer.setRepeats(false);
		updatePresentation(true);
	}

	/** Constructor for specialized bounded slider presentations. */
	protected GLSlider(String text, double minimum, double maximum, double value) {
		this(text, Mode.FIXED, Presentation.BAR, minimum, maximum,
				minimum, maximum, value);
		requireFinite(minimum, "Minimum");
		requireFinite(maximum, "Maximum");
		requireFinite(value, "Value");
		if (!(maximum > minimum))
			throw new IllegalArgumentException("Slider maximum must exceed minimum");
		this.value = clamp(value, minimum, maximum);
		defaultValue = this.value;
	}

	public static GLSlider fixed(String text, double minimum,
			double maximum, double value) {
		return fixed(text, minimum, maximum, value, Presentation.BAR);
	}

	/** Creates a bounded slider with independently selected presentation. */
	public static GLSlider fixed(String text, double minimum,
			double maximum, double value, Presentation presentation) {
		requireFinite(minimum, "Minimum");
		requireFinite(maximum, "Maximum");
		if (!(maximum > minimum))
			throw new IllegalArgumentException("Slider maximum must exceed minimum");
		requireFinite(value, "Value");
		return new GLSlider(text, Mode.FIXED, presentation, minimum, maximum,
				minimum, maximum, clamp(value, minimum, maximum));
	}

    /** Subclass entry point for unbounded controls with application gesture handling. */
    protected GLSlider(String text, double value) {
        this(text, Mode.FREE, Presentation.BUTTONS, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 1, value);
        requireFinite(value, "Value");
    }
	public static GLSlider free(String text, double value) {
		return free(text, value, Presentation.BUTTONS);
	}

	/** Creates an unbounded slider using the requested presentation. */
	public static GLSlider free(String text, double value,
			Presentation presentation) {
		requireFinite(value, "Value");
		return new GLSlider(text, Mode.FREE, presentation,
				Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 1, value);
	}

	/** Creates an unbounded bar with a stable baseline display range. */
	public static GLSlider free(String text, double displayMinimum,
			double displayMaximum, double value) {
		validateRange(displayMinimum, displayMaximum, "Display range");
		requireFinite(value, "Value");
		return new GLSlider(text, Mode.FREE, Presentation.BAR,
				Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
				displayMinimum, displayMaximum, value);
	}

	public final Mode getMode() {
		return mode;
	}

	public final Presentation getPresentation() { return presentation; }

	/** Switches between progress-bar and increment-button interaction. */
	public final void setPresentation(Presentation value) {
		value = java.util.Objects.requireNonNull(value, "Slider presentation");
		if (presentation == value) return;
		if (relativeDragging) endRelativeDrag(null, false);
		else restoreHoverCursor();
		presentation = value;
		hoverPart = pressedPart = armedArrow = HitPart.NONE;
		updatePresentation(false);
	}

	public final String getText() {
		return text;
	}

	@Override
	protected String defaultName() {
		return text;
	}
	@Override protected AccessibleRole defaultAccessibleRole() {
		return AccessibleRole.SLIDER;
	}
	@Override protected Number accessibleCurrentValue() { return value; }
	@Override protected boolean setAccessibleCurrentValue(Number next) {
		if (next == null || !isEnabled()) return false;
		setValue(next.doubleValue());
		return true;
	}
	@Override protected Number accessibleMinimumValue() {
		return Double.isFinite(minimum) ? minimum : null;
	}
	@Override protected Number accessibleMaximumValue() {
		return Double.isFinite(maximum) ? maximum : null;
	}

	public final void setText(String text) {
		text = requireText(text);
		if (this.text.equals(text))
			return;
		String oldText = this.text;
		this.text = text;
		fireAccessibleNameChange(oldText, text);
		invalidateRecording();
	}

	public final double getValue() {
		return value;
	}

	public final void setValue(double value) {
		requireFinite(value, "Value");
		double resolved = resolveValue(value);
		if (Double.compare(this.value, resolved) == 0) {
			updateVisibleRange();
			return;
		}
		double old = this.value;
		this.value = resolved;
		fireAccessibleValueChange(old, resolved);
		updateVisibleRange();
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
		this.defaultValue = resolveValue(defaultValue);
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
		validateRange(minimum, maximum, "Slider range");
		if (integerValuesOnly && Math.ceil(minimum) > Math.floor(maximum))
			throw new IllegalArgumentException(
					"Integer-only slider range must contain an integer");
		if (this.minimum == minimum && this.maximum == maximum)
			return;
		if (sliderGroup != null)
			sliderGroup.validateRangeChange(this, minimum, maximum);
		this.minimum = minimum;
		this.maximum = maximum;
		displayMinimum = minimum;
		displayMaximum = maximum;
		visibleMinimum = minimum;
		visibleMaximum = maximum;
		defaultValue = resolveValue(defaultValue);
		setValue(value);
		invalidateRecording();
	}

	/**
	 * Configures optional value constraints for a FREE slider. A {@code null}
	 * side remains unbounded; the independent display range is unchanged.
	 */
	public final void setValueBounds(Double minimum, Double maximum) {
		requireFree();
		double nextMinimum = minimum == null ? Double.NEGATIVE_INFINITY : minimum;
		double nextMaximum = maximum == null ? Double.POSITIVE_INFINITY : maximum;
		if (minimum != null) requireFinite(minimum, "Slider minimum");
		if (maximum != null) requireFinite(maximum, "Slider maximum");
		if (!(nextMaximum > nextMinimum))
			throw new IllegalArgumentException("Slider maximum must exceed minimum");
		if (integerValuesOnly && Math.ceil(nextMinimum) > Math.floor(nextMaximum))
			throw new IllegalArgumentException(
					"Integer-only slider bounds must contain an integer");
		this.minimum = nextMinimum; this.maximum = nextMaximum;
		defaultValue = resolveValue(defaultValue);
		setValue(value);
	}

	public final java.util.OptionalDouble getValueMinimum() {
		return Double.isFinite(minimum) ? java.util.OptionalDouble.of(minimum)
				: java.util.OptionalDouble.empty();
	}

	public final java.util.OptionalDouble getValueMaximum() {
		return Double.isFinite(maximum) ? java.util.OptionalDouble.of(maximum)
				: java.util.OptionalDouble.empty();
	}

	public final double getStep() {
		return step;
	}

	public final void setStep(double step) {
		if (!Double.isFinite(step) || step <= 0)
			throw new IllegalArgumentException("Slider step must be finite and positive");
		this.step = step;
	}

	/** Returns whether every assigned or edited value is rounded to an integer. */
	public final boolean isIntegerValuesOnly() { return integerValuesOnly; }

	/** Restricts values to integers and makes bar dragging advance in whole steps. */
	public final void setIntegerValuesOnly(boolean value) {
		if (integerValuesOnly == value) return;
		if (value && Math.ceil(minimum) > Math.floor(maximum))
			throw new IllegalStateException(
					"The slider range does not contain an integer");
		cancelValueEditing();
		integerValuesOnly = value;
		defaultValue = resolveValue(defaultValue);
		setValue(this.value);
		invalidateRecording();
	}

	/** Baseline range rendered by a FREE bar before temporary expansion. */
	public final void setDisplayRange(double minimum, double maximum) {
		requireFree();
		validateRange(minimum, maximum, "Display range");
		displayMinimum = minimum;
		displayMaximum = maximum;
		visibleMinimum = minimum;
		visibleMaximum = maximum;
		updateVisibleRange();
		invalidateRecording();
	}

	public final double getDisplayMinimum() {
		return mode == Mode.FIXED ? minimum : displayMinimum;
	}
	public final double getDisplayMaximum() {
		return mode == Mode.FIXED ? maximum : displayMaximum;
	}
	/** Current rendered minimum, including a FREE bar's temporary expansion. */
	public final double getVisibleMinimum() {
		return mode == Mode.FIXED ? minimum : visibleMinimum;
	}
	/** Current rendered maximum, including a FREE bar's temporary expansion. */
	public final double getVisibleMaximum() {
		return mode == Mode.FIXED ? maximum : visibleMaximum;
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
		if (isValueEditing()) return;
		GLSliderStyle style = getGLStyle();
		float width = getWidth();
		float height = getHeight();
		if (width <= 0 || height <= 0)
			return;

		GLCornerRadii radii = sliderGroup == null
				? GLCornerRadii.all(layoutCornerRadius(style.cornerRadius()))
				: sliderGroup.cornerRadii(this, layoutCornerRadius(style.cornerRadius()));
		float border = sliderGroup == null ? layoutBorderWidth(style.borderWidth()) : 0;
		boolean compositeBar = sliderGroup == null && presentation == Presentation.BAR
				&& border > 0;
		if (!compositeBar) context.withParameters(backgroundParameters, () -> {
			if (border > 0)
				context.fillAndDrawUIRoundRect(0, 0, width, height,
						layoutCornerRadius(style.cornerRadius()), border, Color.WHITE,
						style.borderColor(), GLStroke.WidthSpace.SCREEN);
			else
				context.fillUIRoundRect(0, 0, width, height, radii, Color.WHITE);
		});

		if (presentation == Presentation.BAR) {
			double barMinimum = getVisibleMinimum();
			double barMaximum = getVisibleMaximum();
			float progress = barProgress(value, barMinimum, barMaximum);
			Color progressLight = sliderGroup == null ? style.progressLightColor()
					: sliderGroup.progressColor(this, style.progressLightColor(),
							style.progressDarkColor(), false);
			Color progressDark = sliderGroup == null ? style.progressDarkColor()
					: sliderGroup.progressColor(this, style.progressLightColor(),
							style.progressDarkColor(), true);
			GLControlState state = getControlState();
			if (sliderGroup == null && isEnabled()
					&& (state == GLControlState.HOVERED
							|| state == GLControlState.PRESSED)) {
				progressLight = getGLTheme().values().getColor(
						"slider.focusProgressLight", progressLight);
				progressDark = getGLTheme().values().getColor(
						"slider.focusProgressDark", progressDark);
			}
			Color resolvedProgressLight = progressLight;
			Color resolvedProgressDark = progressDark;
			if (compositeBar) {
				GLControlState presentationState = groupGesturePressed && isEnabled()
						? GLControlState.PRESSED : getControlState();
				float progressOpacity = isEnabled() ? 1 : 0.45f;
				Color borderColor = validationFeedbackActive
						? getGLTheme().textEditorStyle().invalidBorderColor()
						: style.borderColor();
				context.fillUISlider(0, 0, width, height, layoutCornerRadius(style.cornerRadius()),
						progress, border, style.background(presentationState),
						withOpacity(resolvedProgressLight, progressOpacity),
						withOpacity(resolvedProgressDark, progressOpacity), borderColor,
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
							Math.max(0, layoutCornerRadius(style.cornerRadius()) - progressInset), progress,
							resolvedProgressLight, resolvedProgressDark);
				} else
					context.fillUIProgressRoundRect(0, 0, width, height,
							radii.topLeft(), radii.bottomLeft(), progress,
							resolvedProgressLight, resolvedProgressDark);
			});
		} else {
			recordArrowButton(context, style, radii, true);
			recordArrowButton(context, style, radii, false);
		}

		if (!compositeBar && sliderGroup == null && border > 0
				&& validationFeedbackActive)
			context.withParameters(borderParameters, () ->
					context.drawUIRoundRect(0, 0, width, height,
							layoutCornerRadius(style.cornerRadius()), layoutBorderWidth(style.borderWidth()), Color.WHITE,
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
		float effectiveSliderRadius = Math.min(layoutCornerRadius(style.cornerRadius()),
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
		if (presentation == Presentation.BUTTONS) {
			contentLeft += style.arrowInset() + style.arrowButtonWidth();
			contentRight -= style.arrowInset() + style.arrowButtonWidth();
		}
		contentRight -= unitSpace();
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

	private float unitRight() {
		var style = getGLStyle();
		return getWidth() - style.padding().right() - (!isValueEditing() && presentation == Presentation.BUTTONS
				? style.arrowInset() + style.arrowButtonWidth() : 0);
	}
	private String visibleUnit() {
		return GLTextFitter.ellipsize(getGLFont(getGLStyle().font()), unit,
				Math.max(0, unitRight() - getGLStyle().padding().left() - 8));
	}
	private float unitSpace() {
		String visible = visibleUnit();
		return visible.isEmpty() ? 0 : getGLFont(getGLStyle().font()).measureText(visible) + 4;
	}
	@Override protected void renderChildren(GLContext2D context) {
		super.renderChildren(context);
		String visible = visibleUnit();
		if (visible.isEmpty()) return;
		var font = getGLFont(getGLStyle().font());
		float x = Math.max(0, unitRight() - font.measureText(visible));
		float baseline = (getHeight() - font.getLineHeight()) * .5f + font.getAscent();
		var previous = context.getMouseTarget();
		context.clearMouseTarget();
		try {
			context.drawUITextShadow(font, visible, x, baseline, getGLTheme().textShadow("slider"));
			context.drawUIText(font, visible, x, baseline, getGLStyle().text(getControlState()));
		} finally {
			if (previous != null) context.setMouseTarget(previous, 0, 0);
		}
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

	@Override protected String toolTipTextAt(GLMouseEvent event) {
		String explicit = super.toolTipTextAt(event);
		if (explicit != null) return explicit;
		GLSliderStyle style = getGLStyle();
		GLFont font = getGLFont(style.font());
		float left = style.padding().left(), right = getWidth() - style.padding().right();
		if (presentation == Presentation.BUTTONS) {
			left += style.arrowInset() + style.arrowButtonWidth();
			right -= style.arrowInset() + style.arrowButtonWidth();
		}
		float width = Math.max(0, right - left - unitSpace());
		String value = valueText();
		if (GLTextFitter.overflowToolTip(font, value, width) != null) return value;
		float valueWidth = font.measureText(value);
		float labelWidth = Math.max(0, width - valueWidth - (value.isEmpty() ? 0 : 6));
		return GLTextFitter.overflowToolTip(font, text, labelWidth);
	}

	@Override
	protected void controlMouseEntered(GLMouseEvent event) {
		updateHoverPart(event);
		showResizeCursor(event);
	}

	@Override
	protected void controlMouseMoved(GLMouseEvent event) {
		updateHoverPart(event);
		showResizeCursor(event);
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
		arrowRepeated=false;
		arrowRepeat.stop();
		if (pressedPart.isArrow()) { arrowRepeat.setInitialDelay(350); arrowRepeat.start(); }
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
		if (presentation == Presentation.BAR && event.hasLocalPosition()) {
			setBarValue(event.getLocalX());
			manipulated = true;
		} else if (presentation == Presentation.BUTTONS
				&& pressedPart == HitPart.CENTER) {
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
		return presentation == Presentation.BAR || pressedPart == HitPart.CENTER;
	}

	@Override
	protected void controlMouseReleased(GLMouseEvent event) {
		arrowRepeat.stop();
		if (presentation == Presentation.BUTTONS) {
			HitPart releasedPart = event.hasLocalPosition()
					? hitPart(event.getLocalX()) : HitPart.NONE;
			if (!arrowRepeated && !dragOccurred && pressedPart.isArrow()
					&& releasedPart == pressedPart)
				armedArrow = pressedPart;
			if (relativeDragging)
				endRelativeDrag(event, true);
		}
		if (sliderGroup != null && !pressedPart.isArrow())
			sliderGroup.pointerReleased(this, true);
		else
			recordValueChange(gestureStartValue, value, "Drag slider");
		pressedPart = HitPart.NONE;
		updateArrowColors();
	}

	@Override
	protected void controlMouseCaptureLost(GLMouseEvent event) {
		arrowRepeat.stop();
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
		if (presentation == Presentation.BUTTONS && action == HitPart.LEFT)
			changeValueFromUser(value - step, "Decrease slider");
		else if (presentation == Presentation.BUTTONS && action == HitPart.RIGHT)
			changeValueFromUser(value + step, "Increase slider");
		else if (!dragOccurred && event.hasLocalPosition()
				&& hitPart(event.getLocalX()) == HitPart.CENTER)
			startValueEditing();
		dragOccurred = false;
	}

	@Override
	protected void controlFocusChanged(boolean focused) {
		if (!focused) {
			arrowRepeat.stop();
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
			if (Double.isFinite(minimum)) {
				changeValueFromUser(minimum, "Set slider to minimum");
				event.consume();
			}
		} else if (getGLInputMap().matches(GLInputActions.NAVIGATE_END, event)) {
			if (Double.isFinite(maximum)) {
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
		arrowRepeat.stop();
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
		if (presentation != Presentation.BUTTONS)
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
		if (presentation != Presentation.BUTTONS || !event.hasLocalPosition())
			return;
		HitPart next = hitPart(event.getLocalX());
		if (hoverPart == next)
			return;
		hoverPart = next;
		updateArrowColors();
	}

	private HitPart hitPart(float localX) {
		if (presentation != Presentation.BUTTONS)
			return HitPart.CENTER;
		GLSliderStyle style = getGLStyle();
		float edge = style.arrowInset() + style.arrowButtonWidth();
		if (localX <= edge)
			return HitPart.LEFT;
		if (localX >= getWidth() - edge)
			return HitPart.RIGHT;
		return HitPart.CENTER;
	}

    /** Normalizes finite ranges without overflowing when their endpoints have opposite signs. */
    static float barProgress(double value,double minimum,double maximum) {
        if(value<=minimum)return 0;
        if(value>=maximum)return 1;
        double span=maximum-minimum;
        double fraction=Double.isFinite(span)?(value-minimum)/span
                :(value*.5-minimum*.5)/(maximum*.5-minimum*.5);
        return (float)Math.max(0,Math.min(1,fraction));
    }

	private void setBarValue(float localX) {
		float progress = Math.max(0, Math.min(1, localX / Math.max(1, getWidth())));
		double barMinimum = getVisibleMinimum();
		double barMaximum = getVisibleMaximum();
		setValue(progress<=0?barMinimum:progress>=1?barMaximum:
                (1.0-progress)*barMinimum+progress*barMaximum);
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
		GLInsets padding = sliderStyle.padding();
		padding = new GLInsets(padding.top(), padding.left(), padding.bottom(), padding.right() + unitSpace());
		editor.setGLStyle(editorStyle.withFont(sliderStyle.font())
				.withGeometry(sliderStyle.cornerRadius(),
				sliderStyle.borderWidth(), editorStyle.height(),
				editorStyle.preferredWidth(), padding));
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
		// Formatted values may carry a unit or symbol (for example "64 %" or
		// "12 €"). Final validation still requires exactly one finite number.
		editor.setInputFilter(GLSlider::isPartialFormattedNumber);
		editor.setInputVerifier(integerValuesOnly ? GLSlider::isValidInteger
				: GLSlider::isValidNumber);
		editor.setValidationFailureMode(
				GLTextEditor.ValidationFailureMode.RESTORE_LAST_VALID);
		if (!interactive)
			editor.setFocusable(false);
		valueEditor = editor;
		syncValueEditorStyle();
		addGL(editor);
		layoutValueEditor();
		invalidateRecording();
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
		invalidateRecording();
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
		if (presentation != Presentation.BUTTONS || !isEnabled() || relativeDragging)
			return;
		pointerController = event.getPointerController();
		pointerController.setCursor(this, hoverPart.isArrow()
				? GLPointerCursor.DEFAULT : GLPointerCursor.HORIZONTAL_RESIZE);
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

	private void requireFree() {
		if (mode != Mode.FREE)
			throw new IllegalStateException(
					"Only FREE sliders have an independent display range");
	}

	private void updateVisibleRange() {
		if (mode == Mode.FIXED) {
			visibleMinimum = minimum;
			visibleMaximum = maximum;
			return;
		}
		if (value >= displayMinimum && value <= displayMaximum) {
			visibleMinimum = displayMinimum;
			visibleMaximum = displayMaximum;
		} else if (value < displayMinimum) {
			if (visibleMaximum != displayMaximum
					|| visibleMinimum >= displayMinimum)
				visibleMinimum = displayMinimum;
			visibleMinimum = Math.min(visibleMinimum, value);
			visibleMaximum = displayMaximum;
		} else {
			if (visibleMinimum != displayMinimum
					|| visibleMaximum <= displayMaximum)
				visibleMaximum = displayMaximum;
			visibleMinimum = displayMinimum;
			visibleMaximum = Math.max(visibleMaximum, value);
		}
	}

	private double resolveValue(double candidate) {
		if (!integerValuesOnly)
			return clamp(candidate, minimum, maximum);
		double rounded = Math.round(candidate);
		return clamp(rounded, Math.ceil(minimum), Math.floor(maximum));
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

	private static void validateRange(double minimum, double maximum,
			String description) {
		requireFinite(minimum, description + " minimum");
		requireFinite(maximum, description + " maximum");
		if (!(maximum > minimum))
			throw new IllegalArgumentException(
					description + " maximum must exceed its minimum");
	}

	private static double clamp(double value, double minimum, double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static boolean isValidNumber(String text) {
		return parseNumber(text) != null;
	}

	private static boolean isPartialFormattedNumber(String text) {
		if (text == null) return false;
		String value = text.trim();
		return value.isEmpty() || value.equals("+") || value.equals("-")
				|| PARTIAL_DECORATED_NUMBER.matcher(value).matches();
	}

	private static boolean isValidInteger(String text) {
		Double parsed = parseNumber(text);
		return parsed != null && parsed == Math.rint(parsed);
	}

	private static Double parseNumber(String text) {
		if (text == null)
			return null;
		Matcher decorated = DECORATED_NUMBER.matcher(text.trim());
		if (!decorated.matches()) return null;
		try {
			double parsed = Double.parseDouble(decorated.group(1).replace(',', '.'));
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


