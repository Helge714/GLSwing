package com.vingame.glswing.component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLRecording;

/** Shared lazy recording lifecycle for panels and regular GL containers. */
final class GLRecordingCache {

	private final AtomicLong version = new AtomicLong(1);
	private volatile long recordedVersion;
	private GLRecording recording;

	void ensure(GLContext2D context, Consumer<GLContext2D> recorder) {
		long targetVersion = version.get();
		if (recording != null && recordedVersion == targetVersion && context.canDraw(recording))
			return;
		context.beginRecording();
		try {
			recorder.accept(context);
			recording = context.endRecording();
			recordedVersion = targetVersion;
		} catch (RuntimeException | Error error) {
			if (context.isRecording())
				context.cancelRecording();
			throw error;
		}
	}

	void invalidate() {
		version.incrementAndGet();
	}

	boolean isDirty() {
		return recording == null || recordedVersion != version.get();
	}

	GLRecording get() {
		return recording;
	}

	void clear() {
		recording = null;
		recordedVersion = 0;
		version.incrementAndGet();
	}
}
