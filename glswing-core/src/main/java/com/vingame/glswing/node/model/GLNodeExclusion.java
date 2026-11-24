package com.vingame.glswing.node.model;

import java.util.*;

/** Removes nodes, forwarding their chosen source to every compatible external destination. */
public final class GLNodeExclusion {
    private GLNodeExclusion() { }
    public static Optional<GLNodeGraphSnapshot> plan(GLNodeGraphSnapshot graph, GLNodeTypeRegistry types,
            GLGraphCyclePolicy policy, Set<GLNodeId> selection) {
        if (selection.isEmpty() || !graph.nodesById().keySet().containsAll(selection)) return Optional.empty();
        var incoming=new HashMap<GLNodeId,GLNodeEndpoint>();
        for (var node:graph.nodes()) if (selection.contains(node.id())) {
            var type=types.find(node.typeId()).orElse(null);
            if (type==null) continue;
            var preference=types.exclusionInput(node.typeId()).orElse(null);
            GLNodeEndpoint chosen=connectedSource(graph,node.id(),preference);
            if (chosen==null) for (var port:type.ports()) {
                if (port.direction()!=GLPortDirection.INPUT) continue;
                chosen=connectedSource(graph,node.id(),port.id());
                if (chosen!=null) break;
            }
            if (chosen!=null) incoming.put(node.id(),chosen);
        }
        var nodes=graph.nodes().stream().filter(n->!selection.contains(n.id())).toList();
        var edges=new ArrayList<>(graph.connections().stream().filter(e->
                !selection.contains(e.input().nodeId())&&!selection.contains(e.output().nodeId())).toList());
        for (var edge:graph.connections()) {
            if (!selection.contains(edge.output().nodeId()) || selection.contains(edge.input().nodeId())) continue;
            var source=edge.output();
            var visited=new HashSet<GLNodeId>();
            while (source!=null && selection.contains(source.nodeId())) {
                if (!visited.add(source.nodeId())) { source=null; break; }
                source=incoming.get(source.nodeId());
            }
            if (source==null) continue;
            final var resolved=source;
            var out=port(graph,types,source); var in=port(graph,types,edge.input());
            if (out==null || in==null || out.direction()!=GLPortDirection.OUTPUT
                    || in.direction()!=GLPortDirection.INPUT || !types.compatibleValueTypes(out.valueTypeId(),in.valueTypeId())) continue;
            if (edges.stream().anyMatch(e->e.output().equals(resolved)&&e.input().equals(edge.input()))) continue;
            var replacement=new GLGraphConnection(GLNodeConnectionId.random(),source,edge.input());
            edges.add(replacement);
            var trial=new GLNodeGraphSnapshot(graph.graphId(),graph.revision(),nodes,edges);
            // Capacity/cycle violations skip only this replacement, never the requested deletion.
            boolean invalid=GLNodeGraphValidator.validate(trial,types,policy).diagnostics().stream().anyMatch(d->
                    d.severity()==GLGraphDiagnostic.Severity.ERROR && d.connectionIds().contains(replacement.id()));
            if (invalid) edges.remove(edges.size()-1);
        }
        return Optional.of(new GLNodeGraphSnapshot(graph.graphId(),graph.revision(),nodes,edges));
    }
    private static GLNodeEndpoint connectedSource(GLNodeGraphSnapshot graph, GLNodeId node, String port) {
        if (port==null) return null;
        return graph.connections().stream().filter(e->e.input().nodeId().equals(node)&&e.input().portId().equals(port))
                .map(GLGraphConnection::output).findFirst().orElse(null);
    }
    private static GLNodePortDescriptor port(GLNodeGraphSnapshot graph, GLNodeTypeRegistry types, GLNodeEndpoint endpoint) {
        return graph.findNode(endpoint.nodeId()).flatMap(n->types.find(n.typeId())).flatMap(t->t.findPort(endpoint.portId())).orElse(null);
    }
}
