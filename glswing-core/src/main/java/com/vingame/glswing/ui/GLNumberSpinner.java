package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.DoubleFunction;

import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLStroke;

/**
 * A compact numeric editor with vertically stacked increment and decrement
 * buttons. Values may be bounded or unbounded and optionally restricted to
 * integers. User edits and repeated button presses each form one history entry.
 */
public class GLNumberSpinner extends GLLayoutGroup {
	public static final float DEFAULT_WIDTH = 120;
	public static final float DEFAULT_HEIGHT = 28;
	public static final float DEFAULT_BUTTON_WIDTH = 24;

	private final CopyOnWriteArrayList<ChangeListener> changeListeners =
			new CopyOnWriteArrayList<>();
	private final NumberEditor editor = new NumberEditor();
    GLNumberSpinnerGroup inputGroup;
	private final StepButton incrementButton = new StepButton(1);
	private final StepButton decrementButton = new StepButton(-1);
	private double minimum;
	private double maximum;
	private double value;
	private double step = 1;
    private String unit="";
    private double defaultValue;
    private Runnable resetAction;
    private GLPopupMenu contextMenu;
    private GLMenuItem resetMenuItem;
	private boolean integerValuesOnly;
	private boolean enabled = true;
	private boolean synchronizing;
	private DoubleFunction<String> valueFormatter = GLNumberSpinner::defaultValueText;

	/** Creates an unbounded spinner. */
	public GLNumberSpinner(double value) {
		this(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, value);
	}

	/** Creates a spinner whose value is clamped to the supplied range. */
	public GLNumberSpinner(double minimum, double maximum, double value) {
		validateRange(minimum, maximum);
		requireFinite(value, "Value");
		this.minimum = minimum;
		this.maximum = maximum;
		this.value = clamp(value);
        this.defaultValue = this.value;

		editor.setPreferredGLSize(DEFAULT_WIDTH - DEFAULT_BUTTON_WIDTH,
				DEFAULT_HEIGHT);
		editor.setHistoryEnabled(false);
		editor.setValidationFailureMode(
				GLTextEditor.ValidationFailureMode.RESTORE_LAST_VALID);
		incrementButton.setPreferredGLSize(DEFAULT_BUTTON_WIDTH,
				DEFAULT_HEIGHT * 0.5f);
		decrementButton.setPreferredGLSize(DEFAULT_BUTTON_WIDTH,
				DEFAULT_HEIGHT * 0.5f);
		addGL(editor, GLConstraints.at(0, 0).span(1, 2)
				.weight(1, 1).fillBoth().connectAll());
		addGL(incrementButton, GLConstraints.at(1, 0)
				.fillBoth().connectAll());
		addGL(decrementButton, GLConstraints.at(1, 1)
				.fillBoth().connectAll());
		applyThemeMetrics();
		updateEditorPolicy();
		syncEditor();
		updateEnabledState();
	}

    /** Non-editable suffix, excluded from selection, caret movement and horizontal scrolling. */
    public final String getUnit(){return unit;}
    public final void setUnit(String unit){
        if(unit==null||unit.indexOf('\n')>=0||unit.indexOf('\r')>=0)throw new IllegalArgumentException("Single-line unit required");
        this.unit=unit;applyThemeMetrics();editor.invalidateRecording();
    }
    public final double getDefaultValue(){return defaultValue;}
    public final void setDefaultValue(double value){requireFinite(value,"Default value");defaultValue=clamp(value);}
    /** Optional reset of a compound control; null restores the normal single-value reset. */
    public final void setResetAction(Runnable action){resetAction=action;}
    public final void reset(){
        if(resetAction!=null)resetAction.run();else setValue(defaultValue);
        syncEditor();
    }
    /** Persistent extensible context menu, also available over the editor and step buttons. */
    public final GLPopupMenu getContextMenu(){
        if(contextMenu==null){
            contextMenu=new GLPopupMenu();
            resetMenuItem=contextMenu.addItem(getGLLocalizer().text(GLUiKeys.NUMBER_RESET),e->{
                double before=value;reset();recordValueChange(before,value,getGLLocalizer().text(GLUiKeys.NUMBER_RESET));
            });
        }
        return contextMenu;
    }
    public final GLMenuItem getResetMenuItem(){getContextMenu();return resetMenuItem;}
    @Override protected void localizerChanged(){
        super.localizerChanged();
        if(resetMenuItem!=null)resetMenuItem.setText(getGLLocalizer().text(GLUiKeys.NUMBER_RESET));
    }
    private void showContextMenu(GLMouseEvent event){
        if(!enabled)return;
        GLParent host=getGLParent();
        while(host instanceof com.vingame.glswing.component.GLContainer c && c.getGLParent()!=null)host=c.getGLParent();
        if(host==null)return;
        var menu=getContextMenu();resetMenuItem.setEnabled(resetAction!=null||Double.compare(value,clamp(defaultValue))!=0);
        menu.show(host,event.getScreenX(),event.getScreenY());event.consume();
    }
    public final double getValue() { return value; }

	/** Assigns a value without creating a history entry. */
	public final void setValue(double value) {
		requireFinite(value, "Value");
		applyValue(value, true);
	}

	public final double getMinimum() { return minimum; }
	public final double getMaximum() { return maximum; }

	/** Changes the accepted range and clamps the current value if necessary. */
	public final void setRange(double minimum, double maximum) {
		validateRange(minimum, maximum);
		if (integerValuesOnly && Math.ceil(minimum) > Math.floor(maximum))
			throw new IllegalArgumentException(
					"Integer-only spinner range must contain an integer");
		if (this.minimum == minimum && this.maximum == maximum) return;
		this.minimum = minimum;
		this.maximum = maximum;
		updateEditorPolicy();
		applyValue(value, true);
	}

	public final double getStep() { return step; }

	public final void setStep(double step) {
		if (!Double.isFinite(step) || step <= 0)
			throw new IllegalArgumentException(
					"Spinner step must be finite and positive");
		this.step = step;
	}

	public final boolean isIntegerValuesOnly() { return integerValuesOnly; }

	/** Restricts editing, assignment and stepping to whole values. */
	public final void setIntegerValuesOnly(boolean value) {
		if (integerValuesOnly == value) return;
		if (value && Math.ceil(minimum) > Math.floor(maximum))
			throw new IllegalStateException(
					"The spinner range does not contain an integer");
		integerValuesOnly = value;
		updateEditorPolicy();
		applyValue(this.value, true);
	}

	public final DoubleFunction<String> getValueFormatter() { return valueFormatter; }

	/** Sets the text representation used outside an active edit. */
	public final void setValueFormatter(DoubleFunction<String> formatter) {
		if (formatter == null)
			throw new IllegalArgumentException(
					"Spinner value formatter must not be null");
		valueFormatter = formatter;
		syncEditor();
	}

	/** Exposes the embedded editor for accessibility and local customization. */
	public final GLTextEditor getEditor() { return editor; }
	public final GLButton getIncrementButton() { return incrementButton; }
	public final GLButton getDecrementButton() { return decrementButton; }
	public final void increment() { setValue(value + step); }
	public final void decrement() { setValue(value - step); }

	public final void addChangeListener(ChangeListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("Change listener must not be null");
		changeListeners.addIfAbsent(listener);
	}

	public final void removeChangeListener(ChangeListener listener) {
		changeListeners.remove(listener);
	}

	/** Enables or disables the editor and both step buttons together. */
	public final void setEnabled(boolean enabled) {
		if (this.enabled == enabled) return;
		this.enabled = enabled;
		updateEnabledState();
	}

	public final boolean isEnabled() { return enabled; }

	@Override protected void themeChanged() {
		super.themeChanged();
		applyThemeMetrics();
	}

	private void applyThemeMetrics() {
		GLThemeValues values = getGLTheme().values();
		float height = values.getFloat("numberSpinner.height", DEFAULT_HEIGHT);
		float width = values.getFloat("numberSpinner.preferredWidth", DEFAULT_WIDTH);
		float buttonWidth = Math.min(width, values.getFloat(
				"numberSpinner.buttonWidth", DEFAULT_BUTTON_WIDTH));
		float radius = values.getFloat("numberSpinner.cornerRadius",
				getGLTheme().buttonStyle().cornerRadius());
		float border = values.getFloat("numberSpinner.borderWidth",
				getGLTheme().buttonStyle().borderWidth());
		GLInsets padding = values.getInsets("numberSpinner.padding",
				getGLTheme().textEditorStyle().padding());
		editor.setPreferredGLSize(Math.max(1, width - buttonWidth), height);
		incrementButton.setPreferredGLSize(buttonWidth, height * 0.5f);
		decrementButton.setPreferredGLSize(buttonWidth, height * 0.5f);
		GLTextEditorStyle editorStyle = getGLTheme().textEditorStyle();
		editor.setBaseStyle(editorStyle.withGeometry(radius, border, height,
				Math.max(1, width - buttonWidth), padding));
		GLButtonStyle buttonStyle = getGLTheme().buttonStyle();
		incrementButton.setGLStyle(buttonStyle.withCornerRadius(radius)
				.withBorder(border, buttonStyle.borderColor()).withHeight(height * 0.5f));
		decrementButton.setGLStyle(buttonStyle.withCornerRadius(radius)
				.withBorder(border, buttonStyle.borderColor()).withHeight(height * 0.5f));
	}

	private void updateEditorPolicy() {
		editor.setInputFilter(integerValuesOnly
				? GLTextInputFormats.INTEGER_NUMBER
				: GLTextInputFormats.DECIMAL_NUMBER);
		editor.setInputVerifier(text -> isValidEditorValue(text) && (inputGroup==null || inputGroup.valid(this,text)));
	}

	boolean isValidEditorValue(String text) {
		Double parsed = parse(text);
		return parsed != null && parsed >= minimum && parsed <= maximum
				&& (!integerValuesOnly || parsed == Math.rint(parsed));
	}

	private void commitEditor() {
		if (synchronizing) return;
		Double parsed = parse(editor.getText());
		if (parsed == null) return;
        if(inputGroup!=null && inputGroup.commit(this,parsed))return;
		double before = value;
		applyValue(parsed, true);
		recordValueChange(before, value,
				getGLLocalizer().text(GLUiKeys.NUMBER_EDIT));
	}

	private void changeFromUser(double candidate, String historyName) {
		double before = value;
		applyValue(candidate, true);
		recordValueChange(before, value, historyName);
	}

	private void applyValue(double candidate, boolean syncText) {
		double next = integerValuesOnly ? Math.rint(candidate) : candidate;
		next = clamp(next);
		if (Double.compare(value, next) == 0) {
			if (syncText && !editor.isFocused()) syncEditor();
			updateEnabledState();
			return;
		}
		value = next;
		if (syncText) syncEditor();
		updateEnabledState();
		ChangeEvent event = new ChangeEvent(this);
		for (ChangeListener listener : changeListeners)
			listener.stateChanged(event);
	}

	private void updateEnabledState() {
		editor.setEnabled(enabled);
		incrementButton.setEnabled(enabled && value < effectiveMaximum());
		decrementButton.setEnabled(enabled && value > effectiveMinimum());
	}

	void syncEditor() {
		String text = valueFormatter.apply(value);
		if (text == null)
			throw new IllegalStateException("Spinner value formatter returned null");
		synchronizing = true;
		try { editor.setText(text); }
		finally { synchronizing = false; }
	}

	private void recordValueChange(double before, double after, String name) {
		if (Double.compare(before, after) == 0) return;
		var history = getGLHistoryManager();
		if (history != null)
			history.record(name, () -> setValue(before), () -> setValue(after));
	}

	private double clamp(double candidate) {
		return Math.max(effectiveMinimum(), Math.min(effectiveMaximum(), candidate));
	}

	private double effectiveMinimum() {
		return integerValuesOnly ? Math.ceil(minimum) : minimum;
	}

	private double effectiveMaximum() {
		return integerValuesOnly ? Math.floor(maximum) : maximum;
	}

	private static Double parse(String text) {
		try {
			double parsed = Double.parseDouble(text.trim().replace(',', '.'));
			return Double.isFinite(parsed) ? parsed : null;
		} catch (RuntimeException invalid) { return null; }
	}

	private static String defaultValueText(double value) {
		return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP)
				.stripTrailingZeros().toPlainString();
	}

	private static void validateRange(double minimum, double maximum) {
		if (Double.isNaN(minimum) || Double.isNaN(maximum)
				|| !(maximum > minimum))
			throw new IllegalArgumentException(
					"Spinner maximum must exceed its minimum");
	}

	private static void requireFinite(double value, String description) {
		if (!Double.isFinite(value))
			throw new IllegalArgumentException(description + " must be finite");
	}

	private final class NumberEditor extends GLTextEditor {
		private NumberEditor() {
            addActionListener(event -> commitEditor());
            addTextChangeListener(event->{if(!synchronizing&&inputGroup!=null)inputGroup.mirror(GLNumberSpinner.this);});
            addCancelListener(event->{if(inputGroup!=null)inputGroup.cancel();});
            addValidationFailureListener(event->{if(inputGroup!=null)inputGroup.cancel();});
        }
        @Override protected void controlMousePressed(GLMouseEvent event){
            if(inputGroup!=null&&isEnabled()&&event.getButton()==MouseEvent.BUTTON1&&event.hasLocalPosition())inputGroup.pressed(GLNumberSpinner.this,event);
            super.controlMousePressed(event);
        }
        @Override protected void controlMouseDragged(GLMouseEvent event){
            if(inputGroup!=null&&event.hasLocalPosition()&&inputGroup.dragged(GLNumberSpinner.this,event))return;
            super.controlMouseDragged(event);
        }
        @Override protected void controlMouseReleased(GLMouseEvent event){
            super.controlMouseReleased(event);if(inputGroup!=null)inputGroup.released(GLNumberSpinner.this);
        }
        @Override protected void controlMouseCaptureLost(GLMouseEvent event){
            if(inputGroup!=null)inputGroup.cancel();super.controlMouseCaptureLost(event);
        }
        @Override protected void detached(GLParent parent){
            if(inputGroup!=null)inputGroup.cancel();super.detached(parent);
        }
        private GLTextEditorStyle baseStyle;
        private GLTextEditorStyle baseStyle(){return baseStyle==null?super.getGLStyle():baseStyle;}
        void setBaseStyle(GLTextEditorStyle style){baseStyle=style;updateUnitPadding();}
        private String visibleUnit(){return GLTextFitter.ellipsize(getGLFont(baseStyle().font()),unit==null?"":unit,Math.max(0,getWidth()-baseStyle().padding().horizontal()-12));}
        private void updateUnitPadding(){
            if(baseStyle==null)return;
            var base=baseStyle;String text=visibleUnit();float space=text.isEmpty()?0:getGLFont(base.font()).measureText(text)+4;
            var pad=base.padding();
            setGLStyle(base.withGeometry(base.cornerRadius(),base.borderWidth(),base.height(),base.preferredWidth(),new GLInsets(pad.top(),pad.left(),pad.bottom(),pad.right()+space)));
        }
        @Override protected void resized(float w,float h){updateUnitPadding();super.resized(w,h);}
        @Override protected void record(GLContext2D context){
            super.record(context);String text=visibleUnit();if(text.isEmpty())return;
            var style=baseStyle();var font=getGLFont(style.font());
            float x=getWidth()-style.padding().right()-font.measureText(text);
            float baseline=(getHeight()-font.getLineHeight())*.5f+font.getAscent();
            var previous=context.getMouseTarget();context.clearMouseTarget();
            try{
                context.drawUITextShadow(font,text,x,baseline,getGLTheme().textShadow("textEditor"));
                context.drawUIText(font,text,x,baseline,isEnabled()?style.textColor():style.disabledTextColor());
            }finally{if(previous!=null)context.setMouseTarget(previous,0,0);}
        }

        @Override protected void controlMouseClicked(GLMouseEvent event){
            if(event.getButton()==MouseEvent.BUTTON3){showContextMenu(event);return;}
            super.controlMouseClicked(event);
        }

		@Override protected void controlKeyPressed(GLKeyEvent event) {
			if (getGLInputMap().matches(GLInputActions.NAVIGATE_UP, event)) {
				changeFromUser(value + step,
						getGLLocalizer().text(GLUiKeys.NUMBER_INCREMENT));
				event.consume();
			} else if (getGLInputMap().matches(GLInputActions.NAVIGATE_DOWN, event)) {
				changeFromUser(value - step,
						getGLLocalizer().text(GLUiKeys.NUMBER_DECREMENT));
				event.consume();
			} else super.controlKeyPressed(event);
		}
	}

	private final class StepButton extends GLButton {
		private final int direction;
		private final Timer repeat = new Timer(70, event -> applyStep());
		private boolean pointerGesture;
		private double gestureStart;

		private StepButton(int direction) {
			super("");
			this.direction = direction;
			repeat.setInitialDelay(350);
			addActionListener(event -> {
				if (!pointerGesture)
					changeFromUser(value + direction * step,
							stepHistoryName());
			});
			updateToolTip();
		}

		private void applyStep() { if(isEnabled())applyValue(value + direction * step, true); else finishGesture(); }

		@Override protected void controlFocusChanged(boolean focused) {
			if(!focused)finishGesture();
			super.controlFocusChanged(focused);
		}

		@Override protected void controlMousePressed(GLMouseEvent event) {
			super.controlMousePressed(event);
			if (!isEnabled() || event.getButton() != MouseEvent.BUTTON1) return;
			pointerGesture = true;
			gestureStart = value;
			applyStep();
			repeat.start();
		}

		@Override protected void controlMouseClicked(GLMouseEvent event) {
			if(event.getButton()==MouseEvent.BUTTON3)showContextMenu(event);
            // Left clicks are applied on press so holding can repeat.
		}

		@Override protected void controlMouseReleased(GLMouseEvent event) {
			finishGesture();
			super.controlMouseReleased(event);
		}

		@Override protected void controlMouseCaptureLost(GLMouseEvent event) {
			finishGesture();
			super.controlMouseCaptureLost(event);
		}

		@Override protected void controlEnabledChanged(boolean enabled) {
			if (!enabled) finishGesture();
			super.controlEnabledChanged(enabled);
		}

		@Override protected void detached(GLParent formerParent) {
			finishGesture();
			super.detached(formerParent);
		}

		@Override protected void localizerChanged() {
			super.localizerChanged();
			updateToolTip();
		}

		private void finishGesture() {
			repeat.stop();
			if (!pointerGesture) return;
			pointerGesture = false;
			recordValueChange(gestureStart, value, stepHistoryName());
		}

		private String stepHistoryName() {
			return getGLLocalizer().text(direction > 0
					? GLUiKeys.NUMBER_INCREMENT : GLUiKeys.NUMBER_DECREMENT);
		}

		private void updateToolTip() {
			setToolTipText(getGLLocalizer().text(direction > 0
					? GLUiKeys.NUMBER_INCREMENT : GLUiKeys.NUMBER_DECREMENT));
		}

		@Override protected void record(GLContext2D context) {
			super.record(context);
			float centerX = getWidth() * 0.5f;
			float centerY = getHeight() * 0.5f;
			GLStroke previous = context.getStroke();
			try {
				context.setStroke(new GLStroke(getGLTheme().values().getFloat(
						"numberSpinner.arrowStrokeWidth", 1.5f)));
				Color color = getGLStyle().text(getControlState());
				context.drawUILine(centerX - 3, centerY + direction * 2,
						centerX, centerY - direction * 2, color);
				context.drawUILine(centerX, centerY - direction * 2,
						centerX + 3, centerY + direction * 2, color);
			} finally { context.setStroke(previous); }
		}
	}
}
