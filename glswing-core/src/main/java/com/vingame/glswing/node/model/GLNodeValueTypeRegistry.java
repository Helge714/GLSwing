package com.vingame.glswing.node.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Thread-safe display registry for application-defined node value types. */
public final class GLNodeValueTypeRegistry {
	private final Map<String, GLNodeValueTypeDescriptor> entries =
			new LinkedHashMap<>();

	public synchronized void register(GLNodeValueTypeDescriptor descriptor) {
		if (descriptor == null) throw new IllegalArgumentException("Value type required");
		if (entries.putIfAbsent(descriptor.id(), descriptor) != null)
			throw new IllegalArgumentException("Value type is already registered: "
					+ descriptor.id());
	}

	public synchronized void replace(GLNodeValueTypeDescriptor descriptor) {
		if (descriptor == null || !entries.containsKey(descriptor.id()))
			throw new IllegalArgumentException("Registered value type required");
		entries.put(descriptor.id(), descriptor);
	}

	public synchronized Optional<GLNodeValueTypeDescriptor> find(String id) {
		return Optional.ofNullable(entries.get(id));
	}

	public synchronized List<GLNodeValueTypeDescriptor> entries() {
		return List.copyOf(entries.values());
	}
}
