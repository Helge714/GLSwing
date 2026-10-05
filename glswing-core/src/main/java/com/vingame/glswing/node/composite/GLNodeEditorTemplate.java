package com.vingame.glswing.node.composite;

import java.util.*;
import com.vingame.glswing.node.model.GLNodeValue;

/** Portable choice-editor definition; entry IDs are distinct from emitted values. */
public record GLNodeEditorTemplate(String id, String label, List<Entry> entries, String initial) {
    public record Entry(String id, String label, GLNodeValue value) {
        public Entry { Objects.requireNonNull(id); Objects.requireNonNull(label); Objects.requireNonNull(value); }
    }
    public GLNodeEditorTemplate {
        Objects.requireNonNull(id); Objects.requireNonNull(label); Objects.requireNonNull(initial);
        entries = List.copyOf(entries);
        if (entries.isEmpty() || entries.stream().noneMatch(e -> e.id().equals(initial))
                || entries.stream().map(Entry::id).distinct().count() != entries.size())
            throw new IllegalArgumentException("Unique entries and a valid default required");
    }
    public GLNodeValue value() {
        return GLNodeValue.of(Map.of("id", id, "label", label, "initial", initial, "entries",
                entries.stream().map(e -> Map.of("id",e.id(),"label",e.label(),"value",e.value().toJavaValue())).toList()));
    }
    public static GLNodeEditorTemplate read(GLNodeValue value) {
        var m = (Map<?,?>) value.toJavaValue();
        var entries = new ArrayList<Entry>();
        for (var raw : (List<?>) m.get("entries")) {
            var e = (Map<?,?>) raw;
            entries.add(new Entry((String)e.get("id"), (String)e.get("label"), GLNodeValue.of(e.get("value"))));
        }
        return new GLNodeEditorTemplate((String)m.get("id"), (String)m.get("label"), entries, (String)m.get("initial"));
    }
    public Map<String,GLNodeValue> mapping() {
        var result = new LinkedHashMap<String,GLNodeValue>();
        entries.forEach(e -> result.put(e.id(), e.value()));
        return Collections.unmodifiableMap(result);
    }
}
