package com.vingame.glswing.node.composite;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.node.model.*;
import com.vingame.glswing.node.binding.*;

class GLCompositeNestedExtractionTest {
    @Test void boundaryOnlyPortsAndParametersSurviveExtractionAndExpansion() {
        var types=new GLNodeTypeRegistry();types.register(new GLNodeTypeDescriptor("delay",
            GLNodePortDescriptor.input("in","number",false,1),GLNodePortDescriptor.output("out","number")));
        var id=GLNodeId.random();var graph=new GLNodeGraph();graph.edit("Delay",e->e.addNode(new GLGraphNode(id,"delay")));
        var property=new GLCompositePropertyDescriptor("time","number",GLCompositeValueKind.DECIMAL,GLNodeValue.of(.03));
        var parent=new GLCompositeNodeDescriptor("parent","parent.asset",1,List.of(
            new GLCompositePortMapping(GLNodePortDescriptor.input("source","number",false,1),new GLNodeEndpoint(id,"in")),
            new GLCompositePortMapping(GLNodePortDescriptor.output("result","number"),new GLNodeEndpoint(id,"out"))),
            List.of(property),List.of(new GLCompositePropertyMapping("time",id,"time")));
        var result=GLCompositeExtraction.extract(graph.snapshot(),GLNodeEditorViewState.empty(),Set.of(id),types,"child.asset","child",parent);
        assertEquals(1,result.graph().nodes().size());assertTrue(result.graph().connections().isEmpty());
        assertEquals(2,result.asset().compositeDescriptor().portMappings().size());
        var remapped=result.remapEnclosing(parent);
        for(var port:remapped.portMappings())assertEquals(result.compositeNode().id(),port.internalEndpoint().nodeId());
        assertEquals(result.compositeNode().id(),remapped.propertyMappings().get(0).internalNodeId());
        var unpacked=GLCompositeUngroup.expand(result.graph(),result.viewState(),result.compositeNode(),result.asset());
        var restored=unpacked.remap(remapped,result.compositeNode().id(),result.asset().compositeDescriptor());
        for(var port:restored.portMappings())assertEquals(unpacked.nodeIds().get(id),port.internalEndpoint().nodeId());
        assertEquals("time",restored.propertyMappings().get(0).internalPropertyId());
    }
}
