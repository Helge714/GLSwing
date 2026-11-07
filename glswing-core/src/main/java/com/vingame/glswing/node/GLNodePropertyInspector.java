package com.vingame.glswing.node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.node.binding.GLNodeEditorBinding;
import com.vingame.glswing.node.model.GLBooleanNodeProperty;
import com.vingame.glswing.node.model.GLCustomNodeProperty;
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
    private java.util.function.UnaryOperator<GLGraphNode> presentationModel=java.util.function.UnaryOperator.identity();
    /** Read-only display projection; graph values and writes remain unchanged. */
    public void setPresentationModel(java.util.function.UnaryOperator<GLGraphNode> projection){presentationModel=java.util.Objects.requireNonNull(projection);refresh();}
    private java.util.function.BiFunction<GLGraphNode,String,String> readOnlyReason=(node,property)->null;
    /** Return a reason to disable an editor, or null to allow editing. */
    public void setReadOnlyReason(java.util.function.BiFunction<GLGraphNode,String,String> reason){
        readOnlyReason=java.util.Objects.requireNonNull(reason);refresh();
    }

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
		List<GLGraphNode> selected = selectedModels().stream().map(presentationModel).toList();
		if (selected.isEmpty()) {
			addGL(empty);
			resized(getWidth(), getHeight());
			return;
		}
		Map<String, GLNodePropertyDescriptor> common = commonProperties(selected);
		for (GLNodePropertyDescriptor descriptor : common.values()) {
			GLContainer control = createControl(descriptor, selected);
			if (control == null) continue;
            String reason=selected.stream().map(n->readOnlyReason.apply(n,descriptor.id())).filter(java.util.Objects::nonNull).findFirst().orElse(null);
            if(reason!=null)disable(control,reason);
			boolean selfLabelled = descriptor instanceof GLCustomNodeProperty || descriptor instanceof GLBooleanNodeProperty
					|| descriptor instanceof GLNumberNodeProperty number
							&& number.editor() == GLNumberNodeProperty.Editor.SLIDER;
			GLLabel label = selfLabelled ? null : new GLLabel(descriptor.displayName());
			rows.add(new Row(label, control, descriptor));
			if (label != null) addGL(label);
			addGL(control);
		}
		if (rows.isEmpty()) addGL(empty);
		setPreferredGLSize(220, PADDING * 2 + (float)rows.stream().mapToDouble(this::rowHeight).sum()
				+ Math.max(0, rows.size() - 1) * GAP);
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
			if (property instanceof GLCustomNodeProperty || selected.get(0).properties().containsKey(property.id()))
				result.put(property.id(), property);
		for (int index = 1; index < selected.size(); index++) {
			GLGraphNode node = selected.get(index);
			Map<String, GLNodePropertyDescriptor> available = new LinkedHashMap<>();
			for (GLNodePropertyDescriptor property : schemas.properties(node.typeId()))
				if (property instanceof GLCustomNodeProperty || node.properties().containsKey(property.id()))
					available.put(property.id(), property);
			result.entrySet().removeIf(entry ->
					!entry.getValue().equals(available.get(entry.getKey())));
		}
		return result;
	}

	private GLContainer createControl(GLNodePropertyDescriptor descriptor,
			List<GLGraphNode> selected) {
		GLNodeValue first = selected.get(0).properties().get(descriptor.id());
        if (descriptor instanceof GLCustomNodeProperty custom)
            return custom.create().apply(selected.get(0), value -> apply(descriptor.id(), value));
		if (descriptor instanceof GLNumberNodeProperty number) {
			double value = numeric(first);
			if (!Double.isFinite(value)) return null;
			if (number.editor() == GLNumberNodeProperty.Editor.SPINNER) {
				GLNumberSpinner spinner = new GLNumberSpinner(number.minimum(),
						number.maximum(), value);
				spinner.setStep(number.step());
                if (!number.unit().isEmpty()) {
                    var format = spinner.getValueFormatter();
                    spinner.setValueFormatter(v -> format.apply(v) + " " + number.unit());
                }
				spinner.setIntegerValuesOnly(number.integer());
				spinner.addChangeListener(event -> applyNumber(number, spinner.getValue()));
				return spinner;
			}
			GLSlider slider = GLSlider.fixed(number.displayName(), number.minimum(),
					number.maximum(), value, number.presentation());
            slider.setDragSensitivity(number.step());
			slider.setStep(number.step());
            slider.setUnit(number.unit());
			slider.setIntegerValuesOnly(number.integer());
			slider.addChangeListener(event -> applyNumber(number, slider.getValue()));
			return slider;
		}
		if (descriptor instanceof GLEnumNodeProperty enumeration
				&& first instanceof GLNodeValue.TextValue text) {
			GLComboBox<String> combo = new GLComboBox<>(enumeration.values());
            combo.setToolTipText(enumeration.toolTipText());
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
        if(selectedModels().stream().anyMatch(n->readOnlyReason.apply(n,property)!=null))return;
		List<GLNodeId> ids = binding.editor().getSelectedNodes().stream()
				.map(binding::findNodeId).flatMap(java.util.Optional::stream).toList();
		if (ids.isEmpty()) return;
		binding.graph().edit("Change " + property, edit -> ids.forEach(id ->
				binding.graph().snapshot().findNode(id).filter(node ->
						node.properties().containsKey(property) || schemas.properties(node.typeId()).stream().anyMatch(p->p instanceof GLCustomNodeProperty&&p.id().equals(property)) || property.startsWith(GLGraphNode.AUTOMATIC_PREFIX))
						.ifPresent(node -> {if(property.startsWith(GLGraphNode.AUTOMATIC_PREFIX)){edit.removeProperty(id,GLGraphNode.AUTOMATIC_DEFAULT_PREFIX+property.substring(GLGraphNode.AUTOMATIC_PREFIX.length()));edit.setProperty(id,property,value);}else edit.setExplicitProperty(id, property, value);})));
	}

	private static double numeric(GLNodeValue value) {
		if (value instanceof GLNodeValue.IntegerValue integer) return integer.value();
		if (value instanceof GLNodeValue.DecimalValue decimal) return decimal.value();
		return Double.NaN;
	}
    private static void disable(GLContainer component,String reason){
        if(component instanceof com.vingame.glswing.ui.GLControl control){control.setEnabled(false);control.setToolTipText(reason);}
        component.getGLChildren().forEach(child->disable(child,reason));
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
				controlWidth = Math.max(0, controlWidth-labelWidth-GAP);
			}
			row.control.setBounds(controlX, y, controlWidth, rowHeight(row));
			y += rowHeight(row) + GAP;
		}
		empty.setBounds(PADDING, PADDING, Math.max(0, width - PADDING * 2), ROW_HEIGHT);
	}

	@Override protected void record(GLContext2D context) { }

    /** Apply a companion property from an application editor (for example a unit menu).
     * Uses the same selection, synchronization guard and model transaction as built-in controls. */
    public void setSelectedProperty(String property, GLNodeValue value) {
        java.util.Objects.requireNonNull(property); java.util.Objects.requireNonNull(value);
        apply(property, value);
    }

    /** Refresh custom controls without replacing a pointer-captured editor. */
    public void synchronizeCustomProperties() {
        var selected = selectedModels();
        if (selected.isEmpty()) return;
        synchronizing = true;
        try { for (var row : rows) if (row.descriptor instanceof GLCustomNodeProperty custom) {
            var value = selected.get(0).properties().get(custom.id());
            custom.update().accept(row.control, selected.get(0));
        }} finally { synchronizing = false; }
    }
    private float rowHeight(Row row) { return row.descriptor instanceof GLCustomNodeProperty
            ? Math.max(ROW_HEIGHT, row.control.getPreferredGLSize().height()) : ROW_HEIGHT; }
	private record Row(GLLabel label, GLContainer control, GLNodePropertyDescriptor descriptor) { }
}



