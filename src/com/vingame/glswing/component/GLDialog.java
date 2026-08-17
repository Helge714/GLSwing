package com.vingame.glswing.component;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.FPSAnimator;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLLocalizer;
import com.vingame.glswing.ui.GLTheme;

/**
 * A GLSwing surface in a freely movable, owner-coupled operating-system dialog.
 * <p>
	 * Owners ({@link GLFrame}, {@link GLSwingPanel} and
	 * {@link GLSwingContainerCanvas}) use an owned {@link JDialog} containing a
	 * direct GL canvas. Dialog content therefore uses the same retained GLSwing
	 * hierarchy as its owner while remaining independently movable on the desktop.
 * <p>
 * Dialogs are intentionally asynchronous: {@link #show(GLParent)} returns
 * immediately and {@link #closedFuture()} completes after the native window has
 * closed. One dialog instance is single-use.
 */
public final class GLDialog implements AutoCloseable {
	public enum Modality {
		MODELESS,
		OWNER_MODAL
	}

	private enum State { NEW, OPENING, OPEN, CLOSED }

	private final CompletableFuture<Void> closedFuture = new CompletableFuture<>();
	private String title;
	private GLContainer content;
	private int width = 520;
	private int height = 360;
	private boolean resizable = true;
	private Modality modality = Modality.OWNER_MODAL;
	private Color background;
	private volatile State state = State.NEW;
	private volatile DialogHost host;

	public GLDialog(String title, GLContainer content) {
		setTitle(title);
		setContent(content);
	}

	public synchronized String getTitle() {
		return title;
	}

	public synchronized void setTitle(String title) {
		requireConfigurable();
		this.title = title == null ? "" : title;
	}

	public synchronized GLContainer getContent() {
		return content;
	}

	public synchronized void setContent(GLContainer content) {
		requireConfigurable();
		if (content == null)
			throw new IllegalArgumentException("Dialog content must not be null");
		if (content.getGLParent() != null)
			throw new IllegalArgumentException(
					"Dialog content must not already belong to a GL parent");
		this.content = content;
	}

	public synchronized void setSize(int width, int height) {
		requireConfigurable();
		if (width <= 0 || height <= 0)
			throw new IllegalArgumentException("Dialog size must be positive");
		this.width = width;
		this.height = height;
	}

	public synchronized int getWidth() {
		return width;
	}

	public synchronized int getHeight() {
		return height;
	}

	public synchronized boolean isResizable() {
		return resizable;
	}

	public synchronized void setResizable(boolean resizable) {
		requireConfigurable();
		this.resizable = resizable;
	}

	public synchronized Modality getModality() {
		return modality;
	}

	public synchronized void setModality(Modality modality) {
		requireConfigurable();
		if (modality == null)
			throw new IllegalArgumentException("Dialog modality must not be null");
		this.modality = modality;
	}

	/** Overrides the inherited theme-derived clear color. */
	public synchronized void setBackground(Color background) {
		requireConfigurable();
		this.background = background;
	}

	public synchronized Color getBackground() {
		return background;
	}

	public synchronized void show(GLParent owner) {
		if (owner == null)
			throw new IllegalArgumentException("Dialog owner must not be null");
		if (state != State.NEW)
			throw new IllegalStateException("A GLDialog instance can only be shown once");
		GLParent root = root(owner);
		if (root instanceof GLFrame frame)
			root = frame.getGLCanvas();
		GLTheme theme = root.getGLTheme();
		GLLocalizer localizer = root.getGLLocalizer();
		Color clear = background == null
				? theme.values().getColor("palette.surface",
						theme.buttonStyle().background()) : background;
		DialogHost created;
		if (root instanceof GLSwingPanel
				|| root instanceof GLSwingContainerCanvas) {
			if (!GLSwingCanvas.hasRequiredAwtModuleAccess())
				throw new IllegalStateException(
						"Canvas dialogs require these VM arguments: "
								+ GLSwingCanvas.REQUIRED_WINDOWS_VM_ARGUMENTS);
			Component component = (Component) root;
			created = new CanvasDialogHost(component, title, content, width, height,
					resizable, modality, clear, theme, localizer, this::finished);
		} else {
			throw new IllegalArgumentException(
					"Dialog owner must resolve to GLFrame, GLSwingPanel or GLCanvas");
		}
		state = State.OPENING;
		host = created;
		created.show();
	}

	public boolean isShowing() {
		DialogHost current = host;
		return state == State.OPEN && current != null && current.isShowing();
	}

	public CompletableFuture<Void> closedFuture() {
		return closedFuture;
	}

	public void toFront() {
		DialogHost current = host;
		if (current != null)
			current.toFront();
	}

	@Override
	public void close() {
		DialogHost current;
		synchronized (this) {
			if (state == State.CLOSED)
				return;
			if (state == State.NEW) {
				state = State.CLOSED;
				closedFuture.complete(null);
				return;
			}
			current = host;
		}
		if (current != null)
			current.close();
	}

	private synchronized void opened() {
		if (state == State.OPENING)
			state = State.OPEN;
	}

	private synchronized void finished(Throwable error) {
		if (state == State.CLOSED)
			return;
		state = State.CLOSED;
		host = null;
		if (error == null)
			closedFuture.complete(null);
		else
			closedFuture.completeExceptionally(error);
	}

	private void requireConfigurable() {
		if (state != State.NEW)
			throw new IllegalStateException("Dialog configuration cannot change after show()");
	}

	private static GLParent root(GLParent owner) {
		GLParent current = owner;
		while (current instanceof GLContainer container
				&& container.getGLParent() != null)
			current = container.getGLParent();
		return current;
	}

	@FunctionalInterface
	private interface Completion {
		void complete(Throwable error);
	}

	private interface DialogHost {
		void show();
		void close();
		void toFront();
		boolean isShowing();
	}

	private final class CanvasDialogHost implements DialogHost {
		private final Component ownerComponent;
		private final String dialogTitle;
		private final GLContainer dialogContent;
		private final int dialogWidth;
		private final int dialogHeight;
		private final boolean dialogResizable;
		private final Modality dialogModality;
		private final Color clearColor;
		private final GLTheme theme;
		private final GLLocalizer localizer;
		private final Completion completion;
		private final AtomicBoolean closing = new AtomicBoolean();
		private volatile JDialog dialog;
		private volatile FPSAnimator animator;

		private CanvasDialogHost(Component ownerComponent, String title,
				GLContainer content, int width, int height, boolean resizable,
				Modality modality, Color clearColor, GLTheme theme,
				GLLocalizer localizer, Completion completion) {
			this.ownerComponent = ownerComponent;
			dialogTitle = title;
			dialogContent = content;
			dialogWidth = width;
			dialogHeight = height;
			dialogResizable = resizable;
			dialogModality = modality;
			this.clearColor = clearColor;
			this.theme = theme;
			this.localizer = localizer;
			this.completion = completion;
		}

		@Override
		public void show() {
			SwingUtilities.invokeLater(this::openOnEdt);
		}

		private void openOnEdt() {
			try {
				if (closing.get()) {
					completion.complete(null);
					return;
				}
				Window ownerWindow = SwingUtilities.getWindowAncestor(ownerComponent);
				if (ownerWindow == null)
					throw new IllegalStateException(
							"The AWT dialog owner must belong to a visible Window");
				JDialog createdDialog = new JDialog(ownerWindow, dialogTitle,
						dialogModality == Modality.OWNER_MODAL
								? ModalityType.DOCUMENT_MODAL : ModalityType.MODELESS);
				DialogCanvas canvas = new DialogCanvas(dialogContent, clearColor,
						theme, localizer);
				canvas.setSharedAutoDrawable((GLAutoDrawable) ownerComponent);
				FPSAnimator createdAnimator = new FPSAnimator(canvas, 144, true);
				dialog = createdDialog;
				animator = createdAnimator;
				createdDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
				createdDialog.setLayout(new BorderLayout());
				createdDialog.add(canvas, BorderLayout.CENTER);
				canvas.setPreferredSize(new Dimension(dialogWidth, dialogHeight));
				createdDialog.setResizable(dialogResizable);
				createdDialog.addWindowListener(new WindowAdapter() {
					@Override
					public void windowClosing(WindowEvent event) {
						close();
					}
				});
				createdDialog.pack();
				createdDialog.setLocationRelativeTo(ownerWindow);
				// Build the GL context, shaders and font textures before the dialog is
				// exposed. Otherwise Windows presents a white peer during this work.
				canvas.display();
				createdAnimator.start();
				opened();
				createdDialog.setVisible(true);
			} catch (Throwable error) {
				completion.complete(error);
			}
		}

		@Override
		public void close() {
			if (!closing.compareAndSet(false, true))
				return;
			Thread shutdown = new Thread(() -> {
				try {
					FPSAnimator currentAnimator = animator;
					if (currentAnimator != null && currentAnimator.isStarted())
						currentAnimator.stop();
					SwingUtilities.invokeLater(() -> {
						JDialog currentDialog = dialog;
						if (currentDialog != null)
							currentDialog.dispose();
						if (dialogContent.getGLParent() != null)
							dialogContent.removeFromGLParent();
						completion.complete(null);
					});
				} catch (Throwable error) {
					completion.complete(error);
				}
			}, "glswing-canvas-dialog-close");
			shutdown.setDaemon(true);
			shutdown.start();
		}

		@Override
		public void toFront() {
			SwingUtilities.invokeLater(() -> {
				if (dialog != null)
					dialog.toFront();
			});
		}

		@Override
		public boolean isShowing() {
			return dialog != null && dialog.isShowing();
		}
	}

	private static final class DialogCanvas extends GLSwingContainerCanvas {
		private static final long serialVersionUID = 1L;
		private final GLContainer content;
		private final Color background;

		private DialogCanvas(GLContainer content, Color background, GLTheme theme,
				GLLocalizer localizer) {
			super(DEFAULT_ANTIALIASING_SAMPLES, 1);
			this.content = content;
			this.background = background;
			setGLTheme(theme);
			setGLLocalizer(localizer);
			addGL(content);
		}

		@Override
		protected void renderContainer(GLContext2D context) {
			context.clear(background);
			content.setBounds(0, 0, getWidth(), getHeight());
		}
	}

}
