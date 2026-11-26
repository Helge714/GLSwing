package com.vingame.glswing.node;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.ui.GLTextEditor;
import com.vingame.glswing.node.binding.*;
import com.vingame.glswing.node.document.*;
import com.vingame.glswing.node.model.*;
class GLNodeTitleTest {
 @Test void resetNameFollowsInheritedAndExplicitLanguageAfterEarlyMenuCreation(){
  var node=new GLNode("Default");var menu=node.getContextMenu();var reset=menu.getItems().get(1);
  assertEquals("Reset name",reset.getText());
  var parent=new GLNodeEditor();parent.setGLLocalizer(com.vingame.glswing.ui.GLLocalizer.GERMAN);parent.addNode(node,0,0);
  assertSame(menu,node.getContextMenu());assertEquals("Namen zurücksetzen",reset.getText());
  node.setGLLocalizer(com.vingame.glswing.ui.GLLocalizer.ENGLISH);assertEquals("Reset name",reset.getText());
  node.clearGLLocalizer();assertEquals("Namen zurücksetzen",reset.getText());
  node.setCustomTitle("Custom");reset.doClick();assertNull(node.getCustomTitle());assertEquals("Default",node.getTitle());
  parent.setGLLocalizer(com.vingame.glswing.ui.GLLocalizer.ENGLISH);assertEquals("Reset name",reset.getText());
 }

 @Test void resettingTitleUsesCurrentDefaultAndIsUndoable(){
  var node=new GLNode("Original");var history=new GLHistoryManager();node.setGLHistoryManager(history);node.setCustomTitle("Custom");node.setDefaultTitle("Translated");
  node.resetTitle();assertNull(node.getCustomTitle());assertEquals("Translated",node.getTitle());assertTrue(history.undo());assertEquals("Custom",node.getTitle());assertTrue(history.redo());assertEquals("Translated",node.getTitle());
 }
 @Test void tooltipsIncludeTheCurrentControlName(){
  class Button extends com.vingame.glswing.ui.GLButton {Button(){super("First");}String tip(){return toolTipTextAt(null);}} var control=new Button();control.setToolTipText("Description");assertEquals("First\nDescription",control.tip());control.setText("Second");assertEquals("Second\nDescription",control.tip());
 }
 @Test void renameSurvivesDefaultUpdatesAndUndoWithoutCopyingAnnotations()throws Exception{
  var node=new GLNode("Oscillator");var history=new GLHistoryManager();node.setGLHistoryManager(history);node.setTitleSuffix(" [Shared]");
  node.editTitle();var editor=(GLTextEditor)node.getGLChildren().stream().filter(c->c instanceof GLTextEditor).findFirst().orElseThrow();
  assertEquals("Oscillator",editor.getText());editor.setText("Töne & Raum");var finish=GLNode.class.getDeclaredMethod("finishTitleEditing",boolean.class);finish.setAccessible(true);finish.invoke(node,true);
  assertEquals("Töne & Raum [Shared]",node.getTitle());assertEquals("Töne & Raum",node.getCustomTitle());
  node.setTitle("Oszillator");assertEquals("Töne & Raum [Shared]",node.getTitle());history.undo();assertNull(node.getCustomTitle());assertEquals("Oszillator [Shared]",node.getTitle());history.redo();assertEquals("Töne & Raum",node.getCustomTitle());
 }
 @Test void confirmingDefaultTitleDoesNotCreateAnOverride()throws Exception{
  var node=new GLNode("Default");var history=new GLHistoryManager();node.setGLHistoryManager(history);node.setTitleSuffix(" [Shared]");node.editTitle();
  var finish=GLNode.class.getDeclaredMethod("finishTitleEditing",boolean.class);finish.setAccessible(true);finish.invoke(node,true);
  assertNull(node.getCustomTitle());assertFalse(history.canUndo());
 }
 @Test void viewStateAndCodecPreserveNamesSeparatelyFromDefaultLabels()throws Exception{
  var node=new GLNode("Default");node.setSize(200,100);node.setCustomTitle("Größe ü");node.setTitleSuffix(" [Instance]");var state=GLNodeViewState.capture(node);
  assertEquals("Größe ü",state.customTitle());var graph=new GLNodeGraph();var id=GLNodeId.random();graph.edit("Node",e->e.addNode(new GLGraphNode(id,"test.node",Map.of())));
  var document=new GLNodeGraphDocument(new GLNodeGraphAsset("test.root",1,GLNodeGraphAssetKind.PROJECT,graph.snapshot(),new GLNodeEditorViewState(Map.of(id,state))));
  var codec=new GLNodeGraphDocumentCodec();var bytes=new ByteArrayOutputStream();codec.write(document,bytes);var restored=codec.read(new ByteArrayInputStream(bytes.toByteArray()),codec.currentSchemaVersion());
  assertEquals(state,restored.root().viewState().nodes().get(id));assertEquals(11,codec.currentSchemaVersion());
 }
}
