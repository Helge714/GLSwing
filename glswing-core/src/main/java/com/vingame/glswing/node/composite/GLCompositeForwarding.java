package com.vingame.glswing.node.composite;

import java.nio.charset.StandardCharsets;
import java.util.*;
import com.vingame.glswing.node.document.*;
import com.vingame.glswing.node.model.*;

/** A public output can reference a public input without introducing an execution node. */
public final class GLCompositeForwarding {
    private static final GLNodeId INPUTS=new GLNodeId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    public static final String TYPE="glswing.composite.forwarding";
    private GLCompositeForwarding(){}
    public static GLNodeEndpoint input(String port){return new GLNodeEndpoint(INPUTS,port);}
    public static boolean isInput(GLNodeEndpoint endpoint){return endpoint!=null&&endpoint.nodeId().equals(INPUTS);}
    public static boolean valid(GLCompositePortMapping port,List<GLCompositePortMapping> ports){
        return port.publicPort().direction()==GLPortDirection.OUTPUT&&ports.stream().anyMatch(p->p.publicPort().direction()==GLPortDirection.INPUT&&p.publicPort().id().equals(port.internalEndpoint().portId()));
    }
    /** Temporary typed-boundary anchors let application adapters process defaults and units normally.
     * They are removed by flattening; they are never persisted or sent to an execution backend. */
    public static GLNodeGraphAsset materialize(GLNodeGraphAsset asset){
        var d=asset.compositeDescriptor();if(d==null||d.portMappings().stream().noneMatch(p->isInput(p.internalEndpoint())))return asset;
        var anchors=new LinkedHashMap<String,GLNodeId>();var nodes=new ArrayList<>(asset.graph().nodes());
        for(var p:d.portMappings())if(isInput(p.internalEndpoint())){
            if(!valid(p,d.portMappings()))throw new IllegalArgumentException("Invalid public-input forwarding reference");
            var name=p.internalEndpoint().portId();if(anchors.containsKey(name))continue;
            var id=new GLNodeId(UUID.nameUUIDFromBytes(("glswing.forward/"+asset.id()+"/"+name).getBytes(StandardCharsets.UTF_8)));
            if(asset.graph().findNode(id).isPresent())throw new IllegalArgumentException("Forwarding anchor ID collision");
            anchors.put(name,id);nodes.add(new GLGraphNode(id,TYPE,Map.of()));
        }
        var ports=d.portMappings().stream().map(p->{
            if(isInput(p.internalEndpoint()))return p.withEndpoints(List.of(new GLNodeEndpoint(anchors.get(p.internalEndpoint().portId()),"out")));
            var id=anchors.get(p.publicPort().id());if(id==null)return p;
            var targets=new ArrayList<>(p.internalEndpoints());targets.add(new GLNodeEndpoint(id,"in"));return p.withEndpoints(targets);
        }).toList();
        var descriptor=new GLCompositeNodeDescriptor(d.nodeTypeId(),d.assetId(),d.minimumAssetVersion(),ports,d.properties(),d.propertyMappings());
        return new GLNodeGraphAsset(asset.id(),asset.version(),asset.kind(),new GLNodeGraphSnapshot(asset.graph().graphId(),asset.graph().revision(),nodes,asset.graph().connections()),asset.viewState(),asset.metadata(),descriptor);
    }
    /** Resolve either side of temporary anchors, with a visited set to bound forwarding cycles. */
    public static List<GLNodeEndpoint> resolve(GLNodeEndpoint start,List<GLGraphConnection> wires,Set<GLNodeId> anchors,boolean downstream){
        var pending=new ArrayDeque<GLNodeEndpoint>();pending.add(start);var visited=new HashSet<GLNodeId>();var result=new LinkedHashSet<GLNodeEndpoint>();
        while(!pending.isEmpty()){
            var next=pending.removeFirst();if(!anchors.contains(next.nodeId())){result.add(next);continue;}
            if(!visited.add(next.nodeId()))continue;
            for(var wire:wires)if((downstream?wire.output():wire.input()).nodeId().equals(next.nodeId()))pending.add(downstream?wire.input():wire.output());
        }
        return List.copyOf(result);
    }
    public static List<GLGraphConnection> collapse(List<GLGraphConnection> wires,Set<GLNodeId> anchors,long limit){
        if(anchors.isEmpty())return List.copyOf(wires);
        var result=new LinkedHashMap<List<GLNodeEndpoint>,GLGraphConnection>();
        for(var wire:wires){
            if(anchors.contains(wire.output().nodeId()))continue;
            for(var target:resolve(wire.input(),wires,anchors,true)){
                var key=List.of(wire.output(),target);
                var id=target.equals(wire.input())?wire.id():new GLNodeConnectionId(UUID.nameUUIDFromBytes((wire.id()+"/forward/"+target).getBytes(StandardCharsets.UTF_8)));
                result.putIfAbsent(key,new GLGraphConnection(id,wire.output(),target));
                if(result.size()>limit)throw new IllegalArgumentException("Expanded forwarding exceeds the connection limit");
            }
        }
        return List.copyOf(result.values());
    }
}
