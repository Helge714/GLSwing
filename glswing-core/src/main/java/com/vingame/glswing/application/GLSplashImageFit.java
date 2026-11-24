package com.vingame.glswing.application;

/** Scaling strategy for a splash background image. */
public enum GLSplashImageFit {
	/** Fill the window and crop excess image area while preserving its ratio. */
	COVER,
	/** Show the complete image and preserve its ratio. */
	CONTAIN,
	/** Fill the window without preserving the image ratio. */
	STRETCH
}
