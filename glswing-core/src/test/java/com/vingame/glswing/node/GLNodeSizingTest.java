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
    @Test void removedRowsShrinkAutomaticAxesButKeepManualResize()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            for(var mode:new GLNodeResizeMode[]{GLNodeResizeMode.HORIZONTAL,GLNodeResizeMode.BOTH}) {
                var graph=new GLNodeGraph();var id=GLNodeId.random();var types=new GLNodeTypeRegistry();
                types.register(new GLNodeTypeDescriptor("dynamic",java.util.stream.IntStream.range(0,4).mapToObj(i->GLNodePortDescriptor.output("out"+i,"number")).toList()));
                graph.edit("Create",e->e.addNode(new GLGraphNode(id,"dynamic")));var editor=new GLNodeEditor();
                var factory=new GLNodeViewFactory(){
                    public GLNode createNode(GLGraphNode model,GLNodeTypeDescriptor type){
                        var n=new GLNode("Group");n.setResizeMode(mode);for(var p:type.ports())n.addOutput(p.id(),new GLLabel(p.id()));return n;
                    }
                    public boolean isViewCurrent(GLNode view,GLGraphNode model,GLNodeTypeDescriptor type){return view.getPorts().size()==type.ports().size();}
                };
                GLNodeEditorViewState saved;
                try(var binding=new GLNodeEditorBinding(graph,types,editor,factory,GLGraphCyclePolicy.ACYCLIC)) {
                    var n=binding.findNodeView(id).orElseThrow();float height=n.getHeight();n.setSize(600,height);saved=binding.captureViewState();
                    types.replace(new GLNodeTypeDescriptor("dynamic",GLNodePortDescriptor.output("out0","number")));binding.refresh();n=binding.findNodeView(id).orElseThrow();
                    assertEquals(600,n.getWidth());assertTrue(n.getHeight()<height);assertEquals(n.getPreferredGLSize().height(),n.getHeight(),.01);
                    if(mode==GLNodeResizeMode.HORIZONTAL){binding.applyViewState(saved);assertEquals(n.getPreferredGLSize().height(),n.getHeight(),.01);}
                    else {n.setSize(600,500);types.replace(new GLNodeTypeDescriptor("dynamic"));binding.refresh();assertEquals(500,binding.findNodeView(id).orElseThrow().getHeight());}
                }
                if(mode==GLNodeResizeMode.HORIZONTAL)try(var reopened=new GLNodeEditorBinding(graph,types,new GLNodeEditor(),factory,GLGraphCyclePolicy.ACYCLIC,saved)) {
                    var n=reopened.findNodeView(id).orElseThrow();assertEquals(600,n.getWidth());assertEquals(n.getPreferredGLSize().height(),n.getHeight(),.01);
                }
            }
        });
    }

}
