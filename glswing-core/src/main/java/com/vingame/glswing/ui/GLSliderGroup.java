package com.vingame.glswing.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLColorInterpolation;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLCornerRadii;
import com.vingame.glswing.rendering.GLStroke;

/**
 * A vertical stack of compatible sliders with one shared outline. Dragging
 * vertically across another row links that slider for the current gesture.
 */
public class GLSliderGroup extends GLContainer {
	private static final float DIRECTION_DOMINANCE = 1.25f;

	private final List<GLSlider> sliders = new ArrayList<>();
	private final Set<GLSlider> gestureSliders = new LinkedHashSet<>();
	private final Map<GLSlider, Double> gestureStartValues =
			new LinkedHashMap<>();
	private GLSlider gestureSource;
	private float gestureAnchorY;
	private float gestureHorizontalDistance;
	private boolean gestureSliding;
	private boolean changingGroupRange;
	private LinkedEditorSession linkedEditorSession;
	private boolean sharedFrameVisible = true;

	public GLSliderGroup(GLSlider... sliders) {
		if (sliders != null)
			for (GLSlider slider : sliders)
				addSlider(slider);
		updatePreferredSize();
	}

	public void addSlider(GLSlider slider) {
		if (slider == null)
			throw new IllegalArgumentException("Grouped slider must not be null");
		if (sliders.contains(slider))
			return;
		validateCompatible(slider);
		sliders.add(slider);
		try {
			addGL(slider);
			slider.joinSliderGroup(this);
		} catch (RuntimeException error) {
			sliders.remove(slider);
			throw error;
		}
		groupStructureChanged();
	}

	public boolean removeSlider(GLSlider slider) {
		return slider != null && sliders.contains(slider) && removeGL(slider);
	}

	public List<GLSlider> getSliders() {
		return List.copyOf(sliders);
	}

	/** Controls the legacy shared frame. Disable it when this group is nested in
	 * a {@link GLLayoutGroup}, which supplies the common outline instead. */
	public void setSharedFrameVisible(boolean visible) {
		if (sharedFrameVisible == visible) return;
		sharedFrameVisible = visible;
		invalidateRecording();
	}

	public boolean isSharedFrameVisible() { return sharedFrameVisible; }

	/** Changes the common range of every grouped FIXED slider atomically. */
	public void setRange(double minimum, double maximum) {
		if (sliders.isEmpty())
			return;
		if (sliders.get(0).getMode() != GLSlider.Mode.FIXED)
			throw new IllegalStateException("FREE slider groups do not have a range");
		changingGroupRange = true;
		try {
			for (GLSlider slider : sliders)
				slider.setRange(minimum, maximum);
		} finally {
			changingGroupRange = false;
		}
	}

	@Override
	protected void record(GLContext2D context) {
		// Children provide the fills. The shared frame is drawn over them below.
	}

	@Override
	protected void renderChildren(GLContext2D context) {
		super.renderChildren(context);
		drawSharedFrame(context);
	}

	@Override
	protected boolean isMouseTargetEnabled() {
		return false;
	}

	@Override
	protected void resized(float width, float height) {
		layoutSliders();
	}

	@Override
	protected void themeChanged() {
		groupStructureChanged();
	}

	GLCornerRadii cornerRadii(GLSlider slider, float radius) {
		int index = sliders.indexOf(slider);
		if (index < 0 || sliders.size() == 1)
			return GLCornerRadii.all(Math.min(radius, sharedCornerRadius()));
		float effectiveRadius = Math.min(radius, sharedCornerRadius());
		float top = index == 0 ? effectiveRadius : 0;
		float bottom = index == sliders.size() - 1 ? effectiveRadius : 0;
		return new GLCornerRadii(top, top, bottom, bottom);
	}

	void sliderPresentationChanged(GLSlider slider) {
		if (!sliders.contains(slider))
			return;
		updatePreferredSize();
		layoutSliders();
		invalidateAllSliderShapes();
	}

	void validateRangeChange(GLSlider source, double minimum, double maximum) {
		if (changingGroupRange)
			return;
		for (GLSlider slider : sliders)
			if (slider != source && (Double.compare(slider.getMinimum(), minimum) != 0
					|| Double.compare(slider.getMaximum(), maximum) != 0))
				throw new IllegalArgumentException(
						"Grouped fixed sliders require the same minimum and maximum");
	}

	void sliderDetached(GLSlider slider) {
		if (!sliders.remove(slider))
			return;
		slider.setGroupGesturePressed(false);
		gestureSliders.remove(slider);
		if (linkedEditorSession != null
				&& linkedEditorSession.sliders.contains(slider))
			linkedEditorSession.close();
		if (gestureSource == slider)
			clearGesture();
		groupStructureChanged();
	}

	void pointerPressed(GLSlider source, GLMouseEvent event) {
		gestureSource = source;
		gestureSliders.clear();
		gestureStartValues.clear();
		addGestureSlider(source);
		gestureAnchorY = source.getY() + event.getLocalY();
		gestureHorizontalDistance = 0;
		gestureSliding = false;
	}

	boolean pointerDragged(GLSlider source, GLMouseEvent event) {
		if (gestureSource != source)
			pointerPressed(source, event);
		if (!gestureSliding) {
			float currentY = source.getY() + event.getLocalY();
			float localX = source.getX() + event.getLocalX();
			float horizontal = Math.abs(event.getDeltaX());
			float vertical = Math.abs(event.getDeltaY());
			boolean verticalIntent = vertical > 0
					&& vertical >= horizontal * DIRECTION_DOMINANCE;
			boolean horizontalIntent = horizontal > 0
					&& horizontal >= vertical * DIRECTION_DOMINANCE;
			if (verticalIntent && localX >= 0 && localX <= getWidth()) {
				Double startValue = gestureStartValues.get(source);
				if (startValue != null
						&& Double.compare(source.getValue(), startValue) != 0)
					source.setValue(startValue);
				updateGestureSelection(currentY);
				gestureHorizontalDistance = 0;
			} else if (horizontalIntent) {
				gestureHorizontalDistance += horizontal;
				if (gestureHorizontalDistance >= GLSlider.DRAG_THRESHOLD)
					gestureSliding = true;
			} else {
				gestureHorizontalDistance = 0;
			}
		}
		return gestureSliding;
	}

	private void addGestureSlider(GLSlider slider) {
		if (gestureSliders.add(slider)) {
			gestureStartValues.put(slider, slider.getValue());
			slider.setGroupGesturePressed(true);
		}
	}

	private void updateGestureSelection(float currentY) {
		float start = Math.min(gestureAnchorY, currentY);
		float end = Math.max(gestureAnchorY, currentY);
		Set<GLSlider> selected = new LinkedHashSet<>();
		for (GLSlider slider : sliders)
			if (slider.isEnabled() && slider.getY() <= end
					&& slider.getY() + slider.getHeight() >= start)
				selected.add(slider);
		for (GLSlider slider : List.copyOf(gestureSliders))
			if (!selected.contains(slider)) {
				gestureSliders.remove(slider);
				gestureStartValues.remove(slider);
				slider.setGroupGesturePressed(false);
			}
		for (GLSlider slider : selected)
			addGestureSlider(slider);
	}

	Color progressColor(GLSlider slider, Color light, Color dark,
			boolean bottomEdge) {
		if (!sliders.contains(slider) || getHeight() <= 0)
			return bottomEdge ? dark : light;
		float y = slider.getY() + (bottomEdge ? slider.getHeight() : 0);
		float amount = Math.max(0, Math.min(1, y / getHeight()));
		return GLColorInterpolation.SRGB.interpolate(light, dark, amount);
	}

	void synchronizeGestureValue(GLSlider source) {
		if (!gestureSliding || gestureSource != source)
			return;
		double sharedValue = source.getValue();
		for (GLSlider slider : gestureSliders)
			if (slider != source && slider.isEnabled())
				slider.setValue(sharedValue);
	}

	void pointerReleased(GLSlider source, boolean startEditing) {
		if (gestureSource != source)
			return;
		List<GLSlider> selected = List.copyOf(gestureSliders);
		Map<GLSlider, Double> before = new LinkedHashMap<>(gestureStartValues);
		Map<GLSlider, Double> after = currentValues(before.keySet());
		boolean changedValues = before.entrySet().stream().anyMatch(entry ->
				Double.compare(entry.getValue(), after.get(entry.getKey())) != 0);
		boolean editSelection = startEditing && !gestureSliding && !changedValues;
		clearGesture();
		if (changedValues)
			recordValues("Drag slider group", before, after);
		if (editSelection && !selected.isEmpty())
			startLinkedEditing(source, selected);
	}

	private void startLinkedEditing(GLSlider source, List<GLSlider> selected) {
		if (linkedEditorSession != null)
			linkedEditorSession.close();
		LinkedEditorSession session = new LinkedEditorSession(source, selected);
		linkedEditorSession = session;
		if (!session.open())
			session.close();
	}

	private void clearGesture() {
		for (GLSlider slider : gestureSliders)
			slider.setGroupGesturePressed(false);
		gestureSource = null;
		gestureSliders.clear();
		gestureStartValues.clear();
		gestureHorizontalDistance = 0;
		gestureSliding = false;
	}

	private final class LinkedEditorSession {
		private final GLSlider source;
		private final List<GLSlider> sliders;
		private final List<GLTextEditor> editors = new ArrayList<>();
		private GLTextEditor activeEditor;
		private boolean closed;

		private LinkedEditorSession(GLSlider source, List<GLSlider> sliders) {
			this.source = source;
			this.sliders = sliders;
		}

		private boolean open() {
			String text = source.editorValueText();
			for (GLSlider slider : sliders) {
				GLTextEditor editor = slider.createValueEditor(text, slider == source);
				if (editor == null)
					return false;
				editors.add(editor);
				if (slider == source)
					activeEditor = editor;
			}
			if (activeEditor == null)
				return false;
			activeEditor.addTextChangeListener(event -> mirrorText());
			activeEditor.addActionListener(event -> commit());
			activeEditor.addCancelListener(event -> close());
			activeEditor.addValidationFailureListener(event -> validationFailed());
			source.focusValueEditor(activeEditor);
			return true;
		}

		private void mirrorText() {
			if (closed)
				return;
			String text = activeEditor.getText();
			for (GLTextEditor editor : editors)
				if (editor != activeEditor)
					editor.setText(text);
		}

		private void commit() {
			if (closed)
				return;
			Double value = GLSlider.parseEditorNumber(activeEditor.getText());
			if (value == null)
				return;
			Map<GLSlider, Double> before = currentValues(sliders);
			for (GLSlider slider : sliders)
				slider.setValue(value);
			recordValues("Edit slider group", before, currentValues(sliders));
			close();
		}

		private void validationFailed() {
			if (closed)
				return;
			List<GLSlider> affected = List.copyOf(sliders);
			close();
			for (GLSlider slider : affected)
				slider.showValidationFailure();
		}

		private void close() {
			if (closed)
				return;
			closed = true;
			for (int index = 0; index < editors.size(); index++)
				sliders.get(index).closeValueEditor(editors.get(index));
			if (linkedEditorSession == this)
				linkedEditorSession = null;
		}
	}

	private Map<GLSlider, Double> currentValues(
			Iterable<GLSlider> affected) {
		Map<GLSlider, Double> values = new LinkedHashMap<>();
		for (GLSlider slider : affected)
			values.put(slider, slider.getValue());
		return values;
	}

	protected void recordValues(String name, Map<GLSlider, Double> before,
			Map<GLSlider, Double> after) {
		boolean changed = before.entrySet().stream().anyMatch(entry ->
				Double.compare(entry.getValue(), after.get(entry.getKey())) != 0);
		if (!changed)
			return;
		var manager = getGLHistoryManager();
		if (manager != null)
			manager.record(name, () -> applyValues(before), () -> applyValues(after));
	}

	private static void applyValues(Map<GLSlider, Double> values) {
		for (Map.Entry<GLSlider, Double> entry : values.entrySet())
			entry.getKey().setValue(entry.getValue());
	}

	private void groupStructureChanged() {
		updatePreferredSize();
		layoutSliders();
		invalidateAllSliderShapes();
		invalidateRecording();
	}

	private void updatePreferredSize() {
		float width = 0;
		float height = 0;
		for (GLSlider slider : sliders) {
			GLSize preferred = slider.getPreferredGLSize();
			width = Math.max(width, preferred.width());
			height += preferred.height();
		}
		setIntrinsicPreferredGLSize(new GLSize(width, height));
	}

	private void layoutSliders() {
		if (sliders.isEmpty())
			return;
		float preferredHeight = 0;
		for (GLSlider slider : sliders)
			preferredHeight += slider.getPreferredGLSize().height();
		float scale = preferredHeight > 0 ? getHeight() / preferredHeight : 0;
		float y = 0;
		for (int index = 0; index < sliders.size(); index++) {
			GLSlider slider = sliders.get(index);
			float rowHeight = index == sliders.size() - 1
					? Math.max(0, getHeight() - y)
					: slider.getPreferredGLSize().height() * scale;
			slider.setBounds(0, y, getWidth(), rowHeight);
			y += rowHeight;
		}
	}

	private void drawSharedFrame(GLContext2D context) {
		if (!sharedFrameVisible || sliders.isEmpty() || getWidth() <= 0 || getHeight() <= 0)
			return;
		GLSliderStyle first = sliders.get(0).getGLStyle();
		float borderWidth = first.borderWidth();
		if (borderWidth <= 0)
			return;
		GLTextEditorStyle editorStyle = getGLTheme().textEditorStyle();
		Color borderColor = first.borderColor();
		context.setStroke(new GLStroke(borderWidth)
				.withCap(GLStroke.Cap.BUTT)
				.withWidthSpace(GLStroke.WidthSpace.SCREEN));
		for (int index = 1; index < sliders.size(); index++) {
			GLSlider slider = sliders.get(index);
			context.drawUILine(0, slider.getY(), getWidth(), slider.getY(),
					borderColor);
		}
		float radius = sharedCornerRadius();
		GLCornerRadii radii = GLCornerRadii.all(radius);
		context.drawUIRoundRect(0, 0, getWidth(), getHeight(), radii,
				borderWidth, borderColor, GLStroke.WidthSpace.SCREEN);
		drawSelectedRuns(context,borderWidth,editorStyle.invalidBorderColor(),
				GLSlider::isValidationFeedbackActive);
		drawSelectedRuns(context,Math.max(1.5f,borderWidth),editorStyle.focusedBorderColor(),
					GLSlider::isValueEditing);
	}

	private void drawSelectedRuns(GLContext2D context,float width,Color color,
			java.util.function.Predicate<GLSlider> selected) {
		GLStroke previous=context.getStroke();
		context.setStroke(new GLStroke(width).withCap(GLStroke.Cap.BUTT)
				.withWidthSpace(GLStroke.WidthSpace.SCREEN));
		try {
		for(int index=0;index<sliders.size();) {
			while(index<sliders.size()&&!selected.test(sliders.get(index)))index++;
			if(index>=sliders.size())break;
			int first=index,last=index;
			while(last+1<sliders.size()&&selected.test(sliders.get(last+1)))last++;
			float y=sliders.get(first).getY();
			float bottom=sliders.get(last).getY()+sliders.get(last).getHeight();
			float radius=sharedCornerRadius();
			GLCornerRadii radii=new GLCornerRadii(first==0?radius:0,first==0?radius:0,
					last==sliders.size()-1?radius:0,last==sliders.size()-1?radius:0);
			context.drawUIRoundRect(0,y,getWidth(),bottom-y,radii,width,color,
					GLStroke.WidthSpace.SCREEN);
			for(int seam=first+1;seam<=last;seam++)
				context.drawUILine(0,sliders.get(seam).getY(),getWidth(),sliders.get(seam).getY(),color);
			index=last+1;
		}
		} finally {context.setStroke(previous);}
	}

	private float sharedCornerRadius() {
		if (sliders.isEmpty())
			return 0;
		float radius = Float.POSITIVE_INFINITY;
		for (GLSlider slider : sliders)
			radius = Math.min(radius, Math.min(slider.getGLStyle().cornerRadius(),
					Math.min(slider.getWidth(), slider.getHeight()) * 0.5f));
		return Math.max(0, radius);
	}

	private void invalidateAllSliderShapes() {
		for (GLSlider slider : sliders)
			slider.groupGeometryChanged();
	}

	private void validateCompatible(GLSlider candidate) {
		if (sliders.isEmpty())
			return;
		GLSlider first = sliders.get(0);
		if (candidate.getMode() != first.getMode())
			throw new IllegalArgumentException(
					"A slider group requires one common slider mode");
		if (candidate.getMode() == GLSlider.Mode.FIXED
				&& (Double.compare(candidate.getMinimum(), first.getMinimum()) != 0
						|| Double.compare(candidate.getMaximum(), first.getMaximum()) != 0))
			throw new IllegalArgumentException(
					"Grouped fixed sliders require the same minimum and maximum");
	}
}
