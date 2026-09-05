package com.vingame.glswing.ui;

import java.awt.Color;
import java.util.Objects;
import java.util.function.Consumer;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLDialog;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.rendering.GLContext2D;

/** Compact color button opening an owner-modal, theme-aware color picker. */
public class GLColorChooser extends GLButton {
	private Color color;
	private GLPipettePreview pipettePreview = GLPipettePreview.DEFAULT;
	private final java.util.concurrent.CopyOnWriteArrayList<Consumer<Color>> listeners=new java.util.concurrent.CopyOnWriteArrayList<>();
	/** Creates a chooser initialized to opaque white. */
	public GLColorChooser() { this(Color.WHITE); }
	/** Creates a chooser initialized to the supplied color. */
	public GLColorChooser(Color color) {
		super("");
		this.color=Objects.requireNonNull(color);
		setIntrinsicPreferredGLSize(new com.vingame.glswing.layout.GLSize(72,28));
		addActionListener(event -> show(this, this.color, GLColorModel.RGB, pipettePreview, picker -> {
			Color before=this.color, after=picker.getColor(); setColor(after);
			var history=getGLHistoryManager();
			if(history!=null && !before.equals(after)) history.record(
					getGLLocalizer().text("glswing.colorPicker.change"),()->setColor(before),()->setColor(after));
		}));
	}
	/** Returns the currently selected sRGB color, including alpha. */
	public final Color getColor() { return color; }
	/** Returns the configuration used by the screen eyedropper preview. */
	public final GLPipettePreview getPipettePreview() { return pipettePreview; }
	/** Configures the screen eyedropper preview for subsequent picker sessions. */
	public final void setPipettePreview(GLPipettePreview preview) { pipettePreview=Objects.requireNonNull(preview); }
	/** Updates the selected color and notifies listeners when it changed. */
	public final void setColor(Color value) {
		Objects.requireNonNull(value); if(color.equals(value)) return;
		color=value; invalidateRecording(); for(var listener:listeners) listener.accept(value);
	}
	/** Adds a listener notified after the selected color changes. */
	public final void addColorChangeListener(Consumer<Color> listener) { listeners.add(Objects.requireNonNull(listener)); }
	/** Removes a previously registered color listener. */
	public final void removeColorChangeListener(Consumer<Color> listener) { listeners.remove(listener); }
	@Override protected void record(GLContext2D context) {
		super.record(context);
		context.fillUIRoundRect(6,6,Math.max(0,getWidth()-12),Math.max(0,getHeight()-12),2,color);
	}
	/** Opens a picker and calls {@code accepted} only after explicit confirmation. */
	public static GLDialog show(GLParent owner, Color color, GLColorModel model, Consumer<GLColorPicker> accepted) {
		return show(owner, color, model, GLPipettePreview.DEFAULT, accepted);
	}
	/** Opens a picker with customized eyedropper preview content. */
	public static GLDialog show(GLParent owner, Color color, GLColorModel model,
			GLPipettePreview preview, Consumer<GLColorPicker> accepted) {
		return show(owner, color, model, model, GLColorExpressionFormat.DEFAULT,
				preview, accepted);
	}
	/** Opens a picker with independent editing model and output formatting. */
	public static GLDialog show(GLParent owner, Color color, GLColorModel model,
			GLColorModel outputModel, GLColorExpressionFormat format,
			GLPipettePreview preview, Consumer<GLColorPicker> accepted) {
		Objects.requireNonNull(owner); Objects.requireNonNull(accepted);
		GLColorPicker picker=new GLColorPicker(color); picker.setColorModel(model);
		picker.setOutputColorModel(Objects.requireNonNull(outputModel));
		picker.setColorExpressionFormat(Objects.requireNonNull(format));
		picker.setPipettePreview(preview);
		picker.setGLHistoryManager(new GLHistoryManager());
		GLButton apply=new GLButton(owner.getGLLocalizer().text("glswing.colorPicker.apply"));
		GLButton cancel=new GLButton(owner.getGLLocalizer().text("glswing.colorPicker.cancel"));
		GLDialogButtonBar actions=new GLDialogButtonBar().addButton(cancel).addButton(apply);
		GLContainer content=new GLContainer() {
			@Override protected void record(GLContext2D context) { }
			@Override protected void resized(float w,float h) {
				picker.setBounds(0,0,w,230); actions.setBounds(12,238,Math.max(1,w-24),28);
			}
		};
		content.addGL(picker); content.addGL(actions);
		GLDialog dialog=new GLDialog(owner.getGLLocalizer().text("glswing.colorPicker.title"),content);
		dialog.setDecoration(GLDialog.Decoration.THEMED);
		dialog.setSize(480,282); dialog.setResizable(false);
		apply.addActionListener(event->{accepted.accept(picker); dialog.close();});
		cancel.addActionListener(event->dialog.close());
		dialog.show(owner); return dialog;
	}
}
