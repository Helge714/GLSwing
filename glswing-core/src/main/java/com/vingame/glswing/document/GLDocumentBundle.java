package com.vingame.glswing.document;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Versioned ZIP container whose indexed entries can be decoded independently.
 * Unopened and unrecognized payloads are copied verbatim during a save.
 */
public final class GLDocumentBundle {
	public static final int CONTAINER_VERSION = 1;
	public static final String MANIFEST_LOCATION = "META-INF/glswing-document.properties";
	@FunctionalInterface public interface Listener {
		void bundleChanged(GLDocumentBundle bundle);
	}
	private final GLDocumentCodecRegistry codecs;
	private final LinkedHashMap<String, GLDocumentEntryDescriptor> descriptors =
			new LinkedHashMap<>();
	private final Map<String, GLDocumentEntry<?>> loaded = new LinkedHashMap<>();
	private final Set<String> removedPayloads = new HashSet<>();
	private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
	private String formatId;
	private int formatVersion;
	private String createdWith;
	private Path path;
	private boolean structureModified;

	private GLDocumentBundle(String formatId, int formatVersion, String createdWith,
			Path path, GLDocumentCodecRegistry codecs) {
		validateFormat(formatId, formatVersion, createdWith);
		if (codecs == null) throw new IllegalArgumentException("Codec registry required");
		this.formatId = formatId; this.formatVersion = formatVersion;
		this.createdWith = createdWith; this.path = normalize(path); this.codecs = codecs;
	}

	/** Creates a new unsaved bundle. */
	public static GLDocumentBundle create(String formatId, int formatVersion,
			String createdWith, GLDocumentCodecRegistry codecs) {
		return new GLDocumentBundle(formatId, formatVersion, createdWith, null, codecs);
	}
	/** Opens only the manifest; entry payloads remain unloaded until requested. */
	public static GLDocumentBundle open(Path path, GLDocumentCodecRegistry codecs)
			throws IOException {
		Path source = normalizeExisting(path);
		Properties manifest = new Properties();
		try (ZipFile archive = new ZipFile(source.toFile())) {
			ZipEntry entry = archive.getEntry(MANIFEST_LOCATION);
			if (entry == null) throw new GLDocumentFormatException("Bundle manifest is missing");
			try (InputStream input = new BufferedInputStream(archive.getInputStream(entry))) {
				manifest.load(input);
			}
		}
		int containerVersion = integer(manifest, "container.version");
		if (containerVersion != CONTAINER_VERSION)
			throw new GLDocumentFormatException("Unsupported container version: "
					+ containerVersion);
		GLDocumentBundle bundle;
		try {
			bundle = new GLDocumentBundle(required(manifest, "format.id"),
					integer(manifest, "format.version"), required(manifest, "created.with"),
					source, codecs);
		} catch (IllegalArgumentException failure) {
			throw new GLDocumentFormatException("Invalid bundle format metadata", failure);
		}
		int count = integer(manifest, "entry.count");
		for (int index = 0; index < count; index++) {
			String prefix = "entry." + index + ".";
			GLDocumentEntryDescriptor descriptor;
			try {
				descriptor = new GLDocumentEntryDescriptor(required(manifest, prefix + "id"),
						required(manifest, prefix + "type"), required(manifest, prefix + "codec"),
						integer(manifest, prefix + "schema"),
						required(manifest, prefix + "location"));
			} catch (IllegalArgumentException failure) {
				throw new GLDocumentFormatException("Invalid entry " + index, failure);
			}
			if (bundle.descriptors.putIfAbsent(descriptor.id(), descriptor) != null)
				throw new GLDocumentFormatException("Duplicate entry ID: " + descriptor.id());
		}
		if (bundle.descriptors.values().stream().map(GLDocumentEntryDescriptor::location)
				.distinct().count() != bundle.descriptors.size())
			throw new GLDocumentFormatException("Multiple entries use the same payload location");
		return bundle;
	}

	public String formatId() { return formatId; }
	public int formatVersion() { return formatVersion; }
	public void setFormatVersion(int value) {
		if (value < 1) throw new IllegalArgumentException("Format version must be positive");
		if (formatVersion != value) { formatVersion = value; structureModified = true; fire(); }
	}
	public String createdWith() { return createdWith; }
	public void setCreatedWith(String value) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("Version required");
		if (!createdWith.equals(value)) { createdWith = value; structureModified = true; fire(); }
	}
	public Path path() { return path; }
	public List<GLDocumentEntryDescriptor> entries() {
		return List.copyOf(descriptors.values());
	}
	public boolean contains(String id) { return descriptors.containsKey(id); }
	public boolean isLoaded(String id) { return loaded.containsKey(id); }
	public boolean isModified() {
		return structureModified || loaded.values().stream()
				.anyMatch(entry -> entry.document().isModified());
	}

	/** Decodes one indexed entry and caches its document for subsequent calls. */
	public synchronized <T> GLDocumentEntry<T> open(String id, Class<T> contentType)
			throws IOException {
		GLDocumentEntryDescriptor descriptor = descriptor(id);
		GLDocumentEntry<?> existing = loaded.get(id);
		if (existing != null) return cast(existing, contentType);
		if (path == null) throw new GLDocumentFormatException("Entry has no persisted payload: " + id);
		GLDocumentCodec<T> codec = codecs.require(descriptor.typeId(), descriptor.codecId(),
				contentType);
		if (!codec.supportsSchemaVersion(descriptor.schemaVersion()))
			throw new GLDocumentSchemaException(descriptor.typeId(),
					descriptor.schemaVersion(), codec.currentSchemaVersion());
		GLDocumentReadResult<T> decoded;
		try (ZipFile archive = new ZipFile(path.toFile())) {
			ZipEntry payload = archive.getEntry(descriptor.location());
			if (payload == null) throw new GLDocumentFormatException("Payload is missing: " + id);
			try (InputStream input = new BufferedInputStream(archive.getInputStream(payload))) {
				decoded = codec.readResult(input, descriptor.schemaVersion());
			}
		}
		GLDocument<T> document = new GLDocument<>(decoded.content(), title(id), entryLocation(descriptor),
				new com.vingame.glswing.history.GLHistoryManager());
		GLDocumentEntry<T> result = new GLDocumentEntry<>(descriptor, document);
		loaded.put(id, result); document.addListener(ignored -> fire());
		if (decoded.migrated()) {
			structureModified = true;
			document.markModified();
		}
		return result;
	}

	/** Adds or replaces an entry using a registered codec. */
	public synchronized <T> GLDocumentEntry<T> put(String id, T content,
			GLDocumentCodec<T> codec) throws GLDocumentFormatException {
		if (content == null || codec == null || !codec.contentType().isInstance(content))
			throw new IllegalArgumentException("Content does not match codec");
		GLDocumentCodec<T> registered = codecs.require(codec.typeId(), codec.codecId(),
				codec.contentType());
		if (registered != codec)
			throw new IllegalArgumentException("Use the codec instance registered for these IDs");
		String location = descriptors.containsKey(id) ? descriptors.get(id).location()
				: uniqueLocation(id, codec);
		GLDocumentEntryDescriptor descriptor = new GLDocumentEntryDescriptor(id, codec.typeId(),
				codec.codecId(), codec.currentSchemaVersion(), location);
		GLDocument<T> document = new GLDocument<>(content, title(id));
		document.markModified(); document.addListener(ignored -> fire());
		GLDocumentEntry<T> entry = new GLDocumentEntry<>(descriptor, document);
		descriptors.put(id, descriptor); loaded.put(id, entry);
		structureModified = true; fire(); return entry;
	}
	public synchronized boolean remove(String id) {
		GLDocumentEntryDescriptor removed = descriptors.remove(id);
		if (removed == null) return false;
		removedPayloads.add(removed.location());
		loaded.remove(id); structureModified = true; fire(); return true;
	}
	/** Saves to the bundle's current path. */
	public synchronized void save() throws IOException {
		if (path == null) throw new IllegalStateException("Bundle has not been assigned a path");
		save(path);
	}

	/** Writes every changed entry and preserves all unloaded archive payloads. */
	public synchronized void save(Path target) throws IOException {
		Path destination = normalize(target);
		if (destination == null || destination.getParent() == null)
			throw new IllegalArgumentException("Bundle destination requires a parent directory");
		Files.createDirectories(destination.getParent());
		Path temporary = Files.createTempFile(destination.getParent(),
				"." + destination.getFileName() + ".", ".tmp");
		Map<String, GLDocumentEntryDescriptor> persistedDescriptors = persistedDescriptors();
		try {
			try (OutputStream file = new BufferedOutputStream(Files.newOutputStream(temporary));
					ZipOutputStream output = new ZipOutputStream(file)) {
				writeManifest(output, persistedDescriptors);
				copyUnchangedPayloads(output, persistedDescriptors);
				for (Map.Entry<String, GLDocumentEntry<?>> loadedEntry : loaded.entrySet())
					writeLoaded(output, persistedDescriptors.get(loadedEntry.getKey()),
							loadedEntry.getValue());
			}
			moveReplacing(temporary, destination);
			path = destination; descriptors.clear(); descriptors.putAll(persistedDescriptors);
			Map<String, GLDocumentEntry<?>> updatedLoaded = new LinkedHashMap<>();
			for (GLDocumentEntry<?> entry : loaded.values()) {
				GLDocumentEntryDescriptor descriptor =
						persistedDescriptors.get(entry.descriptor().id());
				entry.document().setLocation(entryLocation(descriptor));
				entry.document().markPersisted();
				updatedLoaded.put(entry.descriptor().id(), new GLDocumentEntry<>(
						descriptor, entry.document()));
			}
			loaded.clear(); loaded.putAll(updatedLoaded); removedPayloads.clear();
			structureModified = false; fire();
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
	public void addListener(Listener listener) {
		if (listener == null) throw new IllegalArgumentException("Listener required");
		listeners.addIfAbsent(listener);
	}
	public void removeListener(Listener listener) { listeners.remove(listener); }

	private Map<String, GLDocumentEntryDescriptor> persistedDescriptors()
			throws GLDocumentFormatException {
		LinkedHashMap<String, GLDocumentEntryDescriptor> result = new LinkedHashMap<>();
		for (GLDocumentEntryDescriptor descriptor : descriptors.values()) {
			GLDocumentEntry<?> entry = loaded.get(descriptor.id());
			if (entry == null) result.put(descriptor.id(), descriptor);
			else {
				GLDocumentCodec<?> codec = codecs.find(descriptor.typeId(), descriptor.codecId())
						.orElseThrow(() -> new GLDocumentFormatException(
								"Codec disappeared while saving: " + descriptor.typeId()));
				result.put(descriptor.id(), new GLDocumentEntryDescriptor(descriptor.id(),
						descriptor.typeId(), descriptor.codecId(), codec.currentSchemaVersion(),
						descriptor.location()));
			}
		}
		return result;
	}
	private void writeManifest(ZipOutputStream output,
			Map<String, GLDocumentEntryDescriptor> persisted) throws IOException {
		Properties manifest = new Properties();
		manifest.setProperty("container.version", Integer.toString(CONTAINER_VERSION));
		manifest.setProperty("format.id", formatId);
		manifest.setProperty("format.version", Integer.toString(formatVersion));
		manifest.setProperty("created.with", createdWith);
		manifest.setProperty("entry.count", Integer.toString(persisted.size()));
		int index = 0;
		for (GLDocumentEntryDescriptor descriptor : persisted.values()) {
			String prefix = "entry." + index++ + ".";
			manifest.setProperty(prefix + "id", descriptor.id());
			manifest.setProperty(prefix + "type", descriptor.typeId());
			manifest.setProperty(prefix + "codec", descriptor.codecId());
			manifest.setProperty(prefix + "schema", Integer.toString(descriptor.schemaVersion()));
			manifest.setProperty(prefix + "location", descriptor.location());
		}
		output.putNextEntry(new ZipEntry(MANIFEST_LOCATION));
		manifest.store(output, "GLSwing document bundle"); output.closeEntry();
	}
	private void copyUnchangedPayloads(ZipOutputStream output,
			Map<String, GLDocumentEntryDescriptor> persisted) throws IOException {
		if (path == null || !Files.isRegularFile(path)) return;
		var rewritten = loaded.keySet().stream().map(persisted::get)
				.filter(java.util.Objects::nonNull).map(GLDocumentEntryDescriptor::location)
				.collect(java.util.stream.Collectors.toSet());
		var retained = persisted.values().stream().map(GLDocumentEntryDescriptor::location)
				.collect(java.util.stream.Collectors.toSet());
		try (ZipFile archive = new ZipFile(path.toFile())) {
			Enumeration<? extends ZipEntry> entries = archive.entries();
			while (entries.hasMoreElements()) {
				ZipEntry source = entries.nextElement(); String name = source.getName();
				if (source.isDirectory() || MANIFEST_LOCATION.equals(name) || rewritten.contains(name)
						|| removedPayloads.contains(name))
					continue;
				boolean indexed = descriptors.values().stream()
						.anyMatch(descriptor -> descriptor.location().equals(name));
				if (indexed && !retained.contains(name)) continue;
				output.putNextEntry(new ZipEntry(name));
				try (InputStream input = archive.getInputStream(source)) { input.transferTo(output); }
				output.closeEntry();
			}
		}
	}
	private void writeLoaded(ZipOutputStream output, GLDocumentEntryDescriptor descriptor,
			GLDocumentEntry<?> entry) throws IOException {
		GLDocumentCodec<?> codec = codecs.find(descriptor.typeId(), descriptor.codecId())
				.orElseThrow(() -> new GLDocumentFormatException("Missing codec while saving "
						+ descriptor.id()));
		output.putNextEntry(new ZipEntry(descriptor.location()));
		writeUnchecked(codec, entry.document().content(), output); output.closeEntry();
	}
	private String uniqueLocation(String id, GLDocumentCodec<?> codec)
			throws GLDocumentFormatException {
		String extension = codec.fileExtensions().stream().findFirst().orElse("data");
		if (!extension.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) extension = "data";
		String base = "entries/" + id + "." + extension; String candidate = base;
		Set<String> occupied = new HashSet<>();
		descriptors.values().stream().map(GLDocumentEntryDescriptor::location)
				.forEach(occupied::add);
		if (path != null && Files.isRegularFile(path)) try (ZipFile archive = new ZipFile(path.toFile())) {
			Enumeration<? extends ZipEntry> entries = archive.entries();
			while (entries.hasMoreElements()) occupied.add(entries.nextElement().getName());
		} catch (IOException failure) {
			throw new GLDocumentFormatException("Cannot inspect existing bundle payloads", failure);
		}
		int suffix = 2;
		while (occupied.contains(candidate))
			candidate = base + "." + suffix++;
		return candidate;
	}
	private GLDocumentEntryDescriptor descriptor(String id) throws GLDocumentFormatException {
		GLDocumentEntryDescriptor result = descriptors.get(id);
		if (result == null) throw new GLDocumentFormatException("Unknown bundle entry: " + id);
		return result;
	}
	private static <T> GLDocumentEntry<T> cast(GLDocumentEntry<?> entry, Class<T> type)
			throws GLDocumentFormatException {
		if (!type.isInstance(entry.document().content()))
			throw new GLDocumentFormatException("Loaded entry does not contain " + type.getName());
		@SuppressWarnings("unchecked") GLDocumentEntry<T> result = (GLDocumentEntry<T>) entry;
		return result;
	}
	private static void writeUnchecked(GLDocumentCodec<?> codec, Object content,
			OutputStream output) throws IOException {
		writeCaptured(codec, content, output);
	}
	private static <T> void writeCaptured(GLDocumentCodec<T> codec, Object content,
			OutputStream output) throws IOException {
		codec.write(codec.contentType().cast(content), output);
	}
	private static String required(Properties properties, String key)
			throws GLDocumentFormatException {
		String value = properties.getProperty(key);
		if (value == null || value.isBlank()) throw new GLDocumentFormatException(
				"Manifest property is missing: " + key);
		return value;
	}
	private static int integer(Properties properties, String key)
			throws GLDocumentFormatException {
		try {
			int result = Integer.parseInt(required(properties, key));
			if (result < 0) throw new NumberFormatException();
			return result;
		} catch (NumberFormatException failure) {
			throw new GLDocumentFormatException("Invalid integer property: " + key, failure);
		}
	}
	private static void validateFormat(String id, int version, String createdWith) {
		if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]*") || version < 1
				|| createdWith == null || createdWith.isBlank())
			throw new IllegalArgumentException("Invalid bundle format metadata");
	}
	private static Path normalize(Path value) {
		return value == null ? null : value.toAbsolutePath().normalize();
	}
	private static Path normalizeExisting(Path value) throws IOException {
		Path result = normalize(value);
		if (result == null || !Files.isRegularFile(result))
			throw new IOException("Bundle file does not exist: " + result);
		return result;
	}
	private static String title(String id) {
		int slash = id.lastIndexOf('/'); return slash < 0 ? id : id.substring(slash + 1);
	}
	private java.net.URI entryLocation(GLDocumentEntryDescriptor descriptor) {
		return path == null ? null : java.net.URI.create("jar:" + path.toUri().toASCIIString()
				+ "!/" + descriptor.location());
	}
	private static void moveReplacing(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException failure) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
	private void fire() { for (Listener listener : listeners) listener.bundleChanged(this); }
}
