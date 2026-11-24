package com.vingame.glswing.node.model;

/** Numeric property metadata. Sliders are the default editor. */
public record GLNumberNodeProperty(String id, String displayName,
		double minimum, double maximum, double step, boolean integer,
		Editor editor, String unit, com.vingame.glswing.ui.GLSlider.Presentation presentation) implements GLNodePropertyDescriptor {
    public GLNumberNodeProperty(String id, String displayName, double minimum, double maximum,
            double step, boolean integer, Editor editor, String unit) {
        this(id, displayName, minimum, maximum, step, integer, editor, unit,
                com.vingame.glswing.ui.GLSlider.Presentation.BAR);
    }
    public enum Editor { SLIDER, SPINNER }

	public GLNumberNodeProperty {
		if (!valid(id) || displayName == null || displayName.isBlank()
				|| !Double.isFinite(minimum) || !Double.isFinite(maximum)
				|| maximum <= minimum || !Double.isFinite(step) || step <= 0
				|| editor == null || unit == null || presentation == null)
			throw new IllegalArgumentException("Invalid numeric node property");
	}

	/** Creates a bounded numeric property rendered as a slider. */
	public GLNumberNodeProperty(String id, String displayName,
			double minimum, double maximum, double step, boolean integer) {
		this(id, displayName, minimum, maximum, step, integer, Editor.SLIDER, "");
	}

	public GLNumberNodeProperty(String id, String displayName, double minimum, double maximum,
            double step, boolean integer, Editor editor) {
        this(id, displayName, minimum, maximum, step, integer, editor, "");
    }

    private static boolean valid(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}
}

