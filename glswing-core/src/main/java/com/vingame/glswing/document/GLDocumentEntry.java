package com.vingame.glswing.document;

/**
 * A lazily decoded bundle entry and its editable document state.
 * @param descriptor persistent bundle metadata
 * @param document decoded editable document
 * @param <T> entry model type
 */
public record GLDocumentEntry<T>(GLDocumentEntryDescriptor descriptor,
		GLDocument<T> document) {
	public GLDocumentEntry {
		if (descriptor == null || document == null)
			throw new IllegalArgumentException("Descriptor and document required");
	}
}
