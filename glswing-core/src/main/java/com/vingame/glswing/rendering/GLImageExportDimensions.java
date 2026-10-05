package com.vingame.glswing.rendering;

/** Editable frame dimensions, optional aspect lock and independent inside/outside padding. */
public class GLImageExportDimensions {
    // Technical Java direct-buffer limit (four bytes per pixel), not an export policy.
    public static final long MAX_PIXELS=(Integer.MAX_VALUE-8L)/4;
    public static final int MAX_SIDE=(int)MAX_PIXELS;
    public final double contentWidth,contentHeight,defaultPadding;
    private double width,height,padding,ratio;
    private boolean linked=true,addPadding;
    public GLImageExportDimensions(double width,double height,double padding){
        if(!Double.isFinite(width)||!Double.isFinite(height)||width<=0||height<=0||!Double.isFinite(padding)||padding<0)throw new IllegalArgumentException("Invalid export geometry");
        contentWidth=width;contentHeight=height;defaultPadding=padding;reset();
    }
    public static boolean valid(int width,int height){return width>=1&&height>=1&&(long)width*height<=MAX_PIXELS;}
    private static int pixel(double value){return (int)Math.max(1,Math.min(MAX_SIDE,Math.round(value)));}
    public int width(){return pixel(width);}
    public int height(){return pixel(height);}
    public int outputWidth(){return pixel(width()+(addPadding?2*padding:0));}
    public int outputHeight(){return pixel(height()+(addPadding?2*padding:0));}
    public double padding(){return padding;}
    public boolean linked(){return linked;}
    public boolean addPadding(){return addPadding;}
    public void linked(boolean value){linked=value;ratio=width/height;}
    public void addPadding(boolean value){addPadding=value;padding=Math.min(padding,maxPadding());}
    private boolean fits(double w,double h,double pad){
        double ow=Math.max(1,Math.round(w))+(addPadding?Math.round(2*pad):0),oh=Math.max(1,Math.round(h))+(addPadding?Math.round(2*pad):0);
        return ow<=MAX_SIDE&&oh<=MAX_SIDE&&ow*oh<=MAX_PIXELS;
    }
    public void width(int value){
        double next=Math.max(1,Math.min(maxWidth(),value));
        if(linked){double factor=next/width; height=next/ratio;padding*=factor;}
        width=next;padding=Math.min(padding,maxPadding());
    }
    public void height(int value){
        double next=Math.max(1,Math.min(maxHeight(),value));
        if(linked){double factor=next/height;width=next*ratio;padding*=factor;}
        height=next;padding=Math.min(padding,maxPadding());
    }
    public void padding(double value){padding=Math.max(0,Math.min(maxPadding(),value));}
    /** Explicit joint edit establishes a new aspect ratio, independent of the previous lock. */
    public void size(int w,int h){
        width=pixel(w);height=pixel(h);
        if(!fits(width,height,padding)){
            double low=0,high=1;
            for(int i=0;i<60;i++){double f=(low+high)/2;if(fits(width*f,height*f,padding*f))low=f;else high=f;}
            width=Math.max(1,Math.round(width*low));height=Math.max(1,Math.round(height*low));padding*=low;
        }
        ratio=width/height;padding=Math.min(padding,maxPadding());
    }
    public int maxWidth(){return max(v->fits(v,linked?v/ratio:height,linked?padding*v/width:padding),1,MAX_SIDE);}
    public int maxHeight(){return max(v->fits(linked?v*ratio:width,v,linked?padding*v/height:padding),1,MAX_SIDE);}
    public int maxPadding(){
        if(!addPadding)return Math.max(0,(Math.min(width(),height())-1)/2);
        return max(v->fits(width,height,v),0,MAX_SIDE/2);
    }
    private static int max(java.util.function.IntPredicate valid,int low,int high){while(low<high){int mid=(int)(((long)low+high+1)/2);if(valid.test(mid))low=mid;else high=mid-1;}return low;}
    public void reset(){padding=defaultPadding;resetSize();}
    public void resetSize(){
        width=contentWidth+(addPadding?0:2*padding);height=contentHeight+(addPadding?0:2*padding);ratio=width/height;
        double factor=Math.min(1,Math.sqrt(MAX_PIXELS/((width+(addPadding?2*padding:0))*(height+(addPadding?2*padding:0))))*.999999);
        if(factor<.99999){width*=factor;height*=factor;padding*=factor;}
    }
}
