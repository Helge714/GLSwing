package com.vingame.glswing.node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.node.binding.GLNodeEditorBinding;
import com.vingame.glswing.node.model.GLBooleanNodeProperty;
import com.vingame.glswing.node.model.GLEnumNodeProperty;
import com.vingame.glswing.node.model.GLGraphNode;
import com.vingame.glswing.node.model.GLNodeId;
import com.vingame.glswing.node.model.GLNodePropertyDescriptor;
import com.vingame.glswing.node.model.GLNodePropertySchemaRegistry;
import com.vingame.glswing.node.model.GLNodeValue;
import com.vingame.glswing.node.model.GLNumberNodeProperty;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLCheckBox;
import com.vingame.glswing.ui.GLComboBox;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLNumberSpinner;
import com.vingame.glswing.ui.GLSlider;

/**
 * Schema-driven inspector for universal node properties. It deliberately only
 * handles numbers, enums and booleans; applications can place domain-specific
 * editors beside it without coupling them to the node core.
 */
public final class GLNodePropertyInspector extends GLContainer
		implements AutoCloseable {
	private static final float PADDING = 10;
	private static final float ROW_HEIGHT = 30;
	private static final float GAP = 8;

	private final GLNodeEditorBinding binding;
	private final GLNodePropertySchemaRegistry schemas;
	private final GLNodeEditorListener selectionListener = new GLNodeEditorListener() {
		@Override public void selectionChanged(GLNodeEditor editor) { refresh(); }
	};
	private final List<Row> rows = new ArrayList<>();
	private final GLLabel empty = new GLLabel("No editable properties");
	private boolean synchronizing;
	private boolean closed;

	public GLNodePropertyInspector(GLNodeEditorBinding binding,
			GLNodePropertySchemaRegistry schemas) {
		if (binding == null || schemas == null)
			throw new IllegalArgumentException("Binding and property schemas required");
		this.binding = binding;
		this.schemas = schemas;
		binding.editor().addNodeEditorListener(selectionListener);
		refresh();
	}

	/** Rebuilds the controls for properties shared by the current node selection. */
	public void refresh() {
		if (closed) return;
		removeAllGL();
		rows.clear();
		List<GLGraphNode> selected = selectedModels();
		if (selected.isEmpty()) {
			addGL(empty);
			resized(getWidth(), getHeight());
			return;
		}
		Map<String, GLNodePropertyDescriptor> common = commonProperties(selected);
		for (GLNodePropertyDescriptor descriptor : common.values()) {
			GLContainer control = createControl(descriptor, selected);
			if (control == null) continue;
			boolean selfLabelled = descriptor instanceof GLBooleanNodeProperty
					|| descriptor instanceof GLNumberNodeProperty number
							&& number.editor() == GLNumberNodeProperty.Editor.SLIDER;
			GLLabel label = selfLabelled ? null : new GLLabel(descriptor.displayName());
			rows.add(new Row(label, control));
			if (label != null) addGL(label);
			addGL(control);
		}
		if (rows.isEmpty()) addGL(empty);
		setPreferredGLSize(220, PADDING * 2 + Math.max(1, rows.size())
				* ROW_HEIGHT + Math.max(0, rows.size() - 1) * GAP);
		resized(getWidth(), getHeight());
	}

	@Override public void close() {
		if (closed) return;
		closed = true;
		binding.editor().removeNodeEditorListener(selectionListener);
	}

	private List<GLGraphNode> selectedModels() {
		List<GLGraphNode> result = new ArrayList<>();
		for (GLNode view : binding.editor().getSelectedNodes()) {
			GLNodeId id = binding.findNodeId(view).orElse(null);
			if (id != null) binding.graph().snapshot().findNode(id).ifPresent(result::add);
		}
		return result;
	}

	private Map<String, GLNodePropertyDescriptor> commonProperties(
			List<GLGraphNode> selected) {
		LinkedHashMap<String, GLNodePropertyDescriptor> result = new LinkedHashMap<>();
		for (GLNodePropertyDescriptor property : schemas.properties(selected.get(0).typeId()))
			if (selected.get(0).properties().containsKey(property.id()))
				result.put(property.id(), property);
		for (int index = 1; index < selected.size(); index++) {
			GLGraphNode node = selected.get(index);
			Map<String, GLNodePropertyDescriptor> available = new LinkedHashMap<>();
			for (GLNodePropertyDescriptor property : schemas.properties(node.typeId()))
				if (node.properties().containsKey(property.id()))
					available.put(property.id(), property);
			result.entrySet().removeIf(entry ->
					!entry.getValue().equals(available.get(entry.getKey())));
		}
		return result;
	}

	private GLContainer createControl(GLNodePropertyDescriptor descriptor,
			List<GLGraphNode> selected) {
		GLNodeValue first = selected.get(0).properties().get(descriptor.id());
		if (descriptor instanceof GLNumberNodeProperty number) {
			double value = numeric(first);
			if (!Double.isFinite(value)) return null;
			if (number.editor() == GLNumberNodeProperty.Editor.SPINNER) {
				GLNumberSpinner spinner = new GLNumberSpinner(number.minimum(),
						number.maximum(), value);
				spinner.setStep(number.step());
				spinner.setIntegerValuesOnly(number.integer());
				spinner.addChangeListener(event -> applyNumber(number, spinner.getValue()));
				return spinner;
			}
			GLSlider slider = GLSlider.fixed(number.displayName(), number.minimum(),
					number.maximum(), value);
			slider.setStep(number.step());
			slider.setIntegerValuesOnly(number.integer());
			slider.addChangeListener(event -> applyNumber(number, slider.getValue()));
			return slider;
		}
		if (descriptor instanceof GLEnumNodeProperty enumeration
				&& first instanceof GLNodeValue.TextValue text) {
			GLComboBox<String> combo = new GLComboBox<>(enumeration.values());
			if (enumeration.values().contains(text.value())) combo.setSelectedItem(text.value());
			combo.addActionListener(event -> apply(descriptor.id(),
					new GLNodeValue.TextValue(combo.getSelectedItem())));
			return combo;
		}
		if (descriptor instanceof GLBooleanNodeProperty
				&& first instanceof GLNodeValue.BooleanValue value) {
			GLCheckBox checkbox = new GLCheckBox(descriptor.displayName());
			checkbox.setSelected(value.value());
			checkbox.addActionListener(event -> apply(descriptor.id(),
					new GLNodeValue.BooleanValue(checkbox.isSelected())));
			return checkbox;
		}
		return null;
	}

	private void applyNumber(GLNumberNodeProperty descriptor, double value) {
		apply(descriptor.id(), descriptor.integer()
				? new GLNodeValue.IntegerValue(Math.round(value))
				: new GLNodeValue.DecimalValue(value));
	}

	private void apply(String property, GLNodeValue value) {
		if (synchronizing || value == null) return;
		List<GLNodeId> ids = binding.editor().getSelectedNodes().stream()
				.map(binding::findNodeId).flatMap(java.util.Optional::stream).toList();
		if (ids.isEmpty()) return;
		binding.graph().edit("Change " + property, edit -> ids.forEach(id ->
				binding.graph().snapshot().findNode(id).filter(node ->
						node.properties().containsKey(property))
						.ifPresent(node -> edit.setProperty(id, property, value))));
	}

	private static double numeric(GLNodeValue value) {
		if (value instanceof GLNodeValue.IntegerValue integer) return integer.value();
		if (value instanceof GLNodeValue.DecimalValue decimal) return decimal.value();
		return Double.NaN;
	}

	@Override protected void resized(float width, float height) {
		float y = PADDING;
		float labelWidth = Math.min(110, Math.max(70, width * .32f));
		for (Row row : rows) {
			float controlX = PADDING;
			float controlWidth = Math.max(0, width - PADDING * 2);
			if (row.label != null) {
				row.label.setBounds(PADDING, y, labelWidth, ROW_HEIGHT);
				controlX += labelWidth + GAP;
				controlWidth -= labelWidth + GAP;
			}
			row.control.setBounds(controlX, y, controlWidth, ROW_HEIGHT);
			y += ROW_HEIGHT + GAP;
		}
		empty.setBounds(PADDING, PADDING, Math.max(0, width - PADDING * 2), ROW_HEIGHT);
	}

	@Override protected void record(GLContext2D context) { }

	private record Row(GLLabel label, GLContainer control) { }
}
