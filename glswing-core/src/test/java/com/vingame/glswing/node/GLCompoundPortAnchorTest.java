package com.vingame.glswing.node;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.ui.*;
class GLCompoundPortAnchorTest {
    @Test void anchorsToFirstChildAndRestoresHeightAfterDisconnect() {
        var editor=new GLNodeEditor();var source=new GLNode("source");var sink=new GLNode("sink");
        var group=new GLSliderGroup(GLSlider.free("X",1),GLSlider.free("Y",2),GLSlider.free("Z",3));
        var out=source.addOutput("out",new GLLabel("Out"));var in=sink.addInput("in",group);
        in.setContentHiddenWhenConnected(true);sink.setContentPortAnchor(group,group.getSliders().get(0));
        assertThrows(IllegalArgumentException.class,()->sink.setContentPortAnchor(group,new GLLabel("Other")));
        editor.addNode(source,0,0);editor.addNode(sink,200,0);
        float height=sink.getHeight(),row=group.getSliders().get(0).getPreferredGLSize().height();
        assertEquals(group.getY()+row/2,in.getY()+in.getHeight()/2,.01);
        var connection=editor.connect(out,in);
        assertEquals(height-2*row,sink.getHeight(),.01);
        assertEquals(group.getY()+row/2,in.getY()+in.getHeight()/2,.01);
        editor.removeConnection(connection);assertEquals(height,sink.getHeight(),.01);
        sink.setContentPortAnchor(group,null);
        assertEquals(group.getY()+group.getHeight()/2,in.getY()+in.getHeight()/2,.01);
    }
}
