package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import javax.accessibility.AccessibleRole;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLIcon;
import com.vingame.glswing.rendering.GLStroke;

/** Virtualized expandable tree built on the standard list selection model. */
public final class GLTree<T> extends GLContainer {
	public record CellState(boolean selected, boolean lead, boolean treeFocused,
			boolean enabled, GLControlState pointerState) { }

	@FunctionalInterface
	public interface CellRenderer<T> {
		void renderCell(GLContext2D context, GLTree<T> tree, T value, int depth,
				boolean expanded, boolean leaf, CellState state,
				float contentX, float width, float height);
	}
	@FunctionalInterface
	public interface ActivationListener<T> {
		void itemActivated(GLTree<T> tree, T value);
	}
	@FunctionalInterface
	public interface ExpansionListener<T> {
		void expansionChanged(GLTree<T> tree, T value, boolean expanded);
	}
	public interface CellEditor<T> {
		GLContainer createEditor(GLTree<T> tree, T value, GLList.EditController controller);
		void layoutEditor(GLTree<T> tree, T value, GLContainer editor,
				float contentX, float cellWidth, float cellHeight);
		boolean commitEditor(GLTree<T> tree, T value, GLContainer editor);
		default void cancelEditor(GLTree<T> tree, T value, GLContainer editor) { }
	}

	private record Row<T>(T value, T parent, int depth, boolean leaf) { }
	private final GLDefaultListModel<Row<T>> rows = new GLDefaultListModel<>();
	private final GLList<Row<T>> list = new GLList<>(rows);
	private final GLScrollPane scrollPane = new GLScrollPane(list);
	private final Set<T> expanded = new LinkedHashSet<>();
	private final CopyOnWriteArrayList<ActivationListener<T>> activationListeners =
			new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<ExpansionListener<T>> expansionListeners =
			new CopyOnWriteArrayList<>();
	private final GLTreeModel.Listener<T> modelListener = this::modelChanged;
	private GLTreeModel<T> model;
	private CellRenderer<T> cellRenderer = this::renderDefaultCell;
	private Function<? super T, String> itemTextProvider = String::valueOf;
	private Function<? super T, GLIcon> itemIconProvider = ignored -> null;
	private CellEditor<T> cellEditor;
	private GLPopupMenu contextMenu;
	private GLMenuItem renameMenuItem;
	private Row<T> contextRow;
	private T pressedDisclosure;
	private boolean pressedDisclosureExpanded;
	private boolean renameContextMenuEnabled = true;
	private boolean rebuilding;

	public GLTree() { this(new GLDefaultTreeModel<>()); }
	public GLTree(Collection<? extends T> roots) {
		this(new GLDefaultTreeModel<>(roots));
	}
	public GLTree(GLTreeModel<T> model) {
		list.setCellRenderer(this::renderRow);
		list.setItemToolTipProvider((source, row, index, width) -> {
			String text = itemTextProvider.apply(row.value());
			GLListStyle style = list.getGLStyle();
			return GLTextFitter.overflowToolTip(list.getGLFont(style.font()),
					text == null ? "" : text, Math.max(0,
							width - contentX(row) - style.cellPadding().right()));
		});
		list.setKeyHandler(this::handleTreeKey);
		list.addItemMouseListener(new GLList.ItemMouseListener<>() {
			@Override public void itemClicked(GLList.ItemEvent<Row<T>> event) {
				GLTree.this.itemClicked(event);
			}
			@Override public void itemPressed(GLList.ItemEvent<Row<T>> event) {
				GLTree.this.itemPressed(event);
			}
			@Override public void itemReleased(GLList.ItemEvent<Row<T>> event) {
				GLTree.this.itemReleased(event);
			}
		});
		list.addActivationListener((source, row, index) -> activate(row));
		scrollPane.setHorizontalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.NEVER);
		scrollPane.setScrollUnit(list.getRowHeight());
		addGL(scrollPane);
		setIntrinsicPreferredGLSize(scrollPane.getPreferredGLSize());
		setModel(model);
	}

	public GLTreeModel<T> getModel() { return model; }
	@Override protected AccessibleRole defaultAccessibleRole() {
		return AccessibleRole.TREE;
	}
	public void setModel(GLTreeModel<T> value) {
		if (value == null) throw new IllegalArgumentException("Tree model required");
		if (model == value) return;
		if (model != null) model.removeTreeModelListener(modelListener);
		model = value;
		model.addTreeModelListener(modelListener);
		expanded.clear();
		rebuildRows();
	}
	public GLListSelectionModel getSelectionModel() { return list.getSelectionModel(); }
	public GLScrollPane getScrollPane() { return scrollPane; }
	public int getVisibleRowCount() { return rows.getSize(); }
	public T getSelectedValue() {
		Row<T> row = list.getSelectedValue(); return row == null ? null : row.value();
	}
	public List<T> getSelectedValues() {
		return list.getSelectedValues().stream().map(Row::value).toList();
	}
	public void setSelectedValue(T value) {
		int index = rowIndex(value);
		if (index < 0) throw new IllegalArgumentException("Node is not visible: " + value);
		list.setSelectedIndex(index);
	}
	public boolean isExpanded(T value) { return expanded.contains(value); }
	public boolean setExpanded(T value, boolean valueExpanded) {
		if (value == null || model.isLeaf(value)) return false;
		boolean selectedDescendant = !valueExpanded && getSelectedValues().stream()
				.anyMatch(selected -> isVisibleDescendant(selected, value));
		boolean changed = valueExpanded ? expanded.add(value) : expanded.remove(value);
		if (!changed) return false;
		rebuildRows();
		if (selectedDescendant && getSelectedValues().isEmpty()) setSelectedValue(value);
		for (ExpansionListener<T> listener : expansionListeners)
			listener.expansionChanged(this, value, valueExpanded);
		return true;
	}
	public boolean toggleExpanded(T value) { return setExpanded(value, !isExpanded(value)); }
	public void expandAll() {
		for (int root = 0; root < model.getRootCount(); root++)
			expandRecursively(model.getRootAt(root), new HashSet<>());
		rebuildRows();
	}
	public void collapseAll() { expanded.clear(); rebuildRows(); }
	public void setCellRenderer(CellRenderer<T> renderer) {
		if (renderer == null) throw new IllegalArgumentException("Renderer required");
		cellRenderer = renderer; list.setCellRenderer(this::renderRow);
	}
	public void setItemTextProvider(Function<? super T, String> provider) {
		if (provider == null) throw new IllegalArgumentException("Text provider required");
		itemTextProvider = provider; list.setCellRenderer(this::renderRow);
	}
	/** Supplies an optional icon drawn between the disclosure and node text. */
	public void setItemIconProvider(Function<? super T, GLIcon> provider) {
		if (provider == null) throw new IllegalArgumentException("Icon provider required");
		itemIconProvider = provider; list.setCellRenderer(this::renderRow);
	}
	public void setCellEditor(CellEditor<T> editor) {
		cellEditor = editor;
		list.setCellEditor(editor == null ? null : new TreeCellEditor());
	}
	/** Enables the built-in localized Rename action for editable trees. */
	public void setRenameContextMenuEnabled(boolean enabled) {
		renameContextMenuEnabled = enabled;
	}
	public boolean isRenameContextMenuEnabled() { return renameContextMenuEnabled; }
	public boolean startEditing(T value) {
		int index = rowIndex(value); return index >= 0 && list.startEditingAt(index);
	}
	public boolean commitEditing() { return list.commitEditing(); }
	public void cancelEditing() { list.cancelEditing(); }
	public void addActivationListener(ActivationListener<T> listener) {
		if (listener == null) throw new IllegalArgumentException("Listener required");
		activationListeners.addIfAbsent(listener);
	}
	public void removeActivationListener(ActivationListener<T> listener) {
		activationListeners.remove(listener);
	}
	public void addExpansionListener(ExpansionListener<T> listener) {
		if (listener == null) throw new IllegalArgumentException("Listener required");
		expansionListeners.addIfAbsent(listener);
	}
	public void removeExpansionListener(ExpansionListener<T> listener) {
		expansionListeners.remove(listener);
	}
	public void setRowHeight(float height) {
		list.setRowHeight(height); scrollPane.setScrollUnit(height);
	}

	@Override protected void record(GLContext2D context) { }
	@Override protected void resized(float width, float height) {
		scrollPane.setBounds(0, 0, width, height);
	}
	@Override protected void localizerChanged() {
		if (renameMenuItem != null)
			renameMenuItem.setText(getGLLocalizer().text(GLUiKeys.TREE_RENAME));
	}
	private void rebuildRows() {
		if (model == null || rebuilding) return;
		rebuildRows(getSelectedValues());
	}
	private void modelChanged(GLTreeModel.Change<T> change) {
		List<T> selected = new ArrayList<>(getSelectedValues());
		if (change.isReplacement()) {
			for (int index = 0; index < selected.size(); index++)
				if (java.util.Objects.equals(selected.get(index), change.replacedNode()))
					selected.set(index, change.replacement());
			if (expanded.remove(change.replacedNode())) expanded.add(change.replacement());
			if (contextRow != null && java.util.Objects.equals(
					contextRow.value(), change.replacedNode())) contextRow = null;
		}
		rebuildRows(selected);
	}
	private void rebuildRows(List<T> selected) {
		if (model == null || rebuilding) return;
		rebuilding = true;
		try {
			List<Row<T>> visible = new ArrayList<>();
			Set<T> path = new HashSet<>();
			for (int index = 0; index < model.getRootCount(); index++)
				appendVisible(model.getRootAt(index), null, 0, visible, path);
			rows.setElements(visible);
			restoreSelection(selected);
		} finally { rebuilding = false; }
	}
	private void appendVisible(T value, T parent, int depth,
			List<Row<T>> result, Set<T> path) {
		if (!path.add(value)) throw new IllegalStateException("Cycle in tree model: " + value);
		boolean leaf = model.isLeaf(value);
		result.add(new Row<>(value, parent, depth, leaf));
		if (!leaf && expanded.contains(value))
			for (int index = 0; index < model.getChildCount(value); index++)
				appendVisible(model.getChild(value, index), value, depth + 1, result, path);
		path.remove(value);
	}
	private void restoreSelection(List<T> selected) {
		var selection = list.getSelectionModel();
		selection.clearSelection();
		boolean first = true;
		for (T value : selected) {
			int index = rowIndex(value);
			if (index < 0) continue;
			if (first) selection.setSelectionInterval(index, index);
			else selection.addSelectionInterval(index, index);
			first = false;
		}
	}
	private int rowIndex(T value) {
		for (int index = 0; index < rows.getSize(); index++)
			if (java.util.Objects.equals(rows.getElementAt(index).value(), value)) return index;
		return -1;
	}
	private boolean isVisibleDescendant(T candidate, T ancestor) {
		int index = rowIndex(candidate);
		if (index < 0) return false;
		T parent = rows.getElementAt(index).parent();
		while (parent != null) {
			if (java.util.Objects.equals(parent, ancestor)) return true;
			int parentIndex = rowIndex(parent);
			parent = parentIndex < 0 ? null : rows.getElementAt(parentIndex).parent();
		}
		return false;
	}
	private void expandRecursively(T value, Set<T> path) {
		if (!path.add(value)) throw new IllegalStateException("Cycle in tree model: " + value);
		if (!model.isLeaf(value)) {
			expanded.add(value);
			for (int index = 0; index < model.getChildCount(value); index++)
				expandRecursively(model.getChild(value, index), path);
		}
		path.remove(value);
	}
	private boolean handleTreeKey(GLList<Row<T>> source, GLKeyEvent event) {
		Row<T> row = selectedLeadRow();
		if (row == null) return false;
		if (getGLInputMap().matches(GLInputActions.TREE_EDIT, event))
			return startEditing(row.value());
		if (getGLInputMap().matches(GLInputActions.NAVIGATE_RIGHT, event)) {
			if (!row.leaf() && !isExpanded(row.value())) setExpanded(row.value(), true);
			else if (!row.leaf() && model.getChildCount(row.value()) > 0)
				setSelectedValue(model.getChild(row.value(), 0));
			return true;
		}
		if (getGLInputMap().matches(GLInputActions.NAVIGATE_LEFT, event)) {
			if (!row.leaf() && isExpanded(row.value())) setExpanded(row.value(), false);
			else if (row.parent() != null) setSelectedValue(row.parent());
			return true;
		}
		return false;
	}
	private Row<T> selectedLeadRow() {
		int index = list.getSelectionModel().getLeadSelectionIndex();
		return index < 0 || index >= rows.getSize() ? null : rows.getElementAt(index);
	}
	private void itemClicked(GLList.ItemEvent<Row<T>> event) {
		Row<T> row = event.item();
		GLMouseEvent mouse = event.mouseEvent();
		if (mouse.getButton() == MouseEvent.BUTTON3 && cellEditor != null
				&& renameContextMenuEnabled) {
			showContextMenu(row, mouse);
			return;
		}
		if (mouse.getButton() == MouseEvent.BUTTON1 && mouse.hasLocalPosition()
				&& !row.leaf() && mouse.getLocalX() >= disclosureX(row)
				&& mouse.getLocalX() <= disclosureX(row) + disclosureSlot()) {
			// Expansion is committed on release. Consume the following click so a
			// double-click on the disclosure does not also activate the branch.
			mouse.consume();
		}
	}
	private void itemPressed(GLList.ItemEvent<Row<T>> event) {
		pressedDisclosure = null;
		Row<T> row = event.item();
		GLMouseEvent mouse = event.mouseEvent();
		if (mouse.getButton() == MouseEvent.BUTTON1 && mouse.hasLocalPosition()
				&& !row.leaf() && isInDisclosure(row, mouse.getLocalX())) {
			pressedDisclosure = row.value();
			pressedDisclosureExpanded = isExpanded(row.value());
			mouse.consume();
		}
	}
	private void itemReleased(GLList.ItemEvent<Row<T>> event) {
		T pressed = pressedDisclosure;
		pressedDisclosure = null;
		Row<T> row = event.item();
		GLMouseEvent mouse = event.mouseEvent();
		if (pressed == null || mouse.getButton() != MouseEvent.BUTTON1
				|| !mouse.hasLocalPosition()
				|| !java.util.Objects.equals(pressed, row.value())
				|| !isInDisclosure(row, mouse.getLocalX())) return;
		setExpanded(row.value(), !pressedDisclosureExpanded);
		mouse.consume();
	}
	private boolean isInDisclosure(Row<T> row, float x) {
		return x >= disclosureX(row) && x <= disclosureX(row) + disclosureSlot();
	}
	private void showContextMenu(Row<T> row, GLMouseEvent event) {
		ensureContextMenu();
		contextRow = row;
		GLParent host = rootParent();
		if (host != null) contextMenu.show(host, event.getScreenX(), event.getScreenY());
	}
	private void ensureContextMenu() {
		if (contextMenu != null) return;
		contextMenu = new GLPopupMenu();
		renameMenuItem = contextMenu.addItem(
				getGLLocalizer().text(GLUiKeys.TREE_RENAME), event -> {
					Row<T> row = contextRow;
					if (row != null) EventQueue.invokeLater(() -> startEditing(row.value()));
				});
		renameMenuItem.setAcceleratorText("F2");
	}
	private GLParent rootParent() {
		GLParent current = getGLParent();
		while (current instanceof GLContainer container
				&& container.getGLParent() != null) current = container.getGLParent();
		return current;
	}
	private void activate(Row<T> row) {
		if (!row.leaf()) toggleExpanded(row.value());
		else for (ActivationListener<T> listener : activationListeners)
			listener.itemActivated(this, row.value());
	}
	private float indent() { return getGLTheme().values().getFloat("tree.indent", 18); }
	private float disclosureSize() {
		return getGLTheme().values().getFloat("tree.disclosureSize", 11);
	}
	private float disclosureSlot() { return Math.max(14, disclosureSize() + 4); }
	private float disclosureX(Row<T> row) {
		return list.getGLStyle().cellPadding().left() + row.depth() * indent();
	}
	private float contentX(Row<T> row) { return disclosureX(row) + disclosureSlot(); }
	private void renderRow(GLContext2D context, GLList<Row<T>> source, Row<T> row,
			int index, GLList.CellState state, float width, float height) {
		CellState treeState = new CellState(state.selected(), state.lead(),
				state.listFocused(), state.enabled(), state.pointerState());
		cellRenderer.renderCell(context, this, row.value(), row.depth(),
				isExpanded(row.value()), row.leaf(), treeState,
				contentX(row), width, height);
	}
	private void renderDefaultCell(GLContext2D context, GLTree<T> tree, T value,
			int depth, boolean expandedValue, boolean leaf, CellState state,
			float textX, float width, float height) {
		GLListStyle style = list.getGLStyle();
		GLFont font = list.getGLFont(style.font());
		Color color = !state.enabled() ? style.disabledTextColor()
				: state.selected() ? style.selectedTextColor() : style.textColor();
		if (!leaf) {
			float size = disclosureSize();
			float slotX = style.cellPadding().left() + depth * indent();
			boolean pointerInside = state.pointerState() != GLControlState.NORMAL
					&& list.getPointerCellX() >= slotX
					&& list.getPointerCellX() <= slotX + disclosureSlot();
			if (pointerInside) {
				Color surface = getGLTheme().values().getColor(
						state.pointerState() == GLControlState.PRESSED
								? "tree.disclosurePressed" : "tree.disclosureHover",
						state.pointerState() == GLControlState.PRESSED
								? style.rowPressedBackground() : style.rowHoverBackground());
				float radius = getGLTheme().values().getFloat(
						"tree.disclosureCornerRadius", 3);
				context.fillUIRoundRect(slotX, (height - disclosureSlot()) * .5f,
						disclosureSlot(), disclosureSlot(), radius, surface);
			}
			float x = slotX + (disclosureSlot() - size) * 0.5f;
			context.drawUIChevron(x, (height - size) * 0.5f, size, size,
					!expandedValue, 1.5f, color, GLStroke.WidthSpace.SCREEN);
		}
		float contentX = textX;
		GLIcon icon = itemIconProvider.apply(value);
		if (icon != null) {
			float iconSize = getGLTheme().values().getFloat("tree.iconSize", 18);
			float iconGap = getGLTheme().values().getFloat("tree.iconGap", 6);
			context.drawUIIcon(icon, contentX, (height - iconSize) * .5f,
					iconSize, iconSize, color);
			contentX += iconSize + iconGap;
		}
		String raw = itemTextProvider.apply(value);
		GLInsets padding = style.cellPadding();
		String text = GLTextFitter.ellipsize(font, raw == null ? "" : raw,
				Math.max(0, width - contentX - padding.right()));
		float baseline = (height - font.getLineHeight()) * .5f + font.getAscent();
		context.drawUITextShadow(font, text, contentX, baseline,
				getGLTheme().textShadow("tree"));
		context.drawUIText(font, text, contentX, baseline, color);
	}
	private final class TreeCellEditor implements GLList.CellEditor<Row<T>> {
		@Override public GLContainer createEditor(GLList<Row<T>> source, Row<T> row,
				int index, GLList.EditController controller) {
			return cellEditor.createEditor(GLTree.this, row.value(), controller);
		}
		@Override public void layoutEditor(GLList<Row<T>> source, Row<T> row, int index,
				GLContainer editor, float width, float height) {
			cellEditor.layoutEditor(GLTree.this, row.value(), editor,
					contentX(row), width, height);
		}
		@Override public boolean commitEditor(GLList<Row<T>> source, Row<T> row,
				int index, GLContainer editor) {
			return cellEditor.commitEditor(GLTree.this, row.value(), editor);
		}
		@Override public void cancelEditor(GLList<Row<T>> source, Row<T> row,
				int index, GLContainer editor) {
			cellEditor.cancelEditor(GLTree.this, row.value(), editor);
		}
	}
}
