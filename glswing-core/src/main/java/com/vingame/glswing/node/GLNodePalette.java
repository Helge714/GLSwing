package com.vingame.glswing.node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.swing.SwingUtilities;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.node.model.GLNodeCatalog;
import com.vingame.glswing.node.model.GLNodeCatalogEntry;
import com.vingame.glswing.node.model.GLNodePortDescriptor;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLCheckBox;
import com.vingame.glswing.ui.GLDefaultListModel;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLList;
import com.vingame.glswing.ui.GLPopupMenu;
import com.vingame.glswing.ui.GLScrollPane;
import com.vingame.glswing.ui.GLTextEditor;
import com.vingame.glswing.ui.GLUiKeys;

/** Searchable reusable node catalog view and categorized creation popup. */
public final class GLNodePalette extends GLContainer {
	private final GLNodeCatalog catalog;
	private final GLTextEditor search = new GLTextEditor();
	private final GLDefaultListModel<GLNodeCatalogEntry> model =
			new GLDefaultListModel<>();
	private final GLList<GLNodeCatalogEntry> list = new GLList<>(model);
	private final GLScrollPane scroll = new GLScrollPane(list);
	private final GLCheckBox compatibleOnly = new GLCheckBox("Compatible only");
	private final GLLabel status = new GLLabel("");
	private Predicate<GLNodeCatalogEntry> allowed = entry -> true;
	private GLNodePortDescriptor compatiblePort;
	private Consumer<GLNodeCatalogEntry> activation = entry -> { };
	private String selectedTypeId;

	public GLNodePalette(GLNodeCatalog catalog) {
		this.catalog = Objects.requireNonNull(catalog, "Node catalog");
		search.setCommitOnFocusLost(false);
		list.setRowHeight(30);
		list.setItemTextProvider(entry ->
				(catalog.isFavorite(entry.type().id()) ? "[Favorite] " : "")
						+ entry.category() + " / " + entry.displayName());
		list.addActivationListener((source, entry, index) -> activate(entry));
		search.addTextChangeListener(event -> refresh());
		compatibleOnly.addActionListener(event -> refresh());
		scroll.setHorizontalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.NEVER);
		scroll.setContentTracksViewportWidth(true);
		addGL(search); addGL(scroll); addGL(compatibleOnly); addGL(status);
		updateLocalizedText();
		refresh();
	}

	public GLNodeCatalog catalog() { return catalog; }
	public GLTextEditor searchEditor() { return search; }
	public GLList<GLNodeCatalogEntry> nodeList() { return list; }
	public GLCheckBox compatibleOnlyControl() { return compatibleOnly; }
	public GLLabel statusLabel() { return status; }
	public GLNodeCatalogEntry getSelectedEntry() { return list.getSelectedValue(); }

	public void setActivationListener(Consumer<GLNodeCatalogEntry> listener) {
		activation = listener == null ? entry -> { } : listener;
	}

	public void setAllowedPredicate(Predicate<GLNodeCatalogEntry> predicate) {
		allowed = predicate == null ? entry -> true : predicate;
		refresh();
	}

	public void setCompatiblePort(GLNodePortDescriptor port) {
		compatiblePort = port;
		refresh();
	}

	public void setCompatibleOnly(boolean value) {
		compatibleOnly.setSelected(value);
		refresh();
	}

	public void setStatusText(String text) { status.setText(text == null ? "" : text); }

	public boolean toggleSelectedFavorite() {
		GLNodeCatalogEntry selected = getSelectedEntry();
		if (selected == null) return false;
		boolean favorite = catalog.toggleFavorite(selected.type().id());
		refresh();
		return favorite;
	}

	/** Reloads search results while preserving selection by node type. */
	public void refresh() {
		GLNodeCatalogEntry selected = list.getSelectedValue();
		if (selected != null) selectedTypeId = selected.type().id();
		GLNodePortDescriptor port = compatibleOnly.isSelected() ? compatiblePort : null;
		List<GLNodeCatalogEntry> entries = catalog.search(search.getText(), port)
				.stream().filter(allowed).toList();
		list.setItems(entries);
		if (selectedTypeId != null)
			for (int index = 0; index < entries.size(); index++)
				if (selectedTypeId.equals(entries.get(index).type().id())) {
					list.setSelectedIndex(index); break;
				}
	}

	/** Shows the same catalog as a searchable, categorized popup. */
	public GLPopupMenu showPopup(GLParent owner, float screenX, float screenY,
			Consumer<GLNodeCatalogEntry> insertion) {
		GLNodePortDescriptor port = compatibleOnly.isSelected() ? compatiblePort : null;
		return showPopup(owner, screenX, screenY, allowed, port, insertion);
	}

	/** Shows a popup with filters that do not alter this palette's docked state. */
	public GLPopupMenu showPopup(GLParent owner, float screenX, float screenY,
			Predicate<GLNodeCatalogEntry> popupAllowed,
			GLNodePortDescriptor popupCompatiblePort,
			Consumer<GLNodeCatalogEntry> insertion) {

        Objects.requireNonNull(owner, "Popup owner");
		Predicate<GLNodeCatalogEntry> filter = popupAllowed == null
				? entry -> true : popupAllowed;
		Consumer<GLNodeCatalogEntry> target = insertion == null ? activation : insertion;
		GLPopupMenu menu = new GLPopupMenu();
		GLTextEditor popupSearch = menu.addContent(new GLTextEditor(), 300, 36);
		popupSearch.setCommitOnFocusLost(false);
		Map<com.vingame.glswing.ui.GLMenuItem, GLNodeCatalogEntry> flat =
				new HashMap<>();
		Set<com.vingame.glswing.ui.GLMenuItem> categories = new java.util.HashSet<>();
		List<GLNodeCatalogEntry> entries = available("", filter, popupCompatiblePort);
		Map<String, List<GLNodeCatalogEntry>> grouped = new LinkedHashMap<>();
		for (GLNodeCatalogEntry entry : entries)
			grouped.computeIfAbsent(entry.category(), ignored -> new ArrayList<>()).add(entry);
		for (var category : grouped.entrySet()) {
			GLPopupMenu submenu = new GLPopupMenu();
			for (GLNodeCatalogEntry entry : category.getValue()) {
				var item = submenu.addItem(entry.displayName(),
						event -> use(entry, target, filter));
				item.setMnemonicEnabled(false);
			}
			var item = menu.addSubMenu(category.getKey(), submenu);
			item.setMnemonicEnabled(false); categories.add(item);
		}
		for (GLNodeCatalogEntry entry : entries) {
			var item = menu.addItem(entry.category() + " / " + entry.displayName(),
					event -> use(entry, target, filter));
			item.setMnemonicEnabled(false); flat.put(item, entry);
		}
        menu.setItemFilter(categories::contains);
		popupSearch.addTextChangeListener(event -> {
			Set<String> matching = available(popupSearch.getText(), filter,
					popupCompatiblePort).stream()
					.map(value -> value.type().id()).collect(java.util.stream.Collectors.toSet());
			menu.setItemFilter(item -> popupSearch.getText().isBlank()
					? categories.contains(item)
					: flat.containsKey(item) && matching.contains(flat.get(item).type().id()));
		});
		menu.show(owner, screenX, screenY);
		SwingUtilities.invokeLater(() -> popupSearch.requestGLFocus(popupSearch));
		return menu;
	}

	private List<GLNodeCatalogEntry> available(String query) {
		GLNodePortDescriptor port = compatibleOnly.isSelected() ? compatiblePort : null;
		return available(query, allowed, port);
	}

	private List<GLNodeCatalogEntry> available(String query,
			Predicate<GLNodeCatalogEntry> filter, GLNodePortDescriptor port) {
		return catalog.search(query, port).stream().filter(filter).toList();
	}

	private void activate(GLNodeCatalogEntry entry) { use(entry, activation); }
	private void use(GLNodeCatalogEntry entry, Consumer<GLNodeCatalogEntry> target) {
		use(entry, target, allowed);
	}
	private void use(GLNodeCatalogEntry entry, Consumer<GLNodeCatalogEntry> target,
			Predicate<GLNodeCatalogEntry> filter) {
		if (entry == null || !filter.test(entry)) return;
		target.accept(entry);
		catalog.markUsed(entry.type().id());
		selectedTypeId = entry.type().id();
		refresh();
	}

	@Override protected void resized(float width, float height) {
		float pad = 10, inner = Math.max(0, width - pad * 2);
		search.setBounds(pad, pad, inner, 30);
		float footer = 54;
		scroll.setBounds(pad, 48, inner, Math.max(0, height - 48 - footer));
		compatibleOnly.setBounds(pad, Math.max(48, height - 48), inner, 24);
		status.setBounds(pad, Math.max(72, height - 22), inner, 18);
	}

	@Override protected void record(GLContext2D context) { }

	@Override protected void localizerChanged() { updateLocalizedText(); }

	private void updateLocalizedText() {
		compatibleOnly.setText(getGLLocalizer().text(
				GLUiKeys.NODE_PALETTE_COMPATIBLE_ONLY));
	}
}
