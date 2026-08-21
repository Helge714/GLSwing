package com.vingame.glswing.rendering;

/** Resolved backing texture and normalized UV rectangle for one high-level image. */
record GLTextureRegion(GLTexture texture, float u0, float v0, float u1, float v1,
		boolean atlased) {
}
