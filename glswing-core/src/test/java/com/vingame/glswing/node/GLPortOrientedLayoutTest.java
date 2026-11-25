package com.vingame.glswing.node;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.node.binding.GLNodeViewState;
import com.vingame.glswing.ui.GLLabel;

class GLPortOrientedLayoutTest {
    private GLNode node(GLNodeEditor e,String title,boolean mirrored,float width) {
        var n=new GLNode(title);n.addInput("in",new GLLabel("In")).setMaximumConnections(20);n.addOutput("out",new GLLabel("Out"));
        e.addNode(n,0,0);n.setSize(width,100);n.setMirrored(mirrored);return n;
    }
    private void wire(GLNodeEditor e,GLNode a,GLNode b){e.addProjectedConnection(new GLNodeConnection(a.findPort("out"),b.findPort("in")));}
    private void check(GLNodeEditor e,boolean preserve) {
        var before=e.getNodes().stream().map(GLNodeViewState::capture).toList();
        if(preserve)GLNodeAutoLayout.arrangePreservingOrder(e);else GLNodeAutoLayout.arrange(e);
        assertEquals(before,e.getNodes().stream().map(GLNodeViewState::capture).toList(),"Repeated Arrange must be stable");
        for(int i=0;i<e.getNodes().size();i++)for(int j=i+1;j<e.getNodes().size();j++) {
            var a=e.getNodes().get(i);var b=e.getNodes().get(j);double hg=e.getHorizontalNodeSpacing(),vg=e.getVerticalNodeSpacing();
            assertTrue(a.getX()+a.getWidth()+hg<=b.getX()+.02||b.getX()+b.getWidth()+hg<=a.getX()+.02
                ||a.getY()+a.getHeight()+vg<=b.getY()+.02||b.getY()+b.getHeight()+vg<=a.getY()+.02,"Theme clearance: "+a.getTitle()+" / "+b.getTitle());
        }
    }
    @Test void oneMirroredNeighbourUsesACompactUTurnOnEitherSide() {
        for(boolean firstMirrored:List.of(false,true))for(boolean preserve:List.of(false,true)) {
            var e=new GLNodeEditor();var a=node(e,"A",firstMirrored,210);var b=node(e,"B",!firstMirrored,160);wire(e,a,b);
            if(preserve)GLNodeAutoLayout.arrangePreservingOrder(e);else GLNodeAutoLayout.arrange(e);
            assertEquals(a.getX()+a.findPort("out").getCenterXInNode(),b.getX()+b.findPort("in").getCenterXInNode(),.01);
            assertEquals(a.getY()+a.getHeight()+e.getVerticalNodeSpacing(),b.getY(),.01);check(e,preserve);
        }
    }
    @Test void mixedFeedbackFoldsBelowTheForwardPathWithoutChangingGraphSemantics() {
        for(boolean preserve:List.of(false,true)) {
            var e=new GLNodeEditor();var c=node(e,"Return C",true,180);var d=node(e,"Return D",true,180);
            var a=node(e,"A",false,180);var b=node(e,"B",false,180);var source=node(e,"Source",false,180);var sink=node(e,"Sink",false,180);
            wire(e,source,a);wire(e,a,b);wire(e,b,c);wire(e,c,d);wire(e,d,a);wire(e,b,sink);
            var wires=List.copyOf(e.getConnections());var sizes=e.getNodes().stream().map(n->List.of(n.getWidth(),n.getHeight(),n.isMirrored())).toList();
            if(preserve)GLNodeAutoLayout.arrangePreservingOrder(e);else GLNodeAutoLayout.arrange(e);
            assertTrue(a.getX()<b.getX());assertTrue(c.getX()>d.getX());
            assertTrue(c.getY()>=b.getY()+b.getHeight()+e.getVerticalNodeSpacing()-.01);
            assertTrue(d.getY()>=a.getY()+a.getHeight()+e.getVerticalNodeSpacing()-.01);
            assertEquals(b.getX(),c.getX(),.01);assertEquals(a.getX(),d.getX(),.01);
            assertTrue(source.getX()+source.getWidth()<a.getX());assertTrue(sink.getX()>b.getX()+b.getWidth());
            assertEquals(wires,e.getConnections());assertEquals(sizes,e.getNodes().stream().map(n->List.of(n.getWidth(),n.getHeight(),n.isMirrored())).toList());check(e,preserve);
        }
    }
    @Test void singleReturnNodeLandsUnderTheSignalPathInsteadOfInAnExtraColumn() {
        var e=new GLNodeEditor();var a=node(e,"A",false,180);var b=node(e,"B",false,180);var c=node(e,"Return",true,180);
        wire(e,a,b);wire(e,b,c);wire(e,c,a);GLNodeAutoLayout.arrangePreservingOrder(e);
        assertTrue(c.getX()>=a.getX()&&c.getX()+c.getWidth()<=b.getX()+b.getWidth()+.01);
        assertTrue(c.getY()>Math.max(a.getY()+a.getHeight(),b.getY()+b.getHeight()));check(e,true);
    }
    @Test void manyOrientationChangesRemainBoundedAndDeterministic() {
        assertTimeout(Duration.ofSeconds(8),()->{
            var e=new GLNodeEditor();GLNode previous=null;
            for(int i=0;i<1000;i++){var n=node(e,"N"+i,(i/20)%2==1,180);if(previous!=null)wire(e,previous,n);previous=n;}
            GLNodeAutoLayout.arrangePreservingOrder(e);var before=e.getNodes().stream().map(GLNodeViewState::capture).toList();
            GLNodeAutoLayout.arrangePreservingOrder(e);assertEquals(before,e.getNodes().stream().map(GLNodeViewState::capture).toList());
        });
    }
}
