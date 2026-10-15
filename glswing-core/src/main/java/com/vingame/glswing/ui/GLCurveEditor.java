package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.event.*;
import java.util.function.Consumer;
import com.vingame.glswing.component.*;
import com.vingame.glswing.input.*;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;

/** Reusable normalized curve control; persistence and execution belong to the application. */
public class GLCurveEditor extends GLControl {
    private GLFunctionCurve curve=GLFunctionCurve.identity();
    private GLFunctionCurve resetCurve=GLFunctionCurve.identity();
    private Consumer<GLFunctionCurve> changed=c->{};
    private Consumer<GLPopupMenu> contextMenuCustomizer=menu->{};
    public void setContextMenuCustomizer(Consumer<GLPopupMenu> customizer){contextMenuCustomizer=java.util.Objects.requireNonNull(customizer);}
    private Runnable beginEdit=()->{},endEdit=()->{};
    private boolean preview,large,gesture;
    private int selected=-1,dragHandle;
    private double zeroX=Double.NaN,zeroY=Double.NaN,marker=Double.NaN;
    public GLCurveEditor() { metrics(); setToolTipText("Double-click: add point · Drag: move · Shift: grid · Right-click: segment options · Delete: remove"); }
    public GLFunctionCurve getCurve(){return curve;}
    public void setResetCurve(GLFunctionCurve value){resetCurve=java.util.Objects.requireNonNull(value);}
    public void setCurve(GLFunctionCurve value){if(!curve.equals(value)){curve=value;if(selected>=curve.points().size())selected=-1;invalidateRecording();}}
    public void setChangeListener(Consumer<GLFunctionCurve> listener){changed=listener;}
    public void setEditListeners(Runnable begin,Runnable end){beginEdit=begin;endEdit=end;}
    public boolean isPreview(){return preview;}
    public void setPreview(boolean value){if(preview==value)return; finish();preview=value;setFocusable(!value);metrics();invalidateRecording();}
    public void setLarge(boolean value){large=value;metrics();}
    public void setAxes(double x,double y){if(zeroX==x&&zeroY==y)return;zeroX=x;zeroY=y;invalidateRecording();}
    public void setMarker(double x){if(marker==x)return;marker=x;invalidateRecording();}
    private float metric(String name,float fallback){float v=getGLTheme().values().getFloat("curve."+name,fallback);return Float.isFinite(v)&&v>0?v:fallback;}
    private void metrics(){setIntrinsicPreferredGLSize(new GLSize(metric("width",160),metric(preview?"previewHeight":large?"inspectorHeight":"height",preview?36:large?200:120)));}
    @Override protected void themeChanged(){super.themeChanged();metrics();invalidateRecording();}
    private float pad(){return Math.min(metric("padding",9),Math.min(getWidth(),getHeight())/4);}
    private float px(double x){return pad()+(float)x*Math.max(1,getWidth()-2*pad());}
    private float py(double y){return getHeight()-pad()-(float)y*Math.max(1,getHeight()-2*pad());}
    private double nx(GLMouseEvent e){return GLFunctionCurve.clamp((e.getLocalX()-pad())/Math.max(1,getWidth()-2*pad()),0,1);}
    private double ny(GLMouseEvent e){return GLFunctionCurve.clamp((getHeight()-pad()-e.getLocalY())/Math.max(1,getHeight()-2*pad()),0,1);}
    private Color color(String key,Color fallback){return getGLTheme().values().getColor("curve."+key,fallback);}
    @Override protected void record(GLContext2D c){
        var style=getGLTheme().sliderStyle();
        c.setMouseTarget(this);
        c.fillUIRoundRect(0,0,getWidth(),getHeight(),style.cornerRadius(),color("background",style.background()));
        c.beginClip(0,0,getWidth(),getHeight());
        Color grid=color("grid",style.borderColor()),line=color("line",style.progressLightColor()),handle=color("handle",style.textColor());
        if(!preview) for(int i=1;i<4;i++){c.drawLine(px(i/4.0),py(0),px(i/4.0),py(1),grid);c.drawLine(px(0),py(i/4.0),px(1),py(i/4.0),grid);}
        if(zeroX>=0&&zeroX<=1)c.drawLine(px(zeroX),py(0),px(zeroX),py(1),handle);
        if(zeroY>=0&&zeroY<=1)c.drawLine(px(0),py(zeroY),px(1),py(zeroY),handle);
        if(marker>=0&&marker<=1)c.drawLine(px(marker),py(0),px(marker),py(1),getGLTheme().values().getColor("curve.marker"));
        var pts=curve.points();
        for(int i=0;i<pts.size()-1;i++){
            var a=pts.get(i);var b=pts.get(i+1);
            if(a.mode()==GLFunctionCurve.Mode.STEP){c.drawLine(px(a.x()),py(a.y()),px(b.x()),py(a.y()),line);c.drawLine(px(b.x()),py(a.y()),px(b.x()),py(b.y()),line);}
            else {int steps=a.mode()==GLFunctionCurve.Mode.LINEAR?1:Math.max(8,(int)((b.x()-a.x())*getWidth()));
                float x=px(a.x()),y=py(a.y());for(int n=1;n<=steps;n++){double t=a.x()+(b.x()-a.x())*n/steps;float xx=px(t),yy=py(curve.evaluate(t));c.drawLine(x,y,xx,yy,line);x=xx;y=yy;}}
        }
        if(!preview){
            if(selected>=0&&selected<pts.size()){
                var p=pts.get(selected);
                if(selected>0&&pts.get(selected-1).mode()==GLFunctionCurve.Mode.BEZIER)handle(c,p.x(),p.y(),p.inX(),p.inY(),handle);
                if(selected<pts.size()-1&&p.mode()==GLFunctionCurve.Mode.BEZIER)handle(c,p.x(),p.y(),p.outX(),p.outY(),handle);
            }
            float radius=metric("pointRadius",3);
            for(int i=0;i<pts.size();i++){var p=pts.get(i);c.fillUIRoundRect(px(p.x())-radius,py(p.y())-radius,radius*2,radius*2,radius,i==selected?handle:line);}
        }
        c.endClip();
    }
    private void handle(GLContext2D c,double x,double y,double hx,double hy,Color color){c.drawLine(px(x),py(y),px(hx),py(hy),color);c.fillUIRoundRect(px(hx)-2,py(hy)-2,4,4,0,color);}
    private boolean near(GLMouseEvent e,double x,double y){return Math.hypot(e.getLocalX()-px(x),e.getLocalY()-py(y))<=metric("hitRadius",7);}
    private int hit(GLMouseEvent e){for(int i=0;i<curve.points().size();i++){var p=curve.points().get(i);if(near(e,p.x(),p.y()))return i;}return -1;}
    private void start(){if(!gesture){gesture=true;beginEdit.run();}}
    private void finish(){if(gesture){gesture=false;endEdit.run();}}
    private void change(GLFunctionCurve next){if(!curve.equals(next)){setCurve(next);changed.accept(next);}}
    private void edit(Runnable operation){start();try{operation.run();}finally{finish();}}
    @Override protected void controlMousePressed(GLMouseEvent e){
        if(preview||!isEnabled())return;
        if(e.getButton()==MouseEvent.BUTTON3){int point=hit(e);if(point>=0)selected=point;else{double x=nx(e);selected=0;while(selected<curve.points().size()-2&&curve.points().get(selected+1).x()<x)selected++;}menu(e);invalidateRecording();return;}
        if(e.getButton()!=MouseEvent.BUTTON1)return;
        dragHandle=0;
        if(selected>=0){var p=curve.points().get(selected);
            if(selected>0&&curve.points().get(selected-1).mode()==GLFunctionCurve.Mode.BEZIER&&near(e,p.inX(),p.inY()))dragHandle=-1;
            if(selected<curve.points().size()-1&&p.mode()==GLFunctionCurve.Mode.BEZIER&&near(e,p.outX(),p.outY()))dragHandle=1;
        }
        if(dragHandle==0)selected=hit(e);
        if(selected>=0)start();invalidateRecording();e.consume();
    }
    @Override protected void controlMouseClicked(GLMouseEvent e){if(!preview&&isEnabled()&&e.getButton()==MouseEvent.BUTTON1&&e.completesDoubleClick()&&hit(e)<0){edit(()->change(curve.insert(nx(e),ny(e))));e.consume();}}
    @Override protected void controlMouseDragged(GLMouseEvent e){
        if(!gesture||selected<0||preview||!isEnabled())return;
        double x=nx(e),y=ny(e);if((e.getModifiersEx()&InputEvent.SHIFT_DOWN_MASK)!=0){x=Math.rint(x*20)/20;y=Math.rint(y*20)/20;}
        change(dragHandle==0?curve.move(selected,x,y):curve.handle(selected,dragHandle<0,x,y));e.consume();
    }
    @Override protected void controlMouseReleased(GLMouseEvent e){finish();}
    @Override protected void controlMouseCaptureLost(GLMouseEvent e){finish();}
    @Override protected void detached(GLParent p){finish();super.detached(p);}
    @Override protected void controlKeyPressed(GLKeyEvent e){if(preview)return;if(e.getKeyCode()==KeyEvent.VK_DELETE||e.getKeyCode()==KeyEvent.VK_BACK_SPACE){edit(()->change(curve.remove(selected)));selected=-1;e.consume();}}
    private void menu(GLMouseEvent e){
        GLParent root=getGLParent();while(root instanceof GLContainer p&&p.getGLParent()!=null)root=p.getGLParent();if(root==null)return;
        var menu=new GLPopupMenu();
        for(var mode:GLFunctionCurve.Mode.values())menu.addItem(mode.name(),event->edit(()->change(curve.mode(selected,mode))));
        menu.addItem("Link / unlink handles",event->edit(()->change(curve.linked(selected,!curve.points().get(selected).linked()))));
        menu.addItem("Delete point",event->edit(()->{change(curve.remove(selected));selected=-1;}));
        menu.addItem("Mirror left half",event->edit(()->change(curve.symmetric())))
                .setEnabled(curve.points().stream().noneMatch(p->p.mode()==GLFunctionCurve.Mode.STEP)
                        &&curve.points().stream().filter(p->p.x()<.5-.00011).count()<=31);
        menu.addItem("Reset curve",event->edit(()->{selected=-1;change(resetCurve);}));
        contextMenuCustomizer.accept(menu);
        menu.show(root,e.getScreenX(),e.getScreenY());
    }
}
