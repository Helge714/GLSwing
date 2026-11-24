package com.vingame.glswing.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import com.vingame.glswing.ui.GLTextDocument;

/** UTF-8 codec for unstyled plain-text and source-code documents. */
public final class GLPlainTextDocumentCodec implements GLDocumentCodec<GLTextDocument> {
	private final String typeId;
	private final Set<String> extensions;

	public GLPlainTextDocumentCodec(String typeId, String... extensions) {
		if (typeId == null || typeId.isBlank() || extensions == null)
			throw new IllegalArgumentException("Type ID and extensions required");
		this.typeId = typeId;
		LinkedHashSet<String> normalized = new LinkedHashSet<>();
		for (String extension : extensions) {
			if (extension == null || extension.isBlank())
				throw new IllegalArgumentException("File extension must not be blank");
			String value = extension.strip().toLowerCase(java.util.Locale.ROOT);
			while (value.startsWith("*.")) value = value.substring(2);
			while (value.startsWith(".")) value = value.substring(1);
			if (value.isBlank()) throw new IllegalArgumentException("File extension required");
			normalized.add(value);
		}
		this.extensions = java.util.Collections.unmodifiableSet(normalized);
	}
	@Override public String typeId() { return typeId; }
	@Override public String codecId() { return "utf-8"; }
	@Override public Class<GLTextDocument> contentType() { return GLTextDocument.class; }
	@Override public int currentSchemaVersion() { return 1; }
	@Override public Set<String> fileExtensions() { return extensions; }
	@Override public GLTextDocument read(InputStream input, int schemaVersion) throws IOException {
		if (!supportsSchemaVersion(schemaVersion))
			throw new IOException("Unsupported plain-text schema version: " + schemaVersion);
		return new GLTextDocument(new String(input.readAllBytes(), StandardCharsets.UTF_8));
	}
	@Override public void write(GLTextDocument content, OutputStream output) throws IOException {
		output.write(content.getText().getBytes(StandardCharsets.UTF_8));
	}
}
