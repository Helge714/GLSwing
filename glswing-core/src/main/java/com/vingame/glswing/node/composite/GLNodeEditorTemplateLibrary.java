package com.vingame.glswing.node.composite;

import java.util.*;
import com.vingame.glswing.node.model.GLNodeValue;

/** Ordered document library, without application-specific registry IDs or supported signal types. */
public final class GLNodeEditorTemplateLibrary {
    private final Map<String,GLNodeEditorTemplate> definitions = new LinkedHashMap<>();
    public List<GLNodeEditorTemplate> all() { return List.copyOf(definitions.values()); }
    public void put(GLNodeEditorTemplate template) { definitions.put(template.id(), template); }
    public void remove(String id) { definitions.remove(id); }
    public GLNodeValue snapshot() { return GLNodeValue.of(all().stream().map(t -> t.value().toJavaValue()).toList()); }
    /** Validate the whole snapshot before replacing a working library. */
    public void load(GLNodeValue value) {
        var next = new LinkedHashMap<String,GLNodeEditorTemplate>();
        if (value != null) for (var raw : (List<?>) value.toJavaValue()) {
            var t = GLNodeEditorTemplate.read(GLNodeValue.of(raw));
            if (next.putIfAbsent(t.id(), t) != null) throw new IllegalArgumentException("Duplicate template ID: " + t.id());
        }
        definitions.clear(); definitions.putAll(next);
    }
}
