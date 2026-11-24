package com.vingame.glswing.ui;
/** Reusable absolute-controller mapping and soft takeover. Runtime pickup state
 * belongs to a driver session, never to the serialized mapping. */
public record GLDriverMapping(double minimum,double maximum,boolean reversed,boolean logarithmic,boolean pickup){
    public GLDriverMapping {
        if(!Double.isFinite(minimum)||!Double.isFinite(maximum)||minimum>maximum||logarithmic&&(minimum<=0||maximum<=0))throw new IllegalArgumentException("Invalid driver mapping");
    }
    public double map(double normalized){double t=Math.max(0,Math.min(1,normalized));if(reversed)t=1-t;return logarithmic?Math.exp(Math.log(minimum)+t*(Math.log(maximum)-Math.log(minimum))):minimum+t*(maximum-minimum);}
    public static final class Pickup {
        private boolean acquired;private double previous=Double.NaN,lastOutput=Double.NaN;
        public void output(double value){lastOutput=value;}
        public void reset(){acquired=false;previous=lastOutput=Double.NaN;}
        public boolean accepts(GLDriverMapping mapping,double normalized,double current){
            if(!mapping.pickup)return true;
            if(acquired&&Double.isFinite(lastOutput)&&Math.abs(current-lastOutput)>Math.max(1e-9,Math.abs(mapping.maximum-mapping.minimum)*1e-6))reset();
            double value=mapping.map(normalized),low=mapping.map(normalized-1./127),high=mapping.map(normalized+1./127);
            if(!acquired)acquired=current>=Math.min(low,high)-1e-9&&current<=Math.max(low,high)+1e-9||Double.isFinite(previous)&&(previous-current)*(value-current)<=0;
            previous=value;if(acquired)lastOutput=value;return acquired;
        }
    }
}
