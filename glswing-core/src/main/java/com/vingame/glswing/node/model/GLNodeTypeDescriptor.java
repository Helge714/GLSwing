package com.vingame.glswing.node.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable, presentation-independent contract of one node type. */
public record GLNodeTypeDescriptor(String id,
		List<GLNodePortDescriptor> ports) {
	public GLNodeTypeDescriptor {
		if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]*")
				|| ports == null)
			throw new IllegalArgumentException("Invalid node-type descriptor");
		ports = List.copyOf(ports);
		Map<String, GLNodePortDescriptor> unique = new LinkedHashMap<>();
		for (GLNodePortDescriptor port : ports)
			if (port == null || unique.putIfAbsent(port.id(), port) != null)
				throw new IllegalArgumentException(
						"Node port IDs must be unique within a type");
	}

	public GLNodeTypeDescriptor(String id, GLNodePortDescriptor... ports) {
		this(id, List.of(ports));
	}

	public Optional<GLNodePortDescriptor> findPort(String portId) {
		return ports.stream().filter(port -> port.id().equals(portId)).findFirst();
	}

	public Map<String, GLNodePortDescriptor> portsById() {
		LinkedHashMap<String, GLNodePortDescriptor> result = new LinkedHashMap<>();
		for (GLNodePortDescriptor port : ports) result.put(port.id(), port);
		return Collections.unmodifiableMap(result);
	}
}
