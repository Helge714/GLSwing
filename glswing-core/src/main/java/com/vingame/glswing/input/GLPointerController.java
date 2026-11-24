package com.vingame.glswing.input;

/** Applies pointer changes through the window-system backend owning an event. */
public interface GLPointerController {

	GLPointerController NONE = new GLPointerController() {
		@Override
		public boolean isAvailable() {
			return false;
		}

		@Override
		public void setCursor(Object owner, GLPointerCursor cursor) {
		}

		@Override
		public void clearCursor(Object owner) {
		}
	};

	boolean isAvailable();

	void setCursor(Object owner, GLPointerCursor cursor);

	void clearCursor(Object owner);
}
