package com.vingame.glswing.application;

/** OpenGL host used by a standalone {@link GLApplication}. */
public enum GLBackend {
	/** Swing-compatible off-screen rendering through a JOGL GLJPanel. */
	GLJPANEL,
	/** Direct heavyweight GLCanvas rendering with a managed animator. */
	CANVAS
}
