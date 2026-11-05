package com.vingame.glswing.node;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.node.binding.*;
import com.vingame.glswing.node.model.*;
class ReentrantProjectionTest {
 @Test void changesDuringProjectionAreNotDropped(){
  var graph=new GLNodeGraph();var id=GLNodeId.random();var types=new GLNodeTypeRegistry();types.register(new GLNodeTypeDescriptor("test"));
  graph.edit("Create",e->e.addNode(new GLGraphNode(id,"test",Map.of())));
  var editor=new GLNodeEditor();
  var factory=new GLNodeViewFactory(){
   public GLNode createNode(GLGraphNode node,GLNodeTypeDescriptor type){return new GLNode("Test");}
   public void updateNode(GLNode view,GLGraphNode node,GLNodeTypeDescriptor type){
    view.setTitle(node.properties().containsKey("updated")?"Updated":"Initial");
    if(!node.properties().containsKey("updated"))graph.edit("Normalize",e->e.setProperty(id,"updated",GLNodeValue.of(true)));
   }
  };
  try(var binding=new GLNodeEditorBinding(graph,types,editor,factory,GLGraphCyclePolicy.DELAYED_FEEDBACK)){
   assertEquals("Updated",editor.getNodes().get(0).getTitle());
  }
 }
}
