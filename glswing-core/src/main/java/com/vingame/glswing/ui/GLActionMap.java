package com.vingame.glswing.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vingame.glswing.input.GLInputMap;
import com.vingame.glswing.input.GLKeyEvent;

/** Ordered action registry that dispatches semantic shortcuts. */
public final class GLActionMap {
	private final Map<String, GLAction> actions = new LinkedHashMap<>();

	public GLActionMap add(GLAction action) {
		if (action == null) throw new IllegalArgumentException("Action required");
		if (actions.putIfAbsent(action.id(), action) != null)
			throw new IllegalArgumentException("Duplicate action identifier: " + action.id());
		return this;
	}
	public boolean remove(String id) { return actions.remove(id) != null; }
	public GLAction get(String id) { return actions.get(id); }
	public List<GLAction> actions() { return List.copyOf(actions.values()); }
	public void refreshEnabled() { actions.values().forEach(GLAction::refreshEnabled); }
	public void refresh() { actions.values().forEach(GLAction::refresh); }
	public boolean handleKeyPressed(GLKeyEvent event, GLInputMap inputMap, Object source) {
		for (GLAction action : actions.values())
			if (action.handleKeyPressed(event, inputMap, source)) return true;
		return false;
	}
}
