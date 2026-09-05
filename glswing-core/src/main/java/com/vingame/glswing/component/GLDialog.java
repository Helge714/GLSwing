package com.vingame.glswing.component;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.Shape;
import java.awt.GraphicsDevice.WindowTranslucency;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Path2D;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.JComponent;

import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.FPSAnimator;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLLocalizer;
import com.vingame.glswing.ui.GLTheme;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLControl;
import com.vingame.glswing.ui.GLControlState;
import com.vingame.glswing.input.GLMouseEvent;

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
	/** Native chrome, theme-rendered GLSwing chrome, or no title bar. */
	public enum Decoration { SYSTEM, THEMED, NONE }

	private enum State { NEW, OPENING, OPEN, CLOSED }

	private final CompletableFuture<Void> closedFuture = new CompletableFuture<>();
	private String title;
	private GLContainer content;
	private int width = 520;
	private int height = 360;
	private boolean resizable = true;
	private Modality modality = Modality.OWNER_MODAL;
	private Color background;
	private Decoration decoration = Decoration.SYSTEM;
	private Float cornerRadius;
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
	public synchronized Decoration getDecoration() { return decoration; }
	public synchronized void setDecoration(Decoration decoration) {
		requireConfigurable();
		if(decoration==null)throw new IllegalArgumentException("Dialog decoration must not be null");
		this.decoration=decoration;
	}
	/** Overrides the themed radius for an undecorated native window. */
	public synchronized void setCornerRadius(float radius) {
		requireConfigurable();
		if(!Float.isFinite(radius)||radius<0)throw new IllegalArgumentException("Corner radius must be finite and non-negative");
		cornerRadius=radius;
	}
	public synchronized void clearCornerRadius() { requireConfigurable();cornerRadius=null; }
	public synchronized Float getCornerRadiusOverride() { return cornerRadius; }

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
		float resolvedRadius=cornerRadius==null
				? theme.values().getFloat("dialog.cornerRadius",10) : cornerRadius;
		DialogHost created;
		if (root instanceof GLSwingPanel
				|| root instanceof GLSwingContainerCanvas) {
			if (!GLSwingCanvas.hasRequiredAwtModuleAccess())
				throw new IllegalStateException(
						"Canvas dialogs require these VM arguments: "
								+ GLSwingCanvas.REQUIRED_WINDOWS_VM_ARGUMENTS);
			Component component = (Component) root;
			created = new CanvasDialogHost(component, title, content, width, height,
					resizable, modality, clear, theme, localizer, decoration,
					resolvedRadius, this::finished);
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
		private final Decoration dialogDecoration;
		private final float dialogCornerRadius;
		private final AtomicBoolean closing = new AtomicBoolean();
		private volatile JDialog dialog;
		private volatile FPSAnimator animator;

		private CanvasDialogHost(Component ownerComponent, String title,
				GLContainer content, int width, int height, boolean resizable,
				Modality modality, Color clearColor, GLTheme theme,
				GLLocalizer localizer, Decoration decoration,float cornerRadius,
				Completion completion) {
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
			dialogDecoration=decoration;dialogCornerRadius=cornerRadius;
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
				boolean custom=dialogDecoration!=Decoration.SYSTEM;
				if(custom)createdDialog.setUndecorated(true);
				float titleHeight=dialogDecoration==Decoration.THEMED
						? Math.max(24,theme.values().getFloat("dialog.titleBar.height",34)):0;
				boolean translucent=dialogDecoration==Decoration.THEMED&&supportsPerPixelTranslucency();
				Component presentation;
				GLAutoDrawable drawable;
				int horizontalOverflow=0,verticalOverflow=0;
				if(translucent) {
					float softness=Math.max(0,theme.values().getFloat("dialog.shadow.softness",12));
					float offsetX=theme.values().getFloat("dialog.shadow.offsetX",0);
					float offsetY=theme.values().getFloat("dialog.shadow.offsetY",4);
					int left=(int)Math.ceil(softness+Math.max(0,-offsetX)+2);
					int right=(int)Math.ceil(softness+Math.max(0,offsetX)+2);
					int top=(int)Math.ceil(softness+Math.max(0,-offsetY)+2);
					int bottom=(int)Math.ceil(softness+Math.max(0,offsetY)+2);
					DialogPanel panel=new DialogPanel(dialogContent,clearColor,theme,localizer,
							dialogDecoration,dialogTitle,titleHeight,dialogCornerRadius,
							left,top,right,bottom,this::close,()->dialog);
					panel.setSharedAutoDrawable((GLAutoDrawable)ownerComponent);
					presentation=panel;drawable=panel;horizontalOverflow=left+right;verticalOverflow=top+bottom;
					createdDialog.setBackground(new Color(0,0,0,0));
					if(createdDialog.getContentPane() instanceof JComponent component)component.setOpaque(false);
				} else {
					DialogCanvas canvas = new DialogCanvas(dialogContent, clearColor,
							theme, localizer,dialogDecoration,dialogTitle,titleHeight,
							dialogCornerRadius,this::close,()->dialog);
					canvas.setSharedAutoDrawable((GLAutoDrawable) ownerComponent);
					presentation=canvas;drawable=canvas;
				}
				FPSAnimator createdAnimator = new FPSAnimator(drawable, 144, true);
				dialog = createdDialog;
				animator = createdAnimator;
				createdDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
				createdDialog.setLayout(new BorderLayout());
				createdDialog.add(presentation, BorderLayout.CENTER);
				presentation.setPreferredSize(new Dimension(dialogWidth+horizontalOverflow,
						Math.round(dialogHeight+titleHeight)+verticalOverflow));
				createdDialog.setResizable(dialogResizable);
				createdDialog.addWindowListener(new WindowAdapter() {
					@Override
					public void windowClosing(WindowEvent event) {
						close();
					}
				});
				createdDialog.pack();
				if(custom&&!translucent) {
					Runnable shape=()->applyRoundedShape(createdDialog,dialogCornerRadius);
					shape.run();
					createdDialog.addComponentListener(new ComponentAdapter() {
						@Override public void componentResized(ComponentEvent event) {shape.run();}
					});
				}
				createdDialog.setLocationRelativeTo(ownerWindow);
				// Build the GL context, shaders and font textures before the dialog is
				// exposed. Otherwise Windows presents a white peer during this work.
				drawable.display();
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
	private static boolean supportsPerPixelTranslucency() {
		try {return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
				.isWindowTranslucencySupported(WindowTranslucency.PERPIXEL_TRANSLUCENT);}
		catch(java.awt.HeadlessException|SecurityException unsupported) {return false;}
	}
	private static void applyRoundedShape(Window window,float radius) {
		try {
			Shape shape=null;
			if(radius>0) {
				double w=window.getWidth(),h=window.getHeight();
				double r=Math.min(radius,Math.min(w,h)/2),k=.5522847498307936;
				Path2D.Double path=new Path2D.Double();
				path.moveTo(r,0);path.lineTo(w-r,0);
				path.curveTo(w-r+k*r,0,w,r-k*r,w,r);path.lineTo(w,h-r);
				path.curveTo(w,h-r+k*r,w-r+k*r,h,w-r,h);path.lineTo(r,h);
				path.curveTo(r-k*r,h,0,h-r+k*r,0,h-r);path.lineTo(0,r);
				path.curveTo(0,r-k*r,r-k*r,0,r,0);path.closePath();shape=path;
			}
			window.setShape(shape);
		} catch(UnsupportedOperationException|SecurityException ignored) {
			// The window remains square on desktops without shaped-window support.
		}
	}

	private static final class DialogCanvas extends GLSwingContainerCanvas {
		private static final long serialVersionUID = 1L;
		private final GLContainer content;
		private final Color background;

		private final float titleHeight;
		private DialogCanvas(GLContainer content, Color background, GLTheme theme,
				GLLocalizer localizer,Decoration decoration,String title,float titleHeight,
				float cornerRadius,Runnable close,java.util.function.Supplier<Window> window) {
			super(DEFAULT_ANTIALIASING_SAMPLES, 1);
			this.titleHeight=titleHeight;
			this.content = decoration==Decoration.THEMED
					? new ThemedDialogSurface(content,title,titleHeight,cornerRadius,background,close,window,
							localizer.text("glswing.dialog.close")):content;
			this.background = background;
			setGLTheme(theme);
			setGLLocalizer(localizer);
			addGL(this.content);
		}

		@Override
		protected void renderContainer(GLContext2D context) {
			context.clear(background);
			content.setBounds(0, 0, getWidth(), getHeight());
		}
	}

	/** Alpha-preserving presentation used by themed dialogs for soft outer edges. */
	private static final class DialogPanel extends GLSwingPanel {
		private static final long serialVersionUID=1L;
		private final GLContainer content;
		private final int left,top,right,bottom;
		DialogPanel(GLContainer source,Color background,GLTheme theme,GLLocalizer localizer,
				Decoration decoration,String title,float titleHeight,float cornerRadius,
				int left,int top,int right,int bottom,Runnable close,
				java.util.function.Supplier<Window> window) {
			super(DEFAULT_ANTIALIASING_SAMPLES);this.left=left;this.top=top;this.right=right;this.bottom=bottom;
			content=decoration==Decoration.THEMED
					?new ThemedDialogSurface(source,title,titleHeight,cornerRadius,background,close,window,
							localizer.text("glswing.dialog.close")):source;
			setOpaque(false);setGLTheme(theme);setGLLocalizer(localizer);addGL(content);
		}
		@Override protected void render(GLContext2D context) {
			context.clear(new Color(0,0,0,0));
			content.setBounds(left,top,Math.max(0,getWidth()-left-right),Math.max(0,getHeight()-top-bottom));
		}
	}

	private static final class ThemedDialogSurface extends GLContainer {
		private final GLContainer content;
		private final DialogTitleBar titleBar;
		private final DialogCloseButton close;
		private final float titleHeight;
		private final float cornerRadius;
		private final Color background;
		ThemedDialogSurface(GLContainer content,String title,float titleHeight,float cornerRadius,Color background,Runnable closeAction,
				java.util.function.Supplier<Window> window,String closeToolTip) {
			this.content=content;this.titleHeight=titleHeight;this.cornerRadius=cornerRadius;this.background=background;
			titleBar=new DialogTitleBar(title,window,cornerRadius);
			close=new DialogCloseButton(cornerRadius);close.setToolTipText(closeToolTip);close.addActionListener(e->closeAction.run());
			addGL(titleBar);addGL(content);addGL(close);
		}
		@Override protected void resized(float w,float h) {
			titleBar.setBounds(0,0,w,titleHeight);close.setBounds(Math.max(0,w-titleHeight),0,titleHeight,titleHeight);
			content.setBounds(0,titleHeight,w,Math.max(0,h-titleHeight));
		}
		@Override protected void record(GLContext2D context) {
			var values=getGLTheme().values();float softness=Math.max(0,values.getFloat("dialog.shadow.softness",12));
			Color shadow=values.getColor("dialog.shadow.color",new Color(0,0,0,120));
			if(softness>0&&shadow.getAlpha()>0)context.drawUIRoundRectShadow(0,0,getWidth(),getHeight(),
					cornerRadius,softness,values.getFloat("dialog.shadow.offsetX",0),
					values.getFloat("dialog.shadow.offsetY",4),shadow);
			float border=Math.max(0,values.getFloat("dialog.borderWidth",1));
			Color borderColor=values.getColor("dialog.border",
					values.getColor("palette.border",Color.GRAY));
			if(border>0)context.fillAndDrawUIRoundRect(0,0,getWidth(),getHeight(),cornerRadius,
					border,background,borderColor,
					com.vingame.glswing.rendering.GLStroke.WidthSpace.SCREEN);
			else context.fillUIRoundRect(0,0,getWidth(),getHeight(),cornerRadius,background);
		}
		@Override protected void renderChildren(GLContext2D context) {
			float border=Math.max(0,getGLTheme().values().getFloat("dialog.borderWidth",1));
			float inset=Math.min(border,Math.min(getWidth(),getHeight())/2);
			context.beginUIRoundedClip(inset,inset,Math.max(.001f,getWidth()-inset*2),
					Math.max(.001f,getHeight()-inset*2),
					com.vingame.glswing.rendering.GLCornerRadii.all(Math.max(0,cornerRadius-inset)));
			try {super.renderChildren(context);} finally {context.endClip();}
		}
	}

	private static final class DialogTitleBar extends GLControl {
		private final String title;private final java.util.function.Supplier<Window> window;
		private java.awt.Point pressScreen,pressWindow;
		DialogTitleBar(String title,java.util.function.Supplier<Window> window,float cornerRadius) {
			super(false);this.title=title;this.window=window;
		}
		@Override protected void record(GLContext2D context) {
			Color bg=getGLTheme().values().getColor("dialog.titleBar.background",
					getGLTheme().values().getColor("palette.surface",Color.DARK_GRAY));
			Color fg=getGLTheme().values().getColor("dialog.titleBar.text",
					getGLTheme().values().getColor("palette.text",Color.WHITE));
			Color border=getGLTheme().values().getColor("dialog.titleBar.border",
					getGLTheme().values().getColor("palette.border",Color.GRAY));
			context.fillUIRoundRect(0,0,getWidth(),getHeight(),0,bg);
			var font=getGLFont(getGLTheme().labelStyle().font());float baseline=(getHeight()-font.getLineHeight())/2+font.getAscent();
			context.drawUIText(font,title,12,baseline,fg);
			float separator=Math.max(1,getGLTheme().values().getFloat("dialog.titleBar.borderWidth",1));
			context.fillUIRoundRect(0,Math.max(0,getHeight()-separator),getWidth(),separator,0,border);
		}
		@Override protected void controlMousePressed(GLMouseEvent event) {
			if(event.getButton()!=java.awt.event.MouseEvent.BUTTON1)return;
			Window target=window.get();if(target==null)return;
			pressScreen=event.getOriginalEvent().getLocationOnScreen();pressWindow=target.getLocation();
		}
		@Override protected void controlMouseDragged(GLMouseEvent event) {
			Window target=window.get();if(target==null||pressScreen==null)return;
			java.awt.Point now=event.getOriginalEvent().getLocationOnScreen();
			target.setLocation(pressWindow.x+now.x-pressScreen.x,pressWindow.y+now.y-pressScreen.y);
		}
		@Override protected void controlMouseReleased(GLMouseEvent event) {pressScreen=pressWindow=null;}
	}

	/** Flat title-bar target matching native Windows close-button behavior. */
	private static final class DialogCloseButton extends GLButton {
		private final float outerRadius;
		DialogCloseButton(float outerRadius) {super("");this.outerRadius=outerRadius;}
		@Override protected void controlStateChanged(GLControlState oldState,GLControlState newState) {
			super.controlStateChanged(oldState,newState);invalidateRecording();
		}
		@Override protected void controlFocusChanged(boolean focused) {
			super.controlFocusChanged(focused);invalidateRecording();
		}
		@Override protected void record(GLContext2D context) {
			GLControlState state=getControlState();boolean active=state==GLControlState.HOVERED
					|| state==GLControlState.PRESSED || isFocused();
			com.vingame.glswing.rendering.GLCornerRadii hitShape=
					new com.vingame.glswing.rendering.GLCornerRadii(0,outerRadius,0,0);
			context.addUIHitArea(0,0,getWidth(),getHeight());
			if(active) {
				String key=state==GLControlState.PRESSED?"dialog.close.pressed":"dialog.close.hover";
				Color background=getGLTheme().values().getColor(key,
						state==GLControlState.PRESSED?new Color(153,32,22):new Color(196,43,28));
				context.fillUIRoundRect(0,0,getWidth(),getHeight(),hitShape,background);
			}
			Color normal=getGLTheme().values().getColor("dialog.close.text",
					getGLTheme().values().getColor("palette.text",Color.WHITE));
			Color foreground=active?getGLTheme().values().getColor("dialog.close.activeText",Color.WHITE):normal;
			float cx=getWidth()/2,cy=getHeight()/2;
			com.vingame.glswing.rendering.GLStroke previous=context.getStroke();
			try {
				context.setStroke(new com.vingame.glswing.rendering.GLStroke(1.35f)
						.withWidthSpace(com.vingame.glswing.rendering.GLStroke.WidthSpace.SCREEN));
				context.drawUILine(cx-4,cy-4,cx+4,cy+4,foreground);
				context.drawUILine(cx+4,cy-4,cx-4,cy+4,foreground);
			} finally {context.setStroke(previous);}
		}
	}

}
