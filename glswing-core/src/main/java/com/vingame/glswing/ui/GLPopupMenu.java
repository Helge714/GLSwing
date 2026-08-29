package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLMouseWheelEvent;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.input.GLImmediateHoverRegistry;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLComposite;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLCornerRadii;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLLayerEffects;
import com.vingame.glswing.rendering.GLRenderParameters;
import com.vingame.glswing.rendering.GLStroke;

/**
 * Lightweight GPU-rendered popup menu. All open submenu levels share one
 * screen-space overlay, so they can overlap unrelated controls and work
 * identically on {@code GLSwingPanel} and {@code GLSwingContainerCanvas}.
 */
public class GLPopupMenu extends GLContainer {
	private enum AttachedEdge { NONE, TOP, BOTTOM }

	private static final float SCROLL_BAR_WIDTH = 8;
	private static final float SCROLL_BAR_GAP = 3;
	private static final float OVERFLOW_FADE_HEIGHT = 10;
	private static final float HORIZONTAL_OVERFLOW_FADE_WIDTH = 14;
	private static final Map<GLParent, WeakReference<MenuSession>> ACTIVE =
			new WeakHashMap<>();

	private final List<GLContainer> rows = new ArrayList<>();
	private final CopyOnWriteArrayList<GLPopupMenuListener> popupMenuListeners =
			new CopyOnWriteArrayList<>();
	private final MenuScrollBar scrollBar;
	private final MenuHorizontalScrollBar horizontalScrollBar;
	private final MenuFadeOverlay topFade;
	private final MenuFadeOverlay bottomFade;
	private final MenuHorizontalFadeOverlay leftFade;
	private final MenuHorizontalFadeOverlay rightFade;
	private final MenuTitle titleView;
	private GLMenuStyle localStyle;
	private GLMenuStyle appliedStyle;
	private GLMenuItem parentItem;
	private GLMenuBar menuBarOwner;
	private MenuSession session;
	private boolean menuLayoutDirty = true;
	private boolean immediateCpuPickingEnabled = true;
	private float layoutMaximumHeight = Float.POSITIVE_INFINITY;
	private float scrollOffset;
	private float maximumScrollOffset;
	private float horizontalScrollOffset;
	private float maximumHorizontalScrollOffset;
	private AttachedEdge attachedEdge = AttachedEdge.NONE;
	private GLComposite viewportComposite = GLComposite.DEFAULT;
	private boolean viewportClipRequired;
	private String title = "";
	private GLPopupTitlePosition titlePosition = GLPopupTitlePosition.TOP;
	private float rootWidth = Float.NaN;
	private float activeRootWidth = Float.NaN;
	private float activeMaximumWidth = Float.POSITIVE_INFINITY;
	private GLPopupTextOverflow textOverflow = GLPopupTextOverflow.ELLIPSIS;
	private boolean ellipsisActive;

	public GLPopupMenu() {
		scrollBar = new MenuScrollBar(this);
		horizontalScrollBar = new MenuHorizontalScrollBar(this);
		topFade = new MenuFadeOverlay(this, true);
		bottomFade = new MenuFadeOverlay(this, false);
		leftFade = new MenuHorizontalFadeOverlay(this, true);
		rightFade = new MenuHorizontalFadeOverlay(this, false);
		titleView = new MenuTitle(this);
		addGL(scrollBar);
		addGL(horizontalScrollBar);
		addGL(topFade);
		addGL(bottomFade);
		addGL(leftFade);
		addGL(rightFade);
		addGL(titleView);
		scrollBar.setVisible(false);
		horizontalScrollBar.setVisible(false);
		topFade.setVisible(false);
		bottomFade.setVisible(false);
		leftFade.setVisible(false);
		rightFade.setVisible(false);
		titleView.setVisible(false);
	}

	public final GLMenuItem addItem(String text) {
		return addItem(new GLMenuItem(text));
	}

	public final GLMenuItem addItem(String text, ActionListener listener) {
		GLMenuItem item = new GLMenuItem(text);
		item.addActionListener(listener);
		return addItem(item);
	}

	public final GLMenuItem addItem(GLMenuItem item) {
		if (item == null)
			throw new IllegalArgumentException("Menu item must not be null");
		if (isShowing())
			throw new IllegalStateException("Close a menu before changing its rows");
		if (rows.contains(item))
			throw new IllegalArgumentException("Menu item was already added");
		GLPopupMenu subMenu = item.getSubMenu();
		if (subMenu == this || subMenu != null && subMenu.reaches(this))
			throw new IllegalArgumentException("Submenu assignment creates a cycle");
		item.setOwnerMenu(this);
		rows.add(item);
		addGL(item);
		invalidateMenuLayout();
		return item;
	}

	public final boolean removeItem(GLMenuItem item) {
		if (isShowing())
			throw new IllegalStateException("Close a menu before changing its rows");
		if (item == null || !rows.remove(item))
			return false;
		item.setOwnerMenu(null);
		removeGL(item);
		invalidateMenuLayout();
		return true;
	}

	/** Removes every item and separator while retaining popup configuration. */
	public final void removeAllItems() {
		if (isShowing())
			throw new IllegalStateException("Close a menu before changing its rows");
		for (GLContainer row : List.copyOf(rows)) {
			if (row instanceof GLMenuItem item)
				item.setOwnerMenu(null);
			removeGL(row);
		}
		rows.clear();
		invalidateMenuLayout();
	}

	public final GLMenuItem addSubMenu(String text, GLPopupMenu subMenu) {
		if (subMenu == null)
			throw new IllegalArgumentException("Submenu must not be null");
		if (subMenu == this || subMenu.reaches(this))
			throw new IllegalArgumentException("Submenu assignment creates a cycle");
		GLMenuItem item = new GLMenuItem(text);
		item.setSubMenu(subMenu);
		return addItem(item);
	}

	public final void addSeparator() {
		if (isShowing())
			throw new IllegalStateException("Close a menu before changing its rows");
		MenuSeparator separator = new MenuSeparator(this);
		rows.add(separator);
		addGL(separator);
		invalidateMenuLayout();
	}

	public final List<GLMenuItem> getItems() {
		return rows.stream().filter(GLMenuItem.class::isInstance)
				.map(GLMenuItem.class::cast).toList();
	}

	public final String getTitle() {
		return title;
	}

	public final void setTitle(String title) {
		if (title == null)
			throw new IllegalArgumentException("Popup title must not be null");
		if (this.title.equals(title))
			return;
		this.title = title;
		titleView.invalidateRecording();
		invalidateMenuLayout();
	}

	public final GLPopupTitlePosition getTitlePosition() {
		return titlePosition;
	}

	public final void setTitlePosition(GLPopupTitlePosition position) {
		if (position == null)
			throw new IllegalArgumentException("Popup title position must not be null");
		if (titlePosition == position)
			return;
		titlePosition = position;
		titleView.invalidateRecording();
		invalidateMenuLayout();
	}

	/** Forces this menu's outer width only when it is opened as a root popup. */
	public final void setRootWidth(float width) {
		if (!Float.isFinite(width) || width <= 0)
			throw new IllegalArgumentException(
					"Root popup width must be finite and positive");
		if (Float.compare(rootWidth, width) == 0)
			return;
		rootWidth = width;
		invalidateMenuLayout();
	}

	public final void clearRootWidth() {
		if (Float.isNaN(rootWidth))
			return;
		rootWidth = Float.NaN;
		invalidateMenuLayout();
	}

	public final boolean hasRootWidth() {
		return Float.isFinite(rootWidth);
	}

	public final float getRootWidth() {
		return rootWidth;
	}

	public final GLPopupTextOverflow getTextOverflow() {
		return textOverflow;
	}

	public final void setTextOverflow(GLPopupTextOverflow overflow) {
		if (overflow == null)
			throw new IllegalArgumentException("Popup text overflow must not be null");
		if (textOverflow == overflow)
			return;
		textOverflow = overflow;
		horizontalScrollOffset = 0;
		invalidateMenuLayout();
	}

	public final void setGLStyle(GLMenuStyle style) {
		if (style == null)
			throw new IllegalArgumentException("Menu style must not be null");
		if (localStyle == style)
			return;
		localStyle = style;
		styleChanged();
	}

	public final void clearGLStyle() {
		if (localStyle == null)
			return;
		localStyle = null;
		styleChanged();
	}

	public final GLMenuStyle getGLStyle() {
		return localStyle != null ? localStyle : getGLTheme().menuStyle();
	}

	/** Opens this menu in coordinates local to the supplied host. */
	public final void show(GLParent host, float x, float y) {
		show(host, x, y, false);
	}

	final void showFromMenuBar(GLParent host, float x, float y) {
		show(host, x, y, true);
	}

	final GLPopupDirection showFromComboBox(GLParent host, GLContainer anchor,
			boolean matchAnchorWidth) {
		if (host == null || anchor == null)
			throw new IllegalArgumentException("Combo popup host and anchor are required");
		float[] bounds = anchor.getLastRenderedScreenBounds();
		if (bounds == null || bounds[2] <= 0 || bounds[3] <= 0)
			return null;
		float scaleX = bounds[2] / Math.max(0.0001f, anchor.getWidth());
		float scaleY = bounds[3] / Math.max(0.0001f, anchor.getHeight());
		float inset = Math.max(1, getGLStyle().borderWidth());
		float below = Math.max(0, hostHeight(host) - bounds[1] - bounds[3] - inset);
		float above = Math.max(0, bounds[1] - inset);
		float naturalScreenHeight = naturalHeight() * scaleY;
		GLPopupDirection direction = below >= naturalScreenHeight
				? GLPopupDirection.BELOW
				: above >= naturalScreenHeight
						? GLPopupDirection.ABOVE
						: below >= above
								? GLPopupDirection.BELOW
								: GLPopupDirection.ABOVE;
		float available = direction == GLPopupDirection.BELOW ? below : above;
		ComboAnchor comboAnchor = new ComboAnchor(anchor, direction,
				Math.max(1, available / scaleY), matchAnchorWidth,
				Math.max(0.0001f, scaleX), Math.max(0.0001f, scaleY));
		show(host, 0, 0, false, comboAnchor);
		return direction;
	}

	private void show(GLParent host, float x, float y,
			boolean anchoredToMenuBar) {
		show(host, x, y, anchoredToMenuBar, null);
	}

	private void show(GLParent host, float x, float y,
			boolean anchoredToMenuBar, ComboAnchor comboAnchor) {
		if (host == null)
			throw new IllegalArgumentException("Menu host must not be null");
		close();
		MenuSession previous;
		synchronized (ACTIVE) {
			WeakReference<MenuSession> reference = ACTIVE.get(host);
			previous = reference == null ? null : reference.get();
		}
		if (previous != null)
			previous.close();
		MenuSession opened = new MenuSession(host, this, anchoredToMenuBar,
				comboAnchor);
		synchronized (ACTIVE) {
			ACTIVE.put(host, new WeakReference<>(opened));
		}
		opened.openRoot(x, y);
		firePopupMenuOpened();
	}

	public final void close() {
		MenuSession current = session;
		if (current != null)
			current.close();
	}

	public final boolean isShowing() {
		return session != null;
	}

	final GLPopupDirection getAttachedDirection() {
		return attachedEdge == AttachedEdge.TOP ? GLPopupDirection.BELOW
				: attachedEdge == AttachedEdge.BOTTOM ? GLPopupDirection.ABOVE : null;
	}

	public final void addPopupMenuListener(GLPopupMenuListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("Popup-menu listener must not be null");
		popupMenuListeners.addIfAbsent(listener);
	}

	public final void removePopupMenuListener(GLPopupMenuListener listener) {
		popupMenuListeners.remove(listener);
	}

	/**
	 * Selects the immediate CPU bounds path for hover resolution. Disable this on
	 * a root menu to compare it with the normal asynchronous GPU snapshot path.
	 */
	public final void setImmediateCpuPickingEnabled(boolean enabled) {
		if (immediateCpuPickingEnabled == enabled)
			return;
		immediateCpuPickingEnabled = enabled;
		MenuSession current = session;
		if (current != null && current.rootMenu == this)
			current.updateImmediateHoverRegistration();
	}

	public final boolean isImmediateCpuPickingEnabled() {
		return immediateCpuPickingEnabled;
	}

	@Override
	public void mouseWheelMoved(GLMouseWheelEvent event) {
		scrollBy(event);
		event.consume();
	}

	@Override
	protected void record(GLContext2D context) {
		GLMenuStyle style = getGLStyle();
		float width = getWidth();
		float height = getHeight();
		if (width <= 0 || height <= 0)
			return;
		if (attachedEdge == AttachedEdge.NONE) {
			context.fillAndDrawUIRoundRect(0, 0, width, height,
					style.cornerRadius(), style.borderWidth(), style.background(),
					style.borderColor(), GLStroke.WidthSpace.SCREEN);
			return;
		}
		float topRadius = attachedEdge == AttachedEdge.TOP
				? 0 : style.cornerRadius();
		float bottomRadius = attachedEdge == AttachedEdge.BOTTOM
				? 0 : style.cornerRadius();
		GLCornerRadii radii = new GLCornerRadii(topRadius, topRadius,
				bottomRadius, bottomRadius);
		context.fillRoundRect(0, 0, width, height, radii, style.background());
		if (style.borderWidth() > 0) {
			context.setStroke(new GLStroke(style.borderWidth())
					.withWidthSpace(GLStroke.WidthSpace.SCREEN));
			context.drawRoundRect(0, 0, width, height, radii, style.borderColor());
		}
	}

	@Override
	protected void renderChildren(GLContext2D context) {
		if (!viewportClipRequired) {
			super.renderChildren(context);
			return;
		}
		GLInsets padding = getGLStyle().padding();
		float width = getWidth() - padding.horizontal();
		float height = getHeight() - padding.vertical();
		if (width <= 0 || height <= 0)
			return;
		context.beginLayer(padding.left(), padding.top(), width, height);
		try {
			super.renderChildren(context);
		} finally {
			context.endLayer(viewportComposite);
		}
	}

	@Override
	protected void themeChanged() {
		if (localStyle == null && !getGLStyle().equals(appliedStyle))
			styleChanged();
	}

	final void itemHovered(GLMenuItem item) {
		if (session == null)
			return;
		if (item.getSubMenu() == null)
			session.closeAfter(this);
		else
			session.openSubMenu(this, item);
	}

	final void openSubMenu(GLMenuItem item) {
		if (session != null && item.getSubMenu() != null)
			session.openSubMenu(this, item);
	}

	final void closeSession() {
		close();
	}

	final void invalidateMenuLayout() {
		menuLayoutDirty = true;
		invalidateRecording();
	}

	final void claimParentItem(GLMenuItem item) {
		if (item == null)
			throw new IllegalArgumentException("Parent menu item must not be null");
		if (parentItem != null && parentItem != item)
			throw new IllegalArgumentException(
					"A popup menu cannot be assigned to two menu items");
		if (item.getSubMenu() == this)
			return;
		for (GLPopupMenu ancestor = itemOwner(item); ancestor != null;
				ancestor = ancestor.parentItem == null
						? null : itemOwner(ancestor.parentItem))
			if (ancestor == this)
				throw new IllegalArgumentException("Submenu assignment creates a cycle");
		parentItem = item;
	}

	final void releaseParentItem(GLMenuItem item) {
		if (parentItem == item)
			parentItem = null;
	}

	final void claimMenuBar(GLMenuBar menuBar) {
		if (menuBarOwner != null && menuBarOwner != menuBar)
			throw new IllegalArgumentException(
					"A popup menu cannot belong to two menu bars");
		menuBarOwner = menuBar;
	}

	private void prepareLayout(float maximumHeight) {
		maximumHeight = Math.max(1, maximumHeight);
		if (Float.compare(layoutMaximumHeight, maximumHeight) != 0) {
			layoutMaximumHeight = maximumHeight;
			menuLayoutDirty = true;
		}
		if (!menuLayoutDirty)
			return;
		GLMenuStyle style = getGLStyle();
		appliedStyle = style;
		float contentWidth = style.minimumWidth();
		for (GLContainer row : rows)
			if (row instanceof GLMenuItem item)
				contentWidth = Math.max(contentWidth, item.requiredWidth(style));
		if (!title.isEmpty())
			contentWidth = Math.max(contentWidth, titleView.requiredWidth(style));

		GLInsets padding = style.padding();
		float titleHeight = title.isEmpty() ? 0 : style.itemHeight();
		float naturalRowsHeight = 0;
		for (GLContainer row : rows)
			naturalRowsHeight += rowHeight(row, style);
		float totalAvailableRowsHeight = Math.max(0,
				maximumHeight - padding.vertical() - titleHeight);
		float availableRowsHeight = totalAvailableRowsHeight;
		boolean scrollable = naturalRowsHeight > availableRowsHeight;

		for (GLContainer row : rows)
			row.setVisible(false);
		topFade.setVisible(false);
		bottomFade.setVisible(false);
		leftFade.setVisible(false);
		rightFade.setVisible(false);
		horizontalScrollBar.setVisible(false);
		titleView.setVisible(titleHeight > 0);

		float rowViewportY = padding.top()
				+ (titlePosition == GLPopupTitlePosition.TOP ? titleHeight : 0);
		float scrollbarExtra = scrollable ? SCROLL_BAR_GAP + SCROLL_BAR_WIDTH : 0;
		float fixedOuterWidth = activeRootWidth;
		float naturalOuterWidth = contentWidth + padding.horizontal()
				+ scrollbarExtra;
		float requestedOuterWidth = Float.isFinite(fixedOuterWidth)
				? fixedOuterWidth : naturalOuterWidth;
		float popupWidth = textOverflow == GLPopupTextOverflow.EXPAND
				? Math.max(requestedOuterWidth, naturalOuterWidth)
				: Math.min(requestedOuterWidth, activeMaximumWidth);
		popupWidth = Math.max(1, popupWidth);
		float viewportWidth = Math.max(0,
				popupWidth - padding.horizontal() - scrollbarExtra);
		boolean horizontalScrollable = textOverflow
				== GLPopupTextOverflow.HORIZONTAL_SCROLL
				&& contentWidth > viewportWidth + 0.01f;
		float horizontalScrollbarExtra = horizontalScrollable
				? SCROLL_BAR_GAP + SCROLL_BAR_WIDTH : 0;
		availableRowsHeight = Math.max(0,
				totalAvailableRowsHeight - horizontalScrollbarExtra);
		boolean adjustedScrollable = naturalRowsHeight > availableRowsHeight;
		if (adjustedScrollable != scrollable) {
			scrollable = adjustedScrollable;
			scrollbarExtra = scrollable ? SCROLL_BAR_GAP + SCROLL_BAR_WIDTH : 0;
			naturalOuterWidth = contentWidth + padding.horizontal() + scrollbarExtra;
			requestedOuterWidth = Float.isFinite(fixedOuterWidth)
					? fixedOuterWidth : naturalOuterWidth;
			popupWidth = textOverflow == GLPopupTextOverflow.EXPAND
					? Math.max(requestedOuterWidth, naturalOuterWidth)
					: Math.min(requestedOuterWidth, activeMaximumWidth);
			popupWidth = Math.max(1, popupWidth);
			viewportWidth = Math.max(0,
					popupWidth - padding.horizontal() - scrollbarExtra);
			horizontalScrollable = textOverflow
					== GLPopupTextOverflow.HORIZONTAL_SCROLL
					&& contentWidth > viewportWidth + 0.01f;
			horizontalScrollbarExtra = horizontalScrollable
					? SCROLL_BAR_GAP + SCROLL_BAR_WIDTH : 0;
			availableRowsHeight = Math.max(0,
					totalAvailableRowsHeight - horizontalScrollbarExtra);
		}
		scrollBar.setVisible(scrollable);
		horizontalScrollBar.setVisible(horizontalScrollable);
		if (!scrollable)
			scrollOffset = 0;
		else
			scrollOffset = Math.max(0, scrollOffset);
		maximumScrollOffset = scrollable
				? Math.max(0, naturalRowsHeight - availableRowsHeight) : 0;
		scrollOffset = Math.min(scrollOffset, maximumScrollOffset);
		float viewportHeight = scrollable
				? availableRowsHeight : naturalRowsHeight;
		ellipsisActive = textOverflow == GLPopupTextOverflow.ELLIPSIS
				&& contentWidth > viewportWidth + 0.01f;
		maximumHorizontalScrollOffset = horizontalScrollable
				? Math.max(0, contentWidth - viewportWidth) : 0;
		if (!horizontalScrollable)
			horizontalScrollOffset = 0;
		else
			horizontalScrollOffset = Math.min(horizontalScrollOffset,
					maximumHorizontalScrollOffset);
		float rowWidth = horizontalScrollable ? contentWidth : viewportWidth;
		float rowY = -scrollOffset;
		float inset = Math.min(Math.min(padding.left(), padding.right()),
				Math.min(padding.top(), padding.bottom()));
		float edgeRadius = Math.max(0, style.cornerRadius() - inset);
		float standardItemRadius = Math.min(3, style.itemHeight() * 0.25f);
		viewportClipRequired = scrollable || horizontalScrollable
				|| edgeRadius > standardItemRadius;
		viewportComposite = GLComposite.DEFAULT.withEffect(
				GLLayerEffects.roundedClip(new GLCornerRadii(
						attachedEdge == AttachedEdge.TOP ? 0 : edgeRadius,
						attachedEdge == AttachedEdge.TOP ? 0 : edgeRadius,
						attachedEdge == AttachedEdge.BOTTOM ? 0 : edgeRadius,
						attachedEdge == AttachedEdge.BOTTOM ? 0 : edgeRadius)));
		for (int index = 0; index < rows.size(); index++) {
			GLContainer row = rows.get(index);
			float height = rowHeight(row, style);
			boolean intersectsViewport = rowY + height > 0
					&& rowY < viewportHeight;
			if (intersectsViewport) {
				row.setVisible(true);
				row.setBounds(padding.left() - horizontalScrollOffset,
						rowViewportY + rowY, rowWidth, height);
			}
			rowY += height;
		}
		if (scrollable) {
			scrollBar.setBounds(popupWidth - padding.right() - scrollbarExtra,
					rowViewportY, scrollbarExtra, availableRowsHeight);
			scrollBar.setRange(scrollOffset, maximumScrollOffset,
					availableRowsHeight, naturalRowsHeight);
			float fadeHeight = Math.min(OVERFLOW_FADE_HEIGHT,
					availableRowsHeight * 0.5f);
			topFade.setVisible(scrollOffset > 0.01f);
			bottomFade.setVisible(
					scrollOffset < maximumScrollOffset - 0.01f);
			topFade.setBounds(padding.left(), rowViewportY,
					viewportWidth, fadeHeight);
			bottomFade.setBounds(padding.left(),
					rowViewportY + availableRowsHeight - fadeHeight,
					viewportWidth, fadeHeight);
			topFade.bringToFront();
			bottomFade.bringToFront();
			scrollBar.bringToFront();
		}
		if (horizontalScrollable) {
			float fadeWidth = Math.min(HORIZONTAL_OVERFLOW_FADE_WIDTH,
					viewportWidth * 0.5f);
			leftFade.setVisible(horizontalScrollOffset > 0.01f);
			rightFade.setVisible(horizontalScrollOffset
					< maximumHorizontalScrollOffset - 0.01f);
			leftFade.setBounds(padding.left(), rowViewportY,
					fadeWidth, viewportHeight);
			rightFade.setBounds(padding.left() + viewportWidth - fadeWidth,
					rowViewportY, fadeWidth, viewportHeight);
			leftFade.bringToFront();
			rightFade.bringToFront();
			horizontalScrollBar.setBounds(padding.left(),
					rowViewportY + viewportHeight,
					viewportWidth + scrollbarExtra, horizontalScrollbarExtra);
			horizontalScrollBar.setRange(horizontalScrollOffset,
					maximumHorizontalScrollOffset, viewportWidth, contentWidth);
			horizontalScrollBar.bringToFront();
		}
		float popupHeight = padding.vertical() + titleHeight + viewportHeight
				+ horizontalScrollbarExtra;
		setSize(popupWidth, Math.min(maximumHeight, popupHeight));
		if (titleHeight > 0) {
			float titleY = titlePosition == GLPopupTitlePosition.TOP
					? padding.top()
					: rowViewportY + viewportHeight + horizontalScrollbarExtra;
			titleView.setBounds(padding.left(), titleY,
					popupWidth - padding.horizontal(), titleHeight);
			titleView.bringToFront();
		}
		menuLayoutDirty = false;
	}

	private void scrollBy(GLMouseWheelEvent event) {
		if (getGLInputMap().hasModifiers(GLInputActions.SCROLL_HORIZONTAL,
				event.getModifiersEx())
				&& maximumHorizontalScrollOffset > 0) {
			scrollHorizontallyBy(event);
			return;
		}
		if (!scrollBar.isVisible())
			return;
		float rotation = (float) event.getPreciseWheelRotation();
		if (rotation == 0)
			return;
		float distance;
		if (event.getScrollType()
				== java.awt.event.MouseWheelEvent.WHEEL_BLOCK_SCROLL) {
			distance = rotation * Math.max(1,
					(getHeight() - getGLStyle().padding().vertical()) * 0.8f);
		} else {
			float unit = Math.max(2, getGLStyle().itemHeight() * 0.18f);
			distance = rotation * Math.max(1, event.getScrollAmount()) * unit;
			if (Math.abs(rotation) >= 0.999f
					&& Math.abs(rotation - Math.rint(rotation)) < 0.0001f) {
				float minimum = Math.abs(rotation) * getGLStyle().itemHeight();
				distance = Math.copySign(Math.max(Math.abs(distance), minimum),
						distance);
			}
		}
		scrollToOffset(scrollOffset + distance);
	}

	private void scrollHorizontallyBy(GLMouseWheelEvent event) {
		float rotation = (float) event.getPreciseWheelRotation();
		if (rotation == 0)
			return;
		float distance = rotation * Math.max(1, event.getScrollAmount())
				* Math.max(2, getGLStyle().itemHeight() * 0.18f);
		if (Math.abs(rotation) >= 0.999f
				&& Math.abs(rotation - Math.rint(rotation)) < 0.0001f)
			distance = Math.copySign(Math.max(Math.abs(distance),
					getGLStyle().itemHeight()), distance);
		scrollToHorizontalOffset(horizontalScrollOffset + distance);
	}

	private void scrollToHorizontalOffset(float offset) {
		float next = Math.max(0, Math.min(maximumHorizontalScrollOffset, offset));
		if (Math.abs(next - horizontalScrollOffset) < 0.01f)
			return;
		horizontalScrollOffset = next;
		menuLayoutDirty = true;
		if (session != null)
			session.closeAfter(this);
		prepareLayout(layoutMaximumHeight);
		repaintAndRebuildPicking(this);
	}

	private void scrollHorizontallyToFraction(float fraction) {
		if (!horizontalScrollBar.isVisible())
			return;
		scrollToHorizontalOffset(Math.max(0, Math.min(1, fraction))
				* maximumHorizontalScrollOffset);
	}

	final boolean isEllipsisActive() {
		return ellipsisActive;
	}

	private void scrollToOffset(float offset) {
		float next = Math.max(0, Math.min(maximumScrollOffset, offset));
		if (Math.abs(next - scrollOffset) < 0.01f)
			return;
		scrollOffset = next;
		menuLayoutDirty = true;
		if (session != null)
			session.closeAfter(this);
		prepareLayout(layoutMaximumHeight);
		repaintAndRebuildPicking(this);
	}

	private void scrollToFraction(float fraction) {
		if (!scrollBar.isVisible())
			return;
		scrollToOffset(Math.max(0, Math.min(1, fraction))
				* maximumScrollOffset);
	}

	private void setAttachedEdge(AttachedEdge edge) {
		if (edge == null)
			throw new IllegalArgumentException("Attached edge must not be null");
		if (attachedEdge == edge)
			return;
		attachedEdge = edge;
		invalidateRecording();
		invalidateMenuLayout();
	}

	private boolean containsChildViewport(GLPickResult menuHit) {
		if (menuHit == null)
			return false;
		if (!scrollBar.isVisible())
			return true;
		GLInsets padding = getGLStyle().padding();
		return menuHit.getLocalX() >= padding.left()
				&& menuHit.getLocalX() <= getWidth() - padding.right()
				&& menuHit.getLocalY() >= padding.top()
				&& menuHit.getLocalY() <= getHeight() - padding.bottom();
	}

	private static float rowHeight(GLContainer row, GLMenuStyle style) {
		return row instanceof GLMenuItem
				? style.itemHeight() : style.separatorHeight();
	}

	final float naturalHeight() {
		GLMenuStyle style = getGLStyle();
		float height = style.padding().vertical();
		if (!title.isEmpty())
			height += style.itemHeight();
		for (GLContainer row : rows)
			height += rowHeight(row, style);
		return height;
	}

	private void styleChanged() {
		appliedStyle = getGLStyle();
		scrollBar.menuStyleChanged();
		horizontalScrollBar.menuStyleChanged();
		topFade.menuStyleChanged();
		bottomFade.menuStyleChanged();
		leftFade.menuStyleChanged();
		rightFade.menuStyleChanged();
		titleView.invalidateRecording();
		for (GLContainer row : rows)
			if (row instanceof GLMenuItem item)
				item.menuStyleChanged();
			else
				row.invalidateRecording();
		invalidateMenuLayout();
	}

	private static GLPopupMenu itemOwner(GLMenuItem item) {
		return item == null || !(item.getGLParent() instanceof GLPopupMenu menu)
				? null : menu;
	}

	private boolean reaches(GLPopupMenu target) {
		return reaches(target, java.util.Collections.newSetFromMap(
				new java.util.IdentityHashMap<>()));
	}

	private boolean reaches(GLPopupMenu target, java.util.Set<GLPopupMenu> visited) {
		if (this == target)
			return true;
		if (!visited.add(this))
			return false;
		for (GLContainer row : rows)
			if (row instanceof GLMenuItem item && item.getSubMenu() != null
					&& item.getSubMenu().reaches(target, visited))
				return true;
		return false;
	}

	private void firePopupMenuOpened() {
		for (GLPopupMenuListener listener : popupMenuListeners)
			listener.popupMenuOpened(this);
	}

	private void firePopupMenuClosed() {
		for (GLPopupMenuListener listener : popupMenuListeners)
			listener.popupMenuClosed(this);
	}

	private static float hostWidth(GLParent host) {
		if (host instanceof GLContainer container)
			return container.getWidth();
		return host instanceof Component component ? component.getWidth() : 0;
	}

	private static float hostHeight(GLParent host) {
		if (host instanceof GLContainer container)
			return container.getHeight();
		return host instanceof Component component ? component.getHeight() : 0;
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static final class MenuSeparator extends GLContainer {
		private final GLPopupMenu menu;

		private MenuSeparator(GLPopupMenu menu) {
			this.menu = menu;
			setIntrinsicPreferredGLSize(new GLSize(1,
					menu.getGLStyle().separatorHeight()));
		}

		@Override
		protected void record(GLContext2D context) {
			GLMenuStyle style = menu.getGLStyle();
			float y = getHeight() * 0.5f;
			context.fillRect(style.itemHorizontalPadding(), y,
					Math.max(0, getWidth() - style.itemHorizontalPadding() * 2),
					1, style.separatorColor());
		}

		@Override
		protected boolean isMouseTargetEnabled() {
			return false;
		}
	}

	private static final class MenuTitle extends GLContainer {
		private final GLPopupMenu menu;

		private MenuTitle(GLPopupMenu menu) {
			this.menu = menu;
		}

		@Override
		protected void record(GLContext2D context) {
			float width = getWidth();
			float height = getHeight();
			if (width <= 0 || height <= 0 || menu.title.isEmpty())
				return;
			GLMenuStyle style = menu.getGLStyle();
			context.fillRect(0, 0, width, height, style.background());
			context.addUIHitArea(0, 0, width, height);
			GLFont font = menu.getGLFont(style.font());
			float baseline = (height - font.getLineHeight()) * 0.5f
					+ font.getAscent();
			String renderedTitle = GLTextFitter.ellipsize(font, menu.title,
					Math.max(0, width - style.itemHorizontalPadding() * 2));
			context.drawUIText(font, renderedTitle,
					style.itemHorizontalPadding(), baseline, style.textColor());
			float separatorY = menu.titlePosition == GLPopupTitlePosition.TOP
					? height - 1 : 0;
			context.fillRect(style.itemHorizontalPadding(), separatorY,
					Math.max(0, width - style.itemHorizontalPadding() * 2),
					1, style.separatorColor());
		}

		@Override
		public void mouseWheelMoved(GLMouseWheelEvent event) {
			menu.scrollBy(event);
			event.consume();
		}

		private float requiredWidth(GLMenuStyle style) {
			return style.font().measurementFont().measureText(menu.title)
					+ style.itemHorizontalPadding() * 2;
		}
	}

	private static final class MenuFadeOverlay extends GLContainer {
		private final GLPopupMenu menu;
		private final boolean top;

		private MenuFadeOverlay(GLPopupMenu menu, boolean top) {
			this.menu = menu;
			this.top = top;
		}

		@Override
		protected void record(GLContext2D context) {
			float width = getWidth();
			float height = getHeight();
			if (width <= 0 || height <= 0)
				return;
			Color background = menu.getGLStyle().background();
			Color transparent = new Color(background.getRed(),
					background.getGreen(), background.getBlue(), 0);
			Color[] colors = top
					? new Color[] { background, background,
							transparent, transparent }
					: new Color[] { transparent, transparent,
							background, background };
			context.addUIHitArea(0, 0, width, height);
			context.fillPolygon(new float[] {
					0, 0, width, 0, width, height, 0, height
			}, colors);
		}

		@Override
		public void mouseWheelMoved(GLMouseWheelEvent event) {
			menu.scrollBy(event);
			event.consume();
		}

		private void menuStyleChanged() {
			invalidateRecording();
		}
	}

	private static final class MenuHorizontalFadeOverlay extends GLContainer {
		private final GLPopupMenu menu;
		private final boolean left;

		private MenuHorizontalFadeOverlay(GLPopupMenu menu, boolean left) {
			this.menu = menu;
			this.left = left;
		}

		@Override
		protected void record(GLContext2D context) {
			float width = getWidth();
			float height = getHeight();
			if (width <= 0 || height <= 0)
				return;
			Color background = menu.getGLStyle().background();
			Color transparent = new Color(background.getRed(),
					background.getGreen(), background.getBlue(), 0);
			Color[] colors = left
					? new Color[] { background, transparent,
							transparent, background }
					: new Color[] { transparent, background,
							background, transparent };
			context.addUIHitArea(0, 0, width, height);
			context.fillPolygon(new float[] {
					0, 0, width, 0, width, height, 0, height
			}, colors);
		}

		@Override
		public void mouseWheelMoved(GLMouseWheelEvent event) {
			menu.scrollBy(event);
			event.consume();
		}

		private void menuStyleChanged() {
			invalidateRecording();
		}
	}

	private static final class MenuScrollBar extends GLControl {
		private final GLPopupMenu menu;
		private final GLRenderParameters trackParameters =
				new GLRenderParameters();
		private final GLRenderParameters thumbParameters =
				new GLRenderParameters();
		private float scrollOffset;
		private float maximumScrollOffset;
		private float viewportHeight;
		private float contentHeight;
		private float dragOffset;

		private MenuScrollBar(GLPopupMenu menu) {
			super(false);
			this.menu = menu;
			updateColors();
		}

		@Override
		protected void record(GLContext2D context) {
			float width = getWidth();
			float height = getHeight();
			if (width <= 0 || height <= 0)
				return;
			context.fillRect(0, 0, width, height, menu.getGLStyle().background());
			context.addUIHitArea(0, 0, width, height);
			float trackX = Math.min(SCROLL_BAR_GAP, width);
			float trackWidth = Math.max(0, width - trackX);
			context.withParameters(trackParameters, () ->
					context.fillUIRoundRect(trackX, 0, trackWidth, height,
							trackWidth * 0.5f, Color.WHITE));
			float thumbHeight = thumbHeight();
			float thumbY = thumbY(thumbHeight);
			context.withParameters(thumbParameters, () ->
					context.fillUIRoundRect(trackX, thumbY, trackWidth, thumbHeight,
							trackWidth * 0.5f, Color.WHITE));
		}

		@Override
		protected void controlStateChanged(GLControlState oldState,
				GLControlState newState) {
			updateColors();
		}

		@Override
		protected void controlMousePressed(GLMouseEvent event) {
			if (event.getButton() != java.awt.event.MouseEvent.BUTTON1
					|| !event.hasLocalPosition())
				return;
			float height = thumbHeight();
			float y = thumbY(height);
			dragOffset = event.getLocalY() >= y
					&& event.getLocalY() <= y + height
							? event.getLocalY() - y : height * 0.5f;
			updateFromLocalY(event.getLocalY());
		}

		@Override
		protected void controlMouseDragged(GLMouseEvent event) {
			if (event.hasLocalPosition())
				updateFromLocalY(event.getLocalY());
		}

		@Override
		public void mouseWheelMoved(GLMouseWheelEvent event) {
			menu.scrollBy(event);
			event.consume();
		}

		private void updateColors() {
			GLMenuStyle style = menu.getGLStyle();
			GLControlState state = getControlState();
			trackParameters.setColor(style.separatorColor()).setOpacity(0.45f);
			thumbParameters.setColor(state == GLControlState.HOVERED
					|| state == GLControlState.PRESSED
							? style.textColor() : style.acceleratorColor());
			repaintGL();
		}

		private void setRange(float scrollOffset, float maximumScrollOffset,
				float viewportHeight, float contentHeight) {
			if (Float.compare(this.scrollOffset, scrollOffset) == 0
					&& Float.compare(this.maximumScrollOffset,
							maximumScrollOffset) == 0
					&& Float.compare(this.viewportHeight, viewportHeight) == 0
					&& Float.compare(this.contentHeight, contentHeight) == 0)
				return;
			this.scrollOffset = scrollOffset;
			this.maximumScrollOffset = maximumScrollOffset;
			this.viewportHeight = viewportHeight;
			this.contentHeight = contentHeight;
			invalidateRecording();
		}

		private float thumbHeight() {
			if (contentHeight <= 0)
				return getHeight();
			return Math.min(getHeight(), Math.max(14,
					getHeight() * viewportHeight / contentHeight));
		}

		private float thumbY(float thumbHeight) {
			return maximumScrollOffset <= 0 ? 0
					: (getHeight() - thumbHeight)
							* scrollOffset / maximumScrollOffset;
		}

		private void updateFromLocalY(float localY) {
			float thumbHeight = thumbHeight();
			float travel = getHeight() - thumbHeight;
			menu.scrollToFraction(travel <= 0 ? 0
					: (localY - dragOffset) / travel);
		}

		private void menuStyleChanged() {
			updateColors();
			invalidateRecording();
		}
	}

	private static final class MenuHorizontalScrollBar extends GLControl {
		private final GLPopupMenu menu;
		private final GLRenderParameters trackParameters = new GLRenderParameters();
		private final GLRenderParameters thumbParameters = new GLRenderParameters();
		private float scrollOffset;
		private float maximumScrollOffset;
		private float viewportWidth;
		private float contentWidth;
		private float dragOffset;

		private MenuHorizontalScrollBar(GLPopupMenu menu) {
			super(false);
			this.menu = menu;
			updateColors();
		}

		@Override
		protected void record(GLContext2D context) {
			float width = getWidth();
			float height = getHeight();
			if (width <= 0 || height <= 0)
				return;
			context.fillRect(0, 0, width, height, menu.getGLStyle().background());
			context.addUIHitArea(0, 0, width, height);
			float trackY = Math.min(SCROLL_BAR_GAP, height);
			float trackHeight = Math.max(0, height - trackY);
			float trackWidth = Math.min(width, Math.max(0, viewportWidth));
			context.withParameters(trackParameters, () ->
					context.fillUIRoundRect(0, trackY, trackWidth, trackHeight,
							trackHeight * 0.5f, Color.WHITE));
			float thumbWidth = thumbWidth();
			float thumbX = thumbX(thumbWidth);
			context.withParameters(thumbParameters, () ->
					context.fillUIRoundRect(thumbX, trackY, thumbWidth, trackHeight,
							trackHeight * 0.5f, Color.WHITE));
		}

		@Override
		protected void controlStateChanged(GLControlState oldState,
				GLControlState newState) {
			updateColors();
		}

		@Override
		protected void controlMousePressed(GLMouseEvent event) {
			if (event.getButton() != java.awt.event.MouseEvent.BUTTON1
					|| !event.hasLocalPosition())
				return;
			float width = thumbWidth();
			float x = thumbX(width);
			dragOffset = event.getLocalX() >= x
					&& event.getLocalX() <= x + width
							? event.getLocalX() - x : width * 0.5f;
			updateFromLocalX(event.getLocalX());
		}

		@Override
		protected void controlMouseDragged(GLMouseEvent event) {
			if (event.hasLocalPosition())
				updateFromLocalX(event.getLocalX());
		}

		@Override
		public void mouseWheelMoved(GLMouseWheelEvent event) {
			menu.scrollBy(event);
			event.consume();
		}

		private void setRange(float scrollOffset, float maximumScrollOffset,
				float viewportWidth, float contentWidth) {
			if (Float.compare(this.scrollOffset, scrollOffset) == 0
					&& Float.compare(this.maximumScrollOffset,
							maximumScrollOffset) == 0
					&& Float.compare(this.viewportWidth, viewportWidth) == 0
					&& Float.compare(this.contentWidth, contentWidth) == 0)
				return;
			this.scrollOffset = scrollOffset;
			this.maximumScrollOffset = maximumScrollOffset;
			this.viewportWidth = viewportWidth;
			this.contentWidth = contentWidth;
			invalidateRecording();
		}

		private void updateFromLocalX(float mouseX) {
			float thumbWidth = thumbWidth();
			float travel = Math.max(0, viewportWidth - thumbWidth);
			float x = Math.max(0, Math.min(travel, mouseX - dragOffset));
			menu.scrollHorizontallyToFraction(travel <= 0 ? 0 : x / travel);
		}

		private float thumbWidth() {
			if (contentWidth <= 0)
				return viewportWidth;
			return Math.min(viewportWidth, Math.max(18,
					viewportWidth * viewportWidth / contentWidth));
		}

		private float thumbX(float thumbWidth) {
			float travel = Math.max(0, viewportWidth - thumbWidth);
			return maximumScrollOffset <= 0 ? 0
					: travel * scrollOffset / maximumScrollOffset;
		}

		private void updateColors() {
			GLMenuStyle style = menu.getGLStyle();
			GLControlState state = getControlState();
			trackParameters.setColor(style.separatorColor()).setOpacity(0.45f);
			thumbParameters.setColor(state == GLControlState.HOVERED
					|| state == GLControlState.PRESSED
							? style.textColor() : style.acceleratorColor());
			repaintGL();
		}

		private void menuStyleChanged() {
			updateColors();
			invalidateRecording();
		}
	}

	private record ComboAnchor(GLContainer owner, GLPopupDirection direction,
			float maximumHeight, boolean matchOwnerWidth,
			float scaleX, float scaleY) {
	}

	private static final class DismissLayer extends GLContainer {
		private final MenuSession session;

		private DismissLayer(MenuSession session) {
			this.session = session;
		}

		@Override
		protected void record(GLContext2D context) {
			float[] bounds = session.anchorHostBounds();
			if (bounds == null) {
				context.addUIHitArea(0, 0, getWidth(), getHeight());
				return;
			}
			float x0 = clamp(bounds[0], 0, getWidth());
			float y0 = clamp(bounds[1], 0, getHeight());
			float x1 = clamp(bounds[0] + bounds[2], 0, getWidth());
			float y1 = clamp(bounds[1] + bounds[3], 0, getHeight());
			if (y0 > 0)
				context.addUIHitArea(0, 0, getWidth(), y0);
			if (y1 < getHeight())
				context.addUIHitArea(0, y1, getWidth(), getHeight() - y1);
			if (x0 > 0 && y1 > y0)
				context.addUIHitArea(0, y0, x0, y1 - y0);
			if (x1 < getWidth() && y1 > y0)
				context.addUIHitArea(x1, y0, getWidth() - x1, y1 - y0);
		}

		@Override
		public void mousePressed(GLMouseEvent event) {
			session.close();
		}
	}

	private static final class MenuSession {
		private final GLParent host;
		private final GLPopupMenu rootMenu;
		private final DismissLayer dismissLayer;
		private final SessionOverlay sessionOverlay;
		private final MenuOverlay menuOverlay;
		private final List<GLPopupMenu> openMenus = new ArrayList<>();
		private final boolean anchoredToMenuBar;
		private final ComboAnchor comboAnchor;
		private final Component componentHost;
		private final ComponentAdapter resizeListener;
		private GLImmediateHoverRegistry.Registration immediateHoverRegistration;
		private boolean closed;

		private MenuSession(GLParent host, GLPopupMenu rootMenu,
				boolean anchoredToMenuBar, ComboAnchor comboAnchor) {
			this.host = host;
			this.rootMenu = rootMenu;
			this.anchoredToMenuBar = anchoredToMenuBar;
			this.comboAnchor = comboAnchor;
			dismissLayer = new DismissLayer(this);
			sessionOverlay = new SessionOverlay();
			menuOverlay = new MenuOverlay(comboAnchor);
			sessionOverlay.addGL(dismissLayer);
			sessionOverlay.addGL(menuOverlay);
			componentHost = host instanceof Component component ? component : null;
			resizeListener = new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent event) {
					resizeOverlay();
				}
			};
		}

		private void openRoot(float x, float y) {
			resizeOverlay();
			host.addGL(sessionOverlay, GLConstraints.overlay());
			if (componentHost != null)
				componentHost.addComponentListener(resizeListener);
			attach(rootMenu);
			if (comboAnchor != null) {
				positionComboRoot();
				updateImmediateHoverRegistration();
				return;
			}
			float minimumY = verticalFloor();
			float inset = edgeInset(rootMenu);
			rootMenu.setLocation(
					clamp(x, inset, Math.max(inset,
							hostWidth(host) - rootMenu.getWidth() - inset)),
					clamp(y, minimumY, Math.max(minimumY,
							hostHeight(host) - rootMenu.getHeight() - inset)));
			updateImmediateHoverRegistration();
		}

		private void updateImmediateHoverRegistration() {
			if (immediateHoverRegistration != null) {
				immediateHoverRegistration.close();
				immediateHoverRegistration = null;
			}
			if (!closed && rootMenu.immediateCpuPickingEnabled)
				immediateHoverRegistration = GLImmediateHoverRegistry.register(
						host, this::resolveImmediateHover);
		}

		private void openSubMenu(GLPopupMenu parent, GLMenuItem item) {
			if (closed)
				return;
			int parentIndex = openMenus.indexOf(parent);
			if (parentIndex < 0)
				return;
			GLPopupMenu subMenu = item.getSubMenu();
			if (parentIndex + 1 < openMenus.size()
					&& openMenus.get(parentIndex + 1) == subMenu)
				return;
			closeFrom(parentIndex + 1);
			attach(subMenu);

			float inset = edgeInset(subMenu);
			float minimumX = logicalLeft(inset);
			float maximumX = logicalRight(inset);
			float minimumY = logicalTop(inset);
			float maximumY = logicalBottom(inset);
			float x = parent.getX() + parent.getWidth() - 2;
			float y = parent.getY() + item.getY() - parent.getGLStyle().padding().top();
			if (x + subMenu.getWidth() > maximumX)
				x = parent.getX() - subMenu.getWidth() + 2;
			subMenu.setLocation(
					clamp(x, minimumX, Math.max(minimumX,
							maximumX - subMenu.getWidth())),
					clamp(y, minimumY, Math.max(minimumY,
							maximumY - subMenu.getHeight())));
		}

		private void closeAfter(GLPopupMenu menu) {
			int index = openMenus.indexOf(menu);
			if (index >= 0)
				closeFrom(index + 1);
		}

		private void attach(GLPopupMenu menu) {
			menu.session = this;
			AttachedEdge edge = AttachedEdge.NONE;
			if (menu == rootMenu) {
				if (anchoredToMenuBar)
					edge = AttachedEdge.TOP;
				else if (comboAnchor != null)
					edge = comboAnchor.direction() == GLPopupDirection.BELOW
							? AttachedEdge.TOP : AttachedEdge.BOTTOM;
			}
			menu.setAttachedEdge(edge);
			menu.activeRootWidth = menu == rootMenu && comboAnchor != null
					&& comboAnchor.matchOwnerWidth()
						? comboAnchor.owner().getWidth()
						: menu == rootMenu ? rootMenu.rootWidth : Float.NaN;
			menu.activeMaximumWidth = maximumWidthFor(menu);
			menuOverlay.addGL(menu);
			menu.scrollOffset = 0;
			menu.horizontalScrollOffset = 0;
			menu.menuLayoutDirty = true;
			menu.prepareLayout(maximumHeightFor(menu));
			menu.bringToFront();
			openMenus.add(menu);
		}

		private void closeFrom(int index) {
			for (int current = openMenus.size() - 1; current >= index; current--) {
				GLPopupMenu menu = openMenus.remove(current);
				menuOverlay.removeGL(menu);
				menu.session = null;
			}
		}

		private void resizeOverlay() {
			sessionOverlay.setBounds(0, 0, hostWidth(host), hostHeight(host));
			menuOverlay.setBounds(0, 0, hostWidth(host), hostHeight(host));
			positionDismissLayer();
			float minimumY = verticalFloor();
			for (GLPopupMenu menu : openMenus) {
				float inset = edgeInset(menu);
				float maximumWidth = maximumWidthFor(menu);
				if (Float.compare(menu.activeMaximumWidth, maximumWidth) != 0) {
					menu.activeMaximumWidth = maximumWidth;
					menu.menuLayoutDirty = true;
				}
				menu.prepareLayout(maximumHeightFor(menu));
				if (menu == rootMenu && comboAnchor != null) {
					positionComboRoot();
					continue;
				}
				if (comboAnchor != null) {
					float minimumX = logicalLeft(inset);
					float maximumX = logicalRight(inset);
					float logicalTop = logicalTop(inset);
					float logicalBottom = logicalBottom(inset);
					menu.setLocation(
							clamp(menu.getX(), minimumX, Math.max(minimumX,
									maximumX - menu.getWidth())),
							clamp(menu.getY(), logicalTop, Math.max(logicalTop,
									logicalBottom - menu.getHeight())));
					continue;
				}
				menu.setLocation(
						clamp(menu.getX(), inset, Math.max(inset,
								hostWidth(host) - menu.getWidth() - inset)),
						clamp(menu.getY(), minimumY, Math.max(minimumY,
								hostHeight(host) - menu.getHeight() - inset)));
			}
		}

		private float maximumHeightFor(GLPopupMenu menu) {
			if (menu == rootMenu && comboAnchor != null)
				return comboAnchor.maximumHeight();
			if (comboAnchor != null)
				return Math.max(1, (hostHeight(host) - edgeInset(menu) * 2)
						/ comboAnchor.scaleY());
			return Math.max(1, hostHeight(host) - verticalFloor()
					- edgeInset(menu));
		}

		private float maximumWidthFor(GLPopupMenu menu) {
			float availableScreenWidth = Math.max(1,
					hostWidth(host) - edgeInset(menu) * 2);
			return comboAnchor == null ? availableScreenWidth
					: availableScreenWidth / comboAnchor.scaleX();
		}

		private void positionComboRoot() {
			float[] bounds = comboAnchor.owner().getLastRenderedScreenBounds();
			if (bounds == null)
				return;
			float rootX = rootCoordinate(comboAnchor.owner(), true);
			float rootY = rootCoordinate(comboAnchor.owner(), false);
			float popupScreenWidth = rootMenu.getWidth() * comboAnchor.scaleX();
			float inset = edgeInset(rootMenu);
			float desiredScreenX = clamp(bounds[0], inset,
					Math.max(inset, hostWidth(host) - popupScreenWidth - inset));
			float x = rootX + (desiredScreenX - bounds[0]) / comboAnchor.scaleX();
			float y = comboAnchor.direction() == GLPopupDirection.BELOW
					? rootY + comboAnchor.owner().getHeight()
					: rootY - rootMenu.getHeight();
			rootMenu.setLocation(x, y);
		}

		private float[] anchorHostBounds() {
			if (comboAnchor != null)
				return comboAnchor.owner().getLastRenderedScreenBounds();
			GLMenuBar menuBar = rootMenu.menuBarOwner;
			return menuBar == null ? null : menuBar.getLastRenderedScreenBounds();
		}

		private void positionDismissLayer() {
			dismissLayer.setBounds(0, 0, hostWidth(host), hostHeight(host));
		}

		private float logicalLeft(float inset) {
			if (comboAnchor == null)
				return inset;
			float[] bounds = comboAnchor.owner().getLastRenderedScreenBounds();
			return bounds == null ? inset
					: rootCoordinate(comboAnchor.owner(), true)
							+ (inset - bounds[0]) / comboAnchor.scaleX();
		}

		private float logicalRight(float inset) {
			if (comboAnchor == null)
				return hostWidth(host) - inset;
			float[] bounds = comboAnchor.owner().getLastRenderedScreenBounds();
			return bounds == null ? hostWidth(host) - inset
					: rootCoordinate(comboAnchor.owner(), true)
							+ (hostWidth(host) - inset - bounds[0])
									/ comboAnchor.scaleX();
		}

		private float logicalTop(float inset) {
			if (comboAnchor == null)
				return Math.max(verticalFloor(), inset);
			float[] bounds = comboAnchor.owner().getLastRenderedScreenBounds();
			return bounds == null ? inset
					: rootCoordinate(comboAnchor.owner(), false)
							+ (inset - bounds[1]) / comboAnchor.scaleY();
		}

		private float logicalBottom(float inset) {
			if (comboAnchor == null)
				return hostHeight(host) - inset;
			float[] bounds = comboAnchor.owner().getLastRenderedScreenBounds();
			return bounds == null ? hostHeight(host) - inset
					: rootCoordinate(comboAnchor.owner(), false)
							+ (hostHeight(host) - inset - bounds[1])
									/ comboAnchor.scaleY();
		}

		private static float rootCoordinate(GLContainer child, boolean horizontal) {
			float result = horizontal ? child.getX() : child.getY();
			for (GLParent parent = child.getGLParent();
					parent instanceof GLContainer container;
					parent = container.getGLParent())
				result += horizontal ? container.getX() : container.getY();
			return result;
		}

		private float verticalFloor() {
			if (!anchoredToMenuBar || rootMenu.menuBarOwner == null)
				return 0;
			float[] bounds = rootMenu.menuBarOwner.getLastRenderedScreenBounds();
			return bounds == null ? 0
					: clamp(bounds[1] + bounds[3], 0, hostHeight(host));
		}

		private static float edgeInset(GLPopupMenu menu) {
			return Math.max(1, menu.getGLStyle().borderWidth());
		}

		private GLPickResult resolveImmediateHover(float screenX, float screenY) {
			if (closed)
				return null;
			GLMenuBar menuBar = rootMenu.menuBarOwner;
			if (menuBar != null) {
				GLPickResult menuBarHit = menuBar.resolveImmediateHover(
						screenX, screenY);
				if (menuBarHit != null)
					return menuBarHit;
			}
			if (comboAnchor != null) {
				GLPickResult comboHit = hit(comboAnchor.owner(), screenX, screenY);
				if (comboHit != null)
					return comboHit;
			}
			for (int menuIndex = openMenus.size() - 1;
					menuIndex >= 0; menuIndex--) {
				GLPopupMenu menu = openMenus.get(menuIndex);
				GLPickResult menuHit = hit(menu, screenX, screenY);
				if (menu.containsChildViewport(menuHit)) {
					List<GLContainer> rows = menu.getGLChildrenInPaintOrder();
					for (int rowIndex = rows.size() - 1;
							rowIndex >= 0; rowIndex--) {
						GLContainer row = rows.get(rowIndex);
						if (row.isVisible() && (row instanceof GLMenuItem
								|| row instanceof MenuScrollBar
								|| row instanceof MenuHorizontalScrollBar
								|| row instanceof MenuFadeOverlay
								|| row instanceof MenuHorizontalFadeOverlay
								|| row instanceof MenuTitle)) {
							GLPickResult rowHit = hit(row, screenX, screenY);
							if (rowHit != null)
								return rowHit;
						}
					}
				}
				if (menuHit != null)
					return menuHit;
			}
			// The dismiss layer owns every remaining pixel while the menu is open.
			return hit(dismissLayer, screenX, screenY);
		}

		private static GLPickResult hit(GLContainer target,
				float screenX, float screenY) {
			float[] bounds = target.getLastRenderedScreenBounds();
			if (bounds == null || bounds[2] <= 0 || bounds[3] <= 0
					|| screenX < bounds[0] || screenY < bounds[1]
					|| screenX > bounds[0] + bounds[2]
					|| screenY > bounds[1] + bounds[3])
				return null;
			float scaleX = target.getWidth() / bounds[2];
			float scaleY = target.getHeight() / bounds[3];
			float inverseX = -bounds[0] * scaleX;
			float inverseY = -bounds[1] * scaleY;
			float localX = screenX * scaleX + inverseX;
			float localY = screenY * scaleY + inverseY;
			return new GLPickResult(0, target, screenX, screenY, localX, localY,
					scaleX, 0, 0, scaleY, inverseX, inverseY);
		}

		private void close() {
			if (closed)
				return;
			closed = true;
			if (immediateHoverRegistration != null) {
				immediateHoverRegistration.close();
				immediateHoverRegistration = null;
			}
			closeFrom(0);
			host.removeGL(sessionOverlay);
			if (componentHost != null)
				componentHost.removeComponentListener(resizeListener);
			synchronized (ACTIVE) {
				WeakReference<MenuSession> reference = ACTIVE.get(host);
				if (reference != null && reference.get() == this)
					ACTIVE.remove(host);
			}
			rootMenu.session = null;
			rootMenu.firePopupMenuClosed();
		}
	}

	/** Cancels transforms left behind by the host before the popup tree begins. */
	private static final class SessionOverlay extends GLContainer {
		@Override
		protected void record(GLContext2D context) {
		}

		@Override
		protected void transformComponent(GLContext2D context) {
			context.resetTransform();
		}

		@Override
		protected boolean isMouseTargetEnabled() {
			return false;
		}
	}

	/** Reapplies only the owner mapping needed by the first ComboBox popup. */
	private static final class MenuOverlay extends GLContainer {
		private final ComboAnchor comboAnchor;

		private MenuOverlay(ComboAnchor comboAnchor) {
			this.comboAnchor = comboAnchor;
		}

		@Override
		protected void record(GLContext2D context) {
		}

		@Override
		protected void transformComponent(GLContext2D context) {
			if (comboAnchor == null)
				return;
			float[] bounds = comboAnchor.owner().getLastRenderedScreenBounds();
			if (bounds == null)
				return;
			float rootX = MenuSession.rootCoordinate(comboAnchor.owner(), true);
			float rootY = MenuSession.rootCoordinate(comboAnchor.owner(), false);
			context.translate(bounds[0], bounds[1]);
			context.scale(comboAnchor.scaleX(), comboAnchor.scaleY());
			context.translate(-rootX, -rootY);
		}

		@Override
		protected boolean isMouseTargetEnabled() {
			return false;
		}
	}
}
