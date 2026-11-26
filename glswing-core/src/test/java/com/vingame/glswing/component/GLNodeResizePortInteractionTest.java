package com.vingame.glswing.component;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Cursor;
import java.awt.event.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.vingame.glswing.application.*;
import com.vingame.glswing.node.*;
import com.vingame.glswing.ui.GLLabel;

@EnabledIfSystemProperty(named="glswing.test.canvas",matches="true")
class GLNodeResizePortInteractionTest {
    @Test void resizeHoverDoesNotBlockSocketHoverOrPress() throws Exception {
        var editor=new GLNodeEditor();var node=new GLNode("Source");node.setResizeMode(GLNodeResizeMode.HORIZONTAL);
        var in=node.addInput("in",new GLLabel("Input"));var out=node.addOutput("out",new GLLabel("Output"));editor.addNode(node,80,70);
        var handle=GLApplication.builder().title("Port gesture regression").backend(GLBackend.CANVAS).size(700,400).surface(c->editor).launch();
        try {
            var context=handle.readyFuture().get(30,TimeUnit.SECONDS);var canvas=(GLSwingContainerCanvas)context.parent();
            await(()->out.getLastRenderedScreenBounds()!=null);
            for(var port:new GLNodePort[]{in,out})for(int repeat=0;repeat<3;repeat++) {
                var b=port.getLastRenderedScreenBounds();int x=Math.round(b[0]+b[2]/2),y=Math.round(b[1]+b[3]/2);
                int edgeX=x+(port==in?2:-2);int edgeY=y-10;
                move(canvas,edgeX,edgeY);
                await(()->canvas.getCursor().getType()==Cursor.E_RESIZE_CURSOR);
                move(canvas,x,y);
                await(()->canvas.getCursor().getType()==Cursor.DEFAULT_CURSOR);
                SwingUtilities.invokeAndWait(()->canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,x,y,1,false,MouseEvent.BUTTON1)));
                await(editor::isConnectionGestureActive);
                SwingUtilities.invokeAndWait(()->canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,x,y,1,false,MouseEvent.BUTTON1)));
                await(()->!editor.isConnectionGestureActive());
            }
        }finally {handle.close();handle.closedFuture().get(15,TimeUnit.SECONDS);}
    }
    private static void move(GLSwingContainerCanvas canvas,int x,int y)throws Exception {
        SwingUtilities.invokeAndWait(()->canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_MOVED,System.currentTimeMillis(),0,x,y,0,false)));
    }
    private static void await(java.util.function.BooleanSupplier condition)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);var result=new AtomicReference<Boolean>();
        do{SwingUtilities.invokeAndWait(()->result.set(condition.getAsBoolean()));if(result.get())return;Thread.sleep(20);}while(System.nanoTime()<end);
        fail("Pointer state did not settle");
    }
}
