package com.vingame.glswing.test;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.vingame.glswing.node.model.*;
class GLPortNamesTest {
 @Test void portNamesAcceptInternalSpacesButTypesStayIdentifiers(){
  assertEquals("Feedback Gain",GLNodePortDescriptor.input("Feedback Gain","number",false,1).id());
  assertEquals("Audio Out",GLNodePortDescriptor.output("Audio Out","number").id());
  assertThrows(IllegalArgumentException.class,()->GLNodePortDescriptor.input(" In","number",false,1));
  assertThrows(IllegalArgumentException.class,()->GLNodePortDescriptor.input("In","bad type",false,1));
  assertThrows(IllegalArgumentException.class,()->GLNodePortDescriptor.input("In\nOut","number",false,1));
 }
}
