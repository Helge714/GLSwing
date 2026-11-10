package com.vingame.glswing.node;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import javax.swing.SwingUtilities;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.rendering.*;
class GLGraphVisualPerformanceTest {
    private static GLNodeConnection wire(){var a=new GLNode("A");var b=new GLNode("B");return new GLNodeConnection(a.addOutput("out",new GLLabel("Out")),b.addInput("in",new GLLabel("In")));}
    @Test void builtInGradientsReuseDataAndInvalidateEveryVisualInput(){
        var cache=new GLNodeConnectionCurves();var wire=wire();
        var first=cache.colors(wire,32,Color.RED,Color.BLUE,GLColorInterpolation.LINEAR_RGB);
        for(int i=0;i<60;i++)assertSame(first,cache.colors(wire,32,new Color(Color.RED.getRGB(),true),Color.BLUE,GLColorInterpolation.LINEAR_RGB));
        for(int i=0;i<32;i++)assertEquals(GLColorInterpolation.LINEAR_RGB.interpolate(Color.RED,Color.BLUE,i/31f),first[i]);
        assertNotSame(first,cache.colors(wire,33,Color.RED,Color.BLUE,GLColorInterpolation.LINEAR_RGB));
        var changed=cache.colors(wire,32,Color.RED,Color.GREEN,GLColorInterpolation.LINEAR_RGB);assertNotSame(first,changed);
        assertNotSame(changed,cache.colors(wire,32,Color.RED,Color.GREEN,GLColorInterpolation.SRGB));
        var faded=cache.colors(wire,32,new Color(255,0,0,80),Color.GREEN,GLColorInterpolation.SRGB);assertEquals(80,faded[0].getAlpha());
        cache.remove(wire);assertNotSame(faded,cache.colors(wire,32,new Color(255,0,0,80),Color.GREEN,GLColorInterpolation.SRGB));
        int[] evaluations={0};GLColorInterpolator custom=(a,b,t)->{evaluations[0]++;return a;};
        assertNotSame(cache.colors(wire,4,Color.RED,Color.BLUE,custom),cache.colors(wire,4,Color.RED,Color.BLUE,custom));assertEquals(8,evaluations[0]);
    }
    @Test void staticThousandWireSceneUsesOneGradientPerWireAcrossSixtyFrames(){
        var cache=new GLNodeConnectionCurves();var wires=new ArrayList<GLNodeConnection>();
        var arrays=Collections.newSetFromMap(new IdentityHashMap<Color[],Boolean>());
        for(int i=0;i<1000;i++)wires.add(wire());
        for(int frame=0;frame<60;frame++)for(var wire:wires)arrays.add(cache.colors(wire,32,Color.RED,Color.BLUE,GLColorInterpolation.LINEAR_RGB));
        assertEquals(1000,arrays.size());
    }
    @Test void viewportIncludesSocketsShadowsAndZoomedOrPannedNodes(){
        var viewport=GLNodeViewport.of(800,400,0,0,1);int visible=0;
        var node=new GLNode("Bounds");node.setSize(200,100);node.setVisualOverflow(20);
        for(int i=0;i<1000;i++){node.setLocation(i*250,100);if(viewport.includes(node))visible++;}
        assertEquals(4,visible);
        node.setLocation(815,100);assertTrue(viewport.includes(node));node.setLocation(850,100);assertFalse(viewport.includes(node));
        node.setLocation(1100,100);assertTrue(GLNodeViewport.of(800,400,0,0,.5f).includes(node));
        assertFalse(GLNodeViewport.of(800,400,0,0,2).includes(node));
        node.setLocation(-300,100);assertTrue(GLNodeViewport.of(800,400,400,0,1).includes(node));
        var transformed=new GLNode("Transformed"){@Override protected void transformComponent(GLContext2D c){}};
        transformed.setBounds(10000,10000,100,100);assertTrue(viewport.includes(transformed),"Unknown presentation transforms must remain conservative");
    }
    static class CountedNode extends GLNode {
        int traversals;CountedNode(){super("Counted");}
        @Override protected void renderChildren(GLContext2D c){traversals++;super.renderChildren(c);}
    }
    @Test @EnabledIfSystemProperty(named="glswing.test.gl",matches="true")
    void offscreenTreesAreSkippedButCrossingConnectionsRemainVisible()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var editor=new GLNodeEditor();editor.setSize(800,400);
            var a=new CountedNode();var b=new CountedNode();var out=a.addOutput("out",new GLLabel("Out"));var in=b.addInput("in",new GLLabel("In"));
            out.setColor(Color.MAGENTA);in.setColor(Color.MAGENTA);editor.addNode(a,-1400,100);editor.addNode(b,1400,100);editor.connect(out,in);
            editor.setSelectedNodes(List.of(a));
            var image=GLComponentImageCapture.capture(editor,800,400,800,400,false);
            assertEquals(0,a.traversals);assertEquals(0,b.traversals);assertEquals(List.of(a),editor.getSelectedNodes());
            boolean crossing=false;for(int y=0;y<400;y++)if((image.getRGB(400,y)&0xffffff)==0xff00ff)crossing=true;
            assertTrue(crossing,"A cable crossing the viewport must survive when both endpoints are outside");
            editor.setPan(-1400,0);GLComponentImageCapture.capture(editor,800,400,800,400,false);assertTrue(b.traversals>0);assertEquals(0,a.traversals);
            editor.resetView();int before=b.traversals;GLComponentImageCapture.capture(editor,800,400,800,400,false);assertEquals(before,b.traversals);
        });
    }
}
