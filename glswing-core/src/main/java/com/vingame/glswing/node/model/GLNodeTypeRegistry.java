package com.vingame.glswing.node.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Thread-safe registry of node contracts used by validation and compilation. */
public final class GLNodeTypeRegistry {
    private java.util.function.BiPredicate<String,String> valueTypeCompatibility=String::equals;
    /** Optional application conversion contract; default remains strict type equality. */
    public synchronized void setValueTypeCompatibility(java.util.function.BiPredicate<String,String> policy){valueTypeCompatibility=java.util.Objects.requireNonNull(policy);}
    public synchronized boolean compatibleValueTypes(String source,String target){return valueTypeCompatibility.test(source,target);}

    private final Map<String,String> exclusionInputs = new LinkedHashMap<>();
    /** Preferred input for exclusion; an unconnected preference falls back to the first connected input. */
    public synchronized void setExclusionInput(String typeId, String inputPortId) {
        var type=types.get(typeId);
        if (type==null) throw new IllegalArgumentException("Registered node type required");
        if (inputPortId==null) { exclusionInputs.remove(typeId); return; }
        var port=type.findPort(inputPortId).orElse(null);
        if (port==null || port.direction()!=GLPortDirection.INPUT)
            throw new IllegalArgumentException("Exclusion input must be an input port");
        exclusionInputs.put(typeId,inputPortId);
    }
    public synchronized Optional<String> exclusionInput(String typeId) {
        return Optional.ofNullable(exclusionInputs.get(typeId));
    }
    private final Map<String, GLNodeInsertionPorts> insertionPorts = new LinkedHashMap<>();

    /** Null disables wire insertion; no ports are guessed for unconfigured types. */
    public synchronized void setInsertionPorts(String typeId, GLNodeInsertionPorts ports) {
        GLNodeTypeDescriptor type = types.get(typeId);
        if (type == null) throw new IllegalArgumentException("Registered node type required");
        if (ports == null) { insertionPorts.remove(typeId); return; }
        var input = type.findPort(ports.inputPortId()).orElse(null);
        var output = type.findPort(ports.outputPortId()).orElse(null);
        if (input == null || output == null || input.direction() != GLPortDirection.INPUT
                || output.direction() != GLPortDirection.OUTPUT)
            throw new IllegalArgumentException("Insertion requires an input and an output");
        insertionPorts.put(typeId, ports);
    }

    public synchronized Optional<GLNodeInsertionPorts> insertionPorts(String typeId) {
        return Optional.ofNullable(insertionPorts.get(typeId));
    }
	private final Map<String, GLNodeTypeDescriptor> types = new LinkedHashMap<>();
	private final Map<String, List<GLNodeMuteBypass>> muteBypasses =
			new LinkedHashMap<>();

	public synchronized void register(GLNodeTypeDescriptor descriptor) {
		if (descriptor == null)
			throw new IllegalArgumentException("Node-type descriptor required");
		if (types.putIfAbsent(descriptor.id(), descriptor) != null)
			throw new IllegalArgumentException(
					"Node type is already registered: " + descriptor.id());
	}

	/** Replaces a registered contract while preserving its stable type ID. */
	public synchronized void replace(GLNodeTypeDescriptor descriptor) {
		if (descriptor == null || !types.containsKey(descriptor.id()))
			throw new IllegalArgumentException("Registered node type required");
		types.put(descriptor.id(), descriptor);
	}

	public synchronized boolean unregister(String typeId) {
		exclusionInputs.remove(typeId);
		insertionPorts.remove(typeId);
		muteBypasses.remove(typeId);
		return types.remove(typeId) != null;
	}

	public synchronized Optional<GLNodeTypeDescriptor> find(String typeId) {
		return Optional.ofNullable(types.get(typeId));
	}

	public synchronized List<GLNodeTypeDescriptor> descriptors() {
		return List.copyOf(types.values());
	}

	/** Sets an explicit input-to-output mapping used while this type is muted. */
	public synchronized void setMuteBypasses(String typeId,
			List<GLNodeMuteBypass> mappings) {
		GLNodeTypeDescriptor type = types.get(typeId);
		if (type == null || mappings == null)
			throw new IllegalArgumentException("Registered node type and mappings required");
		for (GLNodeMuteBypass mapping : mappings) {
			GLNodePortDescriptor input = type.findPort(mapping.inputPortId()).orElse(null);
			GLNodePortDescriptor output = type.findPort(mapping.outputPortId()).orElse(null);
			if (input == null || output == null || input.direction() != GLPortDirection.INPUT
					|| output.direction() != GLPortDirection.OUTPUT
					|| !compatibleValueTypes(input.valueTypeId(),output.valueTypeId()))
				throw new IllegalArgumentException(
						"Mute bypass requires compatible input and output ports");
		}
		muteBypasses.put(typeId, List.copyOf(mappings));
	}

	/** Returns explicit mappings or the first compatible input/output pair. */
	public synchronized List<GLNodeMuteBypass> muteBypasses(String typeId) {
		List<GLNodeMuteBypass> configured = muteBypasses.get(typeId);
		if (configured != null) return configured;
		GLNodeTypeDescriptor type = types.get(typeId);
		if (type == null) return List.of();
		for (GLNodePortDescriptor input : type.ports()) {
			if (input.direction() != GLPortDirection.INPUT) continue;
			for (GLNodePortDescriptor output : type.ports())
				if (output.direction() == GLPortDirection.OUTPUT
						&& compatibleValueTypes(input.valueTypeId(),output.valueTypeId()))
					return List.of(new GLNodeMuteBypass(input.id(), output.id()));
		}
		return List.of();
	}

	public synchronized Map<String, String> muteBypassMappings(String typeId) {
		LinkedHashMap<String, String> result = new LinkedHashMap<>();
		for (GLNodeMuteBypass mapping : muteBypasses(typeId))
			result.put(mapping.inputPortId(), mapping.outputPortId());
		return Map.copyOf(result);
	}
}
