package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.DoubleFunction;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.rendering.GLContext2D;

/** A bounded slider whose entire track previews the color at each value.
 * Numeric keyboard input and gesture history are inherited from {@link GLSlider}.
 * The sampler is evaluated only when replaced, not on every frame.
 */
public class GLColorSlider extends GLSlider {
	private final Color[] samples = new Color[256];
	private final boolean[] inGamut = new boolean[256];
	/** Creates a channel slider with the conventional bounded-slider model. */
	public GLColorSlider(String name, double minimum, double maximum, double value) {
		super(name, minimum, maximum, value);
		setFocusable(false);
		setColorSampler(ignored -> Color.BLACK);
	}
	/** Supplies colors for actual channel values (not normalized fractions). */
	public final void setColorSampler(DoubleFunction<Color> sampler) {
		setColorSampler(sampler, ignored -> true);
	}
	/** Marks out-of-gamut samples with a contrasting striped lower edge. */
	public final void setColorSampler(DoubleFunction<Color> sampler, java.util.function.DoublePredicate gamut) {
		Objects.requireNonNull(sampler, "Color sampler");
		Objects.requireNonNull(gamut, "Gamut predicate");
		for (int i = 0; i < samples.length; i++) {
			double value=getMinimum()+(getMaximum()-getMinimum())*i/(samples.length-1);
			samples[i] = Objects.requireNonNull(sampler.apply(value));
			inGamut[i] = gamut.test(value);
		}
		invalidateRecording();
	}
	@Override protected void record(GLContext2D context) {
		float w = getWidth(), h = getHeight();
		if (w <= 0 || h <= 0) return;
		for (int i = 0; i < samples.length; i++) {
			float x = w * i / samples.length, next = w * (i + 1) / samples.length;
			for (float y = 0; y < h; y += 6) {
				int checker = (((int) (x / 6) + (int) (y / 6)) & 1) == 0 ? 210 : 140;
				Color color = samples[i];
				float a = color.getAlpha() / 255f;
				Color composite = new Color(Math.round(color.getRed() * a + checker * (1-a)),
						Math.round(color.getGreen() * a + checker * (1-a)),
						Math.round(color.getBlue() * a + checker * (1-a)));
				context.fillUIRoundRect(x, y, next-x, Math.min(6, h-y), 0, composite);
			}
			if (!inGamut[i]) context.fillUIRoundRect(x,Math.max(0,h-3),next-x,Math.min(3,h),0,
					((int)(x/4)&1)==0 ? Color.BLACK : Color.WHITE);
		}
		context.drawUIRoundRect(0, 0, w, h, 0, 1,
				getGLTheme().values().getColor(isFocused()?"palette.accent":"palette.border", Color.DARK_GRAY));
		float x = (float) ((getValue()-getMinimum()) / (getMaximum()-getMinimum())) * (w-5);
		context.drawUIRoundRect(x, 0, 5, h, 0, 2, Color.BLACK);
		context.drawUIRoundRect(x+1, 1, 3, Math.max(0, h-2), 0, 1, Color.WHITE);
	}
	@Override protected void controlMousePressed(GLMouseEvent event) { super.controlMousePressed(event); }
	@Override GLTextEditor createValueEditor(String initialText, boolean interactive) { return null; }
	@Override protected void controlMouseClicked(GLMouseEvent event) {
		if (event.getButton() != MouseEvent.BUTTON1) super.controlMouseClicked(event);
	}
	@Override protected void controlStateChanged(GLControlState oldState, GLControlState newState) {
		super.controlStateChanged(oldState,newState); invalidateRecording();
	}
	@Override protected void controlMouseDragged(GLMouseEvent event) {
		if ((event.getModifiersEx() & java.awt.event.InputEvent.SHIFT_DOWN_MASK)!=0) {
			if(isEnabled()) setValue(getValue()+event.getDeltaX()/Math.max(1,getWidth())*(getMaximum()-getMinimum())*.1);
		} else super.controlMouseDragged(event);
	}
}
