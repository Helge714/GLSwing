package com.vingame.glswing.ui;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.rendering.GLContext2D;

/** Embeddable color editor with channel previews, numeric input and screen sampling.
 * RGB channels use 0..255, hue uses degrees, other unsigned channels use 0..1
 * (OKLCH chroma 0..0.4). Alpha is independent of the selected model.
 */
public class GLColorPicker extends GLContainer {
	private Color color, original;
	private GLColorModel model = GLColorModel.RGB;
	private GLColorModel outputModel = GLColorModel.RGB;
	private double[] channels;
	private boolean syncing;
	private GLPipettePreview pipettePreview = GLPipettePreview.DEFAULT;
	private final GLComboBox<GLColorModel> models = new GLComboBox<>(List.of(GLColorModel.values()));
	private final GLComboBox<GLColorModel> outputModels = new GLComboBox<>(List.of(GLColorModel.values()));
	private final GLColorSlider[] sliders = new GLColorSlider[4];
	private final GLLabel[] labels = new GLLabel[4];
	private final ChannelEditor[] numbers = new ChannelEditor[4];
	private final GLLayoutGroup channelLayout = new GLLayoutGroup();
	private final GLLayoutGroup alphaLayout = new GLLayoutGroup();
	private final GLSliderGroup channelSliders = new GLSliderGroup() {
		@Override protected void recordValues(String name,java.util.Map<GLSlider,Double> before,
				java.util.Map<GLSlider,Double> after) {
			double[] previous=channels.clone();
			for(var entry:before.entrySet()) for(int i=0;i<3;i++) if(sliders[i]==entry.getKey())
				previous[i]=model.minimum(i)+entry.getValue()*(model.maximum(i)-model.minimum(i));
			recordChange(model.color(previous,color.getAlpha()/255d),color);
		}
	};
	private final java.util.Set<Integer> selectedChannels = new java.util.LinkedHashSet<>();
	private int editorSource = -1;
	private final GLInputGroup<Color> channelInput = new GLInputGroup<>(this::getGLHistoryManager);
	private final GLTextEditor expression = new GLTextEditor();
	private final EyedropperButton pipette = new EyedropperButton();
	private final ComparisonSwatch comparison = new ComparisonSwatch();
	private final GLLabel status = new GLLabel("");
	private final CopyOnWriteArrayList<Consumer<Color>> listeners = new CopyOnWriteArrayList<>();
	/** Creates a picker initialized to opaque white. */
	public GLColorPicker() { this(Color.WHITE); }
	/** Creates a picker initialized to the supplied color. */
	public GLColorPicker(Color initial) {
		setIntrinsicPreferredGLSize(new com.vingame.glswing.layout.GLSize(480,230));
		color = original = Objects.requireNonNull(initial); channels=model.channels(color);
		addGL(models); addGL(outputModels); addGL(expression); addGL(pipette); addGL(comparison); addGL(status);
		addGL(channelLayout); addGL(alphaLayout);
		channelSliders.setSharedFrameVisible(false);
		channelLayout.addGL(channelSliders,com.vingame.glswing.layout.GLConstraints.at(0,0)
				.span(1,6).weight(1,1).fillBoth().connectAll());
		channelInput.add(this::getColor,this::setColor);
		models.setGLHistoryManager(new GLHistoryManager());
		outputModels.setGLHistoryManager(new GLHistoryManager());
		models.addActionListener(e -> { if (!syncing) setColorModel(models.getSelectedItem()); });
		outputModels.addActionListener(e -> { if(!syncing) {outputModel=outputModels.getSelectedItem();refreshExpression();} });
		for (int i=0;i<4;i++) {
			final int index=i;
			labels[i]=new GLLabel(""); labels[i].setAlignment(GLLabel.Alignment.RIGHT);
			labels[i].setVerticalAlignment(GLLabel.VerticalAlignment.CENTER);
			numbers[i]=new ChannelEditor(i);
			sliders[i]=new GLColorSlider("",0,1,0) {
				@Override void recordValueChange(double before, double after, String name) {
					if (Double.compare(before,after)==0) return;
					double[] previous=channels.clone();
					if(index<3) previous[index]=model.minimum(index)+before*(model.maximum(index)-model.minimum(index));
					recordChange(model.color(previous,index==3?before:color.getAlpha()/255d),color);
				}
			};
			sliders[i].setStep(.005);
			sliders[i].addChangeListener(e -> {
				if (syncing) return;
				if (index<3) channels[index]=model.minimum(index)+sliders[index].getValue()
						*(model.maximum(index)-model.minimum(index));
				color=model.color(channels,index==3?sliders[3].getValue():color.getAlpha()/255d);
				refresh(); fire();
			});
			numbers[i].addTextChangeListener(e -> {
				if(syncing)return;
				java.util.Set<Integer> affected=affectedChannels(index);
				mirrorEditorText(index,affected);
				try {previewChannel(index,affected);}
				catch(IllegalArgumentException incomplete) {setStatus(text("invalid"));}
			});
			numbers[i].addActionListener(e -> {
				try {
					previewChannel(index,affectedChannels(index));
					recordChange(numbers[index].editBaseline(),color);
					numbers[index].finishEdit(color);
					numbers[index].requestGLFocus(null);
				} catch (IllegalArgumentException failure) { setStatus(text("invalid")); }
			});
			numbers[i].addValidationFailureListener(e -> numbers[index].restoreEditBaseline());
			numbers[i].addCancelListener(e -> numbers[index].restoreEditBaseline());
			addGL(labels[i]);
			if(i<3) {
				channelSliders.addSlider(sliders[i]);
				numbers[i].setPreferredGLSize(new com.vingame.glswing.layout.GLSize(84,28));
				channelLayout.addGL(numbers[i],com.vingame.glswing.layout.GLConstraints.at(1,i*2)
						.span(1,2).weight(0,1).fillBoth().connectAll());
				channelLayout.addGL(new StepButton(i,1),com.vingame.glswing.layout.GLConstraints.at(2,i*2)
						.weight(0,1).fillBoth().connectAll());
				channelLayout.addGL(new StepButton(i,-1),com.vingame.glswing.layout.GLConstraints.at(2,i*2+1)
						.weight(0,1).fillBoth().connectAll());
			} else {
				numbers[i].setPreferredGLSize(new com.vingame.glswing.layout.GLSize(84,28));
				alphaLayout.addGL(sliders[i],com.vingame.glswing.layout.GLConstraints.at(0,0)
						.span(1,2).weight(1,1).fillBoth().connectAll());
				alphaLayout.addGL(numbers[i],com.vingame.glswing.layout.GLConstraints.at(1,0)
						.span(1,2).weight(0,1).fillBoth().connectAll());
				alphaLayout.addGL(new StepButton(i,1),com.vingame.glswing.layout.GLConstraints.at(2,0)
						.weight(0,1).fillBoth().connectAll());
				alphaLayout.addGL(new StepButton(i,-1),com.vingame.glswing.layout.GLConstraints.at(2,1)
						.weight(0,1).fillBoth().connectAll());
			}
		}
		expression.addActionListener(e -> {
			try { Color before=color; setColor(GLThemeValues.parseColor(expression.getText())); recordChange(before,color); }
			catch (IllegalArgumentException failure) { setStatus(text("invalid")); }
		});
		pipette.addActionListener(e -> GLScreenColorSampler.start(this, pipettePreview, sampled -> {
			Color before=color;
			setColor(new Color(sampled.getRed(),sampled.getGreen(),sampled.getBlue(),color.getAlpha()));
			recordChange(before,color);
		}, failure -> setStatus(text("captureUnavailable"))));
		localizerChanged(); refresh();
	}
	/** Returns the currently edited sRGB color, including alpha. */
	public final Color getColor() { return color; }
	/** Updates the edited color and notifies listeners when it changed. */
	public final void setColor(Color value) {
		Objects.requireNonNull(value); boolean changed=!color.equals(value);
		color=value; channels=model.channels(value); refresh(); if(changed) fire();
	}
	/** Returns the model used for channel editing. */
	public final GLColorModel getColorModel() { return model; }
	/** Changes the editing model without changing the represented color. */
	public final void setColorModel(GLColorModel value) {
		model=Objects.requireNonNull(value); channels=model.channels(color); refresh();
	}
	/** Returns the model used to format the output expression. */
	public final GLColorModel getOutputColorModel() { return outputModel; }
	/** Changes the output model without changing the represented color. */
	public final void setOutputColorModel(GLColorModel value) {
		outputModel=Objects.requireNonNull(value);refresh();
	}
	/** Sets the comparison swatch without changing the edited color. */
	public final void setOriginalColor(Color value) {
		original=Objects.requireNonNull(value);comparison.invalidateRecording();invalidateRecording();
	}
	/** Enables or disables screen sampling while retaining the picker button. */
	public final void setPipetteEnabled(boolean enabled) { pipette.setEnabled(enabled); }
	/** Returns the pointer-following screen-sampling preview configuration. */
	public final GLPipettePreview getPipettePreview() { return pipettePreview; }
	/** Configures the preview used by the next eyedropper session. */
	public final void setPipettePreview(GLPipettePreview preview) { pipettePreview = Objects.requireNonNull(preview); }
	/** Adds a listener notified after the represented color changes. */
	public final void addColorChangeListener(Consumer<Color> listener) { listeners.add(Objects.requireNonNull(listener)); }
	/** Removes a previously registered color listener. */
	public final void removeColorChangeListener(Consumer<Color> listener) { listeners.remove(listener); }
	/** Formats the current color with the selected output model. */
	public final String getColorExpression() {
		double[] outputChannels=outputModel==model?channels:outputModel.channels(color);
		return outputModel.format(outputChannels,color.getAlpha()/255d);
	}
	private void fire() { for(var listener:listeners) listener.accept(color); }
	private void recordChange(Color before, Color after) {
		if(before==null || before.equals(after)) return;
		var history=getGLHistoryManager();
		if(history!=null) history.record(text("change"),()->setColor(before),()->setColor(after));
	}
	private java.util.Set<Integer> affectedChannels(int source) {
		return source<3 && selectedChannels.contains(source) && !selectedChannels.isEmpty()
				? java.util.Set.copyOf(selectedChannels) : java.util.Set.of(source);
	}
	private void applyNormalized(int source,double normalized) {
		applyNormalized(source,normalized,java.util.Set.of());
	}
	private void applyNormalized(int source,double normalized,java.util.Set<Integer> preservedEditors) {
		double clamped=Math.max(0,Math.min(1,normalized));
		Color changed;
		if(source==3) {
			changed=new Color(color.getRed(),color.getGreen(),color.getBlue(),(int)Math.round(clamped*255));
		} else {
			double[] values=channels.clone();
			for(int index:affectedChannels(source))
				values[index]=model.minimum(index)+clamped*(model.maximum(index)-model.minimum(index));
			changed=model.color(values,color.getAlpha()/255d);
		}
		boolean differs=!color.equals(changed);color=changed;channels=model.channels(changed);
		refresh(preservedEditors);if(differs)fire();
	}
	private void previewChannel(int index,java.util.Set<Integer> affected) {
		if(syncing)return;
		double value=Double.parseDouble(numbers[index].getText().trim());
		double min=index==3?0:model.minimum(index),max=index==3?1:model.maximum(index);
		if(!Double.isFinite(value)||value<min||value>max)throw new IllegalArgumentException();
		applyNormalized(index,(value-min)/(max-min),affected);
	}
	private void mirrorEditorText(int source,java.util.Set<Integer> affected) {
		String value=numbers[source].getText();int caret=numbers[source].getCaretPosition();
		boolean previous=syncing;syncing=true;
		try {
			for(int index=0;index<4;index++) {
				if(index!=source&&affected.contains(index)) {
					numbers[index].setText(value);numbers[index].setCaretPosition(Math.min(caret,value.length()));
				}
			}
		} finally {syncing=previous;}
	}
	private void adjustNormalized(int source,double delta) {
		if(source==3) {applyNormalized(3,color.getAlpha()/255d+delta);return;}
		double min=model.minimum(source),range=model.maximum(source)-min;
		double normalized=(channels[source]-min)/range;
		double[] changed=channels.clone();
		for(int index:affectedChannels(source)) {
			double indexMin=model.minimum(index),indexRange=model.maximum(index)-indexMin;
			double current=(channels[index]-indexMin)/indexRange;
			changed[index]=indexMin+Math.max(0,Math.min(1,current+delta))*indexRange;
		}
		setColor(model.color(changed,color.getAlpha()/255d));
	}
	private void selectChannels(int anchor,int target,boolean additive) {
		if(!additive) selectedChannels.clear();
		for(int i=Math.min(anchor,target);i<=Math.max(anchor,target);i++) selectedChannels.add(i);
		if(editorSource>=0 && editorSource<3 && selectedChannels.contains(editorSource)
				&& numbers[editorSource].isFocused())
			mirrorEditorText(editorSource,java.util.Set.copyOf(selectedChannels));
		updateChannelHighlight();
	}
	private void updateChannelHighlight() {
		java.awt.geom.Area selected=new java.awt.geom.Area();
		for(int channel:selectedChannels)if(channel<3)
			selected.add(new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Float(
					0,channel*28,Math.max(0,channelLayout.getWidth()),28)));
		channelLayout.setHighlightShape(selected);
	}
	private void finishChannelEditingIfUnfocused() {
		for(int index=0;index<3;index++)if(numbers[index].isFocused())return;
		selectedChannels.clear();channelLayout.clearHighlightShape();editorSource=-1;refresh();
	}
	private String text(String key) { return getGLLocalizer().text("glswing.colorPicker."+key); }
	private void setStatus(String message) {
		boolean visible=message!=null&&!message.isBlank();
		status.setText(visible?message:""); status.setVisible(visible);
	}
	@Override protected void localizerChanged() {
		pipette.setToolTipText(text("pipette"));
		if(channels!=null) setStatus(model.isInGamut(channels)?null:text("outOfGamut"));
	}
	private void refresh() { refresh(java.util.Set.of()); }
	private void refresh(java.util.Set<Integer> preservedEditors) {
		syncing=true;
		try {
			models.setSelectedItem(model);
			outputModels.setSelectedItem(outputModel);
			for(int i=0;i<4;i++) {
				final int index=i; double[] fixed=channels.clone(); GLColorModel selected=model;
				double alpha=color.getAlpha()/255d;
				labels[i].setText(i==3?"Alpha":model.channelName(i));
				double value=i==3?alpha:channels[i];
				sliders[i].setValue(i==3?alpha:(value-model.minimum(i))/(model.maximum(i)-model.minimum(i)));
				sliders[i].setColorSampler(position -> {
					double[] sample=fixed.clone();
					if(index<3) sample[index]=selected.minimum(index)+position*(selected.maximum(index)-selected.minimum(index));
					return selected.color(sample,index==3?position:alpha);
				}, position -> {
					double[] sample=fixed.clone();
					if(index<3) sample[index]=selected.minimum(index)+position*(selected.maximum(index)-selected.minimum(index));
					return selected.isInGamut(sample);
				});
				if(!preservedEditors.contains(i)) numbers[i].setText(String.format(Locale.ROOT,"%.4f",value));
			}
			refreshExpression();
			setStatus(model.isInGamut(channels)?null:text("outOfGamut"));
			comparison.invalidateRecording();
			invalidateRecording();
		} finally { syncing=false; }
	}
	private void refreshExpression() {
		boolean previous=syncing;syncing=true;
		try {expression.setText(getColorExpression());}
		finally {syncing=previous;}
	}
	@Override protected void resized(float width,float height) {
		float right=8, sliderX=92, fieldWidth=84, buttonWidth=24;
		float fieldX=Math.max(sliderX+40,width-right-fieldWidth-buttonWidth);
		pipette.setBounds(8,6,36,32);
		comparison.setBounds(sliderX,8,72,28);
		models.setBounds(fieldX,8,Math.max(1,width-right-fieldX),28);
		for(int i=0;i<4;i++) {
			float y=i<3?46+i*28:138; labels[i].setBounds(8,y,80,28);
		}
		channelLayout.setBounds(sliderX,46,Math.max(120,width-right-sliderX),84);
		alphaLayout.setBounds(sliderX,138,Math.max(120,width-right-sliderX),28);
		channelLayout.layoutGL(); channelSliders.layoutGL();
		updateChannelHighlight();
		alphaLayout.layoutGL();
		expression.setBounds(sliderX,176,Math.max(40,fieldX-sliderX-4),28);
		outputModels.setBounds(fieldX,176,Math.max(1,width-right-fieldX),28);
		status.setBounds(sliderX,210,Math.max(1,width-right-sliderX),20);
	}
	@Override protected void record(GLContext2D context) {
		// Child controls render the complete picker.
	}
	private final class StepButton extends GLButton {
		private final int channel,direction;
		private int modifiers;
		private boolean pointerGesture;
		private final javax.swing.Timer repeat=new javax.swing.Timer(70,e->step());
		StepButton(int channel,int direction) {
			super(""); this.channel=channel;this.direction=direction;
			setPreferredGLSize(new com.vingame.glswing.layout.GLSize(24,21));
			repeat.setInitialDelay(350);
			addActionListener(e -> {
				if (!pointerGesture) { channelInput.begin(text("change")); step(); channelInput.commit(); }
			});
		}
		private void step() {
			double factor=1;
			var input=getGLInputMap();
			if(input.hasModifiers(com.vingame.glswing.input.GLInputActions.SLIDER_PRECISE,modifiers)) factor=input.getValue(com.vingame.glswing.input.GLInputActions.SLIDER_PRECISE_FACTOR,.1);
			else if(input.hasModifiers(com.vingame.glswing.input.GLInputActions.SLIDER_COARSE,modifiers)) factor=input.getValue(com.vingame.glswing.input.GLInputActions.SLIDER_COARSE_FACTOR,10);
			double range=channel==3?1:model.maximum(channel)-model.minimum(channel);
			double unit=channel==3?.01:model.maximum(channel)>=255?1:.01;
			adjustNormalized(channel,direction*unit*factor/range);
		}
		@Override protected void controlMousePressed(com.vingame.glswing.input.GLMouseEvent e) {
			super.controlMousePressed(e);
			if(e.getButton()!=java.awt.event.MouseEvent.BUTTON1) return;
			pointerGesture=true; modifiers=e.getModifiersEx(); channelInput.begin(text("change")); step(); repeat.start();
		}
		@Override protected void controlMouseDragged(com.vingame.glswing.input.GLMouseEvent e) { modifiers=e.getModifiersEx(); }
		private void finish() {repeat.stop();channelInput.commit();}
		@Override protected void controlMouseReleased(com.vingame.glswing.input.GLMouseEvent e) {finish();super.controlMouseReleased(e);pointerGesture=false;}
		@Override protected void controlKeyPressed(com.vingame.glswing.input.GLKeyEvent e) { modifiers=e.getModifiersEx();super.controlKeyPressed(e); }
		@Override protected void controlMouseClicked(com.vingame.glswing.input.GLMouseEvent e) { /* Applied on press, including repeat. */ }
		@Override protected void controlEnabledChanged(boolean enabled) { if(!enabled) {finish();pointerGesture=false;}super.controlEnabledChanged(enabled); }
		@Override protected void controlMouseCaptureLost(com.vingame.glswing.input.GLMouseEvent e) {finish();super.controlMouseCaptureLost(e);}
		@Override protected void detached(com.vingame.glswing.component.GLParent parent) {finish();super.detached(parent);}
		@Override protected void record(GLContext2D context) {
			super.record(context); float x=getWidth()/2,y=getHeight()/2;
			var old=context.getStroke(); context.setStroke(new com.vingame.glswing.rendering.GLStroke(1.5f));
			Color c=getGLTheme().values().getColor("palette.text",Color.WHITE);
			context.drawUILine(x-3,y+direction*2,x,y-direction*2,c);
			context.drawUILine(x,y-direction*2,x+3,y+direction*2,c);context.setStroke(old);
		}
	}
	private final class ComparisonSwatch extends GLControl {
		ComparisonSwatch() { super(false); }
		@Override protected void record(GLContext2D context) {
			float w=getWidth(),h=getHeight(),radius=Math.min(5,h/2);
			context.beginUIRoundedClip(0,0,w,h,com.vingame.glswing.rendering.GLCornerRadii.all(radius));
			try {
				for(float x=0;x<w;x+=6)for(float y=0;y<h;y+=6)
					context.fillUIRoundRect(x,y,Math.min(6,w-x),Math.min(6,h-y),0,
							(((int)(x/6)+(int)(y/6))&1)==0?Color.LIGHT_GRAY:Color.GRAY);
				context.fillUIRoundRect(0,0,w/2,h,0,original);
				context.fillUIRoundRect(w/2,0,w-w/2,h,0,color);
			} finally {context.endClip();}
			Color border=getGLTheme().values().getColor("palette.border",Color.DARK_GRAY);
			context.drawUIRoundRect(0,0,w,h,radius,1,border);
			context.drawUILine(w/2,0,w/2,h,border);
		}
	}
	private static final class EyedropperButton extends GLButton {
		private static final com.vingame.glswing.rendering.GLIcon ICON =
				com.vingame.glswing.rendering.GLIcon.fromSvgResource("com/vingame/glswing/icons/eyedropper.svg");
		EyedropperButton() { super(""); }
		@Override protected void record(GLContext2D context) {
			Color normal=getGLTheme().values().getColor("palette.text",Color.WHITE);
			Color accent=getGLTheme().values().getColor("palette.accent",normal);
			Color tint=!isEnabled()?new Color(normal.getRed(),normal.getGreen(),normal.getBlue(),100)
					:getControlState()==GLControlState.NORMAL?normal:accent;
			if(getControlState()!=GLControlState.NORMAL || isFocused()) {
				Color halo=new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),isFocused()?55:30);
				context.fillUIRoundRect(2,1,Math.max(0,getWidth()-4),Math.max(0,getHeight()-2),8,halo);
			}
			float size=Math.min(22,Math.min(getWidth(),getHeight()));
			context.drawUIIcon(ICON,(getWidth()-size)/2,(getHeight()-size)/2,size,size,tint);
			if(isFocused()) context.drawUIRoundRect(3,2,Math.max(0,getWidth()-6),Math.max(0,getHeight()-4),
					7,1.25f,accent,com.vingame.glswing.rendering.GLStroke.WidthSpace.SCREEN);
		}
	}
	private final class ChannelEditor extends GLTextEditor {
		private final int channel;
		private Color editBaseline;
		private int selectionAnchor;
		private boolean selectionCandidate,groupSelecting;
		private float selectionDx,selectionDy;
		ChannelEditor(int channel) {
			this.channel=channel;setHistoryEnabled(false);
			setInputVerifier(value -> {
				try {
					double number=Double.parseDouble(value.trim());
					double min=channel==3?0:model.minimum(channel);
					double max=channel==3?1:model.maximum(channel);
					return Double.isFinite(number)&&number>=min&&number<=max;
				} catch(RuntimeException invalid) {return false;}
			});
			setValidationFailureMode(ValidationFailureMode.RESTORE_LAST_VALID);
		}
		Color editBaseline() { return editBaseline; }
		void finishEdit(Color value) { editBaseline=value; }
		void restoreEditBaseline() {if(editBaseline!=null)setColor(editBaseline);}
		@Override protected void controlMousePressed(com.vingame.glswing.input.GLMouseEvent event) {
			super.controlMousePressed(event);
			if(channel>=3 || event.getButton()!=java.awt.event.MouseEvent.BUTTON1)return;
			editorSource=channel;
			boolean additive=(event.getModifiersEx()&java.awt.event.InputEvent.CTRL_DOWN_MASK)!=0;
			if(additive && selectedChannels.contains(channel)) {
				selectedChannels.remove(channel);
				selectionCandidate=groupSelecting=false;
			} else {
				selectionAnchor=channel;selectionCandidate=true;groupSelecting=false;
				selectionDx=selectionDy=0;selectChannels(channel,channel,additive);
			}
		}
		@Override protected void controlMouseDragged(com.vingame.glswing.input.GLMouseEvent event) {
			selectionDx+=Math.abs(event.getDeltaX());selectionDy+=Math.abs(event.getDeltaY());
			if(selectionCandidate && selectionDy>=3 && selectionDy>selectionDx*1.25f)
				groupSelecting=true;
			else if(selectionCandidate && selectionDx>=3 && selectionDx>selectionDy*1.25f)
				selectionCandidate=false;
			if(groupSelecting && event.hasLocalPosition()) {
				int target=Math.max(0,Math.min(2,(int)Math.floor((channel*28+event.getLocalY())/28)));
				selectChannels(selectionAnchor,target,false);
				selectAll();
			} else super.controlMouseDragged(event);
		}
		@Override protected void controlMouseReleased(com.vingame.glswing.input.GLMouseEvent event) {
			selectionCandidate=groupSelecting=false;super.controlMouseReleased(event);
		}
		@Override protected void controlFocusChanged(boolean focused) {
			if(focused) {
				editBaseline=color;editorSource=channel;
				if(channel<3&&selectedChannels.isEmpty())selectChannels(channel,channel,false);
			}
			super.controlFocusChanged(focused);
			if(!focused) {
				editBaseline=color;
				if(channel<3)javax.swing.SwingUtilities.invokeLater(
						GLColorPicker.this::finishChannelEditingIfUnfocused);
				else {editorSource=-1;refresh();}
			}
		}
		@Override protected void record(GLContext2D context) {
			super.record(context);
		}
	}
}
