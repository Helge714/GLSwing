package com.vingame.glswing.document;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Ordered registry used to resolve codecs by stable IDs, model type, or extension. */
public final class GLDocumentCodecRegistry {
	private record Key(String typeId, String codecId) { }
	private final Map<Key, GLDocumentCodec<?>> codecs = new LinkedHashMap<>();

	public void register(GLDocumentCodec<?> codec) {
		if (codec == null) throw new IllegalArgumentException("Codec required");
		validateId(codec.typeId(), "Type"); validateId(codec.codecId(), "Codec");
		if (codec.contentType() == null || codec.currentSchemaVersion() < 1)
			throw new IllegalArgumentException("Codec metadata is invalid");
		Key key = new Key(codec.typeId(), codec.codecId());
		if (codecs.putIfAbsent(key, codec) != null)
			throw new IllegalArgumentException("Codec already registered: " + key);
	}
	public void unregister(String typeId, String codecId) {
		codecs.remove(new Key(typeId, codecId));
	}
	public Optional<GLDocumentCodec<?>> find(String typeId, String codecId) {
		return Optional.ofNullable(codecs.get(new Key(typeId, codecId)));
	}
	public <T> Optional<GLDocumentCodec<T>> find(Path path, Class<T> contentType) {
		if (path == null || contentType == null) return Optional.empty();
		String extension = extension(path);
		for (GLDocumentCodec<?> codec : codecs.values())
			if (codec.contentType().equals(contentType)
					&& codec.fileExtensions().stream().map(value -> normalizeExtension(value))
							.anyMatch(extension::equals))
				return Optional.of(cast(codec, contentType));
		return Optional.empty();
	}
	public <T> GLDocumentCodec<T> require(String typeId, String codecId,
			Class<T> contentType) throws GLDocumentFormatException {
		GLDocumentCodec<?> codec = codecs.get(new Key(typeId, codecId));
		if (codec == null)
			throw new GLDocumentFormatException("No codec registered for " + typeId
					+ " using " + codecId);
		if (!codec.contentType().equals(contentType))
			throw new GLDocumentFormatException("Document type " + typeId + " contains "
					+ codec.contentType().getName() + ", not " + contentType.getName());
		return cast(codec, contentType);
	}
	private static <T> GLDocumentCodec<T> cast(GLDocumentCodec<?> codec, Class<T> type) {
		@SuppressWarnings("unchecked") GLDocumentCodec<T> result = (GLDocumentCodec<T>) codec;
		return result;
	}
	private static String extension(Path path) {
		Path fileName = path.getFileName();
		if (fileName == null) return "";
		String value = fileName.toString(); int dot = value.lastIndexOf('.');
		return dot < 0 ? "" : normalizeExtension(value.substring(dot + 1));
	}
	private static String normalizeExtension(String value) {
		String result = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
		while (result.startsWith("*.")) result = result.substring(2);
		while (result.startsWith(".")) result = result.substring(1);
		return result;
	}
	private static void validateId(String value, String name) {
		if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]*"))
			throw new IllegalArgumentException(name + " ID is invalid: " + value);
	}
}
