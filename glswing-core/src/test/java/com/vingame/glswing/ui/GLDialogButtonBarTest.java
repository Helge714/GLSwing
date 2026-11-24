package com.vingame.glswing.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class GLDialogButtonBarTest {
	@Test
	void localizedButtonsUseTheirPreferredWidths() {
		GLButton shortButton = new GLButton("OK");
		GLButton longButton = new GLButton("Wiederherstellen");
		GLDialogButtonBar bar = new GLDialogButtonBar()
				.addButton(shortButton).addButton(longButton);
		bar.setBounds(0, 0, 360, 30);
		bar.layoutGL();

		assertTrue(longButton.getWidth() > shortButton.getWidth());
		assertTrue(longButton.getX() + longButton.getWidth() <= 360.001f);
		assertTrue(shortButton.getX() >= 0);
	}

	@Test
	void relayoutsWhenButtonTextChangesAndShrinksOnlyOnOverflow() {
		GLButton first = new GLButton("Yes");
		GLButton second = new GLButton("No");
		GLDialogButtonBar bar = new GLDialogButtonBar()
				.addButton(first).addButton(second);
		bar.setBounds(0, 0, 260, 30);
		bar.layoutGL();
		float initialWidth = first.getWidth();

		first.setText("Apply changes to every document");
		bar.layoutGL();
		assertTrue(first.getWidth() > initialWidth);

		bar.setSize(120, 30);
		bar.layoutGL();
		assertTrue(first.getWidth() + bar.gap() + second.getWidth() <= 120.001f);
	}
}
