package com.vingame.glswing.ui;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.component.GLParent;

/** Slider with a canonical stored value and application-defined display units. */
public class GLUnitSlider<U extends GLUnit> extends GLSlider {
    private final GLUnitFormats<U> formats;
    private Double minimum, maximum;
    private double storedDefault, storedStep;
    private final boolean integral;
    private U displayUnit;
    private boolean updating;
    private DoubleConsumer valueChanged = value -> { };
    private Consumer<U> unitChanged = unit -> { };
    private Supplier<GLHistoryManager> history = this::getGLHistoryManager;
    private GLHistoryManager.Transaction gesture;
    private final java.util.Map<U,GLMenuItem> unitItems = new java.util.LinkedHashMap<>();

    public GLUnitSlider(String label, double value, Double minimum, Double maximum,
            double step, boolean integral, Presentation presentation, GLUnitFormats<U> formats, U selected) {
        super(label, minimum != null && maximum != null ? Mode.FIXED : Mode.FREE, presentation,
                bound(formats, selected, minimum, Double.NEGATIVE_INFINITY),
                bound(formats, selected, maximum, Double.POSITIVE_INFINITY),
                minimum == null ? 0 : bound(formats, selected, minimum, 0),
                maximum == null ? 1 : bound(formats, selected, maximum, 1),
                formats.stored().convertTo(value, selected));
        if (!Double.isFinite(value) || !Double.isFinite(step) || step <= 0
                || minimum != null && (!Double.isFinite(minimum) || value < minimum)
                || maximum != null && (!Double.isFinite(maximum) || value > maximum)
                || minimum != null && maximum != null && minimum >= maximum
                || integral && value != Math.rint(value)) throw new IllegalArgumentException("Invalid stored slider range");
        this.formats=formats;this.minimum=minimum;this.maximum=maximum;
        storedDefault=value;storedStep=step;this.integral=integral;displayUnit=selected;
        applyUnit(value);
        addChangeListener(event -> { if (!updating) valueChanged.accept(getStoredValue()); });
        if (formats.choices().size()>1) {
            var menu=new GLPopupMenu();
            for(U unit:formats.choices()) {
                var item=menu.addItem(unit.displayName(),event -> {
                    setDisplayUnit(unit);unitChanged.accept(unit);
                });
                item.setEnabled(!unit.equals(selected));unitItems.put(unit,item);
            }
            getContextMenu().addSubMenu("Unit",0,menu);
        }
    }
    private static <U extends GLUnit> double bound(GLUnitFormats<U> formats,U selected,Double value,double fallback) {
        Objects.requireNonNull(formats);Objects.requireNonNull(selected);
        if(!formats.choices().contains(selected))throw new IllegalArgumentException("Unsupported display unit");
        return value==null?fallback:formats.stored().convertTo(value,selected);
    }
    public GLUnitFormats<U> getUnitFormats(){return formats;}
    public U getDisplayUnit(){return displayUnit;}
    public double getStoredValue(){
        double value=displayUnit.convertTo(getValue(),formats.stored());
        if(integral)value=Math.rint(value);
        if(minimum!=null)value=Math.max(minimum,value);
        if(maximum!=null)value=Math.min(maximum,value);
        return value;
    }
    /** Model synchronization does not report another user edit. */
    public void setStoredValue(double value){
        if(!Double.isFinite(value))throw new IllegalArgumentException("Finite value required");
        updating=true;
        try{setValue(formats.stored().convertTo(value,displayUnit));}finally{updating=false;}
    }
    /** Preserves the physical value and reset value, and updates bounds and the suffix. */
    public void setDisplayUnit(U unit){
        if(!formats.choices().contains(unit))throw new IllegalArgumentException("Unsupported display unit");
        if(unit.equals(displayUnit))return;
        double value=getStoredValue();displayUnit=unit;applyUnit(value);
        unitItems.forEach((choice,item)->item.setEnabled(!choice.equals(unit)));
    }
    /** Refreshes presentation metadata without reporting a user edit or replacing the control. */
    public void setStoredMetadata(double reset,Double minimum,Double maximum,double step){
        if(!Double.isFinite(reset)||!Double.isFinite(step)||step<=0
                ||minimum!=null&&(!Double.isFinite(minimum)||reset<minimum)
                ||maximum!=null&&(!Double.isFinite(maximum)||reset>maximum)
                ||minimum!=null&&maximum!=null&&minimum>=maximum
                ||integral&&reset!=Math.rint(reset)
                ||getMode()==Mode.FIXED&&(minimum==null||maximum==null))throw new IllegalArgumentException("Invalid stored slider metadata");
        if(storedDefault==reset&&storedStep==step&&Objects.equals(this.minimum,minimum)&&Objects.equals(this.maximum,maximum))return;
        double value=getStoredValue();this.minimum=minimum;this.maximum=maximum;storedDefault=reset;storedStep=step;
        applyUnit(value);
    }
    private void applyUnit(double value){
        updating=true;
        try{
            setIntegerValuesOnly(false);
            Double min=minimum==null?null:formats.stored().convertTo(minimum,displayUnit);
            Double max=maximum==null?null:formats.stored().convertTo(maximum,displayUnit);
            if(getMode()==Mode.FIXED)setRange(min,max);else {
                setValueBounds(min,max);
                double low=min==null?0:min,high=max==null?1:max;
                if(!(low<high)) {
                    if(max==null)high=Math.min(Double.MAX_VALUE,low+Math.max(1,Math.abs(low)*.1));
                    else low=Math.max(-Double.MAX_VALUE,high-Math.max(1,Math.abs(high)*.1));
                    if(!(low<high)) {
                        if(max==null)low=Math.nextDown(high);else high=Math.nextUp(low);
                    }
                }
                setDisplayRange(low,high);
            }
            setUnit(displayUnit.suffix());setName(getText()+" ["+displayUnit.suffix()+"]");
            double step=displayUnit.equals(formats.stored())?storedStep:displayUnit.preferredStep();
            setStep(step);setDragSensitivity(step);setIntegerValuesOnly(integral&&displayUnit.equals(formats.stored()));
            setValue(formats.stored().convertTo(value,displayUnit));
            setDefaultValue(formats.stored().convertTo(storedDefault,displayUnit));
        }finally{updating=false;}
    }
    public void setStoredValueListener(DoubleConsumer listener){valueChanged=Objects.requireNonNull(listener);}
    public void setUnitSelectionListener(Consumer<U> listener){unitChanged=Objects.requireNonNull(listener);}
    public void setGestureHistory(Supplier<GLHistoryManager> supplier){history=Objects.requireNonNull(supplier);}
    private void begin(){var manager=history.get();if(gesture==null&&manager!=null)gesture=manager.beginTransaction("Change "+getText());}
    private void finish(){if(gesture!=null){var transaction=gesture;gesture=null;transaction.close();}}
    @Override protected void controlMousePressed(GLMouseEvent e){begin();super.controlMousePressed(e);}
    @Override protected void controlMouseReleased(GLMouseEvent e){try{super.controlMouseReleased(e);}finally{finish();}}
    @Override protected void controlMouseCaptureLost(GLMouseEvent e){try{super.controlMouseCaptureLost(e);}finally{finish();}}
    @Override protected void controlKeyPressed(GLKeyEvent e){begin();try{super.controlKeyPressed(e);}finally{finish();}}
    @Override protected void detached(GLParent parent){try{super.detached(parent);}finally{finish();}}
}
