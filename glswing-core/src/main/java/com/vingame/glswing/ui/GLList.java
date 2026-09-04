package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLKeyBinding;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLKeyTarget;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLCornerRadii;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLStroke;

/**
 * Virtualized, renderer-driven list. Only cells intersecting the viewport of a
 * containing {@link GLScrollPane} are attached and rendered.
 */
public final class GLList<T> extends GLControl implements GLVirtualViewportContent {
	/** Immutable state passed to a cell renderer. */
	public record CellState(boolean selected, boolean lead, boolean listFocused,
			boolean enabled, GLControlState pointerState) { }

	/** Draws one cell after the list has painted its standard row background. */
	@FunctionalInterface
	public interface CellRenderer<T> {
		void renderCell(GLContext2D context, GLList<T> list, T value, int index,
				CellState state, float width, float height);
	}
	@FunctionalInterface
	public interface ItemToolTipProvider<T> {
		String toolTipText(GLList<T> source, T item, int index, float cellWidth);
	}

	/**
	 * Mouse event associated with a concrete model element.
	 * {@code selectedBeforePress} describes the selection state before the
	 * current press changed it, which is useful for desktop-style rename gestures.
	 */
	public record ItemEvent<T>(GLList<T> source, T item, int index,
			GLMouseEvent mouseEvent, boolean selectedBeforePress) { }

	@FunctionalInterface
	public interface ItemMouseListener<T> {
		void itemClicked(ItemEvent<T> event);
		default void itemPressed(ItemEvent<T> event) { }
		default void itemReleased(ItemEvent<T> event) { }
	}

	@FunctionalInterface
	public interface ActivationListener<T> {
		void itemActivated(GLList<T> source, T item, int index);
	}

	/** Handles a key before the list applies its standard navigation. */
	@FunctionalInterface
	public interface KeyHandler<T> {
		boolean keyPressed(GLList<T> source, GLKeyEvent event);
	}

	/** Controls the lifetime of an editor created for one visible cell. */
	public interface EditController {
		void commit();
		void cancel();
	}

	/**
	 * Creates, lays out, and commits an inline editor for a list cell. A list has
	 * at most one active editor. Returning {@code false} from
	 * {@link #commitEditor} keeps that editor active.
	 */
	public interface CellEditor<T> {
		GLContainer createEditor(GLList<T> list, T value, int index,
				EditController controller);
		void layoutEditor(GLList<T> list, T value, int index,
				GLContainer editor, float cellWidth, float cellHeight);
		boolean commitEditor(GLList<T> list, T value, int index,
				GLContainer editor);
		default void cancelEditor(GLList<T> list, T value, int index,
				GLContainer editor) { }
	}

	private final Map<Integer, Cell> visibleCells = new LinkedHashMap<>();
	private final ArrayDeque<Cell> cellPool = new ArrayDeque<>();
	private final CopyOnWriteArrayList<ItemMouseListener<T>> itemMouseListeners =
			new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<ActivationListener<T>> activationListeners =
			new CopyOnWriteArrayList<>();
	private final GLListModel.Listener modelListener = this::modelChanged;
	private final GLListSelectionModel.Listener selectionListener =
			(source, oldSelection, newSelection) -> selectionChanged();
	private GLListModel<T> model;
	private GLListSelectionModel selectionModel = new GLListSelectionModel();
	private CellRenderer<T> cellRenderer = this::renderDefaultCell;
	private Function<? super T, String> itemTextProvider = String::valueOf;
	private ItemToolTipProvider<T> itemToolTipProvider;
	private KeyHandler<T> keyHandler;
	private GLListStyle localStyle;
	private CellEditor<T> cellEditor;
	private Cell editingCell;
	private GLContainer editingComponent;
	private int editingIndex = -1;
	private long editingGeneration;
	private boolean committingEditing;
	private Float localRowHeight;
	private int overscanRows = 1;
	private float viewportY;
	private float viewportHeight;
	private int firstVisibleIndex = -1;
	private int lastVisibleIndex = -1;
	private int hoveredIndex = -1;
	private float pointerViewportY = Float.NaN;
	private float pointerCellX = Float.NaN;

	public GLList() { this(new GLDefaultListModel<>()); }
	public GLList(Collection<? extends T> items) {
		this(new GLDefaultListModel<>(items));
	}
	public GLList(GLListModel<T> model) {
		super(true);
		selectionModel.addSelectionListener(selectionListener);
		setModel(model);
	}

	public GLListModel<T> getModel() { return model; }
	public void setModel(GLListModel<T> model) {
		if (model == null)
			throw new IllegalArgumentException("List model must not be null");
		if (this.model == model) return;
		if (this.model != null) this.model.removeListDataListener(modelListener);
		this.model = model;
		model.addListDataListener(modelListener);
		selectionModel.clearSelection();
		modelContentsChanged();
	}

	/** Convenience replacement for lists backed by {@link GLDefaultListModel}. */
	public void setItems(Collection<? extends T> items) {
		if (model instanceof GLDefaultListModel<?> mutable) {
			@SuppressWarnings("unchecked")
			GLDefaultListModel<T> typed = (GLDefaultListModel<T>) mutable;
			typed.setElements(items);
		} else setModel(new GLDefaultListModel<>(items));
	}
	public int getItemCount() { return model.getSize(); }
	public T getItemAt(int index) { return model.getElementAt(index); }

	public GLListSelectionModel getSelectionModel() { return selectionModel; }
	public void setSelectionModel(GLListSelectionModel selectionModel) {
		if (selectionModel == null)
			throw new IllegalArgumentException("Selection model must not be null");
		if (this.selectionModel == selectionModel) return;
		this.selectionModel.removeSelectionListener(selectionListener);
		this.selectionModel = selectionModel;
		selectionModel.retainIndicesBelow(model.getSize());
		selectionModel.addSelectionListener(selectionListener);
		selectionChanged();
	}
	public int getSelectedIndex() { return selectionModel.getMinSelectionIndex(); }
	public int[] getSelectedIndices() { return selectionModel.getSelectedIndices(); }
	public T getSelectedValue() {
		int index = getSelectedIndex();
		return index < 0 ? null : model.getElementAt(index);
	}
	public List<T> getSelectedValues() {
		List<T> result = new ArrayList<>();
		for (int index : selectionModel.getSelectedIndices())
			if (index < model.getSize()) result.add(model.getElementAt(index));
		return List.copyOf(result);
	}
	public void setSelectedIndex(int index) {
		if (index == -1) selectionModel.clearSelection();
		else {
			requireModelIndex(index);
			selectionModel.setSelectionInterval(index, index);
			scrollToIndex(index);
		}
	}

	public CellRenderer<T> getCellRenderer() { return cellRenderer; }
	public void setCellRenderer(CellRenderer<T> renderer) {
		if (renderer == null)
			throw new IllegalArgumentException("Cell renderer must not be null");
		cellRenderer = renderer;
		invalidateVisibleCells();
	}
	public Function<? super T, String> getItemTextProvider() {
		return itemTextProvider;
	}
	public void setItemTextProvider(Function<? super T, String> provider) {
		if (provider == null)
			throw new IllegalArgumentException("Text provider must not be null");
		itemTextProvider = provider;
		invalidateVisibleCells();
	}
	public ItemToolTipProvider<T> getItemToolTipProvider() { return itemToolTipProvider; }
	/** Overrides automatic overflow tooltips for list rows. */
	public void setItemToolTipProvider(ItemToolTipProvider<T> provider) {
		itemToolTipProvider = provider;
	}
	public KeyHandler<T> getKeyHandler() { return keyHandler; }
	public void setKeyHandler(KeyHandler<T> handler) { keyHandler = handler; }

	public GLListStyle getGLStyle() {
		return localStyle != null ? localStyle : GLListStyle.fromTheme(getGLTheme());
	}
	public void setGLStyle(GLListStyle style) {
		if (style == null)
			throw new IllegalArgumentException("List style must not be null");
		localStyle = style;
		styleChanged();
	}
	public void clearGLStyle() {
		if (localStyle == null) return;
		localStyle = null;
		styleChanged();
	}
	public float getRowHeight() {
		return localRowHeight != null ? localRowHeight : getGLStyle().rowHeight();
	}
	public void setRowHeight(float height) {
		if (!Float.isFinite(height) || height <= 0)
			throw new IllegalArgumentException("Row height must be positive");
		localRowHeight = height;
		metricsChanged();
	}
	public void clearRowHeight() {
		if (localRowHeight == null) return;
		localRowHeight = null;
		metricsChanged();
	}
	public int getOverscanRows() { return overscanRows; }
	public void setOverscanRows(int rows) {
		if (rows < 0)
			throw new IllegalArgumentException("Overscan rows must not be negative");
		if (overscanRows == rows) return;
		overscanRows = rows;
		synchronizeVisibleCells();
	}
	public int getFirstVisibleIndex() { return firstVisibleIndex; }
	public int getLastVisibleIndex() { return lastVisibleIndex; }
	/** Returns the row currently below the pointer, or {@code -1}. */
	public int getHoveredIndex() { return hoveredIndex; }
	/** Returns the pointer X coordinate within the hovered cell, or NaN. */
	public float getPointerCellX() { return pointerCellX; }
	/** Returns the installed inline cell editor, or {@code null}. */
	public CellEditor<T> getCellEditor() { return cellEditor; }
	/** Installs an inline editor; {@code null} disables editing. */
	public void setCellEditor(CellEditor<T> editor) {
		if (cellEditor == editor) return;
		cancelEditing();
		cellEditor = editor;
	}
	/** Returns the model index being edited, or {@code -1}. */
	public int getEditingIndex() { return editingIndex; }
	/** Returns the active editor component, or {@code null}. */
	public GLContainer getEditingComponent() { return editingComponent; }
	/** Reports whether an inline editing session is active. */
	public boolean isEditing() { return editingIndex >= 0; }

	/** Starts inline editing after revealing and attaching the requested cell. */
	public boolean startEditingAt(int index) {
		requireModelIndex(index);
		if (cellEditor == null) return false;
		if (editingIndex == index) return true;
		cancelEditing();
		scrollToIndex(index);
		synchronizeVisibleCells();
		Cell cell = visibleCells.get(index);
		if (cell == null) return false;
		T value = model.getElementAt(index);
		long generation = ++editingGeneration;
		EditController controller = new EditController() {
			@Override public void commit() {
				if (editingGeneration == generation) commitEditing();
			}
			@Override public void cancel() {
				if (editingGeneration == generation) cancelEditing();
			}
		};
		GLContainer component = cellEditor.createEditor(this, value, index, controller);
		if (component == null)
			throw new IllegalStateException("Cell editor returned no component");
		editingIndex = index;
		editingCell = cell;
		editingComponent = component;
		cell.addGL(component);
		layoutEditingComponent();
		cell.invalidateRecording();
		if (component instanceof GLKeyTarget target) requestGLFocus(target);
		return true;
	}

	/** Commits the active editor when its handler accepts the value. */
	public boolean commitEditing() {
		if (editingIndex < 0) return true;
		int index = editingIndex;
		T value = model.getElementAt(index);
		boolean accepted;
		committingEditing = true;
		try {
			accepted = cellEditor.commitEditor(this, value, index, editingComponent);
		} finally {
			committingEditing = false;
		}
		if (!accepted)
			return false;
		if (editingIndex == index) finishEditing(false, value, index);
		return true;
	}

	/** Cancels and removes the active inline editor. */
	public void cancelEditing() {
		if (editingIndex < 0) return;
		int index = editingIndex;
		T value = index < model.getSize() ? model.getElementAt(index) : null;
		finishEditing(true, value, index);
	}

	public void addItemMouseListener(ItemMouseListener<T> listener) {
		if (listener == null) throw new IllegalArgumentException("Listener required");
		itemMouseListeners.addIfAbsent(listener);
	}
	public void removeItemMouseListener(ItemMouseListener<T> listener) {
		itemMouseListeners.remove(listener);
	}
	public void addActivationListener(ActivationListener<T> listener) {
		if (listener == null) throw new IllegalArgumentException("Listener required");
		activationListeners.addIfAbsent(listener);
	}
	public void removeActivationListener(ActivationListener<T> listener) {
		activationListeners.remove(listener);
	}

	/** Reveals the requested row using the nearest containing scroll pane. */
	public void scrollToIndex(int index) {
		requireModelIndex(index);
		GLScrollPane pane = containingScrollPane();
		if (pane == null) return;
		float border = getGLStyle().borderWidth();
		pane.scrollRectToVisible(0, border + index * getRowHeight(),
				Math.max(1, getWidth()), getRowHeight());
	}

	@Override protected void record(GLContext2D context) {
		GLListStyle style = getGLStyle();
		float radius = contentCornerRadius(style);
		context.addUIHitArea(0, 0, getWidth(), getHeight());
		context.fillUIRoundRect(0, 0, getWidth(), getHeight(),
				radius, style.background());
	}
	@Override protected void renderChildren(GLContext2D context) {
		GLListStyle style = getGLStyle();
		float border = style.borderWidth();
		float width = Math.max(0, getWidth() - border * 2);
		float height = Math.max(0, getHeight() - border * 2);
		if (width > 0 && height > 0) {
			float radius = Math.max(0, contentCornerRadius(style) - border);
			context.beginUIRoundedClip(border, border, width, height,
					new GLCornerRadii(radius, radius, radius, radius));
			try {
				super.renderChildren(context);
			} finally {
				context.endClip();
			}
		}
		if (style.borderWidth() > 0)
			context.drawUIRoundRect(0, 0, getWidth(), getHeight(),
					contentCornerRadius(style), style.borderWidth(), style.borderColor(),
					GLStroke.WidthSpace.SCREEN);
	}
	private float contentCornerRadius(GLListStyle style) {
		GLScrollPane pane = containingScrollPane();
		return pane != null && pane.getContent() == this ? 0 : style.cornerRadius();
	}
	@Override protected void controlMousePressed(GLMouseEvent event) {
		if (event.getButton() == MouseEvent.BUTTON1 && event.hasLocalPosition()
				&& indexAt(event.getLocalY()) < 0) {
			cancelEditing();
			selectionModel.clearSelection();
		}
	}
	@Override protected void controlMouseEntered(GLMouseEvent event) {
		updatePointerFromListEvent(event);
	}
	@Override protected void controlMouseMoved(GLMouseEvent event) {
		updatePointerFromListEvent(event);
	}
	@Override protected void controlMouseExited(GLMouseEvent event) {
		pointerViewportY = Float.NaN;
		setPointerCellX(Float.NaN);
		setHoveredIndex(-1);
	}
	@Override protected void controlKeyPressed(GLKeyEvent event) {
		if (keyHandler != null && keyHandler.keyPressed(this, event)) {
			event.consume();
			return;
		}
		if (model.getSize() == 0) return;
		var input = getGLInputMap();
		int target = -1;
		int lead = selectionModel.getLeadSelectionIndex();
		if (matchesNavigation(GLInputActions.NAVIGATE_UP, event))
			target = lead < 0 ? model.getSize() - 1 : Math.max(0, lead - 1);
		else if (matchesNavigation(GLInputActions.NAVIGATE_DOWN, event))
			target = lead < 0 ? 0 : Math.min(model.getSize() - 1, lead + 1);
		else if (matchesNavigation(GLInputActions.NAVIGATE_HOME, event)) target = 0;
		else if (matchesNavigation(GLInputActions.NAVIGATE_END, event))
			target = model.getSize() - 1;
		else if (matchesNavigation(GLInputActions.NAVIGATE_PAGE_UP, event))
			target = Math.max(0, (lead < 0 ? 0 : lead) - pageRows());
		else if (matchesNavigation(GLInputActions.NAVIGATE_PAGE_DOWN, event))
			target = Math.min(model.getSize() - 1,
					(lead < 0 ? 0 : lead) + pageRows());
		if (target >= 0) {
			selectFromInput(target, event.getModifiersEx());
			scrollToIndex(target);
			event.consume();
			return;
		}
		if (input.matches(GLInputActions.LIST_SELECT_ALL, event)) {
			if (selectionModel.getSelectionMode()
					!= GLListSelectionModel.SelectionMode.SINGLE)
				selectionModel.setSelectionInterval(0, model.getSize() - 1);
			event.consume();
			return;
		}
		if (input.matches(GLInputActions.ACTIVATE, event)) {
			int index = selectionModel.getLeadSelectionIndex();
			if (index < 0) index = getSelectedIndex();
			if (index >= 0) {
				if (event.getKeyCode() == KeyEvent.VK_SPACE
						&& selectionModel.getSelectionMode()
								== GLListSelectionModel.SelectionMode.MULTIPLE_INTERVAL)
					selectionModel.toggleSelection(index);
				else fireActivated(index);
			}
			event.consume();
		}
	}
	@Override protected void controlFocusChanged(boolean focused) {
		invalidateVisibleCells();
	}
	@Override protected void controlEnabledChanged(boolean enabled) {
		invalidateVisibleCells();
	}
	@Override protected void themeChanged() {
		if (localStyle == null) styleChanged();
	}
	@Override protected void resized(float width, float height) {
		synchronizeVisibleCells();
		layoutEditingComponent();
	}
	@Override public void viewportChanged(float x, float y, float width, float height) {
		viewportY = y;
		viewportHeight = height;
		synchronizeVisibleCells();
		updateHoveredIndexAfterScroll();
	}

	private void modelChanged(GLListModel.Change change) {
		if (editingIndex >= 0 && committingEditing) {
			int index = editingIndex;
			T value = index < model.getSize() ? model.getElementAt(index) : null;
			finishEditing(false, value, index);
		} else cancelEditing();
		if (change.type() == GLListModel.ChangeType.INTERVAL_ADDED)
			selectionModel.insertIndexInterval(change.firstIndex(),
					change.lastIndex() - change.firstIndex() + 1);
		else if (change.type() == GLListModel.ChangeType.INTERVAL_REMOVED)
			selectionModel.removeIndexInterval(change.firstIndex(), change.lastIndex());
		else selectionModel.retainIndicesBelow(model.getSize());
		modelContentsChanged();
	}
	private void modelContentsChanged() {
		recycleAllCells();
		updatePreferredSize();
		synchronizeVisibleCells();
		updateHoveredIndexAfterScroll();
		invalidateRecording();
	}
	private void selectionChanged() {
		invalidateVisibleCells();
	}
	private void styleChanged() {
		invalidateRecording();
		invalidateVisibleCells();
		metricsChanged();
	}
	private void metricsChanged() {
		cancelEditing();
		updatePreferredSize();
		recycleAllCells();
		synchronizeVisibleCells();
		updateHoveredIndexAfterScroll();
	}
	private void updatePreferredSize() {
		GLListStyle style = getGLStyle();
		float height = style.borderWidth() * 2 + model.getSize() * getRowHeight();
		setIntrinsicPreferredGLSize(new GLSize(style.preferredWidth(),
				Math.max(style.borderWidth() * 2, height)));
	}
	private void synchronizeVisibleCells() {
		if (model == null) return;
		int size = model.getSize();
		float rowHeight = getRowHeight();
		float border = getGLStyle().borderWidth();
		float visibleY = viewportHeight > 0 ? viewportY : Math.max(0, -getY());
		float visibleHeight = viewportHeight > 0 ? viewportHeight
				: Math.min(getHeight(), rowHeight * 10);
		int visibleFirst = size == 0 || visibleHeight <= 0 ? -1 : Math.max(0,
				(int) Math.floor((visibleY - border) / rowHeight));
		int visibleLast = visibleFirst < 0 ? -1 : Math.min(size - 1,
				(int) Math.floor((visibleY + visibleHeight - border) / rowHeight));
		int first = visibleFirst < 0 ? -1
				: Math.max(0, visibleFirst - overscanRows);
		int last = visibleLast < 0 ? -1
				: Math.min(size - 1, visibleLast + overscanRows);
		if (editingIndex >= 0 && (editingIndex < first || editingIndex > last))
			cancelEditing();
		for (var iterator = visibleCells.entrySet().iterator(); iterator.hasNext();) {
			var entry = iterator.next();
			if (entry.getKey() < first || entry.getKey() > last) {
				Cell cell = entry.getValue();
				boolean wasHovered = entry.getKey() == hoveredIndex;
				removeGL(cell);
				cell.resetTransientInteractionState();
				if (!wasHovered) cellPool.addLast(cell);
				iterator.remove();
			}
		}
		if (first >= 0 && last >= first) {
			for (int index = first; index <= last; index++) {
				Cell cell = visibleCells.get(index);
				if (cell == null) {
					cell = cellPool.pollFirst();
					if (cell == null) cell = new Cell();
					cell.setIndex(index);
					visibleCells.put(index, cell);
					addGL(cell);
				}
				cell.setBounds(border, border + index * rowHeight,
						Math.max(0, getWidth() - border * 2), rowHeight);
			}
		}
		layoutEditingComponent();
		firstVisibleIndex = visibleFirst;
		lastVisibleIndex = visibleLast;
	}
	private void recycleAllCells() {
		cancelEditing();
		for (var entry : visibleCells.entrySet()) {
			Cell cell = entry.getValue();
			removeGL(cell);
			cell.resetTransientInteractionState();
			if (entry.getKey() != hoveredIndex) cellPool.addLast(cell);
		}
		visibleCells.clear();
		firstVisibleIndex = lastVisibleIndex = -1;
		hoveredIndex = -1;
	}
	private void invalidateVisibleCells() {
		for (Cell cell : visibleCells.values()) cell.invalidateRecording();
		repaintGL();
	}
	private int indexAt(float y) {
		float border = getGLStyle().borderWidth();
		int index = (int) Math.floor((y - border) / getRowHeight());
		return y < border || index < 0 || index >= model.getSize() ? -1 : index;
	}
	private void updatePointerFromListEvent(GLMouseEvent event) {
		if (!event.hasLocalPosition()) return;
		pointerViewportY = event.getLocalY() - viewportY;
		setPointerCellX(event.getLocalX() - getGLStyle().borderWidth());
		setHoveredIndex(indexAt(event.getLocalY()));
	}
	private void updateHoveredIndexAfterScroll() {
		if (!Float.isFinite(pointerViewportY)) return;
		if (pointerViewportY < 0 || pointerViewportY > viewportHeight) {
			pointerViewportY = Float.NaN;
			setHoveredIndex(-1);
			return;
		}
		setHoveredIndex(indexAt(viewportY + pointerViewportY));
	}
	private void setHoveredIndex(int index) {
		if (hoveredIndex == index) return;
		Cell oldCell = visibleCells.get(hoveredIndex);
		hoveredIndex = index;
		Cell newCell = visibleCells.get(index);
		if (oldCell != null) oldCell.invalidateRecording();
		if (newCell != null) newCell.invalidateRecording();
		repaintGL();
	}
	private void setPointerCellX(float x) {
		if (Float.compare(pointerCellX, x) == 0) return;
		pointerCellX = x;
		Cell cell = visibleCells.get(hoveredIndex);
		if (cell != null) cell.invalidateRecording();
	}
	private int pageRows() {
		return Math.max(1, (int) Math.floor(viewportHeight / getRowHeight()) - 1);
	}
	private boolean matchesNavigation(String action, GLKeyEvent event) {
		var input = getGLInputMap();
		int ignoredModifiers = 0;
		Integer extend = input.getModifiers(GLInputActions.LIST_EXTEND_SELECTION);
		Integer toggle = input.getModifiers(GLInputActions.LIST_TOGGLE_SELECTION);
		if (extend != null) ignoredModifiers |= extend;
		if (toggle != null) ignoredModifiers |= toggle;
		GLKeyBinding normalized = new GLKeyBinding(event.getKeyCode(),
				event.getModifiersEx() & ~ignoredModifiers);
		return input.getKeyBindings(action).contains(normalized);
	}
	private void selectFromInput(int index, int modifiers) {
		var input = getGLInputMap();
		boolean extend = input.hasModifiers(GLInputActions.LIST_EXTEND_SELECTION,
				modifiers);
		boolean toggle = input.hasModifiers(GLInputActions.LIST_TOGGLE_SELECTION,
				modifiers);
		if (extend && selectionModel.getAnchorSelectionIndex() >= 0)
			selectionModel.setSelectionInterval(
					selectionModel.getAnchorSelectionIndex(), index);
		else if (toggle) selectionModel.toggleSelection(index);
		else selectionModel.setSelectionInterval(index, index);
	}
	private void fireItemMouseClicked(int index, GLMouseEvent event,
			boolean selectedBeforePress) {
		ItemEvent<T> itemEvent = new ItemEvent<>(this, model.getElementAt(index),
				index, event, selectedBeforePress);
		for (ItemMouseListener<T> listener : itemMouseListeners)
			listener.itemClicked(itemEvent);
	}
	private void fireItemMousePressed(int index, GLMouseEvent event,
			boolean selectedBeforePress) {
		ItemEvent<T> itemEvent = new ItemEvent<>(this, model.getElementAt(index),
				index, event, selectedBeforePress);
		for (ItemMouseListener<T> listener : itemMouseListeners)
			listener.itemPressed(itemEvent);
	}
	private void fireItemMouseReleased(int index, GLMouseEvent event,
			boolean selectedBeforePress) {
		ItemEvent<T> itemEvent = new ItemEvent<>(this, model.getElementAt(index),
				index, event, selectedBeforePress);
		for (ItemMouseListener<T> listener : itemMouseListeners)
			listener.itemReleased(itemEvent);
	}
	private void fireActivated(int index) {
		T value = model.getElementAt(index);
		for (ActivationListener<T> listener : activationListeners)
			listener.itemActivated(this, value, index);
	}
	private void renderDefaultCell(GLContext2D context, GLList<T> list, T value,
			int index, CellState state, float width, float height) {
		GLListStyle style = getGLStyle();
		GLFont font = getGLFont(style.font());
		GLInsets padding = style.cellPadding();
		String raw = itemTextProvider.apply(value);
		String text = GLTextFitter.ellipsize(font, raw == null ? "" : raw,
				Math.max(0, width - padding.horizontal()));
		float baseline = (height - font.getLineHeight()) * .5f + font.getAscent();
		Color color = !state.enabled() ? style.disabledTextColor()
				: state.selected() ? style.selectedTextColor() : style.textColor();
		context.drawUITextShadow(font, text, padding.left(), baseline,
				getGLTheme().textShadow("list"));
		context.drawUIText(font, text, padding.left(), baseline, color);
	}
	private GLScrollPane containingScrollPane() {
		GLContainer current = this;
		while (current.getGLParent() instanceof GLContainer parent) {
			if (parent instanceof GLScrollPane pane) return pane;
			current = parent;
		}
		return null;
	}
	private void requireModelIndex(int index) {
		if (index < 0 || index >= model.getSize())
			throw new IndexOutOfBoundsException("List index: " + index);
	}
	private void layoutEditingComponent() {
		if (editingIndex < 0 || editingCell == null || editingComponent == null
				|| editingIndex >= model.getSize()) return;
		cellEditor.layoutEditor(this, model.getElementAt(editingIndex), editingIndex,
				editingComponent, editingCell.getWidth(), editingCell.getHeight());
	}
	private void finishEditing(boolean cancelled, T value, int index) {
		Cell cell = editingCell;
		GLContainer component = editingComponent;
		CellEditor<T> editor = cellEditor;
		editingGeneration++;
		editingIndex = -1;
		editingCell = null;
		editingComponent = null;
		if (cell != null && component != null) cell.removeGL(component);
		if (cancelled && editor != null && value != null && component != null)
			editor.cancelEditor(this, value, index, component);
		if (cell != null) cell.invalidateRecording();
	}

	private final class Cell extends GLControl {
		private int index = -1;
		private boolean selectedBeforePress;
		private Cell() { super(false); }
		private void setIndex(int index) {
			if (this.index == index) return;
			this.index = index;
			invalidateRecording();
		}
		@Override protected void record(GLContext2D context) {
			if (index < 0 || index >= model.getSize()) return;
			GLListStyle style = getGLStyle();
			boolean selected = selectionModel.isSelectedIndex(index);
			boolean lead = selectionModel.getLeadSelectionIndex() == index;
			GLControlState pointerState = !GLList.this.isEnabled()
					? GLControlState.DISABLED
					: getControlState() == GLControlState.PRESSED
							? GLControlState.PRESSED
							: hoveredIndex == index ? GLControlState.HOVERED
									: GLControlState.NORMAL;
			Color background = !GLList.this.isEnabled() ? style.rowBackground()
					: selected ? (GLList.this.isFocused()
							? style.selectionBackground()
							: style.selectionInactiveBackground())
							: switch (pointerState) {
					case HOVERED -> style.rowHoverBackground();
					case PRESSED -> style.rowPressedBackground();
					case DISABLED, NORMAL -> style.rowBackground();
					};
			context.addUIHitArea(0, 0, getWidth(), getHeight());
			context.fillUIRoundRect(0, 0, getWidth(), getHeight(), 0, background);
			CellState state = new CellState(selected, lead, GLList.this.isFocused(),
					GLList.this.isEnabled(),
					pointerState);
			cellRenderer.renderCell(context, GLList.this, model.getElementAt(index), index,
					state, getWidth(), getHeight());
			if (lead && GLList.this.isFocused()) {
				float focusWidth = 1.5f;
				// Keep the antialiased outside half of the stroke away from the
				// viewport clip. Coincident curves otherwise leave corner pixels at
				// the lower edge on some scale factors.
				float focusInset = focusWidth;
				context.drawUIRoundRect(focusInset, focusInset,
						Math.max(0, getWidth() - focusInset * 2),
						Math.max(0, getHeight() - focusInset * 2),
						focusRadii(focusInset), focusWidth,
						style.focusColor(), GLStroke.WidthSpace.SCREEN);
			}
		}
		private GLCornerRadii focusRadii(float inset) {
			GLScrollPane pane = containingScrollPane();
			boolean directViewport = pane != null && pane.getContent() == GLList.this;
			GLCornerRadii container = directViewport ? pane.viewportContentCornerRadii()
					: GLCornerRadii.all(Math.max(0, getGLStyle().cornerRadius()
							- getGLStyle().borderWidth()));
			boolean atTop = directViewport ? touchesViewportTop(pane) : index == 0;
			boolean atBottom = directViewport
					? touchesViewportBottom(pane) : index + 1 == model.getSize();
			float topLeft = atTop
					? Math.max(0, container.topLeft() - inset) : 0;
			float topRight = atTop
					? Math.max(0, container.topRight() - inset) : 0;
			float bottomRight = atBottom
					? Math.max(0, container.bottomRight() - inset) : 0;
			float bottomLeft = atBottom
					? Math.max(0, container.bottomLeft() - inset) : 0;
			return new GLCornerRadii(topLeft, topRight, bottomRight, bottomLeft);
		}
		private boolean touchesViewportTop(GLScrollPane pane) {
			float edge = pane.getGLStyle().viewportBorderWidth();
			return Math.abs(getY() - viewportY - edge) <= 0.25f;
		}
		private boolean touchesViewportBottom(GLScrollPane pane) {
			float edge = viewportHeight - pane.getGLStyle().viewportBorderWidth();
			return Math.abs(getY() + getHeight() - viewportY - edge) <= 0.25f;
		}
		@Override protected void controlMouseEntered(GLMouseEvent event) {
			updatePointer(event);
		}
		@Override protected void controlMouseMoved(GLMouseEvent event) {
			updatePointer(event);
		}
		@Override protected void controlMouseExited(GLMouseEvent event) {
			if (hoveredIndex != index) return;
			pointerViewportY = Float.NaN;
			setPointerCellX(Float.NaN);
			setHoveredIndex(-1);
		}
		private void updatePointer(GLMouseEvent event) {
			if (!event.hasLocalPosition()) return;
			pointerViewportY = getY() - viewportY + event.getLocalY();
			setPointerCellX(event.getLocalX());
			setHoveredIndex(index);
		}
		@Override protected void controlMousePressed(GLMouseEvent event) {
			if (!GLList.this.isEnabled()) return;
			updatePointer(event);
			selectedBeforePress = selectionModel.isSelectedIndex(index);
			if (editingIndex >= 0 && editingIndex != index) cancelEditing();
			fireItemMousePressed(index, event, selectedBeforePress);
			if (event.isConsumed()) return;
			if (event.getButton() == MouseEvent.BUTTON1) {
				GLList.this.requestGLFocus(GLList.this);
				selectFromInput(index, event.getModifiersEx());
			} else if (event.getButton() == MouseEvent.BUTTON3
					&& !selectionModel.isSelectedIndex(index))
				selectionModel.setSelectionInterval(index, index);
		}
		@Override protected void controlMouseDragged(GLMouseEvent event) {
			updatePointer(event);
		}
		@Override protected void controlMouseReleased(GLMouseEvent event) {
			updatePointer(event);
			fireItemMouseReleased(index, event, selectedBeforePress);
		}
		@Override protected void controlMouseClicked(GLMouseEvent event) {
			if (!GLList.this.isEnabled()) return;
			fireItemMouseClicked(index, event, selectedBeforePress);
			if (!event.isConsumed() && event.getButton() == MouseEvent.BUTTON1
					&& event.completesDoubleClick())
				fireActivated(index);
		}
		@Override protected void controlStateChanged(GLControlState oldState,
				GLControlState newState) {
			invalidateRecording();
		}
		@Override protected String toolTipTextAt(GLMouseEvent event) {
			if (index < 0 || index >= model.getSize()) return null;
			T item = model.getElementAt(index);
			if (itemToolTipProvider != null)
				return itemToolTipProvider.toolTipText(GLList.this, item, index, getWidth());
			GLListStyle style = getGLStyle();
			String text = itemTextProvider.apply(item);
			return GLTextFitter.overflowToolTip(getGLFont(style.font()),
					text == null ? "" : text,
					Math.max(0, getWidth() - style.cellPadding().horizontal()));
		}
	}
}
