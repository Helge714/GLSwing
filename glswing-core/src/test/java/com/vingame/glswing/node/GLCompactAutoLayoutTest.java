package com.vingame.glswing.node;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.node.binding.GLNodeViewState;
import com.vingame.glswing.ui.GLLabel;

class GLCompactAutoLayoutTest {
 private static GLNode node(GLNodeEditor e,String title,float w,float h){
  var n=new GLNode(title);n.addInput("in",new GLLabel("In")).setMaximumConnections(100);n.addOutput("out",new GLLabel("Out"));e.addNode(n,25,35);n.setSize(w,h);return n;
 }
 private static void wire(GLNodeEditor e,GLNode a,GLNode b){e.addProjectedConnection(new GLNodeConnection(a.findPort("out"),b.findPort("in")));}
 private static void clearances(GLNodeEditor editor){
  var nodes=editor.getNodes();for(int i=0;i<nodes.size();i++)for(int j=i+1;j<nodes.size();j++){
   var a=nodes.get(i);var b=nodes.get(j);float hg=editor.getHorizontalNodeSpacing(),vg=editor.getVerticalNodeSpacing();
   assertTrue(a.getX()+a.getWidth()+hg<=b.getX()+.02||b.getX()+b.getWidth()+hg<=a.getX()+.02
    ||a.getY()+a.getHeight()+vg<=b.getY()+.02||b.getY()+b.getHeight()+vg<=a.getY()+.02,a.getTitle()+" overlaps clearance of "+b.getTitle());
  }
 }
 private static void repeat(GLNodeEditor editor){var before=editor.getNodes().stream().map(GLNodeViewState::capture).toList();GLNodeAutoLayout.arrange(editor);assertEquals(before,editor.getNodes().stream().map(GLNodeViewState::capture).toList());}
 @Test void actualPortsAlignRatherThanNodeCenters(){
  var e=new GLNodeEditor();var a=node(e,"Tall",180,400);var b=node(e,"Short",140,100);wire(e,a,b);
  a.findPort("out").setBounds(174,310,12,12);b.findPort("in").setBounds(-6,22,12,12);
  GLNodeAutoLayout.arrange(e);
  assertEquals(a.getY()+a.findPort("out").getCenterYInNode(),b.getY()+b.findPort("in").getCenterYInNode(),.01);
  assertTrue(b.getY()>a.getY()+200);clearances(e);repeat(e);
 }
 @Test void independentComponentsPackIntoCompactShelvesWithoutMovingTheCamera(){
  var e=new GLNodeEditor();e.setBounds(0,0,1200,700);e.setPan(123,-45);e.setZoom(1.5f);
  for(int i=0;i<36;i++)node(e,"Loose "+i,160,100);
  GLNodeAutoLayout.arrange(e);assertTrue(e.getNodes().stream().map(GLNode::getX).distinct().count()>1);assertTrue(e.getNodes().stream().map(GLNode::getY).distinct().count()>1);
  var max=e.getNodes().stream().mapToDouble(n->n.getY()+n.getHeight()).max().orElseThrow();assertTrue(max<1500,"Disconnected nodes must not form a 36-row stack");
  assertEquals(123,e.getPanX());assertEquals(-45,e.getPanY());assertEquals(1.5,e.getZoom());clearances(e);repeat(e);
 }
 @Test void shortComponentsFillTheSpaceBesideATallComponent(){
  var e=new GLNodeEditor();e.setBounds(0,0,1000,400);var tall=node(e,"Tall",200,400);var small=new ArrayList<GLNode>();for(int i=0;i<4;i++)small.add(node(e,"Small "+i,120,80));
  GLNodeAutoLayout.arrange(e);assertTrue(small.stream().anyMatch(n->n.getX()>=tall.getX()+tall.getWidth()+e.getHorizontalNodeSpacing()-.01&&n.getY()<tall.getY()+tall.getHeight()));
  assertTrue(small.stream().anyMatch(a->small.stream().anyMatch(b->a!=b&&a.getX()==b.getX()&&a.getY()!=b.getY())),"Smaller components should stack in free space beside the tall component");clearances(e);repeat(e);
 }
 @Test void narrowBranchesDoNotInheritAnUnrelatedWideColumn(){
  var e=new GLNodeEditor();var source=node(e,"Source",120,100);var wide=node(e,"Wide",480,200);var small=node(e,"Small",100,100);var a=node(e,"Sink A",120,100);var b=node(e,"Sink B",120,100);
  wire(e,source,wide);wire(e,source,small);wire(e,wide,a);wire(e,small,b);GLNodeAutoLayout.arrange(e);
  assertEquals(e.getHorizontalNodeSpacing(),b.getX()-small.getX()-small.getWidth(),.02);
  assertTrue(a.getX()>b.getX()+200);clearances(e);repeat(e);
 }
 @Test void aShortControlBranchStaysNearItsConsumer(){
  var e=new GLNodeEditor();var a=node(e,"A",160,100);var b=node(e,"B",160,100);var c=node(e,"C",160,100);var sink=node(e,"Sink",160,100);var control=node(e,"Control",160,100);
  wire(e,a,b);wire(e,b,c);wire(e,c,sink);wire(e,control,sink);GLNodeAutoLayout.arrange(e);
  assertTrue(control.getX()>=c.getX()-.01);assertEquals(e.getHorizontalNodeSpacing(),sink.getX()-control.getX()-control.getWidth(),.01);clearances(e);repeat(e);
 }
 @Test void crossingBranchesAreOrderedByTheirActualEndpoints(){
  var e=new GLNodeEditor();var root=node(e,"Root",160,100);var a=node(e,"A",160,100);var b=node(e,"B",160,100);var c=node(e,"C",160,100);var d=node(e,"D",160,100);var sink=node(e,"Sink",160,100);
  wire(e,root,a);wire(e,root,b);wire(e,a,d);wire(e,b,c);wire(e,c,sink);wire(e,d,sink);GLNodeAutoLayout.arrange(e);
  assertTrue((a.getY()-b.getY())*(d.getY()-c.getY())>0,"Parallel branches must not cross between their middle columns");clearances(e);repeat(e);
 }
 @Test void feedbackComponentsRemainContiguousAndTheirReturnNodesHaveClearance(){
  var e=new GLNodeEditor();var in=node(e,"In",120,100);var a=node(e,"A",140,100);var b=node(e,"B",180,160);var c=node(e,"Return",120,100);var out=node(e,"Out",120,100);
  wire(e,in,a);wire(e,a,b);wire(e,b,c);wire(e,c,a);wire(e,b,out);var wires=e.getConnections();GLNodeAutoLayout.arrange(e);
  assertTrue(a.getX()>=in.getX()+in.getWidth()+e.getHorizontalNodeSpacing()-.01);
  assertTrue(out.getX()>=c.getX()+c.getWidth()+e.getHorizontalNodeSpacing()-.01);
  assertTrue(c.getY()>=Math.max(a.getY()+a.getHeight(),b.getY()+b.getHeight())+e.getVerticalNodeSpacing()-.01);
  assertEquals(wires,e.getConnections());clearances(e);repeat(e);
 }
 @Test void overlappingFeedbackLoopsAndSelfLoopsStayStable(){
  var e=new GLNodeEditor();var a=node(e,"A",120,90);var b=node(e,"B",140,170);var c=node(e,"C",150,120);var d=node(e,"D",130,160);
  wire(e,a,b);wire(e,b,c);wire(e,c,a);wire(e,b,d);wire(e,d,b);wire(e,c,c);GLNodeAutoLayout.arrange(e);clearances(e);repeat(e);
 }
 @Test void evenUnwiredGroupBoundariesStayAtTheOuterEdges(){
  var e=new GLNodeEditor();var output=node(e,"Group Output",160,80);var a=node(e,"A",180,200);var input=node(e,"Group Input",160,80);var b=node(e,"B",120,90);
  output.setAutoLayoutRole(GLNodeAutoLayout.Role.OUTPUT_BOUNDARY);input.setAutoLayoutRole(GLNodeAutoLayout.Role.INPUT_BOUNDARY);wire(e,a,b);GLNodeAutoLayout.arrange(e);
  for(var n:List.of(a,b)){assertTrue(input.getX()+input.getWidth()+e.getHorizontalNodeSpacing()<=n.getX()+.01);assertTrue(n.getX()+n.getWidth()+e.getHorizontalNodeSpacing()<=output.getX()+.01);}
  clearances(e);repeat(e);
 }
 @Test void directForwardingBetweenBoundariesRemainsAligned(){
  var e=new GLNodeEditor();var a=node(e,"Group Input",160,100);var b=node(e,"Group Output",160,100);a.setAutoLayoutRole(GLNodeAutoLayout.Role.INPUT_BOUNDARY);b.setAutoLayoutRole(GLNodeAutoLayout.Role.OUTPUT_BOUNDARY);wire(e,a,b);
  GLNodeAutoLayout.arrange(e);assertEquals(a.getY()+a.findPort("out").getCenterYInNode(),b.getY()+b.findPort("in").getCenterYInNode(),.01);clearances(e);repeat(e);
 }
 @Test void variedDisconnectedSizesHaveABoundedPackingCost(){
  assertTimeout(Duration.ofSeconds(15),()->{var e=new GLNodeEditor();e.setBounds(0,0,1200,800);for(int i=0;i<600;i++)node(e,"Loose "+i,120+(i*37)%360,80+(i*53)%420);GLNodeAutoLayout.arrange(e);clearances(e);repeat(e);});
 }
 @Test void aLongChainDoesNotClimbOnePortOffsetPerNode(){
  var e=new GLNodeEditor();var chain=new ArrayList<GLNode>();
  for(int i=0;i<12;i++){var n=node(e,"N"+i,160,120);chain.add(n);if(i>0)wire(e,chain.get(i-1),n);}
  for(var n:chain){n.findPort("out").setBounds(154,20,12,12);n.findPort("in").setBounds(-6,80,12,12);}
  GLNodeAutoLayout.arrange(e);double min=chain.stream().mapToDouble(GLNode::getY).min().orElseThrow(),max=chain.stream().mapToDouble(GLNode::getY).max().orElseThrow();
  assertTrue(max-min<60,"Horizontal flow should accept angled wires instead of a 660-pixel climb: "+(max-min));clearances(e);repeat(e);
 }
 @Test void longChainsUseNoRecursiveGraphTraversal(){
  assertTimeout(Duration.ofSeconds(15),()->{var e=new GLNodeEditor();GLNode previous=null;for(int i=0;i<1000;i++){var n=node(e,"N"+i,120,80);if(previous!=null)wire(e,previous,n);previous=n;}
   GLNodeAutoLayout.arrange(e);assertEquals(1000,e.getNodes().size());for(var edge:e.getConnections())assertTrue(edge.getOutput().getNode().getX()<edge.getInput().getNode().getX());repeat(e);
  });
 }
}
