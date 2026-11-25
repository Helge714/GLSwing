package com.vingame.glswing.node;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.io.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.node.binding.*;
import com.vingame.glswing.node.document.*;
import com.vingame.glswing.node.model.*;
import com.vingame.glswing.ui.*;

class GLNodeMirroringTest {
    private GLNode node(GLNodeEditor editor,String name) {
        var n=new GLNode(name);n.addInput("in",new GLLabel("In"));n.addOutput("out",new GLLabel("Out"));editor.addNode(n,0,0);return n;
    }
    @Test void portsAndLabelsMirrorWithoutChangingDirectionOrDimensions() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var editor=new GLNodeEditor();var n=node(editor,"Mirrored");float width=n.getWidth(),height=n.getHeight();
            n.setMirrored(true);
            assertEquals(width,n.findPort("in").getCenterXInNode(),.01);assertEquals(0,n.findPort("out").getCenterXInNode(),.01);
            assertEquals(GLNodePort.Direction.INPUT,n.findPort("in").getDirection());assertEquals(1,n.findPort("in").getOutwardSign());
            assertEquals(-1,n.findPort("out").getOutwardSign());assertEquals(height,n.getHeight());
            var labels=n.getGLChildren().stream().filter(GLLabel.class::isInstance).map(GLLabel.class::cast).toList();
            assertEquals(GLLabel.Alignment.RIGHT,labels.stream().filter(l->l.getText().equals("In")).findFirst().orElseThrow().getAlignment());
            assertEquals(GLLabel.Alignment.LEFT,labels.stream().filter(l->l.getText().equals("Out")).findFirst().orElseThrow().getAlignment());
            n.setMirrored(false);assertEquals(0,n.findPort("in").getCenterXInNode(),.01);assertEquals(width,n.findPort("out").getCenterXInNode(),.01);
        });
    }
    @Test void mirroredChainsFlowRightToLeftAndFeedbackKeepsClearance() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var e=new GLNodeEditor();var a=node(e,"A");var b=node(e,"B");var c=node(e,"C");
            for(var n:e.getNodes())n.setMirrored(true);
            e.addProjectedConnection(new GLNodeConnection(a.findPort("out"),b.findPort("in")));
            e.addProjectedConnection(new GLNodeConnection(b.findPort("out"),c.findPort("in")));
            GLNodeAutoLayout.arrangePreservingOrder(e);
            assertTrue(a.getX()>b.getX());assertTrue(b.getX()>c.getX());
            var before=e.getNodes().stream().map(GLNodeViewState::capture).toList();GLNodeAutoLayout.arrangePreservingOrder(e);assertEquals(before,e.getNodes().stream().map(GLNodeViewState::capture).toList());
            a.setMirrored(false);b.setMirrored(false);e.addProjectedConnection(new GLNodeConnection(c.findPort("out"),a.findPort("in")));
            GLNodeAutoLayout.arrange(e);
            for(var x:e.getNodes())for(var y:e.getNodes())if(x!=y)
                assertFalse(new java.awt.geom.Rectangle2D.Float(x.getX(),x.getY(),x.getWidth(),x.getHeight()).intersects(y.getX(),y.getY(),y.getWidth(),y.getHeight()));
            assertTrue(c.isMirrored());
            for(var n:e.getNodes())n.setMirrored(true);
            GLNodeAutoLayout.arrange(e);var cycle=e.getNodes().stream().map(GLNodeViewState::capture).toList();
            GLNodeAutoLayout.arrange(e);assertEquals(cycle,e.getNodes().stream().map(GLNodeViewState::capture).toList());
        });
    }
    @Test void mirroredGroupBoundariesFollowPhysicalFlow() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var e=new GLNodeEditor();var input=node(e,"Group Input");var inner=node(e,"Inner");var output=node(e,"Group Output");
            input.setAutoLayoutRole(GLNodeAutoLayout.Role.INPUT_BOUNDARY);output.setAutoLayoutRole(GLNodeAutoLayout.Role.OUTPUT_BOUNDARY);
            for(var n:e.getNodes())n.setMirrored(true);
            e.addProjectedConnection(new GLNodeConnection(input.findPort("out"),inner.findPort("in")));
            e.addProjectedConnection(new GLNodeConnection(inner.findPort("out"),output.findPort("in")));
            GLNodeAutoLayout.arrange(e);assertTrue(input.getX()>inner.getX());assertTrue(output.getX()<inner.getX());
        });
    }
    @Test void rightToLeftInsertionMakesRoomAndUndoRetainsOrientation() throws Exception {
        SwingUtilities.invokeAndWait(()->{try(var f=new GLNodeInsertionTest.Fixture(300)){
            for(var n:f.editor.getNodes()){n.setLocation(-n.getX()-n.getWidth(),n.getY());n.setMirrored(true);}
            var before=f.binding.captureViewState();var graph=f.graph.snapshot();
            assertTrue(f.session.insertIntoConnection(f.n,f.edge,f.view(f.n).getX(),f.view(f.n).getY()));
            assertEquals(f.editor.getHorizontalNodeSpacing(),f.view(f.a).getX()-f.view(f.n).getX()-f.view(f.n).getWidth(),.01);
            assertEquals(f.editor.getHorizontalNodeSpacing(),f.view(f.n).getX()-f.view(f.b).getX()-f.view(f.b).getWidth(),.01);
            assertTrue(f.history.undo());assertEquals(graph.nodes(),f.graph.snapshot().nodes());assertEquals(before,f.binding.captureViewState());
        }});
    }
    @Test void insertionHitTestingFollowsMirroredCurveTangents() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var e=new GLNodeEditor();var a=node(e,"A");var b=node(e,"B");a.setMirrored(true);b.setMirrored(true);
            a.setLocation(600,20);b.setLocation(50,240);var wire=new GLNodeConnection(a.findPort("out"),b.findPort("in"));e.addProjectedConnection(wire);
            float x0=a.getX()+wire.getOutput().getCenterXInNode(),y0=a.getY()+wire.getOutput().getCenterYInNode();
            float x1=b.getX()+wire.getInput().getCenterXInNode(),y1=b.getY()+wire.getInput().getCenterYInNode();
            float handle=Math.max(e.getMinimumConnectionHandleLength(),Math.abs(x1-x0)*e.getConnectionHandleFactor());
            double t=.2,u=1-t,x=u*u*u*x0+3*u*u*t*(x0-handle)+3*u*t*t*(x1+handle)+t*t*t*x1;
            double y=u*u*u*y0+3*u*u*t*y0+3*u*t*t*y1+t*t*t*y1;
            try {
                var method=GLNodeEditor.class.getDeclaredMethod("crossingDistance",GLNodeConnection.class,java.awt.geom.Rectangle2D.class);method.setAccessible(true);
                assertTrue(Double.isFinite((double)method.invoke(e,wire,new java.awt.geom.Rectangle2D.Double(x-2,y-2,4,4))));
            } catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
        });
    }
    @Test void schemaElevenRoundTripsNormalAndAuxiliaryOrientations() throws Exception {
        var id=GLNodeId.random();var graph=new GLNodeGraph();graph.edit("Create",e->e.addNode(new GLGraphNode(id,"test")));
        var state=new GLNodeEditorViewState(Map.of(id,new GLNodeViewState(10,20,200,100,"Title",true)),Map.of("input",new GLNodeViewState(0,0,200,100,null,true)));
        var document=new GLNodeGraphDocument(new GLNodeGraphAsset("root",1,GLNodeGraphAssetKind.PROJECT,graph.snapshot(),state));
        var codec=new GLNodeGraphDocumentCodec();var bytes=new ByteArrayOutputStream();codec.write(document,bytes);
        assertEquals(11,codec.currentSchemaVersion());assertEquals(document,codec.read(new ByteArrayInputStream(bytes.toByteArray()),codec.currentSchemaVersion()));
    }
}
