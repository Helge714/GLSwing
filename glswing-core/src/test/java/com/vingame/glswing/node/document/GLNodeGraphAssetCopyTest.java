package com.vingame.glswing.node.document;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.node.model.*;
import com.vingame.glswing.node.binding.*;
import com.vingame.glswing.node.composite.*;
class GLNodeGraphAssetCopyTest {
    @Test void nestedCopiesPreserveWiresPositionsAndFanoutButAllocateFreshIds(){
        var a=new GLGraphNode(GLNodeId.random(),"math");var b=new GLGraphNode(GLNodeId.random(),"math");
        var endpoint=new GLNodeEndpoint(a.id(),"input");
        var wire=new GLGraphConnection(GLNodeConnectionId.random(),new GLNodeEndpoint(a.id(),"out"),new GLNodeEndpoint(b.id(),"input"));
        var port=new GLCompositePortMapping(GLNodePortDescriptor.input("input","value",false,1),endpoint,
            new GLCompositePortPresentation("none",true,Map.of()),List.of(new GLNodeEndpoint(b.id(),"other")));
        var child=new GLNodeGraphAsset("child",1,GLNodeGraphAssetKind.COMPOSITE,
            new GLNodeGraphSnapshot(GLNodeGraphId.random(),7,List.of(a,b),List.of(wire)),
            new GLNodeEditorViewState(Map.of(a.id(),new GLNodeViewState(10,20)),Map.of()),Map.of(),
            new GLCompositeNodeDescriptor("child.type","child",1,List.of(port)));
        var instance=new GLGraphNode(GLNodeId.random(),"child.type");
        var parent=new GLNodeGraphAsset("root",1,GLNodeGraphAssetKind.PROJECT,
            new GLNodeGraphSnapshot(GLNodeGraphId.random(),0,List.of(instance),List.of()),GLNodeEditorViewState.empty());
        var document=new GLNodeGraphDocument(parent,Map.of(child.id(),child),List.of());
        var plan=GLNodeGraphAssetCopy.plan(document,"root","asset.","type.");
        var result=GLNodeGraphAssetCopy.copy(plan,Map.of(),"root.type",new GLNodeGraphAssetCopy.Policy(){});
        assertEquals(2,result.assets().size());
        var copy=result.assets().stream().filter(x->!x.equals(result.selected())).findFirst().orElseThrow();
        assertEquals(copy.compositeDescriptor().nodeTypeId(),result.selected().graph().nodes().get(0).typeId());
        assertNotEquals(a.id(),copy.graph().nodes().get(0).id());assertNotEquals(wire.id(),copy.graph().connections().get(0).id());
        assertEquals(copy.graph().nodes().get(0).id(),copy.compositeDescriptor().portMappings().get(0).internalEndpoint().nodeId());
        assertEquals(copy.graph().nodes().get(1).id(),copy.compositeDescriptor().portMappings().get(0).additionalEndpoints().get(0).nodeId());
        assertEquals(new GLNodeViewState(10,20),copy.viewState().nodes().get(copy.graph().nodes().get(0).id()));
        assertEquals(endpoint,child.compositeDescriptor().portMappings().get(0).internalEndpoint());
        assertThrows(IllegalArgumentException.class,()->GLNodeGraphAssetCopy.copy(plan,Map.of("root","collision"),"root.type",new GLNodeGraphAssetCopy.Policy(){}));
    }
}
