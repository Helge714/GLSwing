package com.vingame.glswing.file;

import java.awt.Color;
import java.awt.EventQueue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.filechooser.FileSystemView;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLIcon;
import com.vingame.glswing.ui.GLControlState;
import com.vingame.glswing.ui.GLDefaultListModel;
import com.vingame.glswing.ui.GLFontSpec;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLList;
import com.vingame.glswing.ui.GLListSelectionModel;
import com.vingame.glswing.ui.GLScrollPane;
import com.vingame.glswing.ui.GLTree;

/** Embeddable, theme-aware filesystem browser with lazy directory navigation. */
public final class GLFileBrowser extends GLContainer {
	/** Immutable metadata for one visible directory entry. */
	public record Entry(Path path, boolean directory, boolean hidden,
			long size, Instant modified) {
		public Entry {
			if (path == null || modified == null) throw new IllegalArgumentException("Entry required");
			path = path.toAbsolutePath().normalize();
		}
		public String displayName() {
			Path name = path.getFileName();
			return name == null ? path.toString() : name.toString();
		}
		private static Entry read(Path path) {
			boolean directory = Files.isDirectory(path);
			boolean hidden = isHidden(path);
			long size = 0; Instant modified = Instant.EPOCH;
			try {
				if (!directory) size = Files.size(path);
				modified = Files.getLastModifiedTime(path).toInstant();
			} catch (IOException ignored) { }
			return new Entry(path, directory, hidden, size, modified);
		}
	}
	@FunctionalInterface public interface DirectoryListener {
		void directoryChanged(GLFileBrowser browser, Path directory);
	}
	@FunctionalInterface public interface SelectionListener {
		void selectionChanged(GLFileBrowser browser, List<Path> selection);
	}
	@FunctionalInterface public interface ActivationListener {
		void fileActivated(GLFileBrowser browser, Path file);
	}

	private static final String ICON_ROOT = "com/vingame/glswing/icons/";
	private static final GLIcon HOME_ICON = icon("filedialog_home.svg");
	private static final GLIcon FOLDER_ICON = icon("filedialog_folder.svg");
	private static final GLIcon FILE_ICON = icon("filedialog_file.svg");
	private static final DateTimeFormatter MODIFIED_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm").withZone(ZoneId.systemDefault());
	private final GLFileSystemTreeModel treeModel = new GLFileSystemTreeModel();
	private final GLTree<GLFileSystemTreeModel.Node> tree = new GLTree<>(treeModel);
	private final GLDefaultListModel<Entry> entryModel = new GLDefaultListModel<>();
	private final GLList<Entry> entryList = new GLList<>(entryModel);
	private final GLScrollPane entryScroll = new GLScrollPane(entryList);
	private final Header header = new Header();
	private final GLLabel nameHeader = new GLLabel("Name");
	private final GLLabel modifiedHeader = new GLLabel("Modified");
	private final GLLabel sizeHeader = new GLLabel("Size");
	private final CopyOnWriteArrayList<DirectoryListener> directoryListeners =
			new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<SelectionListener> selectionListeners =
			new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<ActivationListener> activationListeners =
			new CopyOnWriteArrayList<>();
	private Path directory;
	private GLFileFilter filter;
	private String query = "";
	private boolean showHidden;
	private boolean multipleSelection;
	private long refreshGeneration;
	private long revealGeneration;
	private boolean synchronizingTree;

	/** Creates a browser rooted at the current user's home directory. */
	public GLFileBrowser() { this(userHome()); }
	/** Creates a browser showing an existing directory. */
	public GLFileBrowser(Path initialDirectory) {
		directory = normalizeDirectory(initialDirectory);
		tree.setRowHeight(32);
		tree.setItemTextProvider(GLFileSystemTreeModel.Node::label);
		tree.setItemIconProvider(this::nodeIcon);
		tree.getSelectionModel().setSelectionMode(GLListSelectionModel.SelectionMode.SINGLE);
		tree.getSelectionModel().addSelectionListener((source, oldValue, newValue) -> {
			GLFileSystemTreeModel.Node node = tree.getSelectedValue();
			if (!synchronizingTree && node != null && node.path() != null)
				setDirectory(node.path());
		});
		tree.addExpansionListener((source, node, expanded) -> {
			if (expanded) treeModel.loadChildren(node);
		});
		entryList.setRowHeight(34);
		entryList.setCellRenderer(this::renderEntry);
		entryList.setItemToolTipProvider((source, entry, index, width) ->
				com.vingame.glswing.ui.GLTextFitter.overflowToolTip(rowFont(entry),
						entry.displayName(), columns(width).nameWidth));
		entryList.getSelectionModel().addSelectionListener(
				(source, oldValue, newValue) -> fireSelectionChanged());
		entryList.addActivationListener((source, entry, index) -> {
			if (entry.directory()) setDirectory(entry.path());
			else for (ActivationListener listener : activationListeners)
				listener.fileActivated(this, entry.path());
		});
		entryScroll.setHorizontalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.NEVER);
		header.addGL(nameHeader); header.addGL(modifiedHeader); header.addGL(sizeHeader);
		addGL(tree); addGL(header); addGL(entryScroll);
		applyLocalization();
		refresh();
	}

	/** Returns the absolute, normalized directory currently displayed. */
	public Path getDirectory() { return directory; }
	/** Navigates to an existing directory and refreshes its entries asynchronously. */
	public void setDirectory(Path value) { setDirectory(value, true); }
	void setDirectory(Path value, boolean refresh) {
		Path normalized = normalizeDirectory(value);
		if (normalized.equals(directory)) return;
		directory = normalized;
		entryList.getSelectionModel().clearSelection();
		long previousRefresh = refreshGeneration;
		for (DirectoryListener listener : directoryListeners)
			listener.directoryChanged(this, directory);
		if (refresh && previousRefresh == refreshGeneration && normalized.equals(directory))
			refresh();
	}
	/** Returns the file filter, or {@code null} when every file is accepted. */
	public GLFileFilter getFileFilter() { return filter; }
	/** Sets the file filter used by the next refresh; directories always remain visible. */
	public void setFileFilter(GLFileFilter value) { filter = value; }
	/** Returns the case-insensitive name query used by the next refresh. */
	public String getSearchQuery() { return query; }
	/** Sets the case-insensitive name query used by the next refresh. */
	public void setSearchQuery(String value) {
		query = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
	}
	/** Returns whether hidden entries and hidden directory branches are included. */
	public boolean isShowHidden() { return showHidden; }
	/** Controls hidden-entry visibility and rebuilds the directory roots. */
	public void setShowHidden(boolean value) {
		if (showHidden == value) return;
		showHidden = value; treeModel.setIncludeHidden(value); expandComputer(); reveal(directory);
	}
	/** Selects single-entry or conventional modifier-based multiple selection. */
	public void setMultipleSelection(boolean value) {
		multipleSelection = value;
		entryList.getSelectionModel().setSelectionMode(value
				? GLListSelectionModel.SelectionMode.MULTIPLE_INTERVAL
				: GLListSelectionModel.SelectionMode.SINGLE);
	}
	public boolean isMultipleSelection() { return multipleSelection; }
	/** Returns the selected paths in model order. */
	public List<Path> getSelectedPaths() {
		return entryList.getSelectedValues().stream().map(Entry::path).toList();
	}
	/** Returns the lead selected path, or {@code null} when selection is empty. */
	public Path getSelectedPath() {
		Entry entry = entryList.getSelectedValue(); return entry == null ? null : entry.path();
	}
	/** Returns the details list for advanced presentation and editing integration. */
	public GLList<Entry> getEntryList() { return entryList; }
	/** Returns the lazy directory tree for advanced presentation integration. */
	public GLTree<GLFileSystemTreeModel.Node> getDirectoryTree() { return tree; }
	/** Refreshes the current directory asynchronously. */
	public CompletionStage<List<Entry>> refresh() { return refresh(null); }
	/** Refreshes and selects a path if it remains visible in the resulting entries. */
	public CompletionStage<List<Entry>> refresh(Path selectAfterRefresh) {
		long generation = ++refreshGeneration;
		reveal(directory);
		CompletableFuture<List<Entry>> completion = new CompletableFuture<>();
		Path requestedDirectory = directory;
		GLFileFilter requestedFilter = filter;
		String requestedQuery = query;
		boolean includeHidden = showHidden;
		CompletableFuture.supplyAsync(() -> entries(requestedDirectory, requestedFilter,
				includeHidden, requestedQuery)).whenComplete((values, failure) ->
				EventQueue.invokeLater(() -> {
					if (failure != null) { completion.completeExceptionally(failure); return; }
					if (generation == refreshGeneration && requestedDirectory.equals(directory)) {
						entryList.cancelEditing();
						entryList.getSelectionModel().clearSelection();
						entryModel.setElements(values);
						if (selectAfterRefresh != null) select(selectAfterRefresh);
					}
					completion.complete(values);
				}));
		return completion;
	}
	/** Invalidates a directory in the lazy tree and reloads its expanded occurrences. */
	public void refreshDirectoryTree(Path path) {
		treeModel.refresh(path);
		for (int root = 0; root < treeModel.getRootCount(); root++)
			reloadExpanded(treeModel.getRootAt(root));
		reveal(directory);
	}
	/** Selects a visible entry without changing the current directory. */
	public void select(Path path) {
		int index = indexOf(path);
		if (index >= 0) entryList.setSelectedIndex(index);
	}
	/** Returns the visible model index of a path, or {@code -1}. */
	public int indexOf(Path path) {
		if (path == null) return -1;
		Path normalized = path.toAbsolutePath().normalize();
		for (int index = 0; index < entryModel.getSize(); index++)
			if (entryModel.getElementAt(index).path().equals(normalized)) return index;
		return -1;
	}
	boolean isNameHit(Entry entry, GLMouseEvent event) {
		if (entry == null || !event.hasLocalPosition()) return false;
		GLFont font = rowFont(entry);
		Columns columns = columns(entryList.getWidth());
		float right = columns.nameX + font.measureText(entry.displayName()) + 5;
		return event.getLocalX() >= 32
				&& event.getLocalX() <= Math.min(columns.modifiedX - 10, right);
	}
	void layoutNameEditor(GLContainer editor, float width, float height) {
		Columns columns = columns(width);
		editor.setBounds(32, 3, Math.max(1, columns.modifiedX - 42), Math.max(1, height - 6));
	}
	public void addDirectoryListener(DirectoryListener listener) {
		if (listener == null) throw new IllegalArgumentException("Listener required");
		directoryListeners.addIfAbsent(listener);
	}
	public void removeDirectoryListener(DirectoryListener listener) {
		directoryListeners.remove(listener);
	}
	public void addSelectionListener(SelectionListener listener) {
		if (listener == null) throw new IllegalArgumentException("Listener required");
		selectionListeners.addIfAbsent(listener);
	}
	public void removeSelectionListener(SelectionListener listener) {
		selectionListeners.remove(listener);
	}
	public void addActivationListener(ActivationListener listener) {
		if (listener == null) throw new IllegalArgumentException("Listener required");
		activationListeners.addIfAbsent(listener);
	}
	public void removeActivationListener(ActivationListener listener) {
		activationListeners.remove(listener);
	}

	@Override protected void record(GLContext2D context) { }
	@Override protected void resized(float width, float height) {
		float sidebar = Math.min(230, Math.max(150, width * .24f));
		tree.setBounds(0, 0, sidebar, height);
		float contentX = sidebar + 2;
		header.setBounds(contentX, 0, Math.max(1, width - contentX), 30);
		entryScroll.setBounds(contentX, 30, Math.max(1, width - contentX),
				Math.max(1, height - 30));
	}
	@Override protected void localizerChanged() { applyLocalization(); }
	private void applyLocalization() {
		nameHeader.setText(text("name")); modifiedHeader.setText(text("modified"));
		sizeHeader.setText(text("size"));
		treeModel.reset(systemLocations(), text("thisPc"), text("loading"), text("cannotRead"));
		treeModel.setIncludeHidden(showHidden);
		expandComputer(); reveal(directory);
	}
	private List<GLFileSystemTreeModel.Location> systemLocations() {
		List<GLFileSystemTreeModel.Location> result = new ArrayList<>();
		List<Path> added = new ArrayList<>();
		String oneDrive = System.getenv("OneDrive");
		if (oneDrive != null) try {
			addLocation(result, added, "OneDrive", Path.of(oneDrive));
		} catch (InvalidPathException ignored) { }
		addLocation(result, added, text("desktop"), place("Desktop"));
		addLocation(result, added, text("downloads"), place("Downloads"));
		addLocation(result, added, text("documents"), place("Documents"));
		addLocation(result, added, text("pictures"), place("Pictures"));
		addLocation(result, added, text("music"), place("Music"));
		return result;
	}
	private static void addLocation(List<GLFileSystemTreeModel.Location> result,
			List<Path> added, String label, Path candidate) {
		if (candidate == null || !Files.isDirectory(candidate)) return;
		Path normalized = candidate.toAbsolutePath().normalize();
		if (added.add(normalized)) result.add(new GLFileSystemTreeModel.Location(label, normalized));
	}
	private void expandComputer() {
		for (int index = 0; index < treeModel.getRootCount(); index++) {
			var node = treeModel.getRootAt(index);
			if (node.kind() == GLFileSystemTreeModel.Kind.COMPUTER) tree.setExpanded(node, true);
		}
	}
	private void reveal(Path target) {
		GLFileSystemTreeModel.Node node = treeModel.nearestLoadedAncestor(target);
		if (node != null) reveal(node, target.toAbsolutePath().normalize(), ++revealGeneration);
	}
	private void reveal(GLFileSystemTreeModel.Node node, Path target, long generation) {
		if (generation != revealGeneration || !treeModel.contains(node)) return;
		if (node.path().equals(target)) {
			synchronizingTree = true;
			try { tree.setSelectedValue(node); } finally { synchronizingTree = false; }
			return;
		}
		tree.setExpanded(node, true);
		treeModel.loadChildren(node).thenRun(() -> EventQueue.invokeLater(() -> {
			if (generation != revealGeneration || !treeModel.contains(node)) return;
			for (int index = 0; index < treeModel.getChildCount(node); index++) {
				var child = treeModel.getChild(node, index);
				if (child.path() != null && target.startsWith(child.path())) {
					reveal(child, target, generation); break;
				}
			}
		}));
	}
	private void reloadExpanded(GLFileSystemTreeModel.Node node) {
		if (tree.isExpanded(node)) treeModel.loadChildren(node);
		for (int index = 0; index < treeModel.getChildCount(node); index++)
			reloadExpanded(treeModel.getChild(node, index));
	}
	private GLIcon nodeIcon(GLFileSystemTreeModel.Node node) {
		return switch (node.kind()) {
		case COMPUTER, PLACE -> HOME_ICON;
		case DRIVE, DIRECTORY -> FOLDER_ICON;
		case ERROR -> FILE_ICON;
		case LOADING -> null;
		};
	}
	private void fireSelectionChanged() {
		List<Path> selection = getSelectedPaths();
		for (SelectionListener listener : selectionListeners)
			listener.selectionChanged(this, selection);
	}
	private void renderEntry(GLContext2D context, GLList<Entry> source, Entry entry,
			int index, GLList.CellState state, float width, float height) {
		var theme = getGLTheme(); var listStyle = source.getGLStyle();
		Color background = state.selected()
				? theme.values().getColor("fileDialog.selectionColor",
						state.listFocused() ? listStyle.selectionBackground()
								: listStyle.selectionInactiveBackground())
				: state.pointerState() == GLControlState.NORMAL
						? theme.values().getColor("fileDialog.row.background",
								theme.textEditorStyle().backgroundColor())
						: theme.buttonStyle().background(state.pointerState());
		context.fillUIRoundRect(0, 0, width, height, 0, background);
		GLFont font = rowFont(entry); Color color = rowColor(entry);
		context.drawUIIcon(entry.directory() ? FOLDER_ICON : FILE_ICON,
				9, (height - 18) * .5f, 18, 18, color);
		float baseline = (height - font.getLineHeight()) * .5f + font.getAscent();
		Columns columns = columns(width); var shadow = theme.textShadow("fileDialog");
		if (entryList.getEditingIndex() != index) {
			String name = fit(font, entry.displayName(), columns.nameWidth);
			context.drawUITextShadow(font, name, columns.nameX, baseline, shadow);
			context.drawUIText(font, name, columns.nameX, baseline, color);
		}
		if (!entry.directory()) {
			String modified = MODIFIED_FORMAT.format(entry.modified());
			context.drawUITextShadow(font, modified, columns.modifiedX, baseline, shadow);
			context.drawUIText(font, modified, columns.modifiedX, baseline, color);
			String size = readableSize(entry.size());
			context.drawUITextShadow(font, size, columns.sizeX, baseline, shadow);
			context.drawUIText(font, size, columns.sizeX, baseline, color);
		}
	}
	private GLFont rowFont(Entry entry) {
		GLFontSpec base = getGLTheme().labelStyle().font();
		boolean italic = entry.hidden() && Boolean.parseBoolean(
				getGLTheme().values().get("fileDialog.hiddenItalic", "true"));
		return getGLFont(italic ? new GLFontSpec(base.family(),
				base.style() | GLFont.ITALIC, base.size()) : base);
	}
	private Color rowColor(Entry entry) {
		Color normal = getGLTheme().labelStyle().textColor();
		if (!entry.hidden()) return normal;
		Color muted = new Color(normal.getRed(), normal.getGreen(), normal.getBlue(),
				Math.round(normal.getAlpha() * .65f));
		return getGLTheme().values().getColor("fileDialog.hiddenText", muted);
	}
	private final class Header extends GLContainer {
		@Override protected void record(GLContext2D context) {
			var theme = getGLTheme();
			context.fillAndDrawUIRoundRect(0, 0, getWidth(), getHeight(), 0, 1,
					theme.values().getColor("fileDialog.header.background",
							theme.buttonStyle().background()),
					theme.values().getColor("fileDialog.header.borderColor",
							theme.buttonStyle().borderColor()),
					com.vingame.glswing.rendering.GLStroke.WidthSpace.SCREEN);
		}
		@Override protected void resized(float width, float height) { layoutLabels(); }
		@Override protected void render(GLContext2D context) { layoutLabels(); super.render(context); }
		private void layoutLabels() {
			float contentX = Math.max(entryScroll.getGLStyle().padding().left(),
					entryScroll.getGLStyle().borderWidth())
					+ entryScroll.getGLStyle().viewportBorderWidth()
					+ entryList.getGLStyle().borderWidth();
			Columns c = columns(Math.max(1, entryList.getWidth()));
			nameHeader.setBounds(contentX + c.nameX, 3, c.nameWidth, 24);
			modifiedHeader.setBounds(contentX + c.modifiedX, 3, c.modifiedWidth, 24);
			sizeHeader.setBounds(contentX + c.sizeX, 3, c.sizeWidth, 24);
		}
	}
	private record Columns(float nameX, float nameWidth, float modifiedX,
			float modifiedWidth, float sizeX, float sizeWidth) { }
	private static Columns columns(float width) {
		float nameX = 35, modifiedX = Math.max(220, width - 245);
		float sizeX = Math.max(340, width - 78);
		return new Columns(nameX, Math.max(1, modifiedX - nameX - 10), modifiedX,
				Math.max(1, sizeX - modifiedX - 10), sizeX, Math.max(1, width - sizeX - 10));
	}
	private static List<Entry> entries(Path directory, GLFileFilter filter,
			boolean includeHidden, String query) {
		try (var stream = Files.list(directory)) {
			return stream.filter(path -> includeHidden || !isHidden(path))
					.filter(path -> query.isEmpty() || path.getFileName().toString()
							.toLowerCase(Locale.ROOT).contains(query))
					.filter(path -> filter == null || filter.accepts(path))
					.map(Entry::read)
					.sorted(Comparator.comparing((Entry entry) -> !entry.directory())
							.thenComparing(Entry::displayName, String.CASE_INSENSITIVE_ORDER)).toList();
		} catch (IOException failure) {
			throw new java.util.concurrent.CompletionException(failure);
		}
	}
	private static boolean isHidden(Path path) {
		try { return Files.isHidden(path); } catch (IOException failure) { return false; }
	}
	private static Path normalizeDirectory(Path path) {
		Path value = path == null ? userHome() : path;
		if (!Files.isDirectory(value)) throw new IllegalArgumentException("Directory required: " + value);
		return value.toAbsolutePath().normalize();
	}
	private static Path userHome() {
		return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
	}
	private static Path place(String child) {
		FileSystemView system = FileSystemView.getFileSystemView();
		java.io.File shell = switch (child) {
		case "Desktop" -> system.getHomeDirectory();
		case "Documents" -> system.getDefaultDirectory();
		default -> null;
		};
		if (shell != null && shell.isDirectory()) return shell.toPath().toAbsolutePath().normalize();
		Path candidate = userHome().resolve(child);
		return Files.isDirectory(candidate) ? candidate : userHome();
	}
	private String text(String suffix) {
		return getGLLocalizer().text("glswing.fileDialog." + suffix);
	}
	private static String readableSize(long bytes) {
		if (bytes < 1024) return bytes + " B";
		if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024d);
		if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / 1048576d);
		return String.format("%.1f GB", bytes / 1073741824d);
	}
	private static String fit(GLFont font, String text, float width) {
		if (width <= 0) return "";
		if (font.measureText(text) <= width) return text;
		String ellipsis = "…";
		if (font.measureText(ellipsis) > width) return "";
		int[] points = text.codePoints().toArray();
		int low = 0, high = points.length;
		while (low < high) {
			int middle = (low + high + 1) >>> 1;
			if (font.measureText(new String(points, 0, middle) + ellipsis) <= width) low = middle;
			else high = middle - 1;
		}
		return new String(points, 0, low) + ellipsis;
	}
	private static GLIcon icon(String name) { return GLIcon.fromSvgResource(ICON_ROOT + name); }
}
