package com.vingame.glswing.application;

import com.vingame.glswing.component.GLContainer;

/** Builds the root retained component after startup resources are available. */
@FunctionalInterface
public interface GLApplicationSurfaceFactory {
	GLContainer create(GLApplicationContext context) throws Exception;
}
