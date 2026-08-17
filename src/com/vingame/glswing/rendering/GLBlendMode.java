package com.vingame.glswing.rendering;

/**
 * Destination-aware layer operations supported directly by OpenGL's fixed
 * blend stage. More general blend functions require backdrop sampling.
 */
public enum GLBlendMode {
	/** Premultiplied source-over compositing. */
	NORMAL,
	/** Adds the layer color to the destination. */
	ADD,
	/** Multiplies layer and destination colors. */
	MULTIPLY,
	/** Inverse multiplication, producing a brighter result. */
	SCREEN,
	/** Subtracts the layer color from the destination. */
	SUBTRACT,
	/** Removes destination color and alpha using the layer alpha as a mask. */
	ERASE
}
