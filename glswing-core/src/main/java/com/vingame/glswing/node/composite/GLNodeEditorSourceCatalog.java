package com.vingame.glswing.node.composite;

import java.util.*;
import java.util.function.*;
import com.vingame.glswing.node.model.*;

/** Discovers copyable standalone editors, grouped by node type and property instead of instance. */
public final class GLNodeEditorSourceCatalog {
    private GLNodeEditorSourceCatalog() { }
    public record Candidate<T>(String property, String label, T editor, GLNodeEditorCapabilities capabilities) { }
    public record Source<T>(String type, String property, String label, T editor, List<GLNodeEndpoint> endpoints) {
        public Source { endpoints = List.copyOf(endpoints); }
    }
    public static <T> List<Source<T>> collect(GLNodeGraphSnapshot graph,
            Function<GLGraphNode,List<Candidate<T>>> provider, Predicate<GLNodeEditorCapabilities> allowed) {
        record Key(String type, String property) { }
        var result = new LinkedHashMap<Key,Source<T>>();
        for (var node : graph.nodes()) for (var candidate : provider.apply(node)) {
            if (!candidate.capabilities().linkable() || !allowed.test(candidate.capabilities())) continue;
            var key = new Key(node.typeId(), candidate.property());
            var old = result.get(key);
            var endpoints = new ArrayList<GLNodeEndpoint>();
            if (old != null) endpoints.addAll(old.endpoints());
            endpoints.add(new GLNodeEndpoint(node.id(), candidate.property()));
            result.put(key, new Source<>(node.typeId(), candidate.property(), candidate.label(), candidate.editor(), endpoints));
        }
        return result.values().stream().sorted(Comparator.comparing((Source<T> s)->s.label())
                .thenComparing(s->s.type()).thenComparing(s->s.property())).toList();
    }
}
