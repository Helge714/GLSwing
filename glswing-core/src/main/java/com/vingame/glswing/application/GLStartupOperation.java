package com.vingame.glswing.application;

/** One checked startup operation with optional fine-grained progress. */
@FunctionalInterface
public interface GLStartupOperation<T> {
	T run(GLStartupTaskContext task) throws Exception;
}
