package com.vingame.glswing.ui;

import java.util.*;

/** Immutable, normalized single-valued curve. A point's mode describes its outgoing segment. */
public record GLFunctionCurve(List<Point> points) {
    public enum Mode { LINEAR, STEP, BEZIER }
    public record Point(double x, double y, double inX, double inY, double outX, double outY,
                        Mode mode, boolean linked) {
        public Point {
            for (double v : new double[]{x,y,inX,inY,outX,outY})
                if (!Double.isFinite(v) || v < 0 || v > 1) throw new IllegalArgumentException("Normalized coordinates required");
            Objects.requireNonNull(mode);
        }
        public static Point at(double x, double y) { return new Point(x,y,x,y,x,y,Mode.LINEAR,false); }
    }
    public GLFunctionCurve {
        points = List.copyOf(points);
        if (points.size() < 2 || points.size() > 64 || points.get(0).x != 0 || points.get(points.size()-1).x != 1)
            throw new IllegalArgumentException("Curve requires 2..64 points and fixed X endpoints");
        for (int i=0; i<points.size(); i++) {
            Point p=points.get(i);
            if (p.inX>p.x || p.outX<p.x) throw new IllegalArgumentException("Reversed handle");
            if (i>0) {
                Point a=points.get(i-1);
                if (p.x-a.x<.0001 || a.outX>p.inX) throw new IllegalArgumentException("Crossing curve segments");
            }
        }
    }
    public static GLFunctionCurve identity() { return new GLFunctionCurve(List.of(Point.at(0,0),Point.at(1,1))); }
    public static double clamp(double x, double lo, double hi) { return Math.max(lo,Math.min(hi,x)); }
    private static double cubic(double a,double b,double c,double d,double t) {
        double u=1-t; return u*u*u*a+3*u*u*t*b+3*u*t*t*c+t*t*t*d;
    }
    public double evaluate(double x) {
        if (x<=0) return points.get(0).y;
        if (x>=1) return points.get(points.size()-1).y;
        for (int i=0;i<points.size()-1;i++) {
            Point a=points.get(i), b=points.get(i+1);
            if (x>=b.x) continue;
            if (a.mode==Mode.STEP) return a.y;
            if (a.mode==Mode.LINEAR) return a.y+(b.y-a.y)*(x-a.x)/(b.x-a.x);
            double lo=0,hi=1;
            for(int n=0;n<40;n++) { double t=(lo+hi)/2; if(cubic(a.x,a.outX,b.inX,b.x,t)<x)lo=t;else hi=t; }
            return cubic(a.y,a.outY,b.inY,b.y,(lo+hi)/2);
        }
        return points.get(points.size()-1).y;
    }
    private GLFunctionCurve normalized(List<Point> values) {
        var result=new ArrayList<Point>();
        for(int i=0;i<values.size();i++) {
            Point p=values.get(i);
            double left=i==0?0:(values.get(i-1).x+p.x)/2;
            double right=i==values.size()-1?1:(p.x+values.get(i+1).x)/2;
            double inScale=p.inX>=p.x?0:Math.min(1,(p.x-left)/(p.x-p.inX));
            double outScale=p.outX<=p.x?0:Math.min(1,(right-p.x)/(p.outX-p.x));
            result.add(new Point(p.x,p.y,p.x+(p.inX-p.x)*inScale,p.y+(p.inY-p.y)*inScale,
                    p.x+(p.outX-p.x)*outScale,p.y+(p.outY-p.y)*outScale,p.mode,p.linked));
        }
        return new GLFunctionCurve(result);
    }
    public GLFunctionCurve move(int index,double x,double y) {
        var copy=new ArrayList<>(points); Point p=copy.get(index);
        x=index==0?0:index==copy.size()-1?1:clamp(x,copy.get(index-1).x+.00011,copy.get(index+1).x-.00011);
        y=clamp(y,0,1); double dx=x-p.x,dy=y-p.y;
        copy.set(index,new Point(x,y,clamp(p.inX+dx,0,1),clamp(p.inY+dy,0,1),clamp(p.outX+dx,0,1),clamp(p.outY+dy,0,1),p.mode,p.linked));
        return normalized(copy);
    }
    public GLFunctionCurve handle(int index,boolean incoming,double x,double y) {
        var copy=new ArrayList<>(points); Point p=copy.get(index);
        double left=index==0?0:(points.get(index-1).x+p.x)/2;
        double right=index==points.size()-1?1:(p.x+points.get(index+1).x)/2;
        x=clamp(x,incoming?left:p.x,incoming?p.x:right); y=clamp(y,0,1);
        double ix=incoming?x:p.inX, iy=incoming?y:p.inY, ox=incoming?p.outX:x, oy=incoming?p.outY:y;
        if(p.linked) {
            double dx=x-p.x,dy=y-p.y;
            double scale=1;
            if(dx>0)scale=Math.min(scale,(p.x-left)/dx);
            if(dx<0)scale=Math.min(scale,(right-p.x)/-dx);
            if(dy>0)scale=Math.min(scale,p.y/dy);
            if(dy<0)scale=Math.min(scale,(1-p.y)/-dy);
            if(incoming){ox=p.x-dx*scale;oy=p.y-dy*scale;}else{ix=p.x-dx*scale;iy=p.y-dy*scale;}
        }
        copy.set(index,new Point(p.x,p.y,clamp(ix,0,1),clamp(iy,0,1),clamp(ox,0,1),clamp(oy,0,1),p.mode,p.linked));
        return normalized(copy);
    }
    public GLFunctionCurve insert(double x,double y) {
        if(points.size()==64 || x<=.00011 || x>=.99989) return this;
        if(points.stream().anyMatch(p->Math.abs(p.x-x)<.00011)) return this;
        var copy=new ArrayList<>(points); copy.add(Point.at(x,clamp(y,0,1))); copy.sort(Comparator.comparingDouble(Point::x));
        return normalized(copy);
    }
    public GLFunctionCurve remove(int index) {
        if(index<=0 || index>=points.size()-1)return this;
        var copy=new ArrayList<>(points); copy.remove(index); return normalized(copy);
    }
    public GLFunctionCurve mode(int index,Mode mode) {
        if(index<0 || index>=points.size()-1)return this;
        var copy=new ArrayList<>(points); Point a=copy.get(index), b=copy.get(index+1);
        double dx=(b.x-a.x)/3,dy=(b.y-a.y)/3;
        copy.set(index,new Point(a.x,a.y,a.inX,a.inY,a.x+dx,a.y+dy,mode,a.linked));
        copy.set(index+1,new Point(b.x,b.y,b.x-dx,b.y-dy,b.outX,b.outY,b.mode,b.linked));
        return normalized(copy);
    }
    public GLFunctionCurve linked(int index,boolean linked) {
        if(index<0)return this;
        var copy=new ArrayList<>(points); Point p=copy.get(index);
        copy.set(index,new Point(p.x,p.y,p.inX,p.inY,p.outX,p.outY,p.mode,linked));
        var curve=new GLFunctionCurve(copy);
        return linked?curve.handle(index,false,p.outX,p.outY):curve;
    }
    /** Point symmetry around (0.5,0.5), useful for bipolar transfer curves. */
    public GLFunctionCurve symmetric() {
        if(points.stream().anyMatch(p->p.mode==Mode.STEP))return this;
        var left=new ArrayList<Point>();
        for(Point p:points)if(p.x<.5-.00011)left.add(p);
        if(left.size()*2+1>64)return this;
        Point center=points.stream().filter(p->p.x==.5).findFirst().orElse(Point.at(.5,.5));
        double iy=clamp(center.inY+.5-center.y,0,1);
        left.add(new Point(.5,.5,center.inX,iy,1-center.inX,1-iy,left.get(left.size()-1).mode,true));
        int count=left.size()-1;
        for(int i=count-1;i>=0;i--){Point p=left.get(i);Mode mode=i>0?left.get(i-1).mode:Mode.LINEAR;
            left.add(new Point(1-p.x,1-p.y,1-p.outX,1-p.outY,1-p.inX,1-p.inY,mode,p.linked));}
        return normalized(left);
    }
}

