package com.vingame.glswing.node.composite;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.node.model.GLNodeValue;
class GLCustomEditorCapabilityTest {
 @Test void explicitCustomEditorsCanBeLinked(){
  assertTrue(new GLNodeEditorCapabilities(GLNodeEditorCapabilities.Kind.CUSTOM,true,true,GLNodeValue.of(List.of(0,0,0)),Map.of()).linkable());
  assertFalse(new GLNodeEditorCapabilities(GLNodeEditorCapabilities.Kind.CUSTOM,false,true,GLNodeValue.of(0),Map.of()).linkable());
  assertFalse(new GLNodeEditorCapabilities(GLNodeEditorCapabilities.Kind.CUSTOM,true,false,GLNodeValue.of(0),Map.of()).linkable());
 }
}
