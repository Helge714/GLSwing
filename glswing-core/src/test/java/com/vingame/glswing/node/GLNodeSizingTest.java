package com.vingame.glswing.node;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.node.binding.*;
import com.vingame.glswing.node.model.*;
import com.vingame.glswing.ui.*;
class GLNodeSizingTest {
    @Test void themeMeasurementFinishesSynchronouslyAndKeepsLargerUserSizes()throws Exception{
        GLNode[] captured=new GLNode[1];float[] bounds=new float[2];
        SwingUtilities.invokeAndWait(()->{
            var editor=new GLNodeEditor();var theme=editor.getGLTheme();
            editor.setGLTheme(theme.withSliderStyle(theme.sliderStyle().withGeometry(3,18,330)));
            var node=new GLNode("Sized");node.addInput("in",new GLSlider("Value",0){});
            editor.addNode(node,20,30);captured[0]=node;
            assertEquals(node.getPreferredGLSize().width(),node.getWidth(),.01);
            assertEquals(node.getPreferredGLSize().height(),node.getHeight(),.01);
            assertTrue(node.getWidth()>=330);
            bounds[0]=node.getWidth();bounds[1]=node.getHeight();
        });
        SwingUtilities.invokeAndWait(()->{
            var node=captured[0];assertEquals(bounds[0],node.getWidth());assertEquals(bounds[1],node.getHeight());
            node.setSize(700,500);node.setGLTheme(node.getGLTheme().withSliderStyle(node.getGLTheme().sliderStyle().withGeometry(3,22,350)));
            assertEquals(700,node.getWidth());assertEquals(500,node.getHeight());
        });
    }
    @Test void savedBoundsCannotClipEditorMinimums()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var graph=new GLNodeGraph();var id=GLNodeId.random();var types=new GLNodeTypeRegistry();
            types.register(new GLNodeTypeDescriptor("test.sizing"));
            graph.edit("Create",e->e.addNode(new GLGraphNode(id,"test.sizing")));
            var editor=new GLNodeEditor();var state=new GLNodeEditorViewState(Map.of(id,new GLNodeViewState(20,30,140,30)));
            try(var binding=new GLNodeEditorBinding(graph,types,editor,(model,type)->{
                var node=new GLNode("Wide");var content=new GLLabel("Editor");content.setMinimumGLSize(420,60);node.addContent(content);return node;
            },GLGraphCyclePolicy.ACYCLIC,state)){
                var node=binding.findNodeView(id).orElseThrow();
                assertTrue(node.getWidth()>=420);assertTrue(node.getHeight()>=node.getPreferredGLSize().height());
                binding.applyViewState(new GLNodeEditorViewState(Map.of(id,new GLNodeViewState(25,35,700,500))));
                assertEquals(700,node.getWidth());assertEquals(500,node.getHeight());
                binding.applyViewState(state);assertTrue(node.getWidth()>=420);
            }
        });
    }
}
