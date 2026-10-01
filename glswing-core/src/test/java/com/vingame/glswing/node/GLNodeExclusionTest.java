package com.vingame.glswing.node;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.node.model.*;

class GLNodeExclusionTest {
    static class Fixture {
        final GLNodeTypeRegistry types=new GLNodeTypeRegistry();
        final GLNodeGraph graph=new GLNodeGraph();
        final GLNodeId a=GLNodeId.random(),b=GLNodeId.random(),n=GLNodeId.random(),x=GLNodeId.random(),y=GLNodeId.random(),z=GLNodeId.random();
        Fixture(boolean connectFirst) {
            types.register(new GLNodeTypeDescriptor("source",GLNodePortDescriptor.output("out","audio")));
            types.register(new GLNodeTypeDescriptor("node",GLNodePortDescriptor.input("first","audio",false,1),
                    GLNodePortDescriptor.input("second","audio",false,1),GLNodePortDescriptor.output("audio","audio"),
                    GLNodePortDescriptor.output("otherAudio","audio"),GLNodePortDescriptor.output("text","text")));
            types.register(new GLNodeTypeDescriptor("sink",GLNodePortDescriptor.input("in","audio",true,1)));
            types.register(new GLNodeTypeDescriptor("textSink",GLNodePortDescriptor.input("in","text",true,1)));
            graph.edit("Setup",e->{
                e.addNode(new GLGraphNode(a,"source")); e.addNode(new GLGraphNode(b,"source")); e.addNode(new GLGraphNode(n,"node"));
                e.addNode(new GLGraphNode(x,"sink")); e.addNode(new GLGraphNode(y,"sink")); e.addNode(new GLGraphNode(z,"textSink"));
                // Deliberately connect second first: fallback follows port order, not wire creation order.
                e.addConnection(edge(b,"out",n,"second"));
                if(connectFirst) e.addConnection(edge(a,"out",n,"first"));
                e.addConnection(edge(n,"audio",x,"in")); e.addConnection(edge(n,"otherAudio",y,"in")); e.addConnection(edge(n,"text",z,"in"));
            });
        }
        GLNodeGraphSnapshot exclude(GLNodeId... ids) { return GLNodeExclusion.plan(graph.snapshot(),types,GLGraphCyclePolicy.ACYCLIC,Set.of(ids)).orElseThrow(); }
        void forwardedFrom(GLNodeGraphSnapshot result,GLNodeId id) {
            assertEquals(2,result.connections().size());
            assertTrue(result.connections().stream().allMatch(e->e.output().nodeId().equals(id)));
            assertEquals(Set.of(x,y),result.connections().stream().map(e->e.input().nodeId()).collect(java.util.stream.Collectors.toSet()));
            assertTrue(result.findNode(n).isEmpty());
        }
    }
    static GLGraphConnection edge(GLNodeId a,String out,GLNodeId b,String in) {
        return new GLGraphConnection(GLNodeConnectionId.random(),new GLNodeEndpoint(a,out),new GLNodeEndpoint(b,in));
    }
    @Test void defaultUsesFirstConnectedInputAndAllCompatibleOutputs() {
        var f=new Fixture(true); f.forwardedFrom(f.exclude(f.n),f.a);
    }
    @Test void explicitPreferenceWinsAndUnconnectedPreferenceFallsBack() {
        var f=new Fixture(true); f.types.setExclusionInput("node","second"); f.forwardedFrom(f.exclude(f.n),f.b);
        var missing=new Fixture(false); missing.types.setExclusionInput("node","first"); missing.forwardedFrom(missing.exclude(missing.n),missing.b);
    }
    @Test void noSourceOrNoCompatibleTargetStillDeletes() {
        var f=new Fixture(true);
        var sources=f.exclude(f.a,f.b,f.n); assertTrue(sources.connections().isEmpty()); assertEquals(3,sources.nodes().size());
        f.graph.edit("Only incompatible output",e->{ e.removeNode(f.x); e.removeNode(f.y); });
        var result=f.exclude(f.n); assertTrue(result.connections().isEmpty()); assertTrue(result.findNode(f.n).isEmpty());
    }
    @Test void sourceCapacitySkipsExcessConnectionsWithoutBlockingDeletion() {
        var f=new Fixture(true);
        f.types.replace(new GLNodeTypeDescriptor("source",GLNodePortDescriptor.output("out","audio",GLPortTiming.CURRENT,1)));
        var result=f.exclude(f.n); assertTrue(result.findNode(f.n).isEmpty()); assertEquals(1,result.connections().size());
    }
    @Test void cyclesInsideDeletedSelectionDoNotBlockDeletion() {
        var f=new Fixture(false);
        f.graph.edit("Self loop",e->{
            for(var c:f.graph.snapshot().connections()) if(c.input().nodeId().equals(f.n)) e.removeConnection(c.id());
            e.addConnection(edge(f.n,"audio",f.n,"first"));
        });
        var result=f.exclude(f.n); assertTrue(result.connections().isEmpty()); assertTrue(result.findNode(f.n).isEmpty());
    }
}
