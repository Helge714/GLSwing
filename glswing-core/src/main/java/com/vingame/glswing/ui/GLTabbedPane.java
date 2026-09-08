package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLMouseWheelEvent;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLCornerRadii;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLStroke;

/** GPU-rendered tab container with one visible content component at a time. */
public final class GLTabbedPane extends GLContainer {
	private final List<Tab> tabs = new ArrayList<>();
	private final CopyOnWriteArrayList<GLTabSelectionListener> listeners =
			new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<GLTabCloseListener> closeListeners =
			new CopyOnWriteArrayList<>();
	private final Host contentHost = new Host();
	private final Host tabHost = new Host();
	private GLContainer contentHeader;
	private GLContainer contentFooter;
	private final GLScrollBar tabScrollBar =
			new GLScrollBar(GLScrollBar.Orientation.HORIZONTAL);
	private GLTabbedPaneStyle localStyle;
	private int selectedIndex = -1;
	private boolean layingOut;
	private float tabStripWidth;
	private float tabsWidth;
	private boolean tabsClosable;
	private boolean closedTabHistoryEnabled;
	private final java.util.ArrayDeque<ClosedTab> closedTabs = new java.util.ArrayDeque<>();
	private final java.util.IdentityHashMap<GLContainer, CompletionStage<Boolean>> closeRequests =
			new java.util.IdentityHashMap<>();
	private GLTabCloseRequestHandler closeRequestHandler;

	public GLTabbedPane() {
		addGL(contentHost);
		addGL(tabHost);
		addGL(tabScrollBar);
		tabScrollBar.addScrollListener((source, oldValue, newValue) -> layoutTabs());
		setIntrinsicPreferredGLSize(new GLSize(360, 240));
	}

	public int addTab(String title, GLContainer content) {
		if (title == null || content == null)
			throw new IllegalArgumentException("Tab title and content must not be null");
		Tab tab = new Tab(title, content);
		tabs.add(tab);
		contentHost.addGL(content);
		bringContentChromeToFront();
		tabHost.addGL(tab.button);
		if (selectedIndex < 0)
			selectedIndex = 0;
		updateVisibility();
		updatePreferredSize();
		layoutTabs();
		return tabs.size() - 1;
	}

	public void removeTabAt(int index) {
		checkIndex(index);
		Tab removed = tabs.remove(index);
		if (closedTabHistoryEnabled) {
			closedTabs.addFirst(new ClosedTab(removed.title, removed.content, index));
			while (closedTabs.size() > 10) closedTabs.removeLast();
		}
		removed.content.setConnectedShape(this, null);
		contentHost.removeGL(removed.content);
		tabHost.removeGL(removed.button);
		int old = selectedIndex;
		if (tabs.isEmpty())
			selectedIndex = -1;
		else if (index < selectedIndex)
			selectedIndex--;
		else if (index == selectedIndex)
			selectedIndex = Math.min(index, tabs.size() - 1);
		updateVisibility();
		updatePreferredSize();
		layoutTabs();
		if (old != selectedIndex || index == old)
			fireSelectionChanged(old);
		for (GLTabCloseListener listener : closeListeners)
			listener.tabClosed(this, index, removed.title, removed.content);
	}

	/**
	 * Requests a user-visible tab close and removes the tab only after approval.
	 * Repeated requests for the same tab share the pending result. Programmatic
	 * callers that intentionally bypass confirmation can use {@link #removeTabAt(int)}.
	 */
	public CompletionStage<Boolean> requestCloseTabAt(int index) {
		checkIndex(index);
		Tab tab = tabs.get(index);
		CompletionStage<Boolean> pending = closeRequests.get(tab.content);
		if (pending != null) return pending;
		CompletionStage<Boolean> decision;
		try {
			decision = closeRequestHandler == null
					? CompletableFuture.completedFuture(true)
					: closeRequestHandler.requestClose(this, index, tab.title, tab.content);
			if (decision == null) decision = CompletableFuture.completedFuture(false);
		} catch (RuntimeException failure) {
			decision = CompletableFuture.failedFuture(failure);
		}
		CompletableFuture<Boolean> result = new CompletableFuture<>();
		closeRequests.put(tab.content, result);
		decision.whenComplete((approved, failure) -> java.awt.EventQueue.invokeLater(() -> {
			closeRequests.remove(tab.content);
			if (failure != null || !Boolean.TRUE.equals(approved)) {
				if (failure != null) result.completeExceptionally(failure);
				else result.complete(false);
				return;
			}
			int current = tabs.indexOf(tab);
			if (current >= 0) removeTabAt(current);
			result.complete(current >= 0);
		}));
		return result;
	}

	public GLTabCloseRequestHandler getCloseRequestHandler() {
		return closeRequestHandler;
	}

	/** Sets the single close policy used by close buttons, middle-clicks and menus. */
	public void setCloseRequestHandler(GLTabCloseRequestHandler handler) {
		closeRequestHandler = handler;
	}

	public void removeAllTabs() {
		int old = selectedIndex;
		for (Tab tab : List.copyOf(tabs)) {
			tab.content.setConnectedShape(this, null);
			contentHost.removeGL(tab.content);
			tabHost.removeGL(tab.button);
		}
		tabs.clear();
		selectedIndex = -1;
		updatePreferredSize();
		layoutTabs();
		if (old != -1)
			fireSelectionChanged(old);
	}

	public int getTabCount() { return tabs.size(); }
	/** Returns whether tabs expose close actions. Disabled by default. */
	public boolean areTabsClosable() { return tabsClosable; }
	/** Enables or disables close actions for all tabs. */
	public void setTabsClosable(boolean value) { tabsClosable = value; }
	/** Enables a bounded history that can restore closed tabs from their context menu. */
	public void setClosedTabHistoryEnabled(boolean value) {
		closedTabHistoryEnabled = value;
		if (!value) closedTabs.clear();
	}
	public boolean isClosedTabHistoryEnabled() { return closedTabHistoryEnabled; }
	/** Reopens the most recently closed tab, if one is available. */
	public boolean reopenLastClosedTab() {
		ClosedTab closed = closedTabs.pollFirst();
		return closed != null && restore(closed);
	}
	/** Reopens a retained tab containing the supplied component. */
	public boolean reopenTab(GLContainer content) {
		for (ClosedTab closed : List.copyOf(closedTabs))
			if (closed.content == content) {
				closedTabs.remove(closed);
				return restore(closed);
			}
		return false;
	}
	/** Removes a retained closed-tab entry without affecting an open tab. */
	public boolean forgetClosedTab(GLContainer content) {
		if (content == null) return false;
		for (ClosedTab closed : List.copyOf(closedTabs))
			if (closed.content == content) return closedTabs.remove(closed);
		return false;
	}
	private boolean restore(ClosedTab closed) {
		int index = Math.min(closed.index, tabs.size());
		Tab tab = new Tab(closed.title, closed.content);
		tabs.add(index, tab);
		contentHost.addGL(tab.content);
		bringContentChromeToFront();
		tabHost.addGL(tab.button);
		int old = selectedIndex;
		selectedIndex = index;
		updateVisibility();
		updatePreferredSize();
		layoutTabs();
		ensureSelectedTabVisible();
		fireSelectionChanged(old);
		return true;
	}
	public int getSelectedIndex() { return selectedIndex; }
	public GLContainer getSelectedComponent() {
		return selectedIndex < 0 ? null : tabs.get(selectedIndex).content;
	}
	/** Returns the optional component displayed above the selected tab content. */
	public GLContainer getContentHeader() { return contentHeader; }
	/**
	 * Installs a shared component between the tab strip and selected content.
	 * Its preferred height is reserved without changing tab identity.
	 */
	public void setContentHeader(GLContainer header) {
		if (contentHeader != header) {
			if (contentHeader != null) contentHost.removeGL(contentHeader);
			contentHeader = header;
			if (header != null) contentHost.addGL(header);
		}
		updatePreferredSize();
		layoutTabs();
	}
	/** Returns the optional component displayed below the selected tab content. */
	public GLContainer getContentFooter() { return contentFooter; }
	/**
	 * Installs a shared component below the selected tab content. Its preferred
	 * height is reserved without changing tab identity.
	 */
	public void setContentFooter(GLContainer footer) {
		if (contentFooter != footer) {
			if (contentFooter != null) contentHost.removeGL(contentFooter);
			contentFooter = footer;
			if (footer != null) contentHost.addGL(footer);
		}
		updatePreferredSize();
		layoutTabs();
	}
	private void bringContentChromeToFront() {
		if (contentHeader != null) {
			contentHost.removeGL(contentHeader);
			contentHost.addGL(contentHeader);
		}
		if (contentFooter != null) {
			contentHost.removeGL(contentFooter);
			contentHost.addGL(contentFooter);
		}
	}
	public GLContainer getComponentAt(int index) { return tab(index).content; }
	/** Replaces a tab's content without changing its title, position, or selection. */
	public void setComponentAt(int index, GLContainer content) {
		if (content == null) throw new IllegalArgumentException("Tab content required");
		Tab tab = tab(index);
		if (tab.content == content) return;
		tab.content.setConnectedShape(this, null);
		contentHost.removeGL(tab.content);
		tab.content = content;
		contentHost.addGL(content);
		bringContentChromeToFront();
		updateVisibility();
		updatePreferredSize();
		layoutTabs();
	}
	/** Returns the index of a tab content component, or {@code -1}. */
	public int indexOfComponent(GLContainer content) {
		for (int index = 0; index < tabs.size(); index++)
			if (tabs.get(index).content == content) return index;
		return -1;
	}
	public String getTitleAt(int index) { return tab(index).title; }

	public void setTitleAt(int index, String title) {
		if (title == null)
			throw new IllegalArgumentException("Tab title must not be null");
		Tab tab = tab(index);
		if (tab.title.equals(title))
			return;
		tab.title = title;
		tab.button.invalidateRecording();
		updatePreferredSize();
		layoutTabs();
	}

	public boolean isEnabledAt(int index) { return tab(index).button.isEnabled(); }
	public void setEnabledAt(int index, boolean enabled) {
		Tab tab = tab(index);
		tab.button.setEnabled(enabled);
		if (!enabled && selectedIndex == index) {
			int replacement = nextEnabled(index);
			if (replacement >= 0)
				setSelectedIndex(replacement);
		}
	}

	public void setSelectedIndex(int index) {
		checkIndex(index);
		if (!tabs.get(index).button.isEnabled())
			throw new IllegalArgumentException("A disabled tab cannot be selected");
		if (selectedIndex == index)
			return;
		int old = selectedIndex;
		selectedIndex = index;
		updateVisibility();
		ensureSelectedTabVisible();
		fireSelectionChanged(old);
	}

	public void addSelectionListener(GLTabSelectionListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("Selection listener must not be null");
		listeners.addIfAbsent(listener);
	}

	public void removeSelectionListener(GLTabSelectionListener listener) {
		listeners.remove(listener);
	}
	public void addCloseListener(GLTabCloseListener listener) {
		if (listener == null) throw new IllegalArgumentException("Listener must not be null");
		closeListeners.addIfAbsent(listener);
	}
	public void removeCloseListener(GLTabCloseListener listener) { closeListeners.remove(listener); }

	public GLTabbedPaneStyle getGLStyle() {
		return localStyle != null ? localStyle
				: GLTabbedPaneStyle.fromTheme(getGLTheme());
	}

	public void setGLStyle(GLTabbedPaneStyle style) {
		if (style == null)
			throw new IllegalArgumentException("Tabbed-pane style must not be null");
		localStyle = style;
		styleChanged();
	}

	public void clearGLStyle() {
		if (localStyle == null)
			return;
		localStyle = null;
		styleChanged();
	}

	@Override
	public void mouseWheelMoved(GLMouseWheelEvent event) {
		if (!tabScrollBar.isVisible() || tabScrollBar.getMaximumValue() <= 0)
			return;
		float rotation = (float) event.getPreciseWheelRotation();
		if (rotation == 0)
			return;
		float old = tabScrollBar.getValue();
		tabScrollBar.setValue(old + rotation
				* Math.max(1, event.getScrollAmount()) * 24);
		if (Float.compare(old, tabScrollBar.getValue()) != 0)
			event.consume();
	}

	@Override
	protected void record(GLContext2D context) {
		float width = getWidth();
		float height = getHeight();
		if (width <= 0 || height <= 0)
			return;
		GLTabbedPaneStyle style = getGLStyle();
		float border = layoutBorderWidth(style.borderWidth());
		float radius = layoutCornerRadius(style.cornerRadius());
		if (border > 0)
			context.fillAndDrawUIRoundRect(0, 0, width, height, radius, border,
					style.background(), style.border(), GLStroke.WidthSpace.SCREEN);
		else context.fillUIRoundRect(0, 0, width, height, radius, style.background());
		context.fillUIRoundRect(border, border,
				Math.max(0, width - border * 2), style.tabHeight(),
				new GLCornerRadii(layoutCornerRadius(style.cornerRadius()), layoutCornerRadius(style.cornerRadius()), 0, 0),
				style.tabBarBackground());
	}

	@Override
	protected void render(GLContext2D context) {
		layoutTabs();
		super.render(context);
	}

	@Override
	protected void renderChildren(GLContext2D context) {
		if (getWidth() <= 0 || getHeight() <= 0)
			return;
		context.beginClip(0, 0, getWidth(), getHeight());
		try {
			super.renderChildren(context);
		} finally {
			context.endClip();
		}
	}

	@Override
	protected void resized(float width, float height) { layoutTabs(); }

	@Override
	protected void themeChanged() {
		if (localStyle == null)
			styleChanged();
	}

	private void styleChanged() {
		invalidateRecording();
		for (Tab tab : tabs)
			tab.button.invalidateRecording();
		updatePreferredSize();
		layoutTabs();
	}

	private void layoutTabs() {
		if (layingOut)
			return;
		layingOut = true;
		try {
			GLTabbedPaneStyle style = getGLStyle();
			float border = layoutBorderWidth(style.borderWidth());
			tabStripWidth = Math.max(0, getWidth() - border * 2);
			float[] widths = tabWidths(style, tabStripWidth);
			tabsWidth = totalWidth(widths, style.tabGap());
			boolean scrollable = tabsWidth > tabStripWidth + 0.01f;
			float scrollBarHeight = scrollable
					? tabScrollBar.getGLStyle().thickness() : 0;
			tabScrollBar.setVisible(scrollable);
			if (scrollable) {
				tabScrollBar.setBounds(border, border + style.tabHeight(),
						tabStripWidth, scrollBarHeight);
				tabScrollBar.setValues(tabScrollBar.getValue(), tabStripWidth,
						tabsWidth);
			} else {
				tabScrollBar.setValue(0);
			}
			float x = border - tabScrollBar.getValue();
			for (int index = 0; index < tabs.size(); index++) {
				TabButton button = tabs.get(index).button;
				button.setBounds(x, border, widths[index], style.tabHeight());
				x += widths[index] + style.tabGap();
			}
			tabHost.setBounds(0, 0, getWidth(),
					border + style.tabHeight() + scrollBarHeight);
			float contentY = border + style.tabHeight() + scrollBarHeight;
			float contentHeight = Math.max(0, getHeight() - contentY - border);
			contentHost.setBounds(border, contentY,
					Math.max(0, getWidth() - border * 2), contentHeight);
			GLInsets padding = style.contentPadding();
			float headerHeight = contentHeader != null && contentHeader.isVisible()
					? contentHeader.getPreferredGLSize().height() : 0;
			float footerHeight = contentFooter != null && contentFooter.isVisible()
					? contentFooter.getPreferredGLSize().height() : 0;
			if (contentHeader != null)
				contentHeader.setBounds(padding.left(), padding.top(),
						Math.max(0, contentHost.getWidth() - padding.horizontal()),
						headerHeight);
			if (contentFooter != null)
				contentFooter.setBounds(padding.left(),
						Math.max(padding.top() + headerHeight,
								contentHost.getHeight() - padding.bottom() - footerHeight),
						Math.max(0, contentHost.getWidth() - padding.horizontal()),
						footerHeight);
			for (Tab tab : tabs)
				tab.content.setBounds(padding.left(), padding.top() + headerHeight,
						Math.max(0, contentHost.getWidth() - padding.horizontal()),
						Math.max(0, contentHost.getHeight() - padding.vertical()
								- headerHeight - footerHeight));
			float contentRadius = Math.max(0, layoutCornerRadius(style.cornerRadius()) - layoutBorderWidth(style.borderWidth()));
			GLCornerRadii contentRadii = new GLCornerRadii(0, 0,
					contentRadius, contentRadius);
			for (Tab tab : tabs) {
				if (tab.content instanceof GLTextArea editor)
					editor.setContainerShape(contentRadii, false);
				else if (tab.content instanceof GLTextEditor editor)
					editor.setContainerShape(contentRadii, false);
				else
					tab.content.setConnectedShape(this, contentRadii);
			}
		} finally {
			layingOut = false;
		}
	}

	private float[] tabWidths(GLTabbedPaneStyle style, float available) {
		float[] widths = new float[tabs.size()];
		float sum = 0;
		for (int index = 0; index < tabs.size(); index++) {
			float measured = style.font().measurementFont()
					.measureText(tabs.get(index).title)
					+ style.tabHorizontalPadding() * 2;
			widths[index] = clamp(measured, style.minimumTabWidth(),
					style.maximumTabWidth());
			sum += widths[index];
		}
		float gaps = Math.max(0, tabs.size() - 1) * style.tabGap();
		float excess = sum + gaps - available;
		for (int pass = 0; pass < tabs.size() && excess > 0.01f; pass++) {
			int flexible = 0;
			for (float width : widths)
				if (width > style.minimumTabWidth() + 0.01f)
					flexible++;
			if (flexible == 0)
				break;
			float share = excess / flexible;
			excess = 0;
			for (int index = 0; index < widths.length; index++) {
				float old = widths[index];
				if (old > style.minimumTabWidth() + 0.01f)
					widths[index] = Math.max(style.minimumTabWidth(), old - share);
			}
			excess = totalWidth(widths, style.tabGap()) - available;
		}
		return widths;
	}

	private void updateVisibility() {
		for (int index = 0; index < tabs.size(); index++) {
			Tab tab = tabs.get(index);
			tab.content.setVisible(index == selectedIndex);
			tab.button.invalidateRecording();
		}
		repaintAndRebuildPicking(this);
	}

	private void ensureSelectedTabVisible() {
		layoutTabs();
		if (selectedIndex < 0 || !tabScrollBar.isVisible())
			return;
		TabButton selected = tabs.get(selectedIndex).button;
		float start = selected.getX() + tabScrollBar.getValue();
		float end = start + selected.getWidth();
		float offset = tabScrollBar.getValue();
		if (start < offset)
			tabScrollBar.setValue(start);
		else if (end > offset + tabStripWidth)
			tabScrollBar.setValue(end - tabStripWidth);
	}

	private void updatePreferredSize() {
		GLTabbedPaneStyle style = getGLStyle();
		float width = contentHeader == null || !contentHeader.isVisible() ? 0
				: contentHeader.getPreferredGLSize().width();
		if (contentFooter != null && contentFooter.isVisible())
			width = Math.max(width, contentFooter.getPreferredGLSize().width());
		float height = 0;
		for (Tab tab : tabs) {
			GLSize preferred = tab.content.getPreferredGLSize();
			width = Math.max(width, preferred.width());
			height = Math.max(height, preferred.height());
		}
		setIntrinsicPreferredGLSize(new GLSize(
				Math.max(240, width + style.contentPadding().horizontal()
						+ layoutBorderWidth(style.borderWidth()) * 2),
				Math.max(160, height + style.contentPadding().vertical()
						+ (contentHeader != null && contentHeader.isVisible()
								? contentHeader.getPreferredGLSize().height() : 0)
						+ (contentFooter != null && contentFooter.isVisible()
								? contentFooter.getPreferredGLSize().height() : 0)
						+ style.tabHeight() + layoutBorderWidth(style.borderWidth()) * 2)));
	}

	private int nextEnabled(int disabledIndex) {
		for (int distance = 1; distance < tabs.size(); distance++) {
			int right = (disabledIndex + distance) % tabs.size();
			if (tabs.get(right).button.isEnabled())
				return right;
		}
		return -1;
	}

	private void fireSelectionChanged(int oldIndex) {
		for (GLTabSelectionListener listener : listeners)
			listener.selectionChanged(this, oldIndex, selectedIndex);
	}

	private Tab tab(int index) { checkIndex(index); return tabs.get(index); }
	private void checkIndex(int index) {
		if (index < 0 || index >= tabs.size())
			throw new IndexOutOfBoundsException("Tab index: " + index);
	}
	private static float totalWidth(float[] widths, float gap) {
		float total = Math.max(0, widths.length - 1) * gap;
		for (float width : widths) total += width;
		return total;
	}
	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private final class Tab {
		private String title;
		private GLContainer content;
		private final TabButton button;
		private Tab(String title, GLContainer content) {
			this.title = title;
			this.content = content;
			button = new TabButton(this);
		}
	}
	private record ClosedTab(String title, GLContainer content, int index) { }

	private final class TabButton extends GLControl {
		private final Tab tab;
		private final GLPopupMenu closeMenu = new GLPopupMenu();
		private final GLMenuItem closeItem;
		private final GLMenuItem closeOthersItem;
		private final GLMenuItem reopenClosedItem;
		private boolean closeHovered;
		private TabButton(Tab tab) {
			super(false);
			this.tab = tab;
			closeItem = closeMenu.addItem("", event -> closeTab());
			closeOthersItem = closeMenu.addItem("", event -> closeOtherTabs());
			closeMenu.addSeparator();
			reopenClosedItem = closeMenu.addItem("", event -> reopenLastClosedTab());
		}

		@Override
		protected void record(GLContext2D context) {
			float width = getWidth();
			float height = getHeight();
			if (width <= 0 || height <= 0)
				return;
			GLTabbedPaneStyle style = getGLStyle();
			boolean selected = selectedIndex >= 0
					&& tabs.get(selectedIndex) == tab;
			Color background = !isEnabled() ? style.tabDisabled()
					: selected ? style.tabSelected()
							: switch (getControlState()) {
							case HOVERED -> style.tabHover();
							case PRESSED -> style.tabPressed();
							case DISABLED -> style.tabDisabled();
							case NORMAL -> style.tab();
							};
			// Every tab shares one connected tab-strip silhouette. Keeping the
			// lower corners square also prevents inactive and disabled tabs from
			// looking like unrelated standalone buttons.
			GLCornerRadii radii = new GLCornerRadii(layoutCornerRadius(style.cornerRadius()),
					layoutCornerRadius(style.cornerRadius()), 0, 0);
			context.fillUIRoundRect(0, 0, width, height, radii, background);
			context.addUIHitArea(0, 0, width, height);
			GLFont font = getGLFont(style.font());
			String title = GLTextFitter.ellipsize(font, tab.title,
					Math.max(0, width - style.tabHorizontalPadding() * 2
							- (tabsClosable ? 20 : 0)));
			float contentWidth = width - (tabsClosable ? 20 : 0);
			float textX = (contentWidth - font.measureText(title)) * 0.5f;
			float baseline = (height - font.getLineHeight()) * 0.5f
					+ font.getAscent();
			context.drawUITextShadow(font, title, textX, baseline,
					getGLTheme().textShadow("tabbedPane"));
			context.drawUIText(font, title, textX, baseline,
					isEnabled() ? style.text() : style.textDisabled());
			if (tabsClosable) {
				float centerX = width - 12;
				float centerY = height * 0.5f;
				if (closeHovered)
					context.fillUIRoundRect(centerX - 8, centerY - 8, 16, 16, 4,
							new Color(255, 255, 255, 34));
				Color closeColor = isEnabled() ? style.text() : style.textDisabled();
				context.drawUILine(centerX - 3.5f, centerY - 3.5f,
						centerX + 3.5f, centerY + 3.5f, closeColor);
				context.drawUILine(centerX + 3.5f, centerY - 3.5f,
						centerX - 3.5f, centerY + 3.5f, closeColor);
			}
		}

		@Override
		protected void controlStateChanged(GLControlState oldState,
				GLControlState newState) { invalidateRecording(); }

		@Override
		protected void controlMouseClicked(GLMouseEvent event) {
			if (!isEnabled()) return;
			if (tabsClosable && event.getButton() == MouseEvent.BUTTON2) {
				closeTab();
				event.consume();
			} else if (event.getButton() == MouseEvent.BUTTON1) {
				if (tabsClosable && event.hasLocalPosition()
						&& event.getLocalX() >= getWidth() - 24) closeTab();
				else setSelectedIndex(tabs.indexOf(tab));
			}
		}

		@Override protected void controlMousePressed(GLMouseEvent event) {
			if (tabsClosable && event.getButton() == MouseEvent.BUTTON2) {
				closeTab();
				event.consume();
				return;
			}
			if (!tabsClosable || event.getButton() != MouseEvent.BUTTON3) return;
			closeItem.setText(getGLLocalizer().text(GLUiKeys.TAB_CLOSE));
			closeOthersItem.setText(getGLLocalizer().text(GLUiKeys.TAB_CLOSE_OTHERS));
			reopenClosedItem.setText(getGLLocalizer().text(GLUiKeys.TAB_REOPEN_CLOSED));
			closeOthersItem.setEnabled(tabs.size() > 1);
			reopenClosedItem.setVisible(closedTabHistoryEnabled);
			reopenClosedItem.setEnabled(!closedTabs.isEmpty());
			com.vingame.glswing.component.GLParent host = getGLParent();
			while (host instanceof GLContainer container && container.getGLParent() != null)
				host = container.getGLParent();
			if (host != null) closeMenu.show(host, event.getScreenX(), event.getScreenY());
			event.consume();
		}
		@Override protected void controlMouseMoved(GLMouseEvent event) {
			boolean value = tabsClosable && event.hasLocalPosition()
					&& event.getLocalX() >= getWidth() - 24;
			if (closeHovered != value) { closeHovered = value; invalidateRecording(); }
		}
		@Override protected void controlMouseExited(GLMouseEvent event) {
			if (closeHovered) { closeHovered = false; invalidateRecording(); }
		}
		@Override protected String toolTipTextAt(GLMouseEvent event) {
			String explicit = super.toolTipTextAt(event);
			if (explicit != null) return explicit;
			GLTabbedPaneStyle style = getGLStyle();
			float available = getWidth() - style.tabHorizontalPadding() * 2
					- (tabsClosable ? 20 : 0);
			return GLTextFitter.overflowToolTip(getGLFont(style.font()), tab.title,
					Math.max(0, available));
		}

		private void closeTab() {
			int index = tabs.indexOf(tab);
			if (index >= 0) requestCloseTabAt(index);
		}
		private void closeOtherTabs() {
			closeNextOtherTab(tab);
		}
		private void closeNextOtherTab(Tab retained) {
			int target = -1;
			for (int index = tabs.size() - 1; index >= 0; index--)
				if (tabs.get(index) != retained) { target = index; break; }
			if (target < 0) {
				int remaining = tabs.indexOf(retained);
				if (remaining >= 0) setSelectedIndex(remaining);
				return;
			}
			requestCloseTabAt(target).whenComplete((closed, failure) -> {
				if (failure == null && Boolean.TRUE.equals(closed)) closeNextOtherTab(retained);
			});
		}
	}

	private static final class Host extends GLContainer {
		@Override protected void record(GLContext2D context) { }
		@Override protected boolean isMouseTargetEnabled() { return false; }
	}
}
