package com.vingame.glswing.ui;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class GLDriverTargetTest {
 @Test void discreteTargetsCycleAndAbsoluteValuesReachLastEntry(){double[] value={0};var choice=GLDriverTarget.choice(()->value[0],v->value[0]=v,()->3);choice.pulse();assertEquals(1,value[0]);choice.normalized(1);assertEquals(2,value[0]);choice.pulse();assertEquals(0,value[0]);var toggle=GLDriverTarget.toggle(()->value[0],v->value[0]=v);toggle.pulse();assertEquals(1,value[0]);toggle.pulse();assertEquals(0,value[0]);}
 @Test void mappingsAndPickupDoNotJump(){var mapping=new GLDriverMapping(20,20000,false,true,false);assertEquals(Math.sqrt(20*20000),mapping.map(.5),1e-8);var pickup=new GLDriverMapping.Pickup();var linear=new GLDriverMapping(0,1,false,false,true);assertFalse(pickup.accepts(linear,.1,.5));assertTrue(pickup.accepts(linear,.7,.5));assertFalse(pickup.accepts(linear,.1,.8));pickup.reset();assertFalse(pickup.accepts(linear,.2,.8));}
 @Test void logarithmicPickupUsesControllerResolution(){var p=new GLDriverMapping.Pickup();var m=new GLDriverMapping(20,20000,false,true,true);assertFalse(p.accepts(m,.4,440));assertTrue(p.accepts(m,.5,440));}
 @Test void externalChangesStayDirtyAcrossUndoAndKeepRedo(){var history=new com.vingame.glswing.history.GLHistoryManager();history.perform("edit",()->{},()->{});history.markPersisted();history.markExternalChange();history.undo();history.redo();assertTrue(history.isModified());history.markPersisted();assertFalse(history.isModified());history.undo();history.markExternalChange();assertTrue(history.canRedo());}
}
