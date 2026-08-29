package com.vingame.glswing.test;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;

import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.input.GLInputMaps;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLIcon;
import com.vingame.glswing.ui.GLComboBox;
import com.vingame.glswing.ui.GLItemPresentation;
import com.vingame.glswing.ui.GLMenuBar;
import com.vingame.glswing.ui.GLMenuBarItem;
import com.vingame.glswing.ui.GLMenuItem;
import com.vingame.glswing.ui.GLMenuMnemonics;
import com.vingame.glswing.ui.GLPopupMenu;
import com.vingame.glswing.ui.GLPopupTitlePosition;
import com.vingame.glswing.ui.GLPopupTextOverflow;

/** Checks menu ownership, popup lifetime and nested submenu hierarchy. */
public final class GLMenuSelfTest {

	private GLMenuSelfTest() {
	}

	public static void main(String[] args) {
		testEllipsisNormalization();
		testMnemonics();
		testIconsAndPresentations();
		TestPanel host = new TestPanel();
		host.setSize(400, 300);
		AtomicInteger actions = new AtomicInteger();

		GLPopupMenu nested = new GLPopupMenu();
		GLMenuItem leaf = nested.addItem("Leaf",
				event -> actions.incrementAndGet());
		GLPopupMenu root = new GLPopupMenu();
		GLMenuItem branch = root.addSubMenu("Branch", nested);
		root.addSeparator();
		root.addItem("Other");

		root.show(host, 390, 290);
		if (!root.isShowing() || host.getGLChildren().size() != 1)
			throw new AssertionError("Popup overlay was not attached");
		if (root.getX() + root.getWidth() > host.getWidth()
				|| root.getY() + root.getHeight() > host.getHeight())
			throw new AssertionError("Root popup was not kept inside its host");
		if (root.getX() < 1)
			throw new AssertionError("Popup border was placed below the host edge");

		branch.doClick();
		if (!nested.isShowing() || host.getGLChildren().size() != 1)
			throw new AssertionError("Nested submenu was not attached");
		leaf.doClick();
		if (actions.get() != 1 || root.isShowing() || nested.isShowing()
				|| !host.getGLChildren().isEmpty())
			throw new AssertionError("Leaf activation did not close the menu session");

		GLPopupMenu shared = new GLPopupMenu();
		new GLMenuItem("First").setSubMenu(shared);
		try {
			new GLMenuItem("Second").setSubMenu(shared);
			throw new AssertionError("One submenu was accepted by two owners");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}

		GLPopupMenu cyclic = new GLPopupMenu();
		try {
			cyclic.addSubMenu("Cycle", cyclic);
			throw new AssertionError("A cyclic submenu graph was accepted");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}

		GLPopupMenu tall = new GLPopupMenu();
		for (int index = 0; index < 30; index++)
			tall.addItem("Long menu item " + index);
		tall.show(host, 10, host.getHeight() - 1);
		long visibleItems = tall.getItems().stream()
				.filter(GLMenuItem::isVisible).count();
		if (Math.abs(tall.getHeight() - (host.getHeight() - 1)) > 0.01f
				|| visibleItems <= 0 || visibleItems >= tall.getItems().size())
			throw new AssertionError(
					"Oversized popup did not fill its available viewport");
		tall.close();

		GLPopupMenu titled = new GLPopupMenu();
		titled.setTitle("Fixed footer");
		titled.setTitlePosition(GLPopupTitlePosition.BOTTOM);
		titled.setRootWidth(180);
		for (int index = 0; index < 20; index++)
			titled.addItem("Titled item " + index);
		titled.show(host, 20, 10);
		if (Math.abs(titled.getWidth() - 180) > 0.01f)
			throw new AssertionError("Explicit root popup width was ignored");
		if (!"Fixed footer".equals(titled.getTitle())
				|| titled.getTitlePosition() != GLPopupTitlePosition.BOTTOM)
			throw new AssertionError("Popup title configuration was lost");
		titled.close();

		GLPopupMenu widthModes = new GLPopupMenu();
		widthModes.addItem("An intentionally extremely wide popup-menu item");
		widthModes.setRootWidth(100);
		widthModes.setTextOverflow(GLPopupTextOverflow.ELLIPSIS);
		widthModes.show(host, 20, 20);
		if (Math.abs(widthModes.getWidth() - 100) > 0.01f)
			throw new AssertionError("Ellipsis mode did not retain root width");
		widthModes.close();
		widthModes.setTextOverflow(GLPopupTextOverflow.HORIZONTAL_SCROLL);
		widthModes.show(host, 20, 20);
		if (Math.abs(widthModes.getWidth() - 100) > 0.01f)
			throw new AssertionError(
					"Horizontal-scroll mode did not retain root width");
		widthModes.close();
		widthModes.setTextOverflow(GLPopupTextOverflow.EXPAND);
		widthModes.show(host, 20, 20);
		if (widthModes.getWidth() <= 100)
			throw new AssertionError("Expand mode did not ignore root width");
		widthModes.close();

		GLMenuBar menuBar = new GLMenuBar();
		GLPopupMenu fileMenu = new GLPopupMenu();
		fileMenu.addItem("Open");
		GLMenuBarItem fileItem = menuBar.addMenu("File", fileMenu);
		menuBar.setBounds(0, 0, 400, menuBar.getPreferredGLSize().height());
		host.addGL(menuBar);
		fileMenu.show(host, 0, menuBar.getHeight());
		if (menuBar.getActiveItem() != fileItem || !fileItem.isActive())
			throw new AssertionError("Opening a menu did not activate its bar item");
		fileMenu.close();
		if (menuBar.getActiveItem() != null || fileItem.isActive())
			throw new AssertionError("Closing a menu retained its bar-item state");
		try {
			menuBar.addMenu("Duplicate", fileMenu);
			throw new AssertionError("One popup was accepted twice by a menu bar");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}

		System.out.println("GL menu self-test passed.");
	}

	private static void testEllipsisNormalization() {
		GLMenuItem item = new GLMenuItem("Öffnen ...");
		if (!"Öffnen …".equals(item.getText()))
			throw new AssertionError("Menu ellipsis was not normalized");
		if (!"Öffnen …".equals(item.getActionCommand()))
			throw new AssertionError("Default action command does not match display text");
	}

	private static void testIconsAndPresentations() {
		TestPanel host = new TestPanel();
		host.setSize(400, 300);
		GLIcon icon = GLIcon.fromSvgResource(
				"com/vingame/glswing/icons/test_icon.svg");
		GLPopupMenu plain = new GLPopupMenu();
		plain.addItem("Action");
		plain.addItem("An intentionally wide aligned menu entry");
		plain.setTitle("Tools");
		plain.show(host, 10, 10);
		float plainWidth = plain.getWidth();
		plain.close();

		GLPopupMenu illustrated = new GLPopupMenu();
		GLMenuItem action = illustrated.addItem("Action");
		action.setIcon(icon);
		illustrated.addItem("An intentionally wide aligned menu entry");
		illustrated.setTitle("Tools");
		illustrated.setTitleIcon(icon);
		illustrated.show(host, 10, 10);
		if (action.getIcon() != icon || illustrated.getTitleIcon() != icon
				|| illustrated.getWidth() <= plainWidth)
			throw new AssertionError(
					"Popup icons were not retained or included in layout");
		illustrated.close();

		GLComboBox<String> combo = new GLComboBox<>(java.util.List.of("One"));
		combo.setItemPresentationProvider(
				value -> new GLItemPresentation(value, icon));
		if (combo.getPopupMenu().getItems().get(0).getIcon() != icon)
			throw new AssertionError(
					"Combo-box presentation did not populate popup icons");
	}

	private static void testMnemonics() {
		TestPanel host = new TestPanel();
		host.setSize(400, 300);
		AtomicInteger actions = new AtomicInteger();
		GLPopupMenu file = new GLPopupMenu();
		GLMenuItem save = file.addItem("Save",
				event -> actions.incrementAndGet());
		GLMenuItem saveAs = file.addItem("Save As");
		GLMenuBar bar = new GLMenuBar();
		GLMenuBarItem fileItem = bar.addMenu("File", file);
		bar.setBounds(0, 0, 400, bar.getPreferredGLSize().height());
		host.addGL(bar);

		if (fileItem.getMnemonic() != KeyEvent.VK_F
				|| fileItem.getDisplayedMnemonicIndex() != 0
				|| save.getMnemonic() != KeyEvent.VK_S
				|| save.getDisplayedMnemonicIndex() != 0
				|| saveAs.getMnemonic() != KeyEvent.VK_A)
			throw new AssertionError(
					"Automatic mnemonics were not unique or correctly displayed");
		saveAs.setText("Speichern unter");
		if (saveAs.getMnemonic() != KeyEvent.VK_U
				|| saveAs.getDisplayedMnemonicIndex() != 10)
			throw new AssertionError(
					"Automatic mnemonic did not follow localized text");
		saveAs.setMnemonic(KeyEvent.VK_U);
		try {
			file.addItem(new GLMenuItem("Use", KeyEvent.VK_U));
			throw new AssertionError("Duplicate explicit mnemonic was accepted");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
		saveAs.setMnemonicEnabled(false);
		if (saveAs.getMnemonic() != KeyEvent.VK_UNDEFINED)
			throw new AssertionError("Disabled mnemonic remained assigned");
		saveAs.useAutomaticMnemonic();
		if (saveAs.getMnemonic() != KeyEvent.VK_U)
			throw new AssertionError("Automatic mnemonic was not restored");
		file.show(host, 0, bar.getHeight());
		KeyEvent itemKey = key(host, KeyEvent.VK_S, 0);
		if (!GLMenuMnemonics.handleKeyPressed(host, itemKey, GLInputMaps.DEFAULT)
				|| !itemKey.isConsumed() || actions.get() != 1 || file.isShowing())
			throw new AssertionError("Open-menu mnemonic did not activate its item");

		file.show(host, 0, bar.getHeight());
		KeyEvent wrongModifier = key(host, KeyEvent.VK_S,
				InputEvent.CTRL_DOWN_MASK);
		if (GLMenuMnemonics.handleKeyPressed(host, wrongModifier,
				GLInputMaps.DEFAULT) || actions.get() != 1)
			throw new AssertionError("Menu mnemonic ignored configured modifiers");
		file.close();
	}

	private static KeyEvent key(TestPanel host, int keyCode, int modifiers) {
		return new KeyEvent(host, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
				modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
	}

	private static final class TestPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;

		@Override
		protected void render(GLContext2D context) {
		}
	}
}
