package com.vingame.glswing.node;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.node.binding.*;
import com.vingame.glswing.node.model.*;

class GLModelConnectionHistoryTest {
    @Test void connectionUndoUsesModelEndpointsAfterViewsAreRecreated()throws Exception{
        var types=new GLNodeTypeRegistry();types.register(new GLNodeTypeDescriptor("node",GLNodePortDescriptor.input("in","value",false,1),GLNodePortDescriptor.output("out","value")));
        var graph=new GLNodeGraph();var a=GLNodeId.random();var b=GLNodeId.random();var c=GLNodeId.random();var missing=GLNodeId.random();
        graph.edit("Initial",e->{for(var id:List.of(a,b,c))e.addNode(new GLGraphNode(id,"node"));
            e.addConnection(new GLGraphConnection(GLNodeConnectionId.random(),new GLNodeEndpoint(a,"out"),new GLNodeEndpoint(b,"in")));
            e.addConnection(new GLGraphConnection(GLNodeConnectionId.random(),new GLNodeEndpoint(b,"out"),new GLNodeEndpoint(c,"in")));
            e.addNode(new GLGraphNode(missing,"unavailable-plugin"));
            e.addConnection(new GLGraphConnection(GLNodeConnectionId.random(),new GLNodeEndpoint(c,"out"),new GLNodeEndpoint(missing,"in")));});
        boolean[] rebuild={false};GLNodeViewFactory views=new GLNodeViewFactory(){
            public GLNode createNode(GLGraphNode model,GLNodeTypeDescriptor type){var n=new GLNode("Node");n.addInput("in",new com.vingame.glswing.ui.GLLabel("Input"));n.addOutput("out",new com.vingame.glswing.ui.GLLabel("Output"));return n;}
            public boolean isViewCurrent(GLNode view,GLGraphNode model,GLNodeTypeDescriptor type){return !rebuild[0];}
        };
        var editor=new GLNodeEditor();var history=new GLHistoryManager();
        try(var binding=new GLNodeEditorBinding(graph,types,editor,views,GLGraphCyclePolicy.ACYCLIC)){
            new GLNodeGraphEditingSession(binding,history);
            var before=List.copyOf(editor.getConnections());editor.removeConnection(before.get(0));
            var method=GLNodeEditor.class.getDeclaredMethod("recordConnectionChange",List.class,List.class);method.setAccessible(true);method.invoke(editor,before,List.copyOf(editor.getConnections()));
            assertEquals(2,graph.snapshot().connections().size());
            var old=binding.findNodeView(b).orElseThrow();rebuild[0]=true;binding.refresh();assertNotSame(old,binding.findNodeView(b).orElseThrow());
            assertDoesNotThrow(history::undo);assertEquals(3,graph.snapshot().connections().size());assertEquals(2,editor.getConnections().size());
            assertDoesNotThrow(history::redo);assertEquals(2,graph.snapshot().connections().size());assertEquals(1,editor.getConnections().size());
            binding.close();history.undo();assertEquals(3,graph.snapshot().connections().size());
        }
    }
}
