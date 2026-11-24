package com.vingame.glswing.node;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.ui.*;
class GLNodeLabelAlignmentTest {
    @Test void portLabelsAndConnectedEditorReplacementsAreCentered(){
        var node=new GLNode("Test");
        node.addInput("value",GLSlider.free("Value",0));
        var output=new GLLabel("Output");node.addOutput("out",output);
        var labels=node.getGLChildren().stream().filter(GLLabel.class::isInstance).map(GLLabel.class::cast).toList();
        assertEquals(2,labels.size());
        for(var label:labels)assertEquals(GLLabel.VerticalAlignment.CENTER,label.getVerticalAlignment());
        assertEquals(GLLabel.Alignment.RIGHT,output.getAlignment());
    }
}
