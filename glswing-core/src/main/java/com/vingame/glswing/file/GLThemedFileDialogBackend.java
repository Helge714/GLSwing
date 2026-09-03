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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.swing.Timer;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLDialog;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLIcon;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLCheckBox;
import com.vingame.glswing.ui.GLComboBox;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLList;
import com.vingame.glswing.ui.GLPopupMenu;
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
		private static final GLIcon REFRESH_ICON = icon("filedialog_refresh.svg");
		private static final GLIcon NEW_FOLDER_ICON = icon("filedialog_new_folder.svg");
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
		private final GLFileBrowser browser;
		private final GLList<GLFileBrowser.Entry> fileList;
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
		private boolean preservingInlineEditor;

		private Session(GLFileDialogRequest request, GLParent owner) {
			this.request = request;
			this.owner = owner;
			this.localizer = owner.getGLLocalizer();
			filters = new GLComboBox<>(request.filters());
			directory = initialDirectory(request.initialDirectory());
			browser = new GLFileBrowser(directory);
			fileList = browser.getEntryList();
			browser.setMultipleSelection(request.multiple());
			browser.addDirectoryListener((source, value) -> {
				if (!value.equals(directory)) navigate(value);
			});
			browser.addActivationListener((source, file) -> {
				cancelPendingInlineRename(); approve();
			});
			fileList.setCellEditor(new FileEntryCellEditor());
			fileList.getSelectionModel().addSelectionListener(
					(source, oldSelection, newSelection) -> selectionChanged());
			fileList.addItemMouseListener(this::fileItemClicked);
			filters.setItemTextProvider(GLFileFilter::description);
			filters.addActionListener(event -> refresh());
			approve = new GLButton(request.mode() == GLFileDialogMode.SAVE ? "Save" : "Open");
			history.add(directory);
			fileName.setText(request.suggestedFileName() == null ? "" : request.suggestedFileName());
			up.addActionListener(event -> {
				Path parent = directory.getParent();
				if (parent != null) navigate(parent);
			});
			back.addActionListener(event -> moveHistory(-1));
			forward.addActionListener(event -> moveHistory(1));
			refreshButton.addActionListener(event -> refreshCurrentView());
			newFolder.addActionListener(event -> createFolder());
			search.addTextChangeListener(event -> refresh());
			addressEditor.addActionListener(event -> commitAddress());
			addressEditor.addCancelListener(event -> {
				addressEditor.setText(directory.toString()); addressEditor.selectAll();
			});
			showHidden.addActionListener(event -> hiddenVisibilityChanged());
			approve.addActionListener(event -> approve());
			cancel.addActionListener(event -> finish(GLFileDialogResult.cancelled()));
			selectFolder.addActionListener(event -> finish(
					GLFileDialogResult.approved(List.of(directory))));
			commandBar.addGL(organize); commandBar.addGL(newFolder); commandBar.addGL(showHidden);
			pane.addGL(back); pane.addGL(forward); pane.addGL(up); pane.addGL(addressEditor);
			pane.addGL(search); pane.addGL(refreshButton);
			pane.addGL(commandBar);
			pane.addGL(browser);
			pane.addGL(fileNameLabel); pane.addGL(filterLabel);
			pane.addGL(status); pane.addGL(fileName);
			pane.addGL(filters); pane.addGL(approve);
			pane.addGL(cancel);
			selectFolder.setVisible(false);
			applyLocalization();
			organizeMenu.addItem(text("newFolder"), event -> createFolder());
			organizeMenu.addItem(text("refresh"), event -> refreshCurrentView());
			organizeMenu.addItem(text("toggleHidden"), event -> {
				showHidden.setSelected(!showHidden.isSelected());
				hiddenVisibilityChanged();
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
			search.setHint(text("searchLocation"));
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
		private void refresh() { refresh(null); }
		private void refreshCurrentView() {
			browser.refreshDirectoryTree(directory);
			refresh();
		}
		private void hiddenVisibilityChanged() {
			browser.setShowHidden(showHidden.isSelected());
			refresh();
		}
		private void refresh(Path selectAfterRefresh) {
			long generation = ++refreshGeneration;
			if (!addressEditor.isFocused()) addressEditor.setText(directory.toString());
			up.setEnabled(directory.getParent() != null);
			back.setEnabled(historyIndex > 0);
			forward.setEnabled(historyIndex + 1 < history.size());
			status.setText(text("loading"));
			browser.setFileFilter(filters.getSelectedItem());
			browser.setShowHidden(showHidden.isSelected());
			browser.setSearchQuery(search.getText());
			browser.refresh(selectAfterRefresh)
					.whenComplete((entries, failure) -> EventQueue.invokeLater(() -> {
						if (generation != refreshGeneration || result.isDone()) return;
						status.setText(failure == null
								? text(entries.size() == 1 ? "oneItem" : "items", entries.size())
								: text("cannotRead"));
					}));
		}
		private void selectEntry(Path entry) {
			cancelPendingInlineRename();
			int index = browser.indexOf(entry);
			if (index >= 0) fileList.setSelectedIndex(index);
		}
		private void clearEntrySelection() {
			cancelPendingInlineRename();
			fileList.cancelEditing();
			fileList.getSelectionModel().clearSelection();
			contextEntry = null;
		}
		private void selectionChanged() {
			selected.clear();
			for (GLFileBrowser.Entry entry : fileList.getSelectedValues())
				selected.add(entry.path());
			GLFileBrowser.Entry primary = fileList.getSelectedValue();
			if (primary == null || primary.directory()) {
				if (request.mode() == GLFileDialogMode.OPEN) fileName.setText("");
			} else fileName.setText(primary.path().getFileName().toString());
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
			browser.setDirectory(directory, false);
			clearEntrySelection(); fileName.setText(""); refresh();
		}
		private void moveHistory(int delta) {
			int target = historyIndex + delta;
			if (target < 0 || target >= history.size()) return;
			historyIndex = target; directory = history.get(target);
			browser.setDirectory(directory, false);
			clearEntrySelection(); fileName.setText(""); refresh();
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
				browser.refreshDirectoryTree(directory);
				refresh();
			} catch (java.io.IOException failure) {
				status.setText(text("cannotCreateFolder"));
			}
		}
		private void showEntryMenu(Path entry, GLMouseEvent event) {
			contextEntry = entry;
			if (!selected.contains(entry)) selectEntry(entry);
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
			int index = browser.indexOf(entry);
			if (index < 0) return;
			fileList.setSelectedIndex(index);
			fileList.startEditingAt(index);
		}
		private boolean commitInlineRename(GLFileBrowser.Entry value, GLTextEditor editor) {
			String name = editor.getText().strip();
			Path entry = value.path();
			if (name.isEmpty()) { status.setText(text("renameInvalid")); return false; }
			if (name.equals(entry.getFileName().toString())) return true;
			Path target = entry.resolveSibling(name).normalize();
			if (entry.getParent() == null || !target.getParent().equals(entry.getParent())
					|| Files.exists(target)) {
				status.setText(text("renameInvalid")); return false;
			}
			if (!value.directory() && !extension(entry).equalsIgnoreCase(extension(target))) {
				confirmExtensionChange(value, editor, target); return false;
			}
			performRename(value, target);
			return true;
		}
		private void confirmExtensionChange(GLFileBrowser.Entry entry, GLTextEditor editor,
				Path target) {
			preservingInlineEditor = true;
			ExtensionWarningPane content = new ExtensionWarningPane();
			GLDialog warning = new GLDialog(text("extensionTitle"), content);
			warning.setSize(500, 180);
			warning.closedFuture().thenRun(() -> EventQueue.invokeLater(() -> {
				preservingInlineEditor = false;
				if (fileList.getEditingComponent() == editor)
					pane.requestGLFocus(editor);
			}));
			content.cancel.addActionListener(event -> warning.close());
			content.change.addActionListener(event -> {
				warning.close(); fileList.cancelEditing(); performRename(entry, target);
			});
			warning.show(pane.getGLParent());
		}
		private void performRename(GLFileBrowser.Entry entry, Path target) {
			Path source = entry.path();
			CompletableFuture.runAsync(() -> {
				try { Files.move(source, target); }
				catch (IOException failure) {
					throw new java.util.concurrent.CompletionException(failure);
				}
			}).whenComplete((ignored, failure) -> EventQueue.invokeLater(() -> {
				status.setText(failure == null ? text("renamed") : text("renameFailed"));
				if (failure == null) browser.refreshDirectoryTree(directory);
				refresh(failure == null ? target : null);
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
							if (failure == null && Boolean.TRUE.equals(deleted))
								browser.refreshDirectoryTree(directory);
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
		private static Path initialDirectory(Path requested) {
			Path value = requested == null ? Path.of(System.getProperty("user.home")) : requested;
			if (!Files.isDirectory(value) && value.getParent() != null) value = value.getParent();
			return value.toAbsolutePath().normalize();
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
				browser.setBounds(0, contentY, width, contentH);

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
		private void fileItemClicked(GLList.ItemEvent<GLFileBrowser.Entry> event) {
			GLMouseEvent mouse = event.mouseEvent();
			GLFileBrowser.Entry entry = event.item();
			if (mouse.getButton() == MouseEvent.BUTTON3) {
				showEntryMenu(entry.path(), mouse); return;
			}
			if (mouse.getButton() != MouseEvent.BUTTON1) return;
			if (mouse.completesDoubleClick()) {
				cancelPendingInlineRename(); return;
			}
			if (fileList.isEditing()) {
				fileList.cancelEditing(); return;
			}
			boolean modifyingSelection = fileList.getGLInputMap().hasModifiers(
					GLInputActions.LIST_TOGGLE_SELECTION, mouse.getModifiersEx())
					|| fileList.getGLInputMap().hasModifiers(
							GLInputActions.LIST_EXTEND_SELECTION, mouse.getModifiersEx());
			if (!modifyingSelection && event.selectedBeforePress()
					&& browser.isNameHit(entry, mouse))
				scheduleInlineRename(entry.path());
		}
		private final class FileEntryCellEditor implements GLList.CellEditor<GLFileBrowser.Entry> {
			@Override public GLContainer createEditor(GLList<GLFileBrowser.Entry> source,
					GLFileBrowser.Entry value, int index, GLList.EditController controller) {
				InlineRenameEditor editor = new InlineRenameEditor(controller);
				editor.setText(value.displayName());
				editor.addActionListener(event -> controller.commit());
				editor.addCancelListener(event -> controller.cancel());
				EventQueue.invokeLater(editor::selectAll);
				return editor;
			}
			@Override public void layoutEditor(GLList<GLFileBrowser.Entry> source,
					GLFileBrowser.Entry value, int index, GLContainer editor,
					float width, float height) {
				browser.layoutNameEditor(editor, width, height);
			}
			@Override public boolean commitEditor(GLList<GLFileBrowser.Entry> source,
					GLFileBrowser.Entry value, int index, GLContainer editor) {
				return commitInlineRename(value, (GLTextEditor) editor);
			}
		}
		private final class InlineRenameEditor extends GLTextEditor {
			private final GLList.EditController controller;
			private InlineRenameEditor(GLList.EditController controller) {
				this.controller = controller;
			}
			@Override protected void controlFocusChanged(boolean focused) {
				super.controlFocusChanged(focused);
				if (!focused && !preservingInlineEditor)
					EventQueue.invokeLater(() -> {
						if (!isFocused() && fileList.getEditingComponent() == this
								&& !preservingInlineEditor) controller.cancel();
					});
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
