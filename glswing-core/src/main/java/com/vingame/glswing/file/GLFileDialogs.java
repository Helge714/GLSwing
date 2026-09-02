package com.vingame.glswing.file;

import java.util.Comparator;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import com.vingame.glswing.component.GLParent;

/** Selects native SWT dialogs or the built-in themed fallback. */
public final class GLFileDialogs implements AutoCloseable {
	public enum Preference { AUTO, NATIVE, THEMED }
	private final Preference preference;
	private final GLThemedFileDialogBackend themed = new GLThemedFileDialogBackend();
	private final GLFileDialogBackend nativeBackend;
	private GLFileDialogs(Preference preference) {
		this.preference = preference;
		nativeBackend = ServiceLoader.load(GLFileDialogBackend.class).stream()
				.map(ServiceLoader.Provider::get).filter(GLFileDialogBackend::isNative)
				.filter(GLFileDialogBackend::isAvailable)
				.max(Comparator.comparingInt(GLFileDialogBackend::priority)).orElse(null);
	}
	public static GLFileDialogs auto() { return new GLFileDialogs(Preference.AUTO); }
	public static GLFileDialogs nativeDialogs() { return new GLFileDialogs(Preference.NATIVE); }
	public static GLFileDialogs themed() { return new GLFileDialogs(Preference.THEMED); }
	public boolean isNativeAvailable() { return nativeBackend != null; }
	public CompletionStage<GLFileDialogResult> show(GLFileDialogRequest request, GLParent owner) {
		if (request == null || owner == null)
			return CompletableFuture.failedFuture(new IllegalArgumentException(
					"File-dialog request and owner must not be null"));
		if (preference == Preference.THEMED) return themed.show(request, owner);
		if (nativeBackend == null) {
			if (preference == Preference.NATIVE)
				return CompletableFuture.failedFuture(new IllegalStateException(
						"No native file-dialog backend is installed"));
			return themed.show(request, owner);
		}
		CompletionStage<GLFileDialogResult> nativeResult = nativeBackend.show(request, owner);
		if (preference == Preference.NATIVE) return nativeResult;
		return nativeResult.handle((result, failure) -> failure == null
				? CompletableFuture.completedFuture(result) : themed.show(request, owner))
				.thenCompose(value -> value);
	}
	@Override public void close() {
		if (nativeBackend != null) nativeBackend.close();
		themed.close();
	}
}
