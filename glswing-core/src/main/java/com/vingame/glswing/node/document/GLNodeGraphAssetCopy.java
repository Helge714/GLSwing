package com.vingame.glswing.node.document;

import java.util.*;
import com.vingame.glswing.node.model.*;
import com.vingame.glswing.node.composite.*;
import com.vingame.glswing.node.binding.*;

/** Structural copy of a composite dependency closure, with application-owned metadata rewriting.
 * Plans are immutable and allocate fresh asset/type IDs; each copy allocates fresh graph/node/wire IDs. */
public final class GLNodeGraphAssetCopy {
    private GLNodeGraphAssetCopy() { }
    public record Plan(String selectedId, List<GLNodeGraphAsset> assets, Map<String,String> ids) {
        public Plan { assets=List.copyOf(assets); ids=Map.copyOf(ids); }
    }
    public record Context(Map<String,String> ids, Map<GLNodeId,GLNodeId> nodeIds) {
        public Context { ids=Map.copyOf(ids); nodeIds=Map.copyOf(nodeIds); }
        public String id(String old) { return ids.getOrDefault(old,old); }
        public GLNodeEndpoint endpoint(GLNodeEndpoint endpoint) {
            if(endpoint==null||GLCompositeForwarding.isInput(endpoint))return endpoint;
            var id=nodeIds.get(endpoint.nodeId());
            if(id==null)throw new IllegalArgumentException("Endpoint refers to a node outside its asset: "+endpoint);
            return new GLNodeEndpoint(id,endpoint.portId());
        }
    }
    public interface Policy {
        default Map<String,GLNodeValue> nodeProperties(GLGraphNode node, Context context) { return node.properties(); }
        default Map<String,GLNodeValue> editorOptions(GLCompositePortMapping port, Context context) { return port.presentation().editorOptions(); }
        default Map<String,GLNodeValue> metadata(GLNodeGraphAsset asset, Context context) { return asset.metadata(); }
        default GLNodeValue value(GLNodeValue value, Context context) { return value; }
    }
    public record Result(GLNodeGraphAsset selected, List<GLNodeGraphAsset> assets) {
        public Result { assets=List.copyOf(assets); }
    }
    public static Plan plan(GLNodeGraphDocument document, String selectedId, String assetPrefix, String typePrefix) {
        var selected=document.findAsset(selectedId).orElseThrow();
        var types=new HashMap<String,GLNodeGraphAsset>();
        var all=new ArrayList<>(document.embeddedAssets().values());all.add(document.root());
        for(var asset:all)if(asset.compositeDescriptor()!=null) {
            var previous=types.putIfAbsent(asset.compositeDescriptor().nodeTypeId(),asset);
            if(previous!=null&&!previous.id().equals(asset.id()))throw new IllegalArgumentException("Ambiguous composite type");
        }
        var needed=new LinkedHashMap<String,GLNodeGraphAsset>();collect(selected,types,needed);
        var ids=new LinkedHashMap<String,String>();
        for(var asset:needed.values()) {
            ids.put(asset.id(),assetPrefix+UUID.randomUUID());
            if(asset.compositeDescriptor()!=null)ids.put(asset.compositeDescriptor().nodeTypeId(),typePrefix+UUID.randomUUID());
        }
        return new Plan(selectedId,List.copyOf(needed.values()),ids);
    }
    private static void collect(GLNodeGraphAsset asset, Map<String,GLNodeGraphAsset> types, Map<String,GLNodeGraphAsset> needed) {
        if(needed.putIfAbsent(asset.id(),asset)!=null)return;
        for(var node:asset.graph().nodes()) { var child=types.get(node.typeId());if(child!=null)collect(child,types,needed); }
    }
    public static Result copy(Plan plan, Map<String,String> extraIds, String rootTypeId, Policy policy) {
        var ids=new HashMap<>(plan.ids());
        extraIds.forEach((key,value)->{if(ids.containsKey(key)&&!ids.get(key).equals(value))throw new IllegalArgumentException("Conflicting copy ID: "+key);ids.put(key,value);});
        var result=new ArrayList<GLNodeGraphAsset>();
        for(var asset:plan.assets()) {
            var nodeIds=new LinkedHashMap<GLNodeId,GLNodeId>();asset.graph().nodes().forEach(n->nodeIds.put(n.id(),GLNodeId.random()));
            var localIds=new HashMap<>(ids);nodeIds.forEach((oldId,newId)->localIds.put(oldId.toString(),newId.toString()));
            var context=new Context(localIds,nodeIds);
            var nodes=asset.graph().nodes().stream().map(n->new GLGraphNode(nodeIds.get(n.id()),context.id(n.typeId()),n.typeVersion(),policy.nodeProperties(n,context))).toList();
            var descriptor=asset.compositeDescriptor();
            if(descriptor==null)descriptor=new GLCompositeNodeDescriptor(rootTypeId,context.id(asset.id()),1,List.of());
            else descriptor=new GLCompositeNodeDescriptor(context.id(descriptor.nodeTypeId()),context.id(asset.id()),descriptor.minimumAssetVersion(),
                descriptor.portMappings().stream().map(p->new GLCompositePortMapping(p.publicPort(),context.endpoint(p.internalEndpoint()),
                    new GLCompositePortPresentation(context.id(p.presentation().inputEditorId()),p.presentation().socketVisible(),policy.editorOptions(p,context)),
                    p.additionalEndpoints().stream().map(context::endpoint).toList())).toList(),
                descriptor.properties().stream().map(p->new GLCompositePropertyDescriptor(p.id(),p.valueTypeId(),p.valueKind(),policy.value(p.defaultValue(),context),p.minimum(),p.maximum())).toList(),
                descriptor.propertyMappings().stream().map(p->{
                    var values=new LinkedHashMap<String,GLNodeValue>();p.values().forEach((k,v)->values.put(k,policy.value(v,context)));
                    return new GLCompositePropertyMapping(p.publicPropertyId(),Objects.requireNonNull(nodeIds.get(p.internalNodeId())),p.internalPropertyId(),values);
                }).toList());
            var positions=new LinkedHashMap<GLNodeId,GLNodeViewState>();
            asset.viewState().nodes().forEach((id,state)->{if(nodeIds.containsKey(id))positions.put(nodeIds.get(id),state);});
            var wires=asset.graph().connections().stream().map(c->new GLGraphConnection(GLNodeConnectionId.random(),context.endpoint(c.output()),context.endpoint(c.input()))).toList();
            result.add(new GLNodeGraphAsset(context.id(asset.id()),asset.version(),GLNodeGraphAssetKind.COMPOSITE,
                new GLNodeGraphSnapshot(GLNodeGraphId.random(),0,nodes,wires),new GLNodeEditorViewState(positions,asset.viewState().auxiliaryNodes()),policy.metadata(asset,context),descriptor));
        }
        return new Result(result.stream().filter(a->a.id().equals(ids.get(plan.selectedId()))).findFirst().orElseThrow(),result);
    }
}
