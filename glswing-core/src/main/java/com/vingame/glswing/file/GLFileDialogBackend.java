package com.vingame.glswing.file;

import java.util.concurrent.CompletionStage;
import com.vingame.glswing.component.GLParent;

/** Pluggable native or GLSwing file-dialog implementation. */
public interface GLFileDialogBackend extends AutoCloseable {
	String id();
	boolean isNative();
	default int priority() { return 0; }
	default boolean isAvailable() { return true; }
	CompletionStage<GLFileDialogResult> show(GLFileDialogRequest request, GLParent owner);
	@Override default void close() { }
}
