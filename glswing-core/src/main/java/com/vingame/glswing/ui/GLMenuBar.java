package com.vingame.glswing.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;

/** A classic horizontal menu bar backed by {@link GLPopupMenu}s. */
public final class GLMenuBar extends GLContainer implements GLPopupMenuListener {

	private final List<GLMenuBarItem> items = new ArrayList<>();
	private GLMenuBarStyle localStyle;
	private GLMenuBarStyle appliedStyle;
	private GLMenuBarItem activeItem;

	public GLMenuBar() {
		updatePresentation();
	}

	public GLMenuBarItem addMenu(String text, GLPopupMenu menu) {
		if (menu == null)
			throw new IllegalArgumentException("Menu-bar popup must not be null");
		for (GLMenuBarItem item : items)
			if (item.getMenu() == menu)
				throw new IllegalArgumentException(
						"A popup menu can only occur once in a menu bar");
		menu.claimMenuBar(this);
		GLMenuBarItem item = new GLMenuBarItem(text, menu);
		item.attach(this);
		items.add(item);
		menu.addPopupMenuListener(this);
		addGL(item);
		updatePreferredSizeAndLayout();
		return item;
	}

	public List<GLMenuBarItem> getItems() {
		return List.copyOf(items);
	}

	public GLMenuBarItem getActiveItem() {
		return activeItem;
	}

	public void closeMenu() {
		GLMenuBarItem active = activeItem;
		if (active != null)
			active.getMenu().close();
	}

	public void setGLStyle(GLMenuBarStyle style) {
		if (style == null)
			throw new IllegalArgumentException("Menu-bar style must not be null");
		if (localStyle == style)
			return;
		localStyle = style;
		updatePresentation();
	}

	public void clearGLStyle() {
		if (localStyle == null)
			return;
		localStyle = null;
		updatePresentation();
	}

	public GLMenuBarStyle getGLStyle() {
		return localStyle != null ? localStyle : getGLTheme().menuBarStyle();
	}

	@Override
	protected void record(GLContext2D context) {
		GLMenuBarStyle style = getGLStyle();
		float width = getWidth();
		float height = getHeight();
		if (width <= 0 || height <= 0)
			return;
		context.fillRect(0, 0, width, height, style.background());
		if (style.borderWidth() > 0)
			context.fillRect(0, Math.max(0, height - style.borderWidth()),
					width, style.borderWidth(), style.borderColor());
	}

	@Override
	protected void resized(float width, float height) {
		layoutItems();
	}

	@Override
	protected void themeChanged() {
		if (localStyle == null && !getGLStyle().equals(appliedStyle))
			updatePresentation();
	}

	@Override
	public void popupMenuOpened(GLPopupMenu menu) {
		GLMenuBarItem item = itemFor(menu);
		if (item != null)
			setActiveItem(item);
	}

	@Override
	public void popupMenuClosed(GLPopupMenu menu) {
		if (activeItem != null && activeItem.getMenu() == menu)
			setActiveItem(null);
	}

	void itemClicked(GLMenuBarItem item) {
		if (item == activeItem && item.getMenu().isShowing()) {
			item.getMenu().close();
			return;
		}
		open(item);
	}

	void itemHovered(GLMenuBarItem item) {
		if (activeItem != null && activeItem != item
				&& activeItem.getMenu().isShowing())
			open(item);
	}

	void itemPresentationChanged() {
		updatePreferredSizeAndLayout();
	}

	private void open(GLMenuBarItem item) {
		GLParent host = overlayHost();
		float[] bounds = item.getLastRenderedScreenBounds();
		if (host == null || bounds == null)
			return;
		item.getMenu().showFromMenuBar(host, bounds[0], bounds[1] + bounds[3]);
	}

	private GLParent overlayHost() {
		GLParent parent = getGLParent();
		if (parent == null)
			return null;
		while (parent instanceof GLContainer container
				&& container.getGLParent() != null)
			parent = container.getGLParent();
		return parent;
	}

	private void setActiveItem(GLMenuBarItem item) {
		if (activeItem == item)
			return;
		GLMenuBarItem previous = activeItem;
		activeItem = item;
		if (previous != null)
			previous.setActive(false);
		if (item != null)
			item.setActive(true);
	}

	private GLMenuBarItem itemFor(GLPopupMenu menu) {
		for (GLMenuBarItem item : items)
			if (item.getMenu() == menu)
				return item;
		return null;
	}

	GLPickResult resolveImmediateHover(float screenX, float screenY) {
		for (int index = items.size() - 1; index >= 0; index--) {
			GLMenuBarItem item = items.get(index);
			float[] bounds = item.getLastRenderedScreenBounds();
			if (bounds == null || bounds[2] <= 0 || bounds[3] <= 0
					|| screenX < bounds[0] || screenY < bounds[1]
					|| screenX > bounds[0] + bounds[2]
					|| screenY > bounds[1] + bounds[3])
				continue;
			float scaleX = item.getWidth() / bounds[2];
			float scaleY = item.getHeight() / bounds[3];
			float inverseX = -bounds[0] * scaleX;
			float inverseY = -bounds[1] * scaleY;
			return new GLPickResult(0, item, screenX, screenY,
					screenX * scaleX + inverseX,
					screenY * scaleY + inverseY,
					scaleX, 0, 0, scaleY, inverseX, inverseY);
		}
		return null;
	}

	private void updatePresentation() {
		appliedStyle = getGLStyle();
		for (GLMenuBarItem item : items)
			item.menuBarStyleChanged();
		updatePreferredSizeAndLayout();
		invalidateRecording();
	}

	private void updatePreferredSizeAndLayout() {
		GLMenuBarStyle style = getGLStyle();
		float edgeInset = Math.max(1, style.borderWidth());
		float width = edgeInset * 2;
		for (int index = 0; index < items.size(); index++) {
			if (index > 0)
				width += style.itemGap();
			width += items.get(index).getPreferredGLSize().width();
		}
		setIntrinsicPreferredGLSize(new GLSize(width, style.height()));
		layoutItems();
	}

	private void layoutItems() {
		GLMenuBarStyle style = getGLStyle();
		float x = Math.max(1, style.borderWidth());
		float height = getHeight() > 0 ? getHeight() : style.height();
		for (GLMenuBarItem item : items) {
			float width = item.getPreferredGLSize().width();
			item.setBounds(x, 0, width, height);
			x += width + style.itemGap();
		}
	}
}
