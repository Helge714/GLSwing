package com.vingame.glswing.ui;
import java.util.function.*;
/** Model-facing, transport-independent remote control. Writes must be supplied
 * by the application so external updates need not invoke widget gesture history. */
public final class GLDriverTarget {
    public enum Kind { NUMBER, CHOICE, TOGGLE, ACTION }
    public static final String PROPERTY_PREFIX="glswing.driver.";
    private final Kind kind;private final DoubleSupplier read;private final DoubleConsumer write;
    private final Runnable action;private final IntSupplier count;private final double minimum,maximum;
    private final boolean integral;
    private GLDriverTarget(Kind k,DoubleSupplier read,DoubleConsumer write,Runnable action,IntSupplier count,double min,double max,boolean integral){
        this.kind=k;this.read=read;this.write=write;this.action=action;this.count=count;minimum=min;maximum=max;this.integral=integral;
    }
    public static GLDriverTarget number(DoubleSupplier read,DoubleConsumer write,double min,double max,boolean integral){
        if(!Double.isFinite(min)||!Double.isFinite(max)||min>max)throw new IllegalArgumentException("Invalid driver range");
        return new GLDriverTarget(Kind.NUMBER,read,write,null,()->0,min,max,integral);
    }
    public static GLDriverTarget choice(DoubleSupplier read,DoubleConsumer select,IntSupplier count){return new GLDriverTarget(Kind.CHOICE,read,select,null,count,0,1,true);}
    public static GLDriverTarget toggle(DoubleSupplier read,DoubleConsumer write){return new GLDriverTarget(Kind.TOGGLE,read,write,null,()->2,0,1,true);}
    public static GLDriverTarget action(Runnable fire){return new GLDriverTarget(Kind.ACTION,()->0,v->{},fire,()->0,0,1,true);}
    public Kind kind(){return kind;}public double read(){return read.getAsDouble();}
    public double minimum(){return minimum;}public double maximum(){return maximum;}public boolean integral(){return integral;}
    public void set(double value){
        if(!Double.isFinite(value))return;
        double result=kind==Kind.CHOICE?Math.max(0,Math.min(Math.max(0,count.getAsInt()-1),Math.floor(value))):Math.max(minimum,Math.min(maximum,value));
        write.accept(integral?Math.rint(result):result);
    }
    public void normalized(double value){value=Math.max(0,Math.min(1,value));if(kind==Kind.CHOICE)set(Math.min(count.getAsInt()-1,Math.floor(value*count.getAsInt())));else set(minimum+value*(maximum-minimum));}
    public void pulse(){switch(kind){case ACTION->action.run();case TOGGLE->set(read()==0?1:0);case CHOICE->{int n=count.getAsInt();if(n>0)set(((int)read()+1)%n);}default->{}}}
}
