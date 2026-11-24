package com.vingame.glswing.test;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import com.vingame.glswing.node.*;
import com.vingame.glswing.node.binding.*;
import com.vingame.glswing.node.model.*;
import com.vingame.glswing.ui.GLLabel;
class GLGraphDiagnosticsTest {
 @Test void cycleMarksNodesAndConnectionsAndClearsAfterRepair(){
  var types=new GLNodeTypeRegistry();types.register(new GLNodeTypeDescriptor("test.node",GLNodePortDescriptor.input("in","number",false,1),GLNodePortDescriptor.output("out","number")));
  var graph=new GLNodeGraph();var a=GLNodeId.random();var b=GLNodeId.random();var ab=GLNodeConnectionId.random();var ba=GLNodeConnectionId.random();
  graph.edit("Cycle",e->{e.addNode(new GLGraphNode(a,"test.node"));e.addNode(new GLGraphNode(b,"test.node"));e.addConnection(new GLGraphConnection(ab,new GLNodeEndpoint(a,"out"),new GLNodeEndpoint(b,"in")));e.addConnection(new GLGraphConnection(ba,new GLNodeEndpoint(b,"out"),new GLNodeEndpoint(a,"in")));});
  try(var binding=new GLNodeEditorBinding(graph,types,new GLNodeEditor(),(n,t)->{var view=new GLNode("Test");view.addInput("in",new GLLabel("Input"));view.addOutput("out",new GLLabel("Output"));return view;},GLGraphCyclePolicy.DELAYED_FEEDBACK)){
   assertEquals(GLNodeConnectionStatus.ERROR,binding.findNodeView(a).orElseThrow().getDiagnosticStatus());
   assertTrue(binding.findConnectionView(ab).orElseThrow().getDiagnosticMessage().contains("cycle"));
   var input=binding.findNodeView(a).orElseThrow().findPort("in");input.setContentHiddenWhenConnected(true);assertTrue(input.isContentHiddenByConnection());
   binding.setAdditionalDiagnostics(List.of(new GLGraphDiagnostic(GLGraphDiagnostic.Severity.WARNING,"ignored","Not used",Set.of(a,b),Set.of(ab,ba))),Set.of(ab,ba));
   assertEquals(GLNodeConnectionStatus.WARNING,binding.findNodeView(a).orElseThrow().getDiagnosticStatus());
   assertEquals(1,input.getConnectionCount());assertEquals(0,input.getActiveConnectionCount());assertFalse(input.isContentHiddenByConnection());
   binding.setAdditionalDiagnostics(List.of(new GLGraphDiagnostic(GLGraphDiagnostic.Severity.WARNING,"ignored","One cut",Set.of(a),Set.of(ba))),Set.of(ba));
   assertEquals(GLNodeConnectionStatus.WARNING,binding.findNodeView(a).orElseThrow().getDiagnosticStatus());
   assertEquals(GLNodeConnectionStatus.NORMAL,binding.findNodeView(b).orElseThrow().getDiagnosticStatus());
   assertTrue(binding.findConnectionView(ab).orElseThrow().isActive());assertFalse(binding.findConnectionView(ba).orElseThrow().isActive());
   assertFalse(input.isContentHiddenByConnection());
   binding.setAdditionalDiagnostics(List.of());assertTrue(input.isContentHiddenByConnection());assertEquals(GLNodeConnectionStatus.ERROR,binding.findNodeView(a).orElseThrow().getDiagnosticStatus());
   graph.edit("Repair",e->e.removeConnection(ba));
   assertEquals(GLNodeConnectionStatus.NORMAL,binding.findNodeView(a).orElseThrow().getDiagnosticStatus());assertNull(binding.findConnectionView(ab).orElseThrow().getDiagnosticMessage());
   binding.setAdditionalDiagnostics(List.of(new GLGraphDiagnostic(GLGraphDiagnostic.Severity.ERROR,"backend.feedback","Unsupported processor",Set.of(b),Set.of(ab))));
   assertEquals("Unsupported processor",binding.findNodeView(b).orElseThrow().getDiagnosticMessage());assertEquals("Unsupported processor",binding.findConnectionView(ab).orElseThrow().getDiagnosticMessage());
   binding.refresh();assertEquals(GLNodeConnectionStatus.ERROR,binding.findNodeView(b).orElseThrow().getDiagnosticStatus());
   binding.setAdditionalDiagnostics(List.of());assertNull(binding.findNodeView(b).orElseThrow().getDiagnosticMessage());assertNull(binding.findConnectionView(ab).orElseThrow().getDiagnosticMessage());
  }
 }
 @Test void suppliedGroupInputDoesNotHideOtherMissingInputs(){
  var types=new GLNodeTypeRegistry();types.register(new GLNodeTypeDescriptor("test.required",GLNodePortDescriptor.input("a","number",true,1),GLNodePortDescriptor.input("b","number",true,1)));
  var graph=new GLNodeGraph();var id=GLNodeId.random();graph.edit("Node",e->e.addNode(new GLGraphNode(id,"test.required")));
  assertEquals(2,GLNodeGraphValidator.validate(graph.snapshot(),types,GLGraphCyclePolicy.DELAYED_FEEDBACK).diagnostics().size());
  var result=GLNodeGraphValidator.validate(graph.snapshot(),types,GLGraphCyclePolicy.DELAYED_FEEDBACK,Set.of(new GLNodeEndpoint(id,"a")));
  assertEquals(1,result.diagnostics().size());assertTrue(result.diagnostics().get(0).message().endsWith(": b"));
  assertTrue(GLNodeGraphValidator.validate(graph.snapshot(),types,GLGraphCyclePolicy.DELAYED_FEEDBACK,Set.of(new GLNodeEndpoint(id,"a"),new GLNodeEndpoint(id,"b"))).isValid());
 }
}
