package com.vingame.glswing.application;

import com.jogamp.opengl.GL3;

/** Progress and shared results available while one startup task is running. */
public interface GLStartupTaskContext {
	/** Replaces the optional detail line shown below the current task. */
	void detail(String detail);
	/** Reports determinate task progress between zero and one. */
	void progress(double progress);
	/** Reports progress as completed units out of a positive total. */
	default void progress(long completed, long total) {
		if (total <= 0) throw new IllegalArgumentException("Total work must be positive");
		progress((double) completed / total);
	}
	/** Returns a previously provided startup result with a checked type. */
	<T> T require(String key, Class<T> type);
	/** Returns the current GL3 context from a GL task. */
	GL3 requireGL();
}
