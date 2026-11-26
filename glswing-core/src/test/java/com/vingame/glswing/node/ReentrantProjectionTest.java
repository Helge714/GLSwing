package com.vingame.glswing.node;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.node.binding.*;
import com.vingame.glswing.node.model.*;
class ReentrantProjectionTest {
 @Test void normalizationBeforeBindingCannotReplayAnObsoletePortContract(){
  var graph=new GLNodeGraph();var id=GLNodeId.random();var types=new GLNodeTypeRegistry();
  types.register(new GLNodeTypeDescriptor("trigger",GLNodePortDescriptor.input("next","number",false,1),GLNodePortDescriptor.input("reset","number",false,1)));
  types.register(new GLNodeTypeDescriptor("index",GLNodePortDescriptor.input("next","number",false,1)));
  graph.edit("Create",e->e.addNode(new GLGraphNode(id,"trigger",Map.of("mode",GLNodeValue.of("trigger")))));
  // Reopening a graph installs its existing application listener before the new binding.
  graph.addListener((source,change)->{
   var snapshot=source.snapshot();var node=snapshot.findNode(id).orElseThrow();var desired=(String)node.properties().get("mode").toJavaValue();
   if(!node.typeId().equals(desired))source.replace("Normalize",new GLNodeGraphSnapshot(snapshot.graphId(),snapshot.revision(),List.of(node.withType(desired,node.typeVersion())),snapshot.connections()));
  });
  var editor=new GLNodeEditor();
  try(var binding=new GLNodeEditorBinding(graph,types,editor,(node,type)->{
   var view=new GLNode("Switch");view.addInput("next",new com.vingame.glswing.ui.GLLabel("Next"));
   if(GLNodeValue.of("trigger").equals(node.properties().get("mode")))view.addInput("reset",new com.vingame.glswing.ui.GLLabel("Reset"));
   return view;
  },GLGraphCyclePolicy.ACYCLIC)){
   for(String mode:List.of("index","trigger","index")){
    graph.edit("Inspector mode",e->e.setProperty(id,"mode",GLNodeValue.of(mode)));
    assertTrue(binding.lastProjectionFailure().isEmpty(),()->binding.lastProjectionFailure().map(Throwable::getMessage).orElse(""));
    var view=binding.findNodeView(id).orElseThrow();assertEquals(mode.equals("trigger"),view.findPort("reset")!=null);
   }
  }
 }
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
