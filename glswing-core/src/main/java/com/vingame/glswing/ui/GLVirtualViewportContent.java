package com.vingame.glswing.ui;

/** Receives the visible content-space rectangle from a {@link GLScrollPane}. */
interface GLVirtualViewportContent {
	void viewportChanged(float x, float y, float width, float height);
}
