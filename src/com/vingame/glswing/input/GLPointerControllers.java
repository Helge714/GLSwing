package com.vingame.glswing.input;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Backend adapter factories used by event dispatchers and direct test events. */
public final class GLPointerControllers {
	private static final Map<Component, WeakReference<GLPointerController>> AWT =
			new WeakHashMap<>();
	private static volatile Cursor hiddenCursor;

	private GLPointerControllers() {
	}

	public static GLPointerController forAwt(Component component) {
		if (component == null)
			return GLPointerController.NONE;
		synchronized (AWT) {
			WeakReference<GLPointerController> reference = AWT.get(component);
			GLPointerController controller = reference == null ? null : reference.get();
			if (controller == null) {
				controller = new AwtController(component);
				AWT.put(component, new WeakReference<>(controller));
			}
			return controller;
		}
	}

	private static final class AwtController implements GLPointerController {
		private final Component component;
		private Object owner;
		private Cursor cursorBeforeOverride;

		AwtController(Component component) {
			this.component = component;
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public void setCursor(Object requestedOwner, GLPointerCursor cursor) {
			if (requestedOwner == null || cursor == null)
				throw new IllegalArgumentException("Pointer owner and cursor must not be null");
			try {
				if (owner == null)
					cursorBeforeOverride = component.getCursor();
				owner = requestedOwner;
				component.setCursor(toAwtCursor(cursor));
			} catch (RuntimeException ignored) {
				owner = null;
				cursorBeforeOverride = null;
			}
		}

		@Override
		public void clearCursor(Object requestedOwner) {
			if (owner != requestedOwner)
				return;
			Cursor restore = cursorBeforeOverride;
			owner = null;
			cursorBeforeOverride = null;
			try {
				component.setCursor(restore == null
						? Cursor.getDefaultCursor() : restore);
			} catch (RuntimeException ignored) {
			}
		}

		private Cursor toAwtCursor(GLPointerCursor cursor) {
			return switch (cursor) {
				case DEFAULT -> Cursor.getDefaultCursor();
				case TEXT -> Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
				case MOVE -> Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
				case HORIZONTAL_RESIZE -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
				case VERTICAL_RESIZE -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
				case DIAGONAL_NW_SE_RESIZE ->
						Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
				case DIAGONAL_NE_SW_RESIZE ->
						Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
				case HIDDEN -> invisibleCursor(component);
			};
		}
	}

	private static Cursor invisibleCursor(Component component) {
		Cursor result = hiddenCursor;
		if (result != null)
			return result;
		try {
			Toolkit toolkit = component.getToolkit();
			Dimension size = toolkit.getBestCursorSize(16, 16);
			if (size.width <= 0 || size.height <= 0)
				return Cursor.getDefaultCursor();
			BufferedImage image = new BufferedImage(size.width, size.height,
					BufferedImage.TYPE_INT_ARGB);
			result = toolkit.createCustomCursor(image, new Point(0, 0),
					"GLSwing hidden cursor");
			hiddenCursor = result;
			return result;
		} catch (RuntimeException error) {
			return Cursor.getDefaultCursor();
		}
	}
}
