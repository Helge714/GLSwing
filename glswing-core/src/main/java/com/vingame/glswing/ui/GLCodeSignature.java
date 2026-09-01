package com.vingame.glswing.ui;

import java.util.List;

/** One callable signature returned by a code-intelligence provider. */
public record GLCodeSignature(String label, String documentation,
		List<String> parameters, int activeParameter) {
	public GLCodeSignature {
		if (label == null || documentation == null || parameters == null)
			throw new IllegalArgumentException("Signature fields must not be null");
		parameters = List.copyOf(parameters);
	}

	/** Returns a compact label with the active parameter visibly delimited. */
	public String displayLabel() {
		if (activeParameter < 0 || activeParameter >= parameters.size()) return label;
		String parameter = parameters.get(activeParameter);
		int start = label.indexOf(parameter);
		return start < 0 ? label : label.substring(0, start) + "[" + parameter + "]"
				+ label.substring(start + parameter.length());
	}
}
