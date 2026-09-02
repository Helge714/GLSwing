package com.vingame.glswing.filedialogs.swt;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Window;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLFrame;
import com.vingame.glswing.file.GLFileDialogBackend;
import com.vingame.glswing.file.GLFileDialogMode;
import com.vingame.glswing.file.GLFileDialogRequest;
import com.vingame.glswing.file.GLFileDialogResult;
import com.vingame.glswing.file.GLFileSelectionMode;

/** Native SWT implementation discovered automatically through {@link java.util.ServiceLoader}. */
public final class GLSwtFileDialogBackend implements GLFileDialogBackend {
	private final Object lock = new Object();
	private volatile Display display;
	private volatile Shell shell;
	private volatile Thread eventThread;
	private volatile boolean closing;
	private volatile Window bridgeWindow;
	private volatile Canvas bridgeCanvas;
	@Override public String id() { return "swt-native"; }
	@Override public boolean isNative() { return true; }
	@Override public int priority() { return 100; }
	@Override public boolean isAvailable() {
		try { Class.forName("org.eclipse.swt.widgets.FileDialog", false, getClass().getClassLoader()); return true; }
		catch (LinkageError | ClassNotFoundException failure) { return false; }
	}
	@Override public CompletionStage<GLFileDialogResult> show(
			GLFileDialogRequest request, GLParent owner) {
		CompletableFuture<GLFileDialogResult> result = new CompletableFuture<>();
		if (request.selectionMode() == GLFileSelectionMode.FILES_AND_DIRECTORIES) {
			result.completeExceptionally(new UnsupportedOperationException(
					"SWT native dialogs cannot combine file and directory selection"));
			return result;
		}
		try {
			ensureRuntime(owner);
			display.asyncExec(() -> {
				try { result.complete(open(request)); }
				catch (Throwable failure) { result.completeExceptionally(failure); }
			});
		} catch (Throwable failure) { result.completeExceptionally(failure); }
		return result;
	}
	private GLFileDialogResult open(GLFileDialogRequest request) {
		if (request.selectionMode() == GLFileSelectionMode.DIRECTORIES) {
			DirectoryDialog dialog = new DirectoryDialog(shell, SWT.APPLICATION_MODAL);
			dialog.setText(request.title());
			if (request.initialDirectory() != null)
				dialog.setFilterPath(request.initialDirectory().toAbsolutePath().toString());
			String selected = dialog.open();
			return selected == null ? GLFileDialogResult.cancelled()
					: GLFileDialogResult.approved(List.of(Path.of(selected)));
		}
		int style = request.mode() == GLFileDialogMode.SAVE ? SWT.SAVE : SWT.OPEN;
		if (request.multiple()) style |= SWT.MULTI;
		FileDialog dialog = new FileDialog(shell, style | SWT.APPLICATION_MODAL);
		dialog.setText(request.title());
		if (request.initialDirectory() != null)
			dialog.setFilterPath(request.initialDirectory().toAbsolutePath().toString());
		if (request.suggestedFileName() != null)
			dialog.setFileName(request.suggestedFileName());
		if (!request.filters().isEmpty()) {
			dialog.setFilterNames(request.filters().stream()
					.map(com.vingame.glswing.file.GLFileFilter::description).toArray(String[]::new));
			dialog.setFilterExtensions(request.filters().stream()
					.map(com.vingame.glswing.file.GLFileFilter::swtPattern).toArray(String[]::new));
		}
		if (request.mode() == GLFileDialogMode.SAVE)
			dialog.setOverwrite(request.confirmOverwrite());
		String selected = dialog.open();
		if (selected == null) return GLFileDialogResult.cancelled();
		if (!request.multiple()) return GLFileDialogResult.approved(List.of(Path.of(selected)));
		Path directory = Path.of(dialog.getFilterPath());
		List<Path> paths = new ArrayList<>();
		for (String name : dialog.getFileNames()) paths.add(directory.resolve(name));
		return GLFileDialogResult.approved(paths);
	}
	private void ensureRuntime(GLParent owner) throws Exception {
		if (display != null && !display.isDisposed()) return;
		synchronized (lock) {
			if (display != null && !display.isDisposed()) return;
			if (closing) throw new IllegalStateException("SWT file-dialog backend is closed");
			createBridge(owner);
			java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
			java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
			eventThread = new Thread(() -> {
				try {
					display = new Display();
					shell = SWT_AWT.new_Shell(display, bridgeCanvas);
					ready.countDown();
					while (!closing && !display.isDisposed()) {
						if (!display.readAndDispatch()) display.sleep();
					}
				} catch (Throwable error) { failure.set(error); ready.countDown(); }
				finally {
					if (shell != null && !shell.isDisposed()) shell.dispose();
					if (display != null && !display.isDisposed()) display.dispose();
				}
			}, "glswing-swt-file-dialogs");
			eventThread.setDaemon(true);
			eventThread.start();
			ready.await();
			if (failure.get() != null) throw new IllegalStateException(
					"Cannot initialize SWT file dialogs", failure.get());
		}
	}
	private void createBridge(GLParent owner) throws Exception {
		if (bridgeWindow != null) return;
		Runnable create = () -> {
			Component component = ownerComponent(owner);
			Window parent = component == null ? null
					: javax.swing.SwingUtilities.getWindowAncestor(component);
			bridgeWindow = new Window(parent);
			bridgeWindow.setLayout(new BorderLayout());
			bridgeCanvas = new Canvas();
			bridgeWindow.add(bridgeCanvas, BorderLayout.CENTER);
			bridgeWindow.setSize(1, 1);
			if (parent != null) bridgeWindow.setLocation(parent.getX() + parent.getWidth() / 2,
					parent.getY() + parent.getHeight() / 2);
			bridgeWindow.setVisible(true);
		};
		if (EventQueue.isDispatchThread()) create.run();
		else EventQueue.invokeAndWait(create);
	}
	private static Component ownerComponent(GLParent owner) {
		GLParent current = owner;
		while (current instanceof GLContainer container && container.getGLParent() != null)
			current = container.getGLParent();
		if (current instanceof GLFrame frame) current = frame.getGLCanvas();
		return current instanceof Component component ? component : null;
	}
	@Override public void close() {
		closing = true;
		Display current = display;
		if (current != null && !current.isDisposed()) current.asyncExec(current::wake);
		Window window = bridgeWindow;
		bridgeWindow = null;
		bridgeCanvas = null;
		if (window != null) EventQueue.invokeLater(window::dispose);
	}
}
