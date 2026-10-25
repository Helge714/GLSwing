package com.vingame.glswing.node.composite;

import java.util.*;
import com.vingame.glswing.node.binding.*;
import com.vingame.glswing.node.document.*;
import com.vingame.glswing.node.model.*;

/** Expands one instance, retaining nested instances and the reusable definition. */
public final class GLCompositeUngroup {
    private GLCompositeUngroup() {}
    public record Result(GLNodeGraphSnapshot graph, GLNodeEditorViewState viewState,
            Map<GLNodeId,GLNodeId> nodeIds, Map<String,List<GLNodeEndpoint>> ports) {
        public List<GLNodeEndpoint> resolve(GLNodeEndpoint endpoint, GLNodeId instance) {
            return endpoint.nodeId().equals(instance) ? ports.getOrDefault(endpoint.portId(),List.of()) : List.of(endpoint);
        }
        /** Rebind the containing group's boundary when expanding an instance inside it. */
        public GLCompositeNodeDescriptor remap(GLCompositeNodeDescriptor parent, GLNodeId instance,
                GLCompositeNodeDescriptor child) {
            if(parent==null)return null;
            var mappings=new ArrayList<GLCompositePropertyMapping>();
            for(var p:parent.propertyMappings()) {
                if(!p.internalNodeId().equals(instance)){mappings.add(p);continue;}
                for(var inner:child.propertyMappings())if(inner.publicPropertyId().equals(p.internalPropertyId())) {
                    Map<String,GLNodeValue> values=inner.values();
                    if(!p.values().isEmpty()){
                        values=new LinkedHashMap<>();
                        for(var entry:p.values().entrySet()) {
                            var value=inner.mappedValue(entry.getValue());
                            if(value==null)throw new IllegalArgumentException("Incompatible nested property mapping");
                            values.put(entry.getKey(),value);
                        }
                    }
                    mappings.add(new GLCompositePropertyMapping(p.publicPropertyId(),nodeIds.get(inner.internalNodeId()),inner.internalPropertyId(),values));
                }
            }
            return new GLCompositeNodeDescriptor(parent.nodeTypeId(),parent.assetId(),parent.minimumAssetVersion(),
                parent.portMappings().stream().map(p->p.withEndpoints(p.internalEndpoints().stream().flatMap(e->{
                    var targets=resolve(e,instance);
                    if(!e.nodeId().equals(instance))return targets.stream();
                    return targets.stream().flatMap(target->{
                        if(!GLCompositeForwarding.isInput(target))return java.util.stream.Stream.of(target);
                        return parent.portMappings().stream().filter(input->input.publicPort().direction()==GLPortDirection.INPUT
                            &&input.internalEndpoints().contains(new GLNodeEndpoint(instance,target.portId())))
                            .map(input->GLCompositeForwarding.input(input.publicPort().id()));
                    });
                }).toList())).toList(),parent.properties(),mappings);
        }
    }
    public static Result expand(GLNodeGraphSnapshot parent,GLNodeEditorViewState view,
            GLGraphNode instance,GLNodeGraphAsset asset) {
        asset=GLCompositeForwarding.materialize(asset);
        var descriptor=Objects.requireNonNull(asset.compositeDescriptor());
        if(!instance.typeId().equals(descriptor.nodeTypeId())||parent.findNode(instance.id()).isEmpty())
            throw new IllegalArgumentException("Instance does not match composite");
        var ids=new LinkedHashMap<GLNodeId,GLNodeId>();
        asset.graph().nodes().forEach(n->ids.put(n.id(),GLNodeId.random()));
        var children=new LinkedHashMap<>(asset.graph().nodesById());
        for(var property:descriptor.properties()) {
            var value=instance.properties().getOrDefault(property.id(),property.defaultValue());
            if(!property.accepts(value))throw new IllegalArgumentException("Invalid composite property: "+property.id());
            for(var mapping:descriptor.propertyMappings())if(mapping.publicPropertyId().equals(property.id())){
                var target=children.get(mapping.internalNodeId());var mapped=mapping.mappedValue(value);
                if(target==null||mapped==null)throw new IllegalArgumentException("Invalid composite property target: "+property.id());
                children.put(target.id(),target.withExplicitProperty(mapping.internalPropertyId(),mapped));
            }
        }
        java.util.function.Function<GLNodeEndpoint,GLNodeEndpoint> clone=e->{
            var id=ids.get(e.nodeId());if(id==null)throw new IllegalArgumentException("Missing composite endpoint");
            return new GLNodeEndpoint(id,e.portId());
        };
        var ports=new LinkedHashMap<String,List<GLNodeEndpoint>>();
        descriptor.portMappings().forEach(p->ports.put(p.publicPort().id(),p.internalEndpoints().stream().map(clone).toList()));
        var nodes=new ArrayList<>(parent.nodes().stream().filter(n->!n.id().equals(instance.id())).toList());
        children.values().forEach(n->nodes.add(new GLGraphNode(ids.get(n.id()),n.typeId(),n.typeVersion(),n.properties())));
        var wires=new ArrayList<GLGraphConnection>();
        asset.graph().connections().forEach(c->wires.add(new GLGraphConnection(GLNodeConnectionId.random(),clone.apply(c.output()),clone.apply(c.input()))));
        for(var c:parent.connections()) {
            boolean output=c.output().nodeId().equals(instance.id()),input=c.input().nodeId().equals(instance.id());
            if(!output&&!input){wires.add(c);continue;}
            var outs=output?ports.getOrDefault(c.output().portId(),List.of()):List.of(c.output());
            var ins=input?ports.getOrDefault(c.input().portId(),List.of()):List.of(c.input());
            for(var o:outs)for(var i:ins)wires.add(new GLGraphConnection(GLNodeConnectionId.random(),o,i));
        }
        var anchors=nodes.stream().filter(n->n.typeId().equals(GLCompositeForwarding.TYPE)).map(GLGraphNode::id).collect(java.util.stream.Collectors.toSet());
        if(!anchors.isEmpty()){
            var raw=List.copyOf(wires);
            for(var p:descriptor.portMappings()){
                var resolved=ports.get(p.publicPort().id()).stream().flatMap(e->GLCompositeForwarding.resolve(e,raw,anchors,p.publicPort().direction()==GLPortDirection.INPUT).stream()).distinct().toList();
                // Parent boundary mappings are not graph wires. Retain their forwarding reference
                // until remap can translate the child's public input to the parent's input.
                if(resolved.isEmpty()&&p.publicPort().direction()==GLPortDirection.OUTPUT&&p.isBound()
                        &&anchors.contains(ids.get(p.internalEndpoint().nodeId()))){
                    var source=descriptor.portMappings().stream().filter(input->input.publicPort().direction()==GLPortDirection.INPUT
                        &&input.internalEndpoints().stream().anyMatch(e->e.nodeId().equals(p.internalEndpoint().nodeId()))).findFirst();
                    if(source.isPresent())resolved=List.of(GLCompositeForwarding.input(source.get().publicPort().id()));
                }
                ports.put(p.publicPort().id(),resolved);
            }
            var collapsed=GLCompositeForwarding.collapse(wires,anchors,100000);wires.clear();wires.addAll(collapsed);
            nodes.removeIf(n->anchors.contains(n.id()));ids.entrySet().removeIf(e->anchors.contains(e.getValue()));
        }
        var positions=new LinkedHashMap<>(view.nodes());var origin=positions.remove(instance.id());
        if(origin==null)origin=new GLNodeViewState(0,0);
        // Boundary projections live in auxiliaryNodes and are intentionally excluded.
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY;
        float maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
        for(var id:ids.keySet()){
            var b=asset.viewState().nodes().get(id);if(b==null)continue;
            minX=Math.min(minX,b.x());minY=Math.min(minY,b.y());
            maxX=Math.max(maxX,b.x()+b.width());maxY=Math.max(maxY,b.y()+b.height());
        }
        if(!Float.isFinite(minX)){minX=0;minY=0;maxX=0;maxY=0;}
        float offsetX=origin.x()+origin.width()/2-(minX+maxX)/2;
        float offsetY=origin.y()+origin.height()/2-(minY+maxY)/2;
        int missing=0;
        for(var entry:ids.entrySet()){
            var b=asset.viewState().nodes().get(entry.getKey());
            if(b==null)b=new GLNodeViewState(minX-240,minY+100*missing++);
            positions.put(entry.getValue(),new GLNodeViewState(offsetX+b.x(),offsetY+b.y(),b.width(),b.height(),b.customTitle()));
        }
        return new Result(new GLNodeGraphSnapshot(parent.graphId(),parent.revision(),nodes,wires),
            new GLNodeEditorViewState(positions,view.auxiliaryNodes()),Map.copyOf(ids),Map.copyOf(ports));
    }
}
