package com.vingame.glswing.node.composite;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.node.model.*;
class GLNodeEditorModelsTest {
    @Test void templatesRoundTripAndFailedLoadIsAtomic(){
        var entry=new GLNodeEditorTemplate.Entry("a","Choice",GLNodeValue.of(2.5));
        var template=new GLNodeEditorTemplate("t","Template",List.of(entry),"a");
        assertEquals(template,GLNodeEditorTemplate.read(template.value()));
        var library=new GLNodeEditorTemplateLibrary();library.put(template);var before=library.snapshot();
        assertThrows(IllegalArgumentException.class,()->library.load(GLNodeValue.of(List.of(template.value().toJavaValue(),template.value().toJavaValue()))));
        assertEquals(before,library.snapshot());
        assertThrows(IllegalArgumentException.class,()->new GLNodeEditorTemplate("x","X",List.of(entry),"missing"));
        library.load(before);assertEquals(List.of(template),library.all());
    }
    @Test void catalogGroupsInstancesAndRejectsConnectedOrNonCopyableEditors(){
        var graph=new GLNodeGraphSnapshot(GLNodeGraphId.random(),0,List.of(new GLGraphNode(GLNodeId.random(),"osc"),new GLGraphNode(GLNodeId.random(),"osc")),List.of());
        var result=GLNodeEditorSourceCatalog.<String>collect(graph,node->List.of(
            candidate("frequency",true,true),candidate("input",true,false),candidate("steps",false,true)),c->true);
        assertEquals(1,result.size());assertEquals("frequency",result.get(0).property());assertEquals(2,result.get(0).endpoints().size());
    }
    private GLNodeEditorSourceCatalog.Candidate<String> candidate(String key,boolean copyable,boolean standalone){
        return new GLNodeEditorSourceCatalog.Candidate<>(key,key,key,new GLNodeEditorCapabilities(GLNodeEditorCapabilities.Kind.SLIDER,copyable,standalone,GLNodeValue.of(0),Map.of()));
    }
}
