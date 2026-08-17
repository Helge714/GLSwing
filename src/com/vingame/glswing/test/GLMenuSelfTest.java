package com.vingame.glswing.test;

import java.util.concurrent.atomic.AtomicInteger;

import com.vingame.glswing.component.GLSwingPanel;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLMenuBar;
import com.vingame.glswing.ui.GLMenuBarItem;
import com.vingame.glswing.ui.GLMenuItem;
import com.vingame.glswing.ui.GLPopupMenu;
import com.vingame.glswing.ui.GLPopupTitlePosition;
import com.vingame.glswing.ui.GLPopupTextOverflow;

/** Checks menu ownership, popup lifetime and nested submenu hierarchy. */
public final class GLMenuSelfTest {

	private GLMenuSelfTest() {
	}

	public static void main(String[] args) {
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

	private static final class TestPanel extends GLSwingPanel {
		private static final long serialVersionUID = 1L;

		@Override
		protected void render(GLContext2D context) {
		}
	}
}
