package com.vingame.glswing.ui;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

import com.vingame.glswing.input.GLInputMap;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.rendering.GLIcon;

/**
 * Shared command model for GLSwing controls, semantic shortcuts and localized
 * presentation. One action can back any number of buttons and menu items.
 */
public final class GLAction {
	public static final String ENABLED_PROPERTY = "enabled";
	public static final String SELECTED_PROPERTY = "selected";
	public static final String PRESENTATION_PROPERTY = "presentation";

	private final String id;
	private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
	private GLActionHandler handler;
	private BooleanSupplier enabledCondition;
	private boolean explicitlyEnabled = true;
	private boolean enabled = true;
	private boolean selectable;
	private boolean selected;
	private BooleanSupplier selectedCondition;
	private String text;
	private String textKey;
	private String shortDescription;
	private String shortDescriptionKey;
	private GLIcon icon;
	private GLIcon disabledIcon;

	private GLAction(Builder builder) {
		id = requireIdentifier(builder.id);
		text = builder.text == null ? id : builder.text;
		textKey = builder.textKey;
		shortDescription = builder.shortDescription;
		shortDescriptionKey = builder.shortDescriptionKey;
		icon = builder.icon;
		disabledIcon = builder.disabledIcon;
		handler = builder.handler;
		enabledCondition = builder.enabledCondition;
		explicitlyEnabled = builder.enabled;
		enabled = evaluateEnabled();
		selectable = builder.selectable;
		selected = builder.selected;
		selectedCondition = builder.selectedCondition;
		if (selectedCondition != null) selected = selectedCondition.getAsBoolean();
	}

	public static Builder builder(String id) { return new Builder(id); }

	/** Adapts the standard Swing action name, description, enabled state and invocation. */
	public static GLAction fromSwing(String id, javax.swing.Action swingAction) {
		if (swingAction == null) throw new IllegalArgumentException("Swing action required");
		Builder builder = builder(id)
				.text(stringValue(swingAction.getValue(javax.swing.Action.NAME), id))
				.shortDescription(stringValue(swingAction.getValue(
						javax.swing.Action.SHORT_DESCRIPTION), null))
				.enabled(swingAction.isEnabled())
				.onAction(event -> {
					swingAction.actionPerformed(new ActionEvent(event.source(),
							ActionEvent.ACTION_PERFORMED, id, event.when(), 0));
					return CompletableFuture.completedFuture(null);
				});
		Object selected = swingAction.getValue(javax.swing.Action.SELECTED_KEY);
		if (selected instanceof Boolean value) builder.selected(value);
		GLAction result = builder.build();
		swingAction.addPropertyChangeListener(new SwingBridge(swingAction, result));
		if (selected instanceof Boolean)
			result.addPropertyChangeListener(event -> {
				if (SELECTED_PROPERTY.equals(event.getPropertyName())
						&& !java.util.Objects.equals(swingAction.getValue(
								javax.swing.Action.SELECTED_KEY), event.getNewValue()))
					swingAction.putValue(javax.swing.Action.SELECTED_KEY,
							event.getNewValue());
			});
		return result;
	}

	public String id() { return id; }
	public boolean isEnabled() { return enabled; }
	public boolean isSelectable() { return selectable; }
	public boolean isSelected() { return selected; }
	public String text() { return text; }
	public String textKey() { return textKey; }
	public String shortDescription() { return shortDescription; }
	public String shortDescriptionKey() { return shortDescriptionKey; }
	public GLIcon icon() { return icon; }
	public GLIcon disabledIcon() { return disabledIcon; }

	public String resolvedText(GLLocalizer localizer) {
		return textKey == null ? text : requireLocalizer(localizer).text(textKey);
	}
	public String resolvedShortDescription(GLLocalizer localizer) {
		return shortDescriptionKey == null ? shortDescription
				: requireLocalizer(localizer).text(shortDescriptionKey);
	}
	/** Returns the first configured shortcut in platform display form. */
	public String acceleratorText(GLInputMap inputMap) {
		if (inputMap == null) throw new IllegalArgumentException("Input map required");
		var bindings = inputMap.getKeyBindings(id);
		return bindings.isEmpty() ? "" : bindings.get(0).toDisplayString();
	}

	public void setEnabled(boolean value) {
		if (explicitlyEnabled == value) return;
		explicitlyEnabled = value;
		refreshEnabled();
	}
	/** Re-evaluates the optional availability predicate and notifies all views. */
	public void refreshEnabled() {
		boolean next = evaluateEnabled();
		if (enabled == next) return;
		boolean previous = enabled;
		enabled = next;
		changes.firePropertyChange(ENABLED_PROPERTY, previous, next);
	}
	/** Re-evaluates both availability and externally derived selection state. */
	public void refresh() {
		refreshEnabled();
		refreshSelected();
	}
	public void refreshSelected() {
		if (selectedCondition == null) return;
		setSelectedValue(selectedCondition.getAsBoolean());
	}
	/** Sets the shared toggle state and marks this action as selectable. */
	public void setSelected(boolean value) {
		boolean becameSelectable = !selectable;
		selectable = true;
		setSelectedValue(value);
		if (becameSelectable) presentationChanged();
	}
	public void setText(String value) {
		value = requireText(value, "Action text");
		if (value.equals(text) && textKey == null) return;
		text = value; textKey = null; presentationChanged();
	}
	public void setTextKey(String value) {
		value = requireIdentifier(value);
		if (value.equals(textKey)) return;
		textKey = value; presentationChanged();
	}
	public void setShortDescription(String value) {
		if (java.util.Objects.equals(shortDescription, value)
				&& shortDescriptionKey == null) return;
		shortDescription = value; shortDescriptionKey = null; presentationChanged();
	}
	public void setShortDescriptionKey(String value) {
		value = requireIdentifier(value);
		if (value.equals(shortDescriptionKey)) return;
		shortDescriptionKey = value; presentationChanged();
	}
	public void setIcon(GLIcon value) {
		if (icon == value) return;
		icon = value; presentationChanged();
	}
	public void setDisabledIcon(GLIcon value) {
		if (disabledIcon == value) return;
		disabledIcon = value; presentationChanged();
	}

	public CompletionStage<?> perform(Object source) {
		return perform(source, null);
	}
	public CompletionStage<?> perform(Object source, GLKeyEvent keyEvent) {
		if (!enabled) return CompletableFuture.completedFuture(null);
		if (selectable && selectedCondition == null) setSelected(!selected);
		try {
			CompletionStage<?> result = handler.execute(new GLActionEvent(this, source,
					keyEvent, System.currentTimeMillis()));
			if (result == null) return CompletableFuture.failedFuture(
					new IllegalStateException("Action handler returned null: " + id));
			if (selectedCondition != null)
				result.whenComplete((ignored, failure) -> refreshSelected());
			return result;
		} catch (Throwable failure) {
			return CompletableFuture.failedFuture(failure);
		}
	}
	/** Matches, consumes and invokes this action for a semantic key binding. */
	public boolean handleKeyPressed(GLKeyEvent event, GLInputMap inputMap, Object source) {
		if (event == null || inputMap == null || !inputMap.matches(id, event)) return false;
		if (enabled) perform(source, event);
		event.consume();
		return true;
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		if (listener == null) throw new IllegalArgumentException("Listener required");
		changes.addPropertyChangeListener(listener);
	}
	public void removePropertyChangeListener(PropertyChangeListener listener) {
		changes.removePropertyChangeListener(listener);
	}

	private boolean evaluateEnabled() {
		return explicitlyEnabled && (enabledCondition == null || enabledCondition.getAsBoolean());
	}
	private void presentationChanged() {
		changes.firePropertyChange(PRESENTATION_PROPERTY, null, this);
	}
	private void setSelectedValue(boolean value) {
		if (selected == value) return;
		boolean previous = selected;
		selected = value;
		changes.firePropertyChange(SELECTED_PROPERTY, previous, value);
	}
	private static GLLocalizer requireLocalizer(GLLocalizer value) {
		if (value == null) throw new IllegalArgumentException("Localizer required");
		return value;
	}
	private static String requireIdentifier(String value) {
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("Action identifier must not be blank");
		return value;
	}
	private static String requireText(String value, String description) {
		if (value == null) throw new IllegalArgumentException(description + " must not be null");
		return value;
	}
	private static String stringValue(Object value, String fallback) {
		return value instanceof String text ? text : fallback;
	}

	public static final class Builder {
		private final String id;
		private GLActionHandler handler = event -> CompletableFuture.completedFuture(null);
		private BooleanSupplier enabledCondition;
		private boolean enabled = true;
		private boolean selectable;
		private boolean selected;
		private BooleanSupplier selectedCondition;
		private String text;
		private String textKey;
		private String shortDescription;
		private String shortDescriptionKey;
		private GLIcon icon;
		private GLIcon disabledIcon;

		private Builder(String id) { this.id = requireIdentifier(id); }
		public Builder text(String value) { text = requireText(value, "Action text"); return this; }
		public Builder textKey(String value) { textKey = requireIdentifier(value); return this; }
		public Builder shortDescription(String value) { shortDescription = value; return this; }
		public Builder shortDescriptionKey(String value) {
			shortDescriptionKey = requireIdentifier(value); return this;
		}
		public Builder icon(GLIcon value) { icon = value; return this; }
		public Builder disabledIcon(GLIcon value) { disabledIcon = value; return this; }
		public Builder enabled(boolean value) { enabled = value; return this; }
		public Builder enabledWhen(BooleanSupplier condition) {
			if (condition == null) throw new IllegalArgumentException("Enabled predicate required");
			enabledCondition = condition; return this;
		}
		/** Creates a self-toggling action with the supplied initial state. */
		public Builder selected(boolean value) {
			selectable = true; selected = value; selectedCondition = null; return this;
		}
		/** Creates an externally derived selectable action. */
		public Builder selectedWhen(BooleanSupplier condition) {
			if (condition == null) throw new IllegalArgumentException("Selected predicate required");
			selectable = true; selectedCondition = condition; return this;
		}
		public Builder onAction(GLActionHandler value) {
			if (value == null) throw new IllegalArgumentException("Action handler required");
			handler = value; return this;
		}
		public Builder onAction(Runnable value) {
			if (value == null) throw new IllegalArgumentException("Action handler required");
			handler = event -> { value.run(); return CompletableFuture.completedFuture(null); };
			return this;
		}
		public GLAction build() { return new GLAction(this); }
	}

	private static final class SwingBridge implements PropertyChangeListener {
		private final javax.swing.Action source;
		private final WeakReference<GLAction> target;
		SwingBridge(javax.swing.Action source, GLAction target) {
			this.source = source; this.target = new WeakReference<>(target);
		}
		@Override public void propertyChange(java.beans.PropertyChangeEvent event) {
			GLAction action = target.get();
			if (action == null) { source.removePropertyChangeListener(this); return; }
			if (event.getPropertyName() == null) return;
			switch (event.getPropertyName()) {
			case "enabled" -> action.setEnabled(source.isEnabled());
			case javax.swing.Action.NAME -> action.setText(stringValue(event.getNewValue(), action.id));
			case javax.swing.Action.SHORT_DESCRIPTION -> action.setShortDescription(
					stringValue(event.getNewValue(), null));
			case javax.swing.Action.SELECTED_KEY -> action.setSelected(
					Boolean.TRUE.equals(event.getNewValue()));
			default -> { }
			}
		}
	}
}
