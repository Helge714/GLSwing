package com.vingame.glswing.node.composite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.node.model.GLNodeValue;
import com.vingame.glswing.ui.GLSlider;

/** Thread-safe registry of built-in and application-provided input editors. */
public final class GLNodeInputEditorRegistry {
	public static final String NONE = "none";
	public static final String SLIDER = "glswing.slider";
	private final Map<String, GLNodeInputEditorDescriptor> entries =
			new LinkedHashMap<>();
	private final Map<String, GLNodeInputEditorFactory> factories =
			new LinkedHashMap<>();

	public GLNodeInputEditorRegistry() {
		register(new GLNodeInputEditorDescriptor(NONE, "No editor", java.util.Set.of(),
				Map.of()));
		register(new GLNodeInputEditorDescriptor(SLIDER, "Slider", java.util.Set.of(
				"number", "float", "double", "integer", "int"),
				Map.of("minimum", GLNodeValue.of(0.0),
						"maximum", GLNodeValue.of(1.0),
						"minimumEnabled", GLNodeValue.of(true),
						"maximumEnabled", GLNodeValue.of(true),
						"default", GLNodeValue.of(0.0),
						"presentation", GLNodeValue.of("bar"),
						"numericType", GLNodeValue.of("float"))), context -> {
			double minimum = decimal(context, "minimum", 0);
			double maximum = decimal(context, "maximum", 1);
			double value = decimal(context, "default", minimum);
			boolean useMinimum = bool(context, "minimumEnabled", true);
			boolean useMaximum = bool(context, "maximumEnabled", true);
			GLSlider.Presentation presentation = "buttons".equalsIgnoreCase(
					text(context, "presentation", "bar"))
							? GLSlider.Presentation.BUTTONS : GLSlider.Presentation.BAR;
			boolean fixed = useMinimum && useMaximum;
			GLSlider slider = fixed
					? GLSlider.fixed(context.port().id(), minimum, maximum, value, presentation)
					: GLSlider.free(context.port().id(), minimum, maximum, value);
			if (!fixed) {
				slider.setPresentation(presentation);
				slider.setValueBounds(useMinimum ? minimum : null,
						useMaximum ? maximum : null);
			}
			slider.setIntegerValuesOnly("integer".equalsIgnoreCase(
					text(context, "numericType", "float")));
			slider.setDefaultValue(value);
			return slider;
		});
	}

	public synchronized void register(GLNodeInputEditorDescriptor descriptor) {
		if (descriptor == null) throw new IllegalArgumentException("Input editor required");
		if (entries.putIfAbsent(descriptor.id(), descriptor) != null)
			throw new IllegalArgumentException("Input editor is already registered: "
					+ descriptor.id());
	}

	/** Registers portable metadata together with its application/runtime view factory. */
	public synchronized void register(GLNodeInputEditorDescriptor descriptor,
			GLNodeInputEditorFactory factory) {
		if (factory == null) throw new IllegalArgumentException("Input editor factory required");
		register(descriptor);
		factories.put(descriptor.id(), factory);
	}

	public synchronized Optional<GLNodeInputEditorDescriptor> find(String id) {
		return Optional.ofNullable(entries.get(id));
	}

	public synchronized List<GLNodeInputEditorDescriptor> entries() {
		return List.copyOf(entries.values());
	}

	/** Builds the configured control, or returns empty when only metadata is registered. */
	public synchronized Optional<GLContainer> create(GLNodeInputEditorContext context) {
		if (context == null) throw new IllegalArgumentException("Input editor context required");
		String id = context.presentation().inputEditorId();
		GLNodeInputEditorFactory factory = factories.get(id);
		return factory == null ? Optional.empty()
				: Optional.ofNullable(factory.createEditor(context));
	}

	public synchronized List<GLNodeInputEditorDescriptor> compatibleWith(
			String valueTypeId) {
		return entries.values().stream().filter(entry -> entry.id().equals(NONE)
				|| entry.supports(valueTypeId)).toList();
	}

	private static double decimal(GLNodeInputEditorContext context, String id,
			double fallback) {
		GLNodeValue value = context.presentation().editorOptions().get(id);
		if (value instanceof GLNodeValue.DecimalValue decimal) return decimal.value();
		if (value instanceof GLNodeValue.IntegerValue integer) return integer.value();
		return fallback;
	}

	private static boolean bool(GLNodeInputEditorContext context, String id,
			boolean fallback) {
		GLNodeValue value = context.presentation().editorOptions().get(id);
		return value instanceof GLNodeValue.BooleanValue item ? item.value() : fallback;
	}

	private static String text(GLNodeInputEditorContext context, String id,
			String fallback) {
		GLNodeValue value = context.presentation().editorOptions().get(id);
		return value instanceof GLNodeValue.TextValue item ? item.value() : fallback;
	}
}
