package com.vingame.glswing.test;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.vingame.glswing.ui.GLComboBox;
import com.vingame.glswing.ui.GLPopupTitlePosition;

/** CPU checks for combo-box model, selection and popup configuration. */
public final class GLComboBoxSelfTest {

	private GLComboBoxSelfTest() {
	}

	public static void main(String[] args) {
		GLComboBox<String> combo = new GLComboBox<>(
				List.of("Gamma", "Alpha", "Beta"));
		check(combo.getItemCount() == 3, "Initial items were not accepted");
		check(combo.getSelectedIndex() == 0
				&& "Gamma".equals(combo.getSelectedItem()),
				"First item was not selected initially");

		AtomicInteger actions = new AtomicInteger();
		combo.addActionListener(event -> actions.incrementAndGet());
		combo.setSelectedIndex(2);
		check(actions.get() == 1 && "Beta".equals(combo.getSelectedItem()),
				"Selection did not update or fire its action");

		combo.sortItems(Comparator.naturalOrder());
		check("Beta".equals(combo.getSelectedItem())
				&& combo.getSelectedIndex() == 1,
				"Sorting did not preserve the selected item");
		combo.insertItemAt("Delta", 2);
		combo.removeItem("Alpha");
		check(combo.getItems().equals(List.of("Beta", "Delta", "Gamma")),
				"Insert/remove produced an unexpected model");

		combo.setItemTextProvider(String::toUpperCase);
		combo.setPopupTitle("Choose one");
		combo.getPopupMenu().setTitlePosition(GLPopupTitlePosition.BOTTOM);
		check("Choose one".equals(combo.getPopupTitle()),
				"Popup title was not stored");
		check(combo.isPopupMatchesComboWidth(),
				"Combo popup does not match its width by default");
		combo.setPopupMatchesComboWidth(false);
		check(!combo.isPopupMatchesComboWidth(),
				"Popup width policy did not change");
		check(combo.getPopupMenu().getItems().size() == combo.getItemCount(),
				"Popup rows are out of sync with the combo model");

		combo.removeAllItems();
		check(combo.getSelectedIndex() == -1 && combo.getItemCount() == 0,
				"Clearing retained items or selection");

		GLComboBox<String> hierarchical = new GLComboBox<>();
		GLComboBox<String>.SubMenu countries =
				hierarchical.addSubMenu("Countries");
		countries.addItem("Germany").addItem("France");
		GLComboBox<String>.SubMenu cities = countries.addSubMenu("Cities");
		cities.addItem("Berlin").addItem("Paris");
		check(hierarchical.getItems().equals(
				List.of("Germany", "France", "Berlin", "Paris")),
				"Nested items were not flattened in selection order");
		check(hierarchical.getPopupMenu().getItems().size() == 1,
				"Root combo popup did not contain its submenu entry");
		var countriesItem = hierarchical.getPopupMenu().getItems().get(0);
		var citiesItem = countriesItem.getSubMenu().getItems().get(2);
		var parisItem = citiesItem.getSubMenu().getItems().get(1);
		parisItem.doClick();
		check("Paris".equals(hierarchical.getSelectedItem()),
				"Nested leaf activation did not select its combo value");
		System.out.println("GL combo-box self-test passed.");
	}

	private static void check(boolean condition, String message) {
		if (!condition)
			throw new AssertionError(message);
	}
}
