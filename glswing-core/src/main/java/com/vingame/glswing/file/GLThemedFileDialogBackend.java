package com.vingame.glswing.file;

import java.awt.EventQueue;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.swing.filechooser.FileSystemView;
import javax.swing.Timer;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLDialog;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLIcon;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLCheckBox;
import com.vingame.glswing.ui.GLComboBox;
import com.vingame.glswing.ui.GLFontSpec;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLPopupMenu;
import com.vingame.glswing.ui.GLScrollPane;
import com.vingame.glswing.ui.GLTextEditor;

/** Built-in theme-aware filesystem fallback implemented entirely with GLSwing controls. */
public final class GLThemedFileDialogBackend implements GLFileDialogBackend {
	@Override public String id() { return "glswing-themed"; }
	@Override public boolean isNative() { return false; }
	@Override public CompletionStage<GLFileDialogResult> show(
			GLFileDialogRequest request, GLParent owner) {
		Session session = new Session(request, owner);
		session.show();
		return session.result;
	}

	private static final class Session {
		private static final String ICON_ROOT = "com/vingame/glswing/icons/";
		private static final GLIcon BACK_ICON = icon("filedialog_back.svg");
		private static final GLIcon FORWARD_ICON = icon("filedialog_forward.svg");
		private static final GLIcon UP_ICON = icon("filedialog_up.svg");
		private static final GLIcon HOME_ICON = icon("filedialog_home.svg");
		private static final GLIcon REFRESH_ICON = icon("filedialog_refresh.svg");
		private static final GLIcon FOLDER_ICON = icon("filedialog_folder.svg");
		private static final GLIcon FILE_ICON = icon("filedialog_file.svg");
		private static final GLIcon NEW_FOLDER_ICON = icon("filedialog_new_folder.svg");
		private static final DateTimeFormatter MODIFIED_FORMAT =
				DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm")
						.withZone(ZoneId.systemDefault());
		private final GLFileDialogRequest request;
		private final GLParent owner;
		private final CompletableFuture<GLFileDialogResult> result = new CompletableFuture<>();
		private final Pane pane = new Pane();
		private final GLButton back = new IconButton(BACK_ICON, "Back");
		private final GLButton forward = new IconButton(FORWARD_ICON, "Forward");
		private final GLButton up = new IconButton(UP_ICON, "Up one level");
		private final GLButton refreshButton = new IconButton(REFRESH_ICON, "Refresh");
		private final GLButton newFolder = new IconTextButton(NEW_FOLDER_ICON, "New folder");
		private final GLTextEditor addressEditor = new GLTextEditor();
		private final HintTextEditor search = new HintTextEditor();
		private final GLContainer commandBar = new ChromeContainer("fileDialog.commandBar");
		private final GLButton organize = new GLButton("Organize");
		private final GLPopupMenu organizeMenu = new GLPopupMenu();
		private final GLPopupMenu entryMenu = new GLPopupMenu();
		private Path contextEntry;
		private Timer inlineRenameTimer;
		private final GLContainer places = new ChromeContainer("fileDialog.sidebar");
		private final GLScrollPane placesScroll = new GLScrollPane(places);
		private final GLButton desktopPlace = new PlaceRow("Desktop", place("Desktop"), FOLDER_ICON);
		private final GLButton documentsPlace = new PlaceRow("Documents", place("Documents"), FILE_ICON);
		private final GLButton downloadsPlace = new PlaceRow("Downloads", place("Downloads"), FOLDER_ICON);
		private final GLButton picturesPlace = new PlaceRow("Pictures", place("Pictures"), FOLDER_ICON);
		private final GLButton musicPlace = new PlaceRow("Music", place("Music"), FOLDER_ICON);
		private final GLContainer listHeader = new FileListHeader();
		private final GLLabel nameHeader = new GLLabel("Name");
		private final GLLabel modifiedHeader = new GLLabel("Modified");
		private final GLLabel sizeHeader = new GLLabel("Size");
		private final GLContainer rows = new FileListContainer();
		private final GLScrollPane scroll = new GLScrollPane(rows);
		private final GLLabel fileNameLabel = new GLLabel("File name:");
		private final GLLabel filterLabel = new GLLabel("File type:");
		private final GLLabel status = new GLLabel("");
		private final GLCheckBox showHidden = new GLCheckBox("Show hidden files");
		private final com.vingame.glswing.ui.GLLocalizer localizer;
		private final GLTextEditor fileName = new GLTextEditor();
		private final GLComboBox<GLFileFilter> filters;
		private final GLButton approve;
		private final GLButton cancel = new GLButton("Cancel");
		private final GLButton selectFolder = new GLButton("Select current folder");
		private final List<Path> selected = new ArrayList<>();
		private Path directory;
		private final List<Path> history = new ArrayList<>();
		private int historyIndex;
		private GLDialog dialog;
		private long refreshGeneration;

		private Session(GLFileDialogRequest request, GLParent owner) {
			this.request = request;
			this.owner = owner;
			this.localizer = owner.getGLLocalizer();
			filters = new GLComboBox<>(request.filters());
			placesScroll.setHorizontalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.NEVER);
			scroll.setHorizontalScrollBarPolicy(GLScrollPane.ScrollBarPolicy.NEVER);
			filters.setItemTextProvider(GLFileFilter::description);
			filters.addActionListener(event -> refresh());
			approve = new GLButton(request.mode() == GLFileDialogMode.SAVE ? "Save" : "Open");
			directory = initialDirectory(request.initialDirectory());
			history.add(directory);
			fileName.setText(request.suggestedFileName() == null ? "" : request.suggestedFileName());
			up.addActionListener(event -> {
				Path parent = directory.getParent();
				if (parent != null) navigate(parent);
			});
			back.addActionListener(event -> moveHistory(-1));
			forward.addActionListener(event -> moveHistory(1));
			refreshButton.addActionListener(event -> refresh());
			newFolder.addActionListener(event -> createFolder());
			search.addTextChangeListener(event -> refresh());
			addressEditor.addActionListener(event -> commitAddress());
			addressEditor.addCancelListener(event -> {
				addressEditor.setText(directory.toString()); addressEditor.selectAll();
			});
			showHidden.addActionListener(event -> refresh());
			desktopPlace.addActionListener(event -> navigate(place("Desktop")));
			documentsPlace.addActionListener(event -> navigate(place("Documents")));
			downloadsPlace.addActionListener(event -> navigate(place("Downloads")));
			picturesPlace.addActionListener(event -> navigate(place("Pictures")));
			musicPlace.addActionListener(event -> navigate(place("Music")));
			approve.addActionListener(event -> approve());
			cancel.addActionListener(event -> finish(GLFileDialogResult.cancelled()));
			selectFolder.addActionListener(event -> finish(
					GLFileDialogResult.approved(List.of(directory))));
			String oneDriveValue = System.getenv("OneDrive");
			if (oneDriveValue != null && Files.isDirectory(Path.of(oneDriveValue))) {
				Path oneDrivePath = Path.of(oneDriveValue).toAbsolutePath().normalize();
				GLButton oneDrive = new PlaceRow("OneDrive", oneDrivePath, HOME_ICON);
				oneDrive.addActionListener(event -> navigate(oneDrivePath)); places.addGL(oneDrive);
			}
			places.addGL(desktopPlace); places.addGL(downloadsPlace);
			places.addGL(documentsPlace); places.addGL(picturesPlace); places.addGL(musicPlace);
			Path firstRoot = java.nio.file.FileSystems.getDefault().getRootDirectories()
					.iterator().hasNext() ? java.nio.file.FileSystems.getDefault()
							.getRootDirectories().iterator().next() : userHome();
			GLButton computer = new PlaceRow(text("thisPc"), firstRoot, HOME_ICON);
			computer.addActionListener(event -> navigate(firstRoot)); places.addGL(computer);
			for (Path root : java.nio.file.FileSystems.getDefault().getRootDirectories()) {
				GLButton drive = new PlaceRow(root.toString(), root, FOLDER_ICON);
				drive.setToolTipText(root.toString());
				drive.addActionListener(event -> navigate(root));
				places.addGL(drive);
			}
			commandBar.addGL(organize); commandBar.addGL(newFolder); commandBar.addGL(showHidden);
			listHeader.addGL(nameHeader); listHeader.addGL(modifiedHeader); listHeader.addGL(sizeHeader);
			pane.addGL(back); pane.addGL(forward); pane.addGL(up); pane.addGL(addressEditor);
			pane.addGL(search); pane.addGL(refreshButton);
			pane.addGL(commandBar);
			pane.addGL(placesScroll); pane.addGL(listHeader); pane.addGL(scroll);
			pane.addGL(fileNameLabel); pane.addGL(filterLabel);
			pane.addGL(status); pane.addGL(fileName);
			pane.addGL(filters); pane.addGL(approve);
			pane.addGL(cancel);
			selectFolder.setVisible(false);
			applyLocalization();
			organizeMenu.addItem(text("newFolder"), event -> createFolder());
			organizeMenu.addItem(text("refresh"), event -> refresh());
			organizeMenu.addItem(text("toggleHidden"), event -> {
				showHidden.setSelected(!showHidden.isSelected()); refresh();
			});
			organize.addActionListener(event -> {
				float[] bounds = organize.getLastRenderedScreenBounds();
				GLParent popupHost = pane.getGLParent();
				if (bounds != null && popupHost != null)
					organizeMenu.show(popupHost, bounds[0], bounds[1] + bounds[3]);
			});
			entryMenu.addItem(text("contextOpen"), event -> openContextEntry());
			entryMenu.addSeparator();
			entryMenu.addItem(text("contextCopy"), event -> copyContextEntry(false));
			entryMenu.addItem(text("contextCopyPath"), event -> copyContextEntry(true));
			entryMenu.addItem(text("contextRename"), event -> renameContextEntry());
			entryMenu.addSeparator();
			entryMenu.addItem(text("contextDelete"), event -> confirmDeleteContextEntry());
		}
		private void applyLocalization() {
			back.setToolTipText(text("back")); forward.setToolTipText(text("forward"));
			up.setToolTipText(text("up")); refreshButton.setToolTipText(text("refresh"));
			newFolder.setText(text("newFolder"));
			organize.setText(text("organize"));
			desktopPlace.setText(text("desktop"));
			documentsPlace.setText(text("documents")); downloadsPlace.setText(text("downloads"));
			picturesPlace.setText(text("pictures")); musicPlace.setText(text("music"));
			nameHeader.setText(text("name")); modifiedHeader.setText(text("modified"));
			sizeHeader.setText(text("size")); search.setHint(text("searchLocation"));
			fileNameLabel.setText(text("fileName")); filterLabel.setText(text("fileType"));
			showHidden.setText(text("showHidden")); cancel.setText(text("cancel"));
			selectFolder.setText(text("selectFolder"));
			approve.setText(text(request.mode() == GLFileDialogMode.SAVE ? "save"
					: request.selectionMode() == GLFileSelectionMode.DIRECTORIES
							? "selectFolder" : "open"));
		}
		private String text(String suffix, Object... parameters) {
			return localizer.text("glswing.fileDialog." + suffix, parameters);
		}
		private void show() {
			dialog = new GLDialog(request.title(), pane);
			dialog.setSize(980, 640);
			dialog.closedFuture().whenComplete((ignored, failure) -> {
				if (!result.isDone()) result.complete(GLFileDialogResult.cancelled());
			});
			dialog.show(owner);
			refresh();
		}
		private void refresh() {
			long generation = ++refreshGeneration;
			for (var place : places.getGLChildren()) place.invalidateRecording();
			if (!addressEditor.isFocused()) addressEditor.setText(directory.toString());
			up.setEnabled(directory.getParent() != null);
			back.setEnabled(historyIndex > 0);
			forward.setEnabled(historyIndex + 1 < history.size());
			status.setText(text("loading"));
			GLFileFilter filter = filters.getSelectedItem();
			boolean includeHidden = showHidden.isSelected();
			String query = search.getText().strip().toLowerCase(java.util.Locale.ROOT);
			CompletableFuture.supplyAsync(() -> list(directory, filter, includeHidden, query))
					.whenComplete((entries, failure) -> EventQueue.invokeLater(() -> {
						if (generation != refreshGeneration || result.isDone()) return;
						rows.removeAllGL();
						List<FileEntry> visible = failure == null ? entries : List.of();
						float rowWidth = Math.max(1, rows.getWidth());
						float y = 0;
						for (FileEntry entry : visible) {
							FileRow row = new FileRow(entry);
							row.setBounds(0, y, rowWidth, 34);
							rows.addGL(row); y += 34;
						}
						status.setText(failure == null
								? text(visible.size() == 1 ? "oneItem" : "items", visible.size())
								: text("cannotRead"));
						rows.setPreferredGLSize(1, Math.max(1, y));
					}));
		}
		private void selectEntry(Path entry) {
			cancelPendingInlineRename();
			if (Files.isDirectory(entry)) {
				selected.clear(); selected.add(entry);
				fileName.setText("");
			} else {
				if (!request.multiple()) selected.clear();
				if (!selected.contains(entry)) selected.add(entry);
				fileName.setText(entry.getFileName().toString());
			}
			for (var child : rows.getGLChildren()) child.invalidateRecording();
		}
		private void clearEntrySelection() {
			cancelPendingInlineRename();
			for (var child : rows.getGLChildren())
				if (child instanceof FileRow row && row.renaming)
					row.endRename();
			selected.clear();
			contextEntry = null;
			if (request.mode() == GLFileDialogMode.OPEN)
				fileName.setText("");
			for (var child : rows.getGLChildren()) child.invalidateRecording();
		}
		private void cancelPendingInlineRename() {
			if (inlineRenameTimer != null) inlineRenameTimer.stop();
			inlineRenameTimer = null;
		}
		private void scheduleInlineRename(Path entry) {
			cancelPendingInlineRename();
			Object configuredInterval = Toolkit.getDefaultToolkit()
					.getDesktopProperty("awt.multiClickInterval");
			int delay = (configuredInterval instanceof Integer value ? value : 500) + 80;
			inlineRenameTimer = new Timer(delay, event -> {
				inlineRenameTimer = null; beginInlineRename(entry);
			});
			inlineRenameTimer.setRepeats(false); inlineRenameTimer.start();
		}
		private void navigate(Path target) {
			if (target == null || !Files.isDirectory(target)) return;
			Path normalized = target.toAbsolutePath().normalize();
			if (normalized.equals(directory)) return;
			while (history.size() > historyIndex + 1) history.remove(history.size() - 1);
			history.add(normalized); historyIndex = history.size() - 1;
			directory = normalized;
			selected.clear(); fileName.setText(""); refresh();
		}
		private void moveHistory(int delta) {
			int target = historyIndex + delta;
			if (target < 0 || target >= history.size()) return;
			historyIndex = target; directory = history.get(target);
			selected.clear(); fileName.setText(""); refresh();
		}
		private void commitAddress() {
			try {
				Path target = Path.of(addressEditor.getText().strip());
				if (!Files.isDirectory(target)) {
					status.setText(text("invalidPath")); return;
				}
				navigate(target); addressEditor.setText(target.toAbsolutePath().normalize().toString());
			} catch (java.nio.file.InvalidPathException failure) {
				status.setText(text("invalidPath"));
			}
		}
		private void createFolder() {
			try {
				String base = text("newFolder");
				Path target = directory.resolve(base);
				for (int suffix = 2; Files.exists(target); suffix++)
					target = directory.resolve(base + " (" + suffix + ")");
				Files.createDirectory(target);
				refresh();
			} catch (java.io.IOException failure) {
				status.setText(text("cannotCreateFolder"));
			}
		}
		private void showEntryMenu(Path entry, GLMouseEvent event) {
			contextEntry = entry;
			selectEntry(entry);
			GLParent popupHost = pane.getGLParent();
			if (popupHost != null) entryMenu.show(popupHost,
					event.getScreenX(), event.getScreenY());
		}
		private void openContextEntry() {
			Path entry = contextEntry;
			if (entry == null) return;
			if (Files.isDirectory(entry)) navigate(entry); else approve();
		}
		private void copyContextEntry(boolean pathOnly) {
			Path entry = contextEntry;
			if (entry == null) return;
			if (pathOnly) Toolkit.getDefaultToolkit().getSystemClipboard()
					.setContents(new StringSelection(entry.toString()), null);
			else Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
					new FileListTransferable(List.of(entry.toFile())), null);
			status.setText(text(pathOnly ? "pathCopied" : "copied"));
		}
		private void renameContextEntry() {
			Path entry = contextEntry;
			if (entry == null || entry.getFileName() == null) return;
			EventQueue.invokeLater(() -> beginInlineRename(entry));
		}
		private void beginInlineRename(Path entry) {
			cancelPendingInlineRename();
			for (var child : rows.getGLChildren())
				if (child instanceof FileRow row && row.path.equals(entry)) {
					selectEntry(entry); row.beginRename(); return;
				}
		}
		private void commitInlineRename(FileRow row) {
			String name = row.renameEditor.getText().strip();
			Path entry = row.path;
			if (name.isEmpty()) { status.setText(text("renameInvalid")); return; }
			if (name.equals(entry.getFileName().toString())) { row.endRename(); return; }
			Path target = entry.resolveSibling(name).normalize();
			if (entry.getParent() == null || !target.getParent().equals(entry.getParent())
					|| Files.exists(target)) {
				status.setText(text("renameInvalid")); return;
			}
			if (!row.entry.directory() && !extension(entry).equalsIgnoreCase(extension(target))) {
				confirmExtensionChange(row, target); return;
			}
			performRename(row, target);
		}
		private void confirmExtensionChange(FileRow row, Path target) {
			ExtensionWarningPane content = new ExtensionWarningPane();
			GLDialog warning = new GLDialog(text("extensionTitle"), content);
			warning.setSize(500, 180);
			warning.closedFuture().thenRun(() -> EventQueue.invokeLater(() -> {
				if (row.renaming) pane.requestGLFocus(row.renameEditor);
			}));
			content.cancel.addActionListener(event -> warning.close());
			content.change.addActionListener(event -> {
				warning.close(); performRename(row, target);
			});
			warning.show(pane.getGLParent());
		}
		private void performRename(FileRow row, Path target) {
			Path source = row.path;
			row.endRename();
			CompletableFuture.runAsync(() -> {
				try { Files.move(source, target); }
				catch (IOException failure) {
					throw new java.util.concurrent.CompletionException(failure);
				}
			}).whenComplete((ignored, failure) -> EventQueue.invokeLater(() -> {
				status.setText(failure == null ? text("renamed") : text("renameFailed"));
				selected.clear(); if (failure == null) selected.add(target); refresh();
			}));
		}
		private static String extension(Path path) {
			String name = path.getFileName().toString();
			int dot = name.lastIndexOf('.');
			return dot <= 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1);
		}
		private void confirmDeleteContextEntry() {
			Path entry = contextEntry;
			if (entry == null) return;
			DeletePane content = new DeletePane(entry);
			GLDialog deleteDialog = new GLDialog(text("deleteTitle"), content);
			deleteDialog.setSize(470, 170);
			content.cancel.addActionListener(event -> deleteDialog.close());
			content.delete.addActionListener(event -> {
				deleteDialog.close();
				CompletableFuture.supplyAsync(() -> Desktop.isDesktopSupported()
						&& Desktop.getDesktop().moveToTrash(entry.toFile()))
						.whenComplete((deleted, failure) -> EventQueue.invokeLater(() -> {
							status.setText(failure == null && Boolean.TRUE.equals(deleted)
									? text("movedToTrash") : text("deleteFailed"));
							refresh();
						}));
			});
			deleteDialog.show(pane.getGLParent());
		}
		private void approve() {
			if (request.mode() == GLFileDialogMode.OPEN && request.multiple()
					&& !selected.isEmpty()) { finish(GLFileDialogResult.approved(selected)); return; }
			if (request.mode() == GLFileDialogMode.OPEN && selected.size() == 1
					&& Files.isDirectory(selected.get(0))
					&& request.selectionMode() != GLFileSelectionMode.FILES) {
				finish(GLFileDialogResult.approved(List.of(selected.get(0)))); return;
			}
			String name = fileName.getText().strip();
			if (name.isEmpty()) {
				if (request.selectionMode() != GLFileSelectionMode.FILES)
					finish(GLFileDialogResult.approved(List.of(directory)));
				return;
			}
			Path chosen = directory.resolve(name).normalize();
			if (request.mode() == GLFileDialogMode.SAVE) chosen = appendExtension(chosen,
					filters.getSelectedItem());
			if (request.mode() == GLFileDialogMode.OPEN && !Files.exists(chosen)) return;
			if (Files.isDirectory(chosen)) { directory = chosen; fileName.setText(""); refresh(); return; }
			if (request.mode() == GLFileDialogMode.SAVE && request.confirmOverwrite()
					&& Files.exists(chosen)) { confirmOverwrite(chosen); return; }
			finish(GLFileDialogResult.approved(List.of(chosen)));
		}
		private void confirmOverwrite(Path chosen) {
			ConfirmPane content = new ConfirmPane(chosen);
			GLDialog confirmation = new GLDialog(text("replaceTitle"), content);
			confirmation.setSize(430, 160);
			content.replace.addActionListener(event -> {
				confirmation.close();
				finish(GLFileDialogResult.approved(List.of(chosen)));
			});
			content.cancel.addActionListener(event -> confirmation.close());
			confirmation.show(owner);
		}
		private void finish(GLFileDialogResult value) {
			if (!result.complete(value)) return;
			if (dialog != null) dialog.close();
		}
		private static List<FileEntry> list(Path directory, GLFileFilter filter,
				boolean includeHidden, String query) {
			try (var stream = Files.list(directory)) {
				return stream.filter(path -> includeHidden || !isHidden(path))
						.filter(path -> query.isEmpty() || path.getFileName().toString()
								.toLowerCase(java.util.Locale.ROOT).contains(query))
						.filter(path -> filter == null || filter.accepts(path))
						.map(FileEntry::read)
						.sorted(Comparator.comparing((FileEntry entry) -> !entry.directory())
								.thenComparing(entry -> entry.path().getFileName().toString(),
										String.CASE_INSENSITIVE_ORDER)).toList();
			} catch (java.io.IOException failure) { return List.of(); }
		}
		private static boolean isHidden(Path path) {
			try { return Files.isHidden(path); }
			catch (java.io.IOException failure) { return false; }
		}
		private record FileEntry(Path path, boolean directory, boolean hidden,
				long size, Instant modified) {
			private static FileEntry read(Path path) {
				boolean directory = Files.isDirectory(path);
				boolean hidden = isHidden(path);
				long size = 0; Instant modified = Instant.EPOCH;
				try {
					if (!directory) size = Files.size(path);
					modified = Files.getLastModifiedTime(path).toInstant();
				} catch (java.io.IOException ignored) { }
				return new FileEntry(path, directory, hidden, size, modified);
			}
		}
		private static Path initialDirectory(Path requested) {
			Path value = requested == null ? Path.of(System.getProperty("user.home")) : requested;
			if (!Files.isDirectory(value) && value.getParent() != null) value = value.getParent();
			return value.toAbsolutePath().normalize();
		}
		private static Path userHome() {
			return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
		}
		private static Path place(String child) {
			FileSystemView system = FileSystemView.getFileSystemView();
			java.io.File shellFolder = switch (child) {
			case "Desktop" -> system.getHomeDirectory();
			case "Documents" -> system.getDefaultDirectory();
			default -> null;
			};
			if (shellFolder != null && shellFolder.isDirectory())
				return shellFolder.toPath().toAbsolutePath().normalize();
			Path candidate = userHome().resolve(child);
			return Files.isDirectory(candidate) ? candidate : userHome();
		}
		private static String readableSize(long bytes) {
			if (bytes < 1024) return bytes + " B";
			if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024d);
			if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / 1048576d);
			return String.format("%.1f GB", bytes / 1073741824d);
		}
		private static FileColumns fileColumns(float width) {
			float nameX = 35;
			float modifiedX = Math.max(220, width - 245);
			float sizeX = Math.max(340, width - 78);
			return new FileColumns(nameX,
					Math.max(1, modifiedX - nameX - 10), modifiedX,
					Math.max(1, sizeX - modifiedX - 10), sizeX,
					Math.max(1, width - sizeX - 10));
		}
		private record FileColumns(float nameX, float nameWidth,
				float modifiedX, float modifiedWidth,
				float sizeX, float sizeWidth) { }
		private static String fitText(GLFont font, String text, float width) {
			if (width <= 0) return "";
			if (font.measureText(text) <= width) return text;
			String ellipsis = "…";
			if (font.measureText(ellipsis) > width) return "";
			int[] points = text.codePoints().toArray();
			int low = 0, high = points.length;
			while (low < high) {
				int middle = (low + high + 1) >>> 1;
				String candidate = new String(points, 0, middle) + ellipsis;
				if (font.measureText(candidate) <= width) low = middle;
				else high = middle - 1;
			}
			return new String(points, 0, low) + ellipsis;
		}
		private static GLIcon icon(String name) { return GLIcon.fromSvgResource(ICON_ROOT + name); }
		private static Path appendExtension(Path path, GLFileFilter filter) {
			if (filter == null || filter.extensions().isEmpty()) return path;
			String name = path.getFileName().toString();
			if (name.lastIndexOf('.') >= 0) return path;
			return path.resolveSibling(name + "." + filter.extensions().get(0));
		}
		private final class Pane extends GLContainer {
			@Override protected void record(GLContext2D context) {
				var theme = getGLTheme();
				java.awt.Color background = theme.values().getColor("fileDialog.background",
						theme.textEditorStyle().backgroundColor());
				context.fillUIRoundRect(0, 0, getWidth(), getHeight(), 0, background);
				float footerY = Math.max(0, getHeight() - 108);
				context.fillUIRoundRect(0, footerY, getWidth(), getHeight() - footerY, 0,
						theme.values().getColor("fileDialog.footer.background",
							theme.buttonStyle().background()));
			}
			@Override protected void resized(float width, float height) {
				float margin = 12, toolbarY = 10, toolbarH = 34;
				back.setBounds(margin, toolbarY, 36, toolbarH);
				forward.setBounds(margin + 42, toolbarY, 36, toolbarH);
				up.setBounds(margin + 84, toolbarY, 36, toolbarH);
				float searchW = 235;
				search.setBounds(width - margin - searchW, toolbarY, searchW, toolbarH);
				refreshButton.setBounds(width - margin - searchW - 42, toolbarY, 36, toolbarH);
				addressEditor.setBounds(margin + 128, toolbarY,
						Math.max(1, width - margin * 2 - searchW - 176), toolbarH);
				commandBar.setBounds(0, 54, width, 42);

				float contentY = 96, bottomArea = 108;
				float contentH = Math.max(1, height - contentY - bottomArea);
				float sidebarW = 205;
				placesScroll.setBounds(0, contentY, sidebarW, contentH);
				float placesHeight = places.getGLChildren().size() * 34 + 18;
				places.setPreferredGLSize(1, placesHeight);
				float listX = sidebarW + 2;
				float listW = Math.max(1, width - listX);
				listHeader.setBounds(listX, contentY, listW, 30);
				scroll.setBounds(listX, contentY + 30, listW, Math.max(1, contentH - 30));

				float formY = height - 92, labelW = 92, actionW = 112, rightW = 250;
				fileNameLabel.setBounds(margin + 2, formY + 5, labelW, 28);
				fileName.setBounds(margin + labelW, formY,
						Math.max(1, width - margin * 3 - labelW - rightW), 32);
				filterLabel.setVisible(false);
				filters.setBounds(width - margin - rightW, formY, rightW, 32);
				status.setBounds(margin + labelW, formY + 42,
						Math.max(1, width - margin * 3 - labelW - rightW), 26);
				approve.setBounds(width - margin - actionW * 2 - 10,
						formY + 42, actionW, 34);
				cancel.setBounds(width - margin - actionW, formY + 42, actionW, 34);
			}
		}
		private final class FileRow extends GLButton {
			private final FileEntry entry;
			private final Path path;
			private final GLTextEditor renameEditor = new InlineRenameEditor();
			private boolean renaming;
			private boolean renameClosedByFocusLoss;
			private boolean suppressRenameClick;
			private FileRow(FileEntry entry) {
				super(""); this.entry = entry; this.path = entry.path();
				renameEditor.addActionListener(event -> commitInlineRename(this));
				renameEditor.addCancelListener(event -> endRename());
			}
			private void beginRename() {
				if (renaming) return;
				renaming = true; renameEditor.setText(path.getFileName().toString());
				addGL(renameEditor); resized(getWidth(), getHeight());
				invalidateRecording(); requestGLFocus(renameEditor); renameEditor.selectAll();
			}
			private void endRename() {
				if (!renaming) return;
				renaming = false; removeGL(renameEditor); invalidateRecording();
			}
			private String displayName() {
				return path.getFileName() == null
						? path.toString() : path.getFileName().toString();
			}
			private boolean isNameHit(GLMouseEvent event) {
				if (!event.hasLocalPosition()) return false;
				var font = rowFont();
				FileColumns columns = fileColumns(getWidth());
				float columnRight = columns.nameX() + columns.nameWidth();
				float textRight = columns.nameX() + font.measureText(displayName()) + 5;
				return event.getLocalX() >= 32
						&& event.getLocalX() <= Math.min(columnRight, textRight);
			}
			private GLFont rowFont() {
				GLFontSpec base = getGLTheme().labelStyle().font();
				boolean italic = entry.hidden() && Boolean.parseBoolean(
						getGLTheme().values().get("fileDialog.hiddenItalic", "true"));
				return getGLFont(italic
						? new GLFontSpec(base.family(), base.style() | GLFont.ITALIC, base.size())
						: base);
			}
			private java.awt.Color rowTextColor() {
				java.awt.Color normal = getGLTheme().labelStyle().textColor();
				if (!entry.hidden()) return normal;
				java.awt.Color muted = new java.awt.Color(normal.getRed(), normal.getGreen(),
						normal.getBlue(), Math.round(normal.getAlpha() * 0.65f));
				return getGLTheme().values().getColor("fileDialog.hiddenText", muted);
			}
			@Override protected void resized(float width, float height) {
				if (renaming) {
					FileColumns columns = fileColumns(width);
					renameEditor.setBounds(32, 3,
							Math.max(1, columns.modifiedX() - 42),
							Math.max(1, height - 6));
				}
			}
			@Override protected void record(GLContext2D context) {
				var theme = getGLTheme();
				var style = theme.buttonStyle();
				boolean isSelected = selected.contains(path);
				java.awt.Color rowColor = isSelected
						? theme.values().getColor("fileDialog.selectionColor", style.focusColor())
						: getControlState() == com.vingame.glswing.ui.GLControlState.NORMAL
								? theme.values().getColor("fileDialog.row.background",
										theme.textEditorStyle().backgroundColor())
								: style.background(getControlState());
				context.fillUIRoundRect(0, 0, getWidth(), getHeight(), 0, rowColor);
				var font = rowFont();
				java.awt.Color color = rowTextColor();
				context.drawUIIcon(entry.directory() ? FOLDER_ICON : FILE_ICON,
						9, 8, 18, 18, color);
				var textShadow = theme.textShadow("fileDialog");
				float baseline = 8 + font.getAscent();
				if (!renaming) {
					String name = displayName();
					context.drawUITextShadow(font, name, 35, baseline, textShadow);
					context.drawUIText(font, name, 35, baseline, color);
				}
				if (!entry.directory()) {
					FileColumns columns = fileColumns(getWidth());
					String modified = MODIFIED_FORMAT.format(entry.modified());
					float modifiedX = columns.modifiedX();
					context.drawUITextShadow(font, modified, modifiedX, baseline, textShadow);
					context.drawUIText(font, modified, modifiedX, baseline, color);
					String size = readableSize(entry.size());
					float sizeX = columns.sizeX();
					context.drawUITextShadow(font, size, sizeX, baseline, textShadow);
					context.drawUIText(font, size, sizeX, baseline, color);
				}
			}
			@Override protected void controlMousePressed(GLMouseEvent event) {
				if (event.getButton() == MouseEvent.BUTTON1 && renameClosedByFocusLoss) {
					suppressRenameClick = true;
					renameClosedByFocusLoss = false;
				}
			}
			@Override protected void controlMouseClicked(GLMouseEvent event) {
				if (event.getButton() == MouseEvent.BUTTON3) {
					showEntryMenu(path, event); return;
				}
				if (event.getButton() != MouseEvent.BUTTON1) return;
				if (suppressRenameClick) {
					suppressRenameClick = false;
					event.consume(); return;
				}
				if (event.getClickCount() >= 2) {
					cancelPendingInlineRename();
					if (entry.directory()) navigate(path);
					else approve();
				} else if (selected.contains(path) && isNameHit(event)) {
					scheduleInlineRename(path);
				} else {
					selectEntry(path);
				}
			}
			private final class InlineRenameEditor extends GLTextEditor {
				@Override protected void controlFocusChanged(boolean focused) {
					if (!focused && renaming) {
						renameClosedByFocusLoss = true;
						EventQueue.invokeLater(() -> renameClosedByFocusLoss = false);
					}
					super.controlFocusChanged(focused);
				}
			}
			@Override protected void controlStateChanged(
					com.vingame.glswing.ui.GLControlState oldState,
					com.vingame.glswing.ui.GLControlState newState) {
				super.controlStateChanged(oldState, newState); invalidateRecording();
			}
		}
		private final class FileListContainer extends GLContainer {
			@Override protected void record(GLContext2D context) {
				context.addUIHitArea(0, 0, getWidth(), getHeight());
			}
			@Override protected void resized(float width, float height) {
				float rowWidth = Math.max(1, width);
				for (var child : getGLChildren())
					child.setBounds(0, child.getY(), rowWidth, child.getHeight());
			}
			@Override public void mouseClicked(GLMouseEvent event) {
				if (event.getButton() != MouseEvent.BUTTON1) return;
				clearEntrySelection(); event.consume();
			}
		}
		private final class PlaceRow extends GLButton {
			private final Path target;
			private final GLIcon icon;
			private PlaceRow(String text, Path target, GLIcon icon) {
				super(text); this.target = target; this.icon = icon;
			}
			@Override protected void record(GLContext2D context) {
				var theme = getGLTheme();
				var style = theme.buttonStyle();
				boolean active = directory.equals(target);
				java.awt.Color background = active
						? theme.values().getColor("fileDialog.sidebar.selectionColor",
								style.focusColor())
						: getControlState() == com.vingame.glswing.ui.GLControlState.NORMAL
								? theme.values().getColor("fileDialog.sidebar.background",
										theme.textEditorStyle().backgroundColor())
								: style.background(getControlState());
				context.fillUIRoundRect(0, 0, getWidth(), getHeight(), 0, background);
				var font = getGLFont(theme.labelStyle().font());
				java.awt.Color color = theme.labelStyle().textColor();
				context.drawUIIcon(icon, 9, (getHeight() - 18) * .5f, 18, 18, color);
				String renderedText = fitText(font, getText(), Math.max(0, getWidth() - 44));
				float baseline = (getHeight() - font.getLineHeight()) * .5f + font.getAscent();
				context.drawUITextShadow(font, renderedText, 36, baseline,
						theme.textShadow("fileDialog"));
				context.drawUIText(font, renderedText, 36, baseline, color);
			}
			@Override protected void controlStateChanged(
					com.vingame.glswing.ui.GLControlState oldState,
					com.vingame.glswing.ui.GLControlState newState) {
				super.controlStateChanged(oldState, newState); invalidateRecording();
			}
		}
		private static class IconButton extends GLButton {
			private final GLIcon icon;
			private IconButton(GLIcon icon, String toolTip) {
				super(""); this.icon = icon; setToolTipText(toolTip);
			}
			@Override protected void record(GLContext2D context) {
				super.record(context);
				java.awt.Color tint = getGLTheme().buttonStyle().text(getControlState());
				float size = Math.min(18, Math.min(getWidth(), getHeight()) - 8);
				context.drawUIIcon(icon, (getWidth() - size) * .5f,
						(getHeight() - size) * .5f, size, size, tint);
			}
		}
		private static final class HintTextEditor extends GLTextEditor {
			private String hint = "";
			private void setHint(String value) { hint = value; invalidateRecording(); }
			@Override protected void record(GLContext2D context) {
				super.record(context);
				if (!getText().isEmpty() || hint.isEmpty() || isFocused()) return;
				var style = getGLStyle();
				var font = getGLFont(style.font());
				java.awt.Color base = style.disabledTextColor();
				float x = style.padding().left();
				float baseline = (getHeight() - font.getLineHeight()) * .5f + font.getAscent();
				context.drawUITextShadow(font, hint, x, baseline,
						getGLTheme().textShadow("textEditor"));
				context.drawUIText(font, hint, x, baseline, base);
			}
		}
		private static final class IconTextButton extends GLButton {
			private final GLIcon icon;
			private IconTextButton(GLIcon icon, String text) { super(text); this.icon = icon; }
			@Override protected void record(GLContext2D context) {
				super.record(context);
				java.awt.Color tint = getGLTheme().buttonStyle().text(getControlState());
				context.drawUIIcon(icon, 9, (getHeight() - 16) * .5f, 16, 16, tint);
			}
		}
		private final class FileListHeader extends ChromeContainer {
			private FileListHeader() { super("fileDialog.header"); }
			@Override protected void render(GLContext2D context) {
				layoutColumns();
				super.render(context);
			}
			@Override protected void resized(float width, float height) {
				layoutColumns();
			}
			private void layoutColumns() {
				var style = scroll.getGLStyle();
				float contentX = Math.max(style.padding().left(), style.borderWidth());
				float contentWidth = rows.getWidth() > 0 ? rows.getWidth()
						: Math.max(1, getWidth() - contentX
								- Math.max(style.padding().right(), style.borderWidth()));
				FileColumns columns = fileColumns(contentWidth);
				nameHeader.setBounds(contentX + columns.nameX(), 3,
						columns.nameWidth(), 24);
				modifiedHeader.setBounds(contentX + columns.modifiedX(), 3,
						columns.modifiedWidth(), 24);
				sizeHeader.setBounds(contentX + columns.sizeX(), 3,
						columns.sizeWidth(), 24);
			}
		}
		private static class ChromeContainer extends GLContainer {
			private final String prefix;
			private ChromeContainer(String prefix) { this.prefix = prefix; }
			@Override protected void record(GLContext2D context) {
				var theme = getGLTheme();
				java.awt.Color background = theme.values().getColor(prefix + ".background",
						theme.buttonStyle().background());
				java.awt.Color border = theme.values().getColor(prefix + ".borderColor",
						theme.buttonStyle().borderColor());
				float radius = prefix.endsWith("sidebar") || prefix.endsWith("header")
						|| prefix.endsWith("commandBar") ? 0
						: theme.values().getFloat(prefix + ".cornerRadius",
								theme.buttonStyle().cornerRadius());
				context.fillAndDrawUIRoundRect(0, 0, getWidth(), getHeight(), radius, 1,
						background, border,
						com.vingame.glswing.rendering.GLStroke.WidthSpace.SCREEN);
			}
			@Override protected void resized(float width, float height) {
				if (getGLChildren().isEmpty()) return;
				if (prefix.endsWith("sidebar")) {
					float y = 12;
					for (var component : getGLChildren()) {
						component.setBounds(10, y, Math.max(1, width - 20), 28); y += 34;
					}
				} else if (prefix.endsWith("commandBar")) {
					getGLChildren().get(0).setBounds(12, 6, 105, 30);
					getGLChildren().get(1).setBounds(124, 6, 132, 30);
					var hiddenFiles = getGLChildren().get(2);
					float right = 12;
					float available = Math.max(1, width - 265 - right);
					float checkBoxWidth = Math.min(available,
							hiddenFiles.getPreferredGLSize().width());
					hiddenFiles.setBounds(width - right - checkBoxWidth,
							(height - 25) * 0.5f, checkBoxWidth, 25);
				}
			}
		}
		private static final class FileListTransferable implements Transferable {
			private static final DataFlavor[] FLAVORS = {
					DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor };
			private final List<java.io.File> files;
			private FileListTransferable(List<java.io.File> files) { this.files = List.copyOf(files); }
			@Override public DataFlavor[] getTransferDataFlavors() { return FLAVORS.clone(); }
			@Override public boolean isDataFlavorSupported(DataFlavor flavor) {
				return DataFlavor.javaFileListFlavor.equals(flavor)
						|| DataFlavor.stringFlavor.equals(flavor);
			}
			@Override public Object getTransferData(DataFlavor flavor)
					throws UnsupportedFlavorException {
				if (DataFlavor.javaFileListFlavor.equals(flavor)) return files;
				if (DataFlavor.stringFlavor.equals(flavor)) return files.stream()
						.map(java.io.File::getAbsolutePath).collect(java.util.stream.Collectors.joining("\n"));
				throw new UnsupportedFlavorException(flavor);
			}
		}
		private final class ExtensionWarningPane extends GLContainer {
			private final GLLabel message = new GLLabel(text("extensionMessage"));
			private final GLButton change = new GLButton(text("extensionChange"));
			private final GLButton cancel = new GLButton(text("cancel"));
			private ExtensionWarningPane() {
				addGL(message); addGL(change); addGL(cancel);
			}
			@Override protected void record(GLContext2D context) { }
			@Override protected void resized(float width, float height) {
				message.setBounds(18, 22, Math.max(1, width - 36), 46);
				cancel.setBounds(width - 202, height - 47, 86, 30);
				change.setBounds(width - 106, height - 47, 88, 30);
			}
		}
		private final class DeletePane extends GLContainer {
			private final GLLabel message;
			private final GLButton delete = new GLButton(text("contextDelete"));
			private final GLButton cancel = new GLButton(text("cancel"));
			private DeletePane(Path path) {
				message = new GLLabel(text("deleteMessage", path.getFileName()));
				addGL(message); addGL(delete); addGL(cancel);
			}
			@Override protected void record(GLContext2D context) { }
			@Override protected void resized(float width, float height) {
				message.setBounds(18, 24, Math.max(1, width - 36), 35);
				cancel.setBounds(width - 202, height - 47, 86, 30);
				delete.setBounds(width - 106, height - 47, 88, 30);
			}
		}
		private final class ConfirmPane extends GLContainer {
			private final GLLabel message;
			private final GLButton replace = new GLButton(text("replace"));
			private final GLButton cancel = new GLButton(text("cancel"));
			private ConfirmPane(Path path) {
				message = new GLLabel(text("replaceMessage", path.getFileName()));
				addGL(message); addGL(replace); addGL(cancel);
			}
			@Override protected void record(GLContext2D context) { }
			@Override protected void resized(float width, float height) {
				message.setBounds(18, 20, Math.max(1, width - 36), 30);
				cancel.setBounds(width - 188, height - 48, 80, 30);
				replace.setBounds(width - 98, height - 48, 80, 30);
			}
		}
	}
}
