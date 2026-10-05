package com.vingame.glswing.node;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.node.binding.*;
import com.vingame.glswing.node.model.*;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.ui.GLLabel;

class GLNodeInsertionTest {
    static class Fixture implements AutoCloseable {
        final GLNodeTypeRegistry types = new GLNodeTypeRegistry();
        final GLNodeGraph graph = new GLNodeGraph();
        final GLNodeEditor editor = new GLNodeEditor();
        final GLHistoryManager history = new GLHistoryManager();
        final GLNodeId a=GLNodeId.random(), b=GLNodeId.random(), c=GLNodeId.random(), n=GLNodeId.random(), unrelated=GLNodeId.random();
        final GLNodeConnectionId edge=GLNodeConnectionId.random();
        final GLNodeEditorBinding binding;
        final GLNodeGraphEditingSession session;
        Fixture(float targetX) {
            types.register(new GLNodeTypeDescriptor("pass",GLNodePortDescriptor.input("in","signal",false,1),GLNodePortDescriptor.output("out","signal")));
            types.setInsertionPorts("pass",new GLNodeInsertionPorts("in","out"));
            graph.edit("Setup",e->{
                for(var id:List.of(a,b,c,n,unrelated)) e.addNode(new GLGraphNode(id,"pass"));
                e.addConnection(new GLGraphConnection(edge,new GLNodeEndpoint(a,"out"),new GLNodeEndpoint(b,"in")));
                e.addConnection(new GLGraphConnection(GLNodeConnectionId.random(),new GLNodeEndpoint(b,"out"),new GLNodeEndpoint(c,"in")));
            });
            editor.setGLHistoryManager(history);
            binding=new GLNodeEditorBinding(graph,types,editor,(model,type)->{
                var view=new GLNode("Pass"); view.addInput("in",new GLLabel("In")); view.addOutput("out",new GLLabel("Out")); return view;
            },GLGraphCyclePolicy.ACYCLIC,new GLNodeEditorViewState(Map.of(a,new GLNodeViewState(0,0,140,100),b,new GLNodeViewState(targetX,0,140,100),c,new GLNodeViewState(targetX+220,0,140,100),n,new GLNodeViewState(170,240,140,100),unrelated,new GLNodeViewState(targetX+400,240,140,100))));
            session=new GLNodeGraphEditingSession(binding,history);
        }
        GLNode view(GLNodeId id) { return binding.findNodeView(id).orElseThrow(); }
        public void close() { binding.close(); }
    }
    @Test void insertionMovesChainAndUndoRestoresWholeGesture() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(()-> { try(var f=new Fixture(300)) {
            var before=f.binding.captureViewState(); var graph=f.graph.snapshot();
            f.view(f.n).setLocation(180,20);
            assertTrue(f.session.insertIntoConnection(f.n,f.edge,170,240));
            assertEquals(200,f.view(f.n).getX()); assertEquals(400,f.view(f.b).getX());
            assertEquals(620,f.view(f.c).getX()); assertEquals(700,f.view(f.unrelated).getX());
            assertEquals(3,f.graph.snapshot().connections().size());
            assertTrue(f.editor.getConnections().stream().anyMatch(c->c.getOutput().getNode()==f.view(f.a)&&c.getInput().getNode()==f.view(f.n)));
            assertTrue(f.editor.getConnections().stream().anyMatch(c->c.getOutput().getNode()==f.view(f.n)&&c.getInput().getNode()==f.view(f.b)));
            var after=f.binding.captureViewState();
            f.history.undo(); assertEquals(180,f.view(f.n).getX()); assertEquals(20,f.view(f.n).getY());
            assertEquals(graph.connections(),f.graph.snapshot().connections()); assertEquals(300,f.view(f.b).getX());
            f.history.undo(); assertEquals(before,f.binding.captureViewState());
            assertFalse(f.history.canUndo());
            f.history.redo(); f.history.redo(); assertEquals(after,f.binding.captureViewState()); assertEquals(3,f.graph.snapshot().connections().size());
        }});
    }
    @Test void enoughRoomKeepsChainAndConfiguredPortsAreRequired() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(()-> { try(var f=new Fixture(650)) {
            assertFalse(f.session.canInsertIntoConnection(f.b,f.edge));
            f.types.setInsertionPorts("pass",null); assertFalse(f.session.canInsertIntoConnection(f.n,f.edge));
            f.types.setInsertionPorts("pass",new GLNodeInsertionPorts("in","out"));
            f.view(f.n).setLocation(320,20);
            assertTrue(f.session.insertIntoConnection(f.n,f.edge,170,240));
            assertEquals(320,f.view(f.n).getX()); assertEquals(650,f.view(f.b).getX()); assertEquals(870,f.view(f.c).getX());
        }});
    }
    @Test void dragHighlightsOnlyOneWireAndCaptureLossDoesNotInsert() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(()-> { try(var f=new Fixture(650)) {
            var node=f.view(f.n);
            var connection=f.binding.findConnectionView(f.edge).orElseThrow();
            float y=(connection.getOutput().getCenterYInNode()+connection.getInput().getCenterYInNode())/2;
            f.editor.beginNodeMove(node); f.editor.dragNodeMove(node,150,y-270);
            assertSame(connection,f.editor.getInsertionCandidate());
            f.editor.endNodeMove(node,false); assertNull(f.editor.getInsertionCandidate()); assertEquals(2,f.graph.snapshot().connections().size());
            f.editor.beginNodeMove(node); f.editor.dragNodeMove(node,1,0);
            assertSame(connection,f.editor.getInsertionCandidate());
            f.editor.endNodeMove(node); assertEquals(3,f.graph.snapshot().connections().size()); assertNull(f.editor.getInsertionCandidate());
        }});
    }
    @Test void multiSelectionAndAlreadyConnectedNodesNeverOfferInsertion() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(()-> { try(var f=new Fixture(650)) {
            f.editor.setSelectedNodes(List.of(f.view(f.n),f.view(f.unrelated)));
            f.editor.beginNodeMove(f.view(f.n)); f.editor.dragNodeMove(f.view(f.n),150,-240);
            assertNull(f.editor.getInsertionCandidate()); f.editor.endNodeMove(f.view(f.n));
            assertFalse(f.session.canInsertIntoConnection(f.a,f.edge));
            assertThrows(IllegalArgumentException.class,()->f.types.setInsertionPorts("pass",new GLNodeInsertionPorts("out","in")));
        }});
    }
    @Test void placementUsesSamePreviewAndSingleUndo() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(()-> { try(var f=new Fixture(650)) {
            var before=f.binding.captureViewState();
            f.editor.setSelectedNodes(List.of(f.view(f.n)));
            var move=f.session.beginMoveSelection(170,240);
            var connection=f.binding.findConnectionView(f.edge).orElseThrow();
            float y=(connection.getOutput().getCenterYInNode()+connection.getInput().getCenterYInNode())/2-30;
            move.moveTo(320,y); assertSame(connection,f.editor.getInsertionCandidate());
            move.confirm(); assertEquals(3,f.graph.snapshot().connections().size());
            f.history.undo(); assertEquals(320,f.view(f.n).getX()); assertEquals(2,f.graph.snapshot().connections().size());
            f.history.undo(); assertEquals(before,f.binding.captureViewState()); assertFalse(f.history.canUndo());
            var cancelled=f.session.beginMoveSelection(170,240);
            cancelled.moveTo(320,y); cancelled.cancel(); assertNull(f.editor.getInsertionCandidate());
            assertEquals(before,f.binding.captureViewState()); assertEquals(2,f.graph.snapshot().connections().size());
        }});
    }
    @Test void exclusionReconnectsAndOnlyClosesSimpleChains() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(()-> { try(var f=new Fixture(300)) {
            var before=f.graph.snapshot();
            f.editor.setSelectedNodes(List.of(f.view(f.b)));
            assertTrue(f.session.canExcludeSelection()); f.session.excludeSelection();
            assertTrue(f.graph.snapshot().findNode(f.b).isEmpty());
            assertEquals(200,f.view(f.c).getX()); // Source width 140 + theme spacing 60.
            assertTrue(f.graph.snapshot().connections().stream().anyMatch(e->e.output().nodeId().equals(f.a)&&e.input().nodeId().equals(f.c)));
            f.history.undo(); assertEquals(before.connections(),f.graph.snapshot().connections()); assertEquals(520,f.view(f.c).getX());
            f.graph.edit("Branch",e->e.addConnection(new GLGraphConnection(GLNodeConnectionId.random(),new GLNodeEndpoint(f.b,"out"),new GLNodeEndpoint(f.unrelated,"in"))));
            f.editor.setSelectedNodes(List.of(f.view(f.b))); f.session.excludeSelection();
            assertEquals(520,f.view(f.c).getX()); assertEquals(700,f.view(f.unrelated).getX());
            assertEquals(2,f.graph.snapshot().connections().size());
        }});
    }
    @Test void clipboardAndDuplicatePreservePointerOffsetAndCancel() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(()-> { try(var f=new Fixture(650)) {
            f.editor.setSelectedNodes(List.of(f.view(f.n)));
            f.session.copySelection(195,270); // Node starts at 170,240: offset 25,30.
            var paste=f.session.beginPaste(525,530);
            var copy=f.editor.getSelectedNodes().get(0);
            assertEquals(500,copy.getX()); assertEquals(500,copy.getY());
            paste.moveTo(625,630); assertEquals(600,copy.getX()); assertEquals(600,copy.getY());
            paste.cancel(); assertEquals(5,f.graph.snapshot().nodes().size());
            f.editor.setSelectedNodes(List.of(f.view(f.n)));
            var duplicate=f.session.beginDuplicate(195,270);
            var dup=f.editor.getSelectedNodes().get(0);
            assertEquals(170,dup.getX()); assertEquals(240,dup.getY());
            duplicate.moveTo(295,370); assertEquals(270,dup.getX()); assertEquals(340,dup.getY());
            duplicate.confirm(); f.history.undo(); assertEquals(5,f.graph.snapshot().nodes().size());
        }});
    }
    @Test void sourceWithoutInputIsDeletedAndMultiNodeChainReconnects() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(()-> { try(var f=new Fixture(300)) {
            f.editor.setSelectedNodes(List.of(f.view(f.a)));
            assertTrue(f.session.canExcludeSelection()); f.session.excludeSelection(); assertEquals(4,f.graph.snapshot().nodes().size());
            f.history.undo(); assertEquals(5,f.graph.snapshot().nodes().size());
            f.graph.edit("Next",e->e.addConnection(new GLGraphConnection(GLNodeConnectionId.random(),new GLNodeEndpoint(f.c,"out"),new GLNodeEndpoint(f.unrelated,"in"))));
            f.editor.setSelectedNodes(List.of(f.view(f.b),f.view(f.c)));
            assertTrue(f.session.canExcludeSelection()); f.session.excludeSelection();
            assertEquals(3,f.graph.snapshot().nodes().size()); assertEquals(1,f.graph.snapshot().connections().size());
            var edge=f.graph.snapshot().connections().get(0);
            assertEquals(f.a,edge.output().nodeId()); assertEquals(f.unrelated,edge.input().nodeId());
        }});
    }
}
