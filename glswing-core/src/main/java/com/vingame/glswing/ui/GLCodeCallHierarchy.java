package com.vingame.glswing.ui;

import java.util.List;

/** First-level incoming and outgoing calls for one source symbol. */
public record GLCodeCallHierarchy(String symbol, GLCodeLocation declaration,
		List<GLCodeCall> incoming, List<GLCodeCall> outgoing) {
	public GLCodeCallHierarchy {
		if (symbol == null || symbol.isBlank() || declaration == null
				|| incoming == null || outgoing == null)
			throw new IllegalArgumentException("Invalid code call hierarchy");
		incoming = List.copyOf(incoming);
		outgoing = List.copyOf(outgoing);
	}
}
