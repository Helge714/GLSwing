package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLCornerRadii;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLIcon;
import com.vingame.glswing.rendering.GLRenderParameters;
import com.vingame.glswing.rendering.GLStroke;
import com.vingame.glswing.rendering.GLTransform;

/**
 * Lightweight generic combo box backed by a normal {@link GLPopupMenu}.
 * Items therefore inherit popup scrolling, titles and top-level overlay order.
 */
public class GLComboBox<T> extends GLControl
		implements GLPopupMenuListener {

	private interface ComboEntry<E> {
	}

	private static final class ValueEntry<E> implements ComboEntry<E> {
		private final E value;

		private ValueEntry(E value) {
			this.value = value;
		}
	}

	private static final class GroupEntry<E> implements ComboEntry<E> {
		private final String text;
		private final List<ComboEntry<E>> children = new ArrayList<>();

		private GroupEntry(String text) {
			this.text = text;
		}
	}

	private final List<ComboEntry<T>> rootEntries = new ArrayList<>();
	private final List<ValueEntry<T>> items = new ArrayList<>();
	private final CopyOnWriteArrayList<ActionListener> actionListeners =
			new CopyOnWriteArrayList<>();
	private final GLPopupMenu popupMenu = new GLPopupMenu();
	private final GLRenderParameters backgroundParameters =
			new GLRenderParameters();
	private final GLRenderParameters pressedGradientParameters =
			new GLRenderParameters();
	private final GLRenderParameters textParameters = new GLRenderParameters();
	private final GLRenderParameters arrowParameters = new GLRenderParameters();
	private Function<? super T, String> itemTextProvider =
			item -> item == null ? "" : item.toString();
	private Function<? super T, GLItemPresentation> itemPresentationProvider;
	private GLComboBoxStyle localStyle;
	private GLComboBoxStyle appliedStyle;
	private ValueEntry<T> selectedEntry;
	private boolean popupMatchesComboWidth = true;
	private GLPopupDirection popupDirection;

	public GLComboBox() {
		super(true);
		popupMenu.addPopupMenuListener(this);
		popupMenu.setTitlePosition(GLPopupTitlePosition.TOP);
		updatePresentation();
	}

	public GLComboBox(List<? extends T> values) {
		this();
		if (values == null)
			throw new IllegalArgumentException("Combo-box items must not be null");
		for (T value : values) {
			ValueEntry<T> entry = new ValueEntry<>(value);
			rootEntries.add(entry);
			items.add(entry);
		}
		selectedEntry = items.isEmpty() ? null : items.get(0);
		rebuildPopupItems();
		updatePresentation();
	}

	public final void addItem(T item) {
		ValueEntry<T> entry = new ValueEntry<>(item);
		rootEntries.add(entry);
		items.add(entry);
		if (selectedEntry == null)
			selectedEntry = entry;
		rebuildPopupItems();
		updatePresentation();
	}

	public final void insertItemAt(T item, int index) {
		if (index < 0 || index > items.size())
			throw new IndexOutOfBoundsException("Combo-box item index: " + index);
		ValueEntry<T> entry = new ValueEntry<>(item);
		if (index == items.size())
			rootEntries.add(entry);
		else
			insertBefore(rootEntries, items.get(index), entry);
		rebuildFlatItems();
		if (selectedEntry == null)
			selectedEntry = entry;
		rebuildPopupItems();
		updatePresentation();
	}

	public final T removeItemAt(int index) {
		ValueEntry<T> removedEntry = items.get(index);
		T removed = removedEntry.value;
		removeEntry(rootEntries, removedEntry);
		rebuildFlatItems();
		if (selectedEntry == removedEntry)
			selectedEntry = items.isEmpty() ? null
					: items.get(Math.min(index, items.size() - 1));
		rebuildPopupItems();
		updatePresentation();
		return removed;
	}

	public final boolean removeItem(T item) {
		int index = indexOfValue(item);
		if (index < 0)
			return false;
		removeItemAt(index);
		return true;
	}

	public final void removeAllItems() {
		if (rootEntries.isEmpty())
			return;
		rootEntries.clear();
		items.clear();
		selectedEntry = null;
		rebuildPopupItems();
		updatePresentation();
	}

	public final void sortItems(Comparator<? super T> comparator) {
		if (comparator == null)
			throw new IllegalArgumentException("Combo-box comparator must not be null");
		sortEntries(rootEntries, comparator);
		rebuildFlatItems();
		rebuildPopupItems();
		updatePresentation();
	}

	/**
	 * Adds a nested choice group. Leaves added to the returned submenu behave like
	 * normal combo-box items and become the current selection when activated.
	 */
	public final SubMenu addSubMenu(String text) {
		GroupEntry<T> group = new GroupEntry<>(requireGroupText(text));
		rootEntries.add(group);
		rebuildPopupItems();
		updatePresentation();
		return new SubMenu(group);
	}

	/** Convenience overload for one-level groups. */
	public final SubMenu addSubMenu(String text, List<? extends T> values) {
		if (values == null)
			throw new IllegalArgumentException("Submenu items must not be null");
		SubMenu menu = addSubMenu(text);
		for (T value : values)
			menu.addItem(value);
		return menu;
	}

	/** Mutable builder for an arbitrarily nested combo-box submenu. */
	public final class SubMenu {
		private final GroupEntry<T> group;

		private SubMenu(GroupEntry<T> group) {
			this.group = group;
		}

		public SubMenu addItem(T value) {
			ValueEntry<T> entry = new ValueEntry<>(value);
			group.children.add(entry);
			rebuildFlatItems();
			if (selectedEntry == null)
				selectedEntry = entry;
			rebuildPopupItems();
			updatePresentation();
			return this;
		}

		public SubMenu addSubMenu(String text) {
			GroupEntry<T> nested = new GroupEntry<>(requireGroupText(text));
			group.children.add(nested);
			rebuildPopupItems();
			updatePresentation();
			return new SubMenu(nested);
		}

		public void sortItems(Comparator<? super T> comparator) {
			if (comparator == null)
				throw new IllegalArgumentException(
						"Combo-box comparator must not be null");
			sortEntries(group.children, comparator);
			rebuildFlatItems();
			rebuildPopupItems();
			updatePresentation();
		}

		public int getItemCount() {
			return countValues(group.children);
		}
	}

	public final int getItemCount() {
		return items.size();
	}

	public final T getItemAt(int index) {
		return items.get(index).value;
	}

	public final List<T> getItems() {
		return items.stream().map(entry -> entry.value).toList();
	}

	public final int getSelectedIndex() {
		return selectedEntry == null ? -1 : items.indexOf(selectedEntry);
	}

	public final T getSelectedItem() {
		return selectedEntry == null ? null : selectedEntry.value;
	}

	public final void setSelectedIndex(int index) {
		if (index < -1 || index >= items.size())
			throw new IndexOutOfBoundsException("Combo-box selection index: " + index);
		ValueEntry<T> next = index < 0 ? null : items.get(index);
		if (selectedEntry == next)
			return;
		selectedEntry = next;
		updatePresentation();
		fireActionPerformed();
	}

	public final void setSelectedItem(T item) {
		int index = indexOfValue(item);
		if (item != null && index < 0)
			throw new IllegalArgumentException("Item is not part of this combo box");
		setSelectedIndex(index);
	}

	public final void setItemTextProvider(Function<? super T, String> provider) {
		if (provider == null)
			throw new IllegalArgumentException("Item text provider must not be null");
		itemTextProvider = provider;
		rebuildPopupItems();
		updatePresentation();
	}

	public final Function<? super T, String> getItemTextProvider() {
		return itemTextProvider;
	}

	/** Supplies text and icons for both the selected value and popup rows. */
	public final void setItemPresentationProvider(
			Function<? super T, GLItemPresentation> provider) {
		if (provider == null)
			throw new IllegalArgumentException(
					"Item presentation provider must not be null");
		itemPresentationProvider = provider;
		rebuildPopupItems();
		updatePresentation();
	}

	public final Function<? super T, GLItemPresentation>
			getItemPresentationProvider() {
		return itemPresentationProvider;
	}

	public final void clearItemPresentationProvider() {
		if (itemPresentationProvider == null)
			return;
		itemPresentationProvider = null;
		rebuildPopupItems();
		updatePresentation();
	}

	public final void addActionListener(ActionListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("Action listener must not be null");
		actionListeners.addIfAbsent(listener);
	}

	public final void removeActionListener(ActionListener listener) {
		actionListeners.remove(listener);
	}

	public final ActionListener[] getActionListeners() {
		return actionListeners.toArray(ActionListener[]::new);
	}

	public final GLPopupMenu getPopupMenu() {
		return popupMenu;
	}

	public final void setPopupTitle(String title) {
		popupMenu.setTitle(title);
		popupMenu.setTitlePosition(GLPopupTitlePosition.TOP);
	}

	public final String getPopupTitle() {
		return popupMenu.getTitle();
	}

	public final boolean isPopupMatchesComboWidth() {
		return popupMatchesComboWidth;
	}

	public final void setPopupMatchesComboWidth(boolean matches) {
		popupMatchesComboWidth = matches;
	}

	public final GLPopupTextOverflow getPopupTextOverflow() {
		return popupMenu.getTextOverflow();
	}

	public final void setPopupTextOverflow(GLPopupTextOverflow overflow) {
		popupMenu.setTextOverflow(overflow);
		rebuildPopupItems();
	}

	public final boolean isPopupVisible() {
		return popupMenu.isShowing();
	}

	public final void showPopup() {
		if (!isEnabled() || items.isEmpty() || popupMenu.isShowing())
			return;
		// A combo-box title is deliberately always the fixed top row. General
		// popup menus may still place their title at either edge.
		popupMenu.setTitlePosition(GLPopupTitlePosition.TOP);
		GLParent host = overlayHost();
		if (host != null)
			popupMenu.showFromComboBox(host, this, popupMatchesComboWidth);
	}

	public final void hidePopup() {
		popupMenu.close();
	}

	public final void setGLStyle(GLComboBoxStyle style) {
		if (style == null)
			throw new IllegalArgumentException("Combo-box style must not be null");
		if (localStyle == style)
			return;
		localStyle = style;
		updatePresentation();
	}

	public final void clearGLStyle() {
		if (localStyle == null)
			return;
		localStyle = null;
		updatePresentation();
	}

	public final GLComboBoxStyle getGLStyle() {
		return localStyle != null ? localStyle : getGLTheme().comboBoxStyle();
	}

	@Override
	protected void record(GLContext2D context) {
		GLComboBoxStyle style = getGLStyle();
		float width = getWidth();
		float height = getHeight();
		if (width <= 0 || height <= 0)
			return;
		float radius = style.cornerRadius();
		float topRadius = popupDirection == GLPopupDirection.ABOVE ? 0 : radius;
		float bottomRadius = popupDirection == GLPopupDirection.BELOW ? 0 : radius;
		GLCornerRadii radii = new GLCornerRadii(topRadius, topRadius,
				bottomRadius, bottomRadius);
		context.withParameters(backgroundParameters, () -> {
			if (style.borderWidth() > 0)
				context.fillAndDrawUIRoundRect(0, 0, width, height, radii,
						style.borderWidth(), Color.WHITE, style.borderColor(),
						GLStroke.WidthSpace.SCREEN);
			else
				context.fillUIRoundRect(0, 0, width, height, radii, Color.WHITE);
		});
		float gradientInset = style.borderWidth() > 0
				? Math.min(style.borderWidth() + 1,
						Math.min(width, height) * 0.5f) : 0;
		context.withParameters(pressedGradientParameters, () ->
				context.fillUIVerticalGradientRoundRect(
						gradientInset, gradientInset,
						Math.max(0.0001f, width - gradientInset * 2),
						Math.max(0.0001f, height - gradientInset * 2),
						Math.max(0, topRadius - gradientInset),
						Math.max(0, bottomRadius - gradientInset),
						style.pressedLightColor(), style.pressedDarkColor()));

		GLItemPresentation selectedPresentation = selectedEntry == null
				? GLItemPresentation.text("") : presentation(selectedEntry.value);
		String selectedText = selectedPresentation.text();
		if (!selectedText.isEmpty()) {
			GLFont font = getGLFont(style.font());
			GLIcon selectedIcon = !isEnabled()
					&& selectedPresentation.disabledIcon() != null
							? selectedPresentation.disabledIcon()
							: selectedPresentation.icon();
			float iconSize = selectedIcon == null ? 0
					: Math.min(18, Math.max(1, height - 8));
			float textX = style.padding().left();
			if (selectedIcon != null) {
				context.drawUIIcon(selectedIcon, textX,
						(height - iconSize) * 0.5f, iconSize, iconSize,
						isEnabled() ? style.text(getControlState())
								: style.disabledTextColor());
				textX += iconSize + 5;
			}
			float availableTextWidth = Math.max(0, width
					- style.arrowAreaWidth() - style.padding().right() - textX);
			String renderedText = GLTextFitter.ellipsize(font, selectedText,
					availableTextWidth);
			float baseline = (height - font.getLineHeight()) * 0.5f + font.getAscent();
			float renderedTextX = textX;
			context.withParameters(textParameters, () ->
					context.drawUIText(font, renderedText,
							renderedTextX, baseline, Color.WHITE));
		}

		float arrowSize = Math.min(11, Math.max(4, height - 8));
		float arrowX = width - style.arrowAreaWidth()
				+ (style.arrowAreaWidth() - arrowSize) * 0.5f;
		float arrowY = (height - arrowSize) * 0.5f;
		float angle = popupDirection == null ? (float) (Math.PI * 0.5)
				: (float) (-Math.PI * 0.5);
		GLTransform previous = context.getTransform();
		context.setTransform(previous.copy().rotate(angle,
				arrowX + arrowSize * 0.5f, arrowY + arrowSize * 0.5f));
		context.withParameters(arrowParameters, () ->
				context.drawUIChevron(arrowX, arrowY, arrowSize, arrowSize,
						true, 1.5f, Color.WHITE, GLStroke.WidthSpace.SCREEN));
		context.setTransform(previous);
	}

	@Override
	protected void controlStateChanged(GLControlState oldState,
			GLControlState newState) {
		updateColors();
	}

	@Override
	protected void controlMouseClicked(GLMouseEvent event) {
		if (!isEnabled() || event.getButton() != MouseEvent.BUTTON1)
			return;
		if (popupMenu.isShowing())
			popupMenu.close();
		else
			showPopup();
	}

	@Override
	protected void controlKeyPressed(GLKeyEvent event) {
		if (getGLInputMap().matches(GLInputActions.ACTIVATE, event)) {
			if (popupMenu.isShowing())
				popupMenu.close();
			else
				showPopup();
			event.consume();
		} else if (getGLInputMap().matches(GLInputActions.NAVIGATE_UP, event)) {
			int selectedIndex = getSelectedIndex();
			if (!items.isEmpty())
				selectIndexFromUser(Math.max(0, selectedIndex - 1));
			event.consume();
		} else if (getGLInputMap().matches(GLInputActions.NAVIGATE_DOWN, event)) {
			int selectedIndex = getSelectedIndex();
			if (!items.isEmpty())
				selectIndexFromUser(Math.min(items.size() - 1, selectedIndex + 1));
			event.consume();
		}
	}

	@Override
	protected void themeChanged() {
		if (localStyle == null && !getGLStyle().equals(appliedStyle))
			updatePresentation();
	}

	@Override
	protected void detached(GLParent formerParent) {
		popupMenu.close();
	}

	@Override
	public void popupMenuOpened(GLPopupMenu menu) {
		popupDirection = menu.getAttachedDirection();
		updateColors();
		invalidateRecording();
	}

	@Override
	public void popupMenuClosed(GLPopupMenu menu) {
		popupDirection = null;
		updateColors();
		invalidateRecording();
	}

	private void rebuildPopupItems() {
		popupMenu.close();
		popupMenu.removeAllItems();
		buildPopupLevel(popupMenu, rootEntries);
	}

	private void buildPopupLevel(GLPopupMenu menu,
			List<ComboEntry<T>> entries) {
		for (ComboEntry<T> entry : entries) {
			if (entry instanceof ValueEntry<?> rawValue) {
				@SuppressWarnings("unchecked")
				ValueEntry<T> value = (ValueEntry<T>) rawValue;
				GLItemPresentation presentation = presentation(value.value);
				GLMenuItem item = menu.addItem(presentation.text(),
						event -> selectEntry(value));
				item.setIcon(presentation.icon());
				item.setDisabledIcon(presentation.disabledIcon());
			} else if (entry instanceof GroupEntry<?> rawGroup) {
				@SuppressWarnings("unchecked")
				GroupEntry<T> group = (GroupEntry<T>) rawGroup;
				GLPopupMenu subMenu = new GLPopupMenu();
				subMenu.setTextOverflow(popupMenu.getTextOverflow());
				buildPopupLevel(subMenu, group.children);
				menu.addSubMenu(group.text, subMenu);
			}
		}
	}

	private void updatePresentation() {
		GLComboBoxStyle style = getGLStyle();
		appliedStyle = style;
		GLFont font = measurementFont(style.font());
		float widest = 0;
		boolean hasIcons = false;
		for (ValueEntry<T> item : items) {
			GLItemPresentation presentation = presentation(item.value);
			widest = Math.max(widest, font.measureText(presentation.text()));
			hasIcons |= presentation.icon() != null
					|| presentation.disabledIcon() != null;
		}
		GLInsets padding = style.padding();
		setIntrinsicPreferredGLSize(new GLSize(
				Math.max(style.minimumWidth(), widest + padding.horizontal()
						+ (hasIcons ? Math.min(18,
								Math.max(1, style.height() - 8)) + 5 : 0)
						+ style.arrowAreaWidth()), style.height()));
		updateColors();
		invalidateRecording();
	}

	private void updateColors() {
		GLComboBoxStyle style = getGLStyle();
		GLControlState state = getControlState();
		backgroundParameters.setColor(popupMenu.isShowing()
				? style.pressedBackground() : style.background(state));
		pressedGradientParameters.setOpacity(popupMenu.isShowing()
				|| state == GLControlState.PRESSED ? 1 : 0);
		textParameters.setColor(style.text(state));
		arrowParameters.setColor(state == GLControlState.DISABLED
				? style.disabledTextColor() : style.arrowColor());
		repaintGL();
	}

	private String text(T item) {
		return presentation(item).text();
	}

	private GLItemPresentation presentation(T item) {
		if (itemPresentationProvider == null)
			return GLItemPresentation.text(
					Objects.requireNonNullElse(itemTextProvider.apply(item), ""));
		return Objects.requireNonNull(itemPresentationProvider.apply(item),
				"Item presentation provider returned null");
	}

	private GLFont measurementFont(GLFontSpec specification) {
		return getGLParent() == null ? specification.measurementFont()
				: getGLFont(specification);
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

	private void fireActionPerformed() {
		String command = selectedEntry == null ? "" : text(selectedEntry.value);
		ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
				command, System.currentTimeMillis(), 0);
		for (ActionListener listener : actionListeners)
			listener.actionPerformed(event);
	}

	private void selectEntry(ValueEntry<T> entry) {
		selectIndexFromUser(items.indexOf(entry));
	}

	private void selectIndexFromUser(int index) {
		int before = getSelectedIndex();
		if (before == index)
			return;
		setSelectedIndex(index);
		var manager = getGLHistoryManager();
		if (manager != null)
			manager.record("Change combo-box selection",
					() -> setSelectedIndex(before),
					() -> setSelectedIndex(index));
	}

	private int indexOfValue(T value) {
		for (int index = 0; index < items.size(); index++)
			if (Objects.equals(items.get(index).value, value))
				return index;
		return -1;
	}

	private void rebuildFlatItems() {
		items.clear();
		collectValues(rootEntries, items);
	}

	private static <E> void collectValues(List<ComboEntry<E>> entries,
			List<ValueEntry<E>> destination) {
		for (ComboEntry<E> entry : entries)
			if (entry instanceof ValueEntry<?> rawValue) {
				@SuppressWarnings("unchecked")
				ValueEntry<E> value = (ValueEntry<E>) rawValue;
				destination.add(value);
			} else if (entry instanceof GroupEntry<?> rawGroup) {
				@SuppressWarnings("unchecked")
				GroupEntry<E> group = (GroupEntry<E>) rawGroup;
				collectValues(group.children, destination);
			}
	}

	private static <E> boolean insertBefore(List<ComboEntry<E>> entries,
			ValueEntry<E> target, ValueEntry<E> inserted) {
		for (int index = 0; index < entries.size(); index++) {
			ComboEntry<E> entry = entries.get(index);
			if (entry == target) {
				entries.add(index, inserted);
				return true;
			}
			if (entry instanceof GroupEntry<?> rawGroup) {
				@SuppressWarnings("unchecked")
				GroupEntry<E> group = (GroupEntry<E>) rawGroup;
				if (insertBefore(group.children, target, inserted))
					return true;
			}
		}
		return false;
	}

	private static <E> boolean removeEntry(List<ComboEntry<E>> entries,
			ValueEntry<E> target) {
		if (entries.remove(target))
			return true;
		for (ComboEntry<E> entry : entries)
			if (entry instanceof GroupEntry<?> rawGroup) {
				@SuppressWarnings("unchecked")
				GroupEntry<E> group = (GroupEntry<E>) rawGroup;
				if (removeEntry(group.children, target))
					return true;
			}
		return false;
	}

	private static <E> void sortEntries(List<ComboEntry<E>> entries,
			Comparator<? super E> comparator) {
		for (ComboEntry<E> entry : entries)
			if (entry instanceof GroupEntry<?> rawGroup) {
				@SuppressWarnings("unchecked")
				GroupEntry<E> group = (GroupEntry<E>) rawGroup;
				sortEntries(group.children, comparator);
			}
		for (int start = 0; start < entries.size();) {
			while (start < entries.size() && !(entries.get(start) instanceof ValueEntry<?>))
				start++;
			int end = start;
			while (end < entries.size() && entries.get(end) instanceof ValueEntry<?>)
				end++;
			if (end > start)
				entries.subList(start, end).sort((first, second) -> {
					@SuppressWarnings("unchecked")
					ValueEntry<E> left = (ValueEntry<E>) first;
					@SuppressWarnings("unchecked")
					ValueEntry<E> right = (ValueEntry<E>) second;
					return comparator.compare(left.value, right.value);
				});
			start = Math.max(end, start + 1);
		}
	}

	private static <E> int countValues(List<ComboEntry<E>> entries) {
		int count = 0;
		for (ComboEntry<E> entry : entries)
			if (entry instanceof ValueEntry<?>)
				count++;
			else if (entry instanceof GroupEntry<?> rawGroup) {
				@SuppressWarnings("unchecked")
				GroupEntry<E> group = (GroupEntry<E>) rawGroup;
				count += countValues(group.children);
			}
		return count;
	}

	private static String requireGroupText(String text) {
		if (text == null)
			throw new IllegalArgumentException("Combo-box submenu text must not be null");
		return text;
	}
}
