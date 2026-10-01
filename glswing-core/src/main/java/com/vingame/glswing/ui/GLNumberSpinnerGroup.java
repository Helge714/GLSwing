package com.vingame.glswing.ui;

import java.util.*;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.layout.GLConstraints;

/** Connected numeric fields. Drag vertically across rows, release, then type to edit them together. */
public class GLNumberSpinnerGroup extends GLLayoutGroup {
    private final List<GLNumberSpinner> members=new ArrayList<>();
    private final Set<GLNumberSpinner> selected=new LinkedHashSet<>();
    private GLNumberSpinner source;
    private float anchor;
    private boolean selecting,editing,mirroring;
    public GLNumberSpinnerGroup(GLNumberSpinner... spinners){for(var spinner:spinners)addSpinner(spinner);}
    public final void addSpinner(GLNumberSpinner spinner){
        Objects.requireNonNull(spinner);
        if(members.contains(spinner))return;
        if(spinner.inputGroup!=null)throw new IllegalArgumentException("Spinner already belongs to a group");
        members.add(spinner);spinner.inputGroup=this;
        addGL(spinner,GLConstraints.at(0,members.size()-1).weight(1,1).fillBoth().connectAll());
    }
    public final List<GLNumberSpinner> getSpinners(){return List.copyOf(members);}
    void pressed(GLNumberSpinner spinner,GLMouseEvent event){
        cancel();source=spinner;anchor=spinner.getY()+event.getLocalY();selecting=true;selected.add(spinner);
    }
    boolean dragged(GLNumberSpinner spinner,GLMouseEvent event){
        if(!selecting||source!=spinner)return false;
        float y=spinner.getY()+event.getLocalY();
        if(Math.abs(y-anchor)<4&&selected.size()<2)return false;
        selected.clear();float top=Math.min(anchor,y),bottom=Math.max(anchor,y);
        for(var member:members)if(member.isEnabled()&&member.getY()<=bottom&&member.getY()+member.getHeight()>=top)selected.add(member);
        highlight();return selected.size()>1;
    }
    void released(GLNumberSpinner spinner){
        if(source!=spinner||!selecting)return;
        selecting=false;editing=selected.size()>1;
        if(editing){mirror(spinner);spinner.getEditor().selectAll();highlight();}else cancel();
    }
    void mirror(GLNumberSpinner spinner){
        if(!editing||source!=spinner||mirroring)return;
        mirroring=true;try{for(var member:selected)if(member!=source)member.getEditor().setText(source.getEditor().getText());}finally{mirroring=false;}
    }
    boolean valid(GLNumberSpinner spinner,String text){
        return !editing||source!=spinner||selected.stream().allMatch(s->s.isEnabled()&&s.isValidEditorValue(text));
    }
    boolean commit(GLNumberSpinner spinner,double value){
        if(!editing||source!=spinner)return false;
        var before=new LinkedHashMap<GLNumberSpinner,Double>();var after=new LinkedHashMap<GLNumberSpinner,Double>();
        for(var member:selected){before.put(member,member.getValue());after.put(member,value);}
        editing=false;applyValues(Collections.unmodifiableMap(after));cancel();
        var history=getGLHistoryManager();
        if(history!=null&&!before.equals(after))history.record(getGLLocalizer().text(GLUiKeys.NUMBER_EDIT),()->applyValues(before),()->applyValues(after));
        return true;
    }
    /** Override to apply dependent model values atomically instead of sequential change listeners. */
    protected void applyValues(Map<GLNumberSpinner,Double> values){values.forEach(GLNumberSpinner::setValue);}
    void cancel(){
        editing=false;selecting=false;source=null;
        for(var member:selected){member.setHighlighted(false);member.syncEditor();}
        selected.clear();
    }
    private void highlight(){for(var member:members)member.setHighlighted(selected.contains(member));}
}
