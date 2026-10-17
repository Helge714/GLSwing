package com.vingame.glswing.node.composite;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.vingame.glswing.node.model.*;
import com.vingame.glswing.node.document.*;
import com.vingame.glswing.node.binding.*;
import com.vingame.glswing.history.GLHistoryManager;

class GLCompositeForwardingTest {
    private GLNodeGraphAsset asset(String type){
        var d=new GLCompositeNodeDescriptor("forward","forward.asset",1,List.of(
            new GLCompositePortMapping(GLNodePortDescriptor.input("in",type,false,1),null),
            new GLCompositePortMapping(GLNodePortDescriptor.output("out",type),GLCompositeForwarding.input("in"))));
        return new GLNodeGraphAsset(d.assetId(),1,GLNodeGraphAssetKind.COMPOSITE,new GLNodeGraph().snapshot(),GLNodeEditorViewState.empty(),Map.of(),d);
    }
    @Test void renameRemoveUndoAndCopyKeepInputReferences(){
        var a=asset("resource");var registry=new GLCompositeGraphRegistry();registry.registerAsset(a);var types=new GLNodeTypeRegistry();types.register(a.compositeDescriptor().nodeType());var history=new GLHistoryManager();
        GLCompositeInterfaceTransactions.updatePort(registry,types,a.id(),"in",new GLCompositePortMapping(GLNodePortDescriptor.input("renamed","resource",false,1),null),history,"Rename");
        assertEquals(GLCompositeForwarding.input("renamed"),registry.findAsset(a.id()).orElseThrow().compositeDescriptor().portMappings().get(1).internalEndpoint());
        GLCompositeInterfaceTransactions.removePort(registry,types,a.id(),"renamed",history,"Remove");assertFalse(registry.findAsset(a.id()).orElseThrow().compositeDescriptor().portMappings().get(0).isBound());
        history.undo();assertEquals(GLCompositeForwarding.input("renamed"),registry.findAsset(a.id()).orElseThrow().compositeDescriptor().portMappings().get(1).internalEndpoint());
        assertEquals(GLCompositeForwarding.input("in"),new GLNodeGraphAssetCopy.Context(Map.of(),Map.of()).endpoint(GLCompositeForwarding.input("in")));
    }
    @Test void flattenAndUngroupSpliceAllValueTypesWithoutProcessors(){
        for(var type:List.of("number","trigger","vector","resource")){
            var a=asset(type);var registry=new GLCompositeGraphRegistry();registry.registerAsset(a);
            var src=GLNodeId.random();var group=GLNodeId.random();var sink=GLNodeId.random();var graph=new GLNodeGraph();
            graph.edit("test",e->{e.addNode(new GLGraphNode(src,"source",Map.of()));e.addNode(new GLGraphNode(group,"forward",Map.of()));e.addNode(new GLGraphNode(sink,"sink",Map.of()));e.addConnection(new GLGraphConnection(GLNodeConnectionId.random(),new GLNodeEndpoint(src,"out"),new GLNodeEndpoint(group,"in")));e.addConnection(new GLGraphConnection(GLNodeConnectionId.random(),new GLNodeEndpoint(group,"out"),new GLNodeEndpoint(sink,"in")));});
            var root=new GLNodeGraphAsset("root",1,GLNodeGraphAssetKind.PROJECT,graph.snapshot(),GLNodeEditorViewState.empty());
            var flat=GLCompositeGraphFlattener.flatten(root,registry,GLCompositeExpansionLimits.DEFAULT);assertTrue(flat.isValid());assertEquals(2,flat.graph().orElseThrow().nodes().size());assertEquals(2,flat.origins().size());assertEquals(1,flat.graph().orElseThrow().connections().size());assertEquals(src,flat.graph().orElseThrow().connections().get(0).output().nodeId());
            var unpacked=GLCompositeUngroup.expand(graph.snapshot(),GLNodeEditorViewState.empty(),graph.snapshot().findNode(group).orElseThrow(),a);assertEquals(2,unpacked.graph().nodes().size());assertEquals(1,unpacked.graph().connections().size());assertEquals(sink,unpacked.graph().connections().get(0).input().nodeId());
        }
    }
    @Test void ungroupInsideParentRetainsBoundaryOnlyForwarding(){
        var child=asset("resource");var id=GLNodeId.random();var graph=new GLNodeGraph();
        graph.edit("Instance",e->e.addNode(new GLGraphNode(id,"forward",Map.of())));
        var parent=new GLCompositeNodeDescriptor("parent","parent.asset",1,List.of(
            new GLCompositePortMapping(GLNodePortDescriptor.input("source","resource",false,1),new GLNodeEndpoint(id,"in")),
            new GLCompositePortMapping(GLNodePortDescriptor.output("result","resource"),new GLNodeEndpoint(id,"out"))));
        var expanded=GLCompositeUngroup.expand(graph.snapshot(),GLNodeEditorViewState.empty(),graph.snapshot().findNode(id).orElseThrow(),child);
        var remapped=expanded.remap(parent,id,child.compositeDescriptor());
        assertEquals(GLCompositeForwarding.input("source"),remapped.portMappings().get(1).internalEndpoint());
        assertTrue(expanded.graph().nodes().isEmpty());
    }
}
