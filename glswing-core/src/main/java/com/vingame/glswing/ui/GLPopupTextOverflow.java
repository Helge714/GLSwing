package com.vingame.glswing.ui;

/** Controls how a popup reacts when its natural content is wider than allowed. */
public enum GLPopupTextOverflow {
	/** Grow to the natural content width, even when a root width was requested. */
	EXPAND,
	/** Keep the constrained width and shorten labels with trailing dots. */
	ELLIPSIS,
	/** Keep the constrained width and scroll the unabridged content horizontally. */
	HORIZONTAL_SCROLL
}
