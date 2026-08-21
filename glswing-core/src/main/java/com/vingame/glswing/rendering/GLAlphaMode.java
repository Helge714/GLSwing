package com.vingame.glswing.rendering;

/** How a sprite turns texture alpha into coverage and pipeline state. */
public enum GLAlphaMode {
	/** The sprite is treated as fully opaque. */
	OPAQUE,
	/** Pixels below the material cutoff are discarded before depth is written. */
	CUTOUT,
	/** Ordinary source-alpha blending; depth is tested but not written by default. */
	BLEND
}

