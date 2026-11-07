package com.vingame.glswing.node;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import com.vingame.glswing.node.binding.*;
import com.vingame.glswing.node.model.*;
import com.vingame.glswing.node.document.*;
import com.vingame.glswing.ui.GLLabel;
class GLCameraLayoutRoutingTest {
 @Test void cameraIsPersistedWithoutZoom()throws Exception{
  var state=new GLNodeEditorViewState(Map.of(),Map.of(),new GLNodeCameraState(-123,456));
  var asset=new GLNodeGraphAsset("root",1,GLNodeGraphAssetKind.PROJECT,new GLNodeGraph().snapshot(),state);
  var codec=new GLNodeGraphDocumentCodec();var bytes=new java.io.ByteArrayOutputStream();codec.write(new GLNodeGraphDocument(asset,Map.of(),List.of()),bytes);
  assertEquals(state,codec.read(new java.io.ByteArrayInputStream(bytes.toByteArray()),codec.currentSchemaVersion()).root().viewState());
 }
 @Test void replacementMovesOnlyToAnEmptyCompatibleInput(){
  var editor=new GLNodeEditor();var source=new GLNode("Source");var other=new GLNode("Other");var target=new GLNode("Math");
  var first=source.addOutput("out",new GLLabel("Out"));var second=other.addOutput("out",new GLLabel("Out"));var a=target.addInput("a",new GLLabel("A"));var b=target.addInput("b",new GLLabel("B"));
  editor.addNode(source,0,0);editor.addNode(other,0,100);editor.addNode(target,300,0);
  target.setConnectionReplacementRouter((old,next)->old.getInput()==a?b:a);
  editor.connect(first,a);editor.connect(second,a);
  assertTrue(editor.getConnections().stream().anyMatch(c->c.getOutput()==first&&c.getInput()==b));
  assertTrue(editor.getConnections().stream().anyMatch(c->c.getOutput()==second&&c.getInput()==a));
  var third=new GLNode("Third");var out=third.addOutput("out",new GLLabel("Out"));editor.addNode(third,0,200);editor.connect(out,a);
  assertEquals(2,editor.getConnections().size());assertTrue(editor.getConnections().stream().anyMatch(c->c.getOutput()==first&&c.getInput()==b));
 }
 @Test void arrangeAlignsIndependentChainsAndKeepsSpacing(){
  var editor=new GLNodeEditor();var a=new GLNode("A");var b=new GLNode("B");var c=new GLNode("C");var d=new GLNode("D");
  var ao=a.addOutput("o",new GLLabel("Out"));var bo=b.addOutput("o",new GLLabel("Out"));var ci=c.addInput("i",new GLLabel("In"));var di=d.addInput("i",new GLLabel("In"));
  editor.addNode(a,0,0);editor.addNode(b,0,200);editor.addNode(c,300,0);editor.addNode(d,300,200);editor.connect(ao,di);editor.connect(bo,ci);
  GLNodeAutoLayout.arrange(editor);assertEquals(a.getY(),d.getY(),.01);assertEquals(b.getY(),c.getY(),.01);
  assertTrue(d.getX()>=a.getX()+a.getWidth()+editor.getHorizontalNodeSpacing());assertTrue(b.getY()>=a.getY()+a.getHeight()+editor.getVerticalNodeSpacing());
 }
}
