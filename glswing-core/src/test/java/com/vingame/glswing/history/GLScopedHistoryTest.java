package com.vingame.glswing.history;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class GLScopedHistoryTest {
 @Test void independentStacksShareSaveCheckpoint(){
  var h=new GLHistoryManager();int[] root={0},group={0};h.setActiveScope("root");
  h.perform("Root",()->root[0]=1,()->root[0]=0);h.setActiveScope("group");assertFalse(h.canUndo());
  h.perform("Group",()->group[0]=2,()->group[0]=0);h.markPersisted();assertFalse(h.isModified());
  h.setActiveScope("root");assertTrue(h.undo());assertEquals(0,root[0]);assertEquals(2,group[0]);assertTrue(h.isModified());
  h.setActiveScope("group");assertTrue(h.undo());assertEquals(0,group[0]);h.redo();assertTrue(h.isModified());
  h.setActiveScope("root");h.redo();assertFalse(h.isModified());h.reset();h.setActiveScope("group");assertFalse(h.canUndo());assertFalse(h.isModified());
 }
 @Test void synchronizationDoesNotRecordAndRestoresRecordingAfterFailure(){
  var h=new GLHistoryManager();assertThrows(IllegalStateException.class,()->h.withoutRecording(()->{h.perform("Sync",()->{},()->{});throw new IllegalStateException();}));
  assertFalse(h.isApplying());assertFalse(h.canUndo());assertFalse(h.isModified());
  h.perform("Edit",()->{},()->{});assertTrue(h.canUndo());
 }
 @Test void transactionStaysWithStartingGraphAcrossNavigation(){
  var h=new GLHistoryManager();int[] value={0};h.setActiveScope("root");
  try(var transaction=h.beginTransaction("Create and open")){h.perform("Create",()->value[0]=1,()->value[0]=0);h.setActiveScope("group");}
  assertFalse(h.canUndo());h.setActiveScope("root");assertTrue(h.undo());assertEquals(0,value[0]);
 }
}
