package com.vingame.glswing.test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.ui.*;

class GLUnitSliderTest {
    enum Unit implements GLUnit {
        METRE("length",1,"m"), CENTIMETRE("length",.01,"cm"), SECOND("time",1,"s");
        private final String dimension,suffix;private final double scale;
        Unit(String dimension,double scale,String suffix){this.dimension=dimension;this.scale=scale;this.suffix=suffix;}
        public String id(){return name();} public String displayName(){return name();}public String suffix(){return suffix;}
        public Object dimension(){return dimension;}public double toBase(double v){return v*scale;}public double fromBase(double v){return v/scale;}
    }
    @Test void oneSidedBoundsHaveNonzeroDisplayRanges(){
        var lower=new GLUnitSlider<>("Premul",1.,1.,null,.01,false,GLSlider.Presentation.BAR,formats(),Unit.METRE);
        assertTrue(lower.getVisibleMaximum()>lower.getVisibleMinimum());
        assertEquals(1.,lower.getValue());
        lower.setDisplayUnit(Unit.CENTIMETRE);
        assertEquals(100.,lower.getVisibleMinimum());
        assertTrue(lower.getVisibleMaximum()>100.);
        var upper=new GLUnitSlider<>("Negative",0.,null,0.,.01,false,GLSlider.Presentation.BAR,formats(),Unit.METRE);
        assertTrue(upper.getVisibleMinimum()<upper.getVisibleMaximum());
        assertEquals(0.,upper.getValue());
    }
    private GLUnitFormats<Unit> formats(){return new GLUnitFormats<>(Unit.METRE,Unit.METRE,List.of(Unit.METRE,Unit.CENTIMETRE));}
    @Test void unitMenuPreservesCanonicalValueBoundsAndResetWithoutReportingValueEdits(){
        var slider=new GLUnitSlider<>("Length",1.,0.,2.,.01,false,GLSlider.Presentation.BAR,formats(),Unit.METRE);
        var calls=new AtomicInteger();slider.setStoredValueListener(v->calls.incrementAndGet());
        slider.getContextMenu().getItems().stream().filter(i->i.getText().equals("Unit")).findFirst().orElseThrow().getSubMenu().getItems().get(1).doClick();
        assertEquals(Unit.CENTIMETRE,slider.getDisplayUnit());assertEquals(100.,slider.getValue());assertEquals(1.,slider.getStoredValue());
        assertEquals(200.,slider.getValueMaximum().orElseThrow());assertEquals("cm",slider.getUnit());assertEquals(0,calls.get());
        slider.setValue(150);assertEquals(1.5,slider.getStoredValue());assertEquals(1,calls.get());
        slider.setStoredValue(.75);assertEquals(75.,slider.getValue());assertEquals(1,calls.get());
        slider.reset();assertEquals(1.,slider.getStoredValue());assertEquals(2,calls.get());
        slider.setDisplayUnit(Unit.METRE);assertEquals(1.,slider.getValue());assertEquals(2.,slider.getValueMaximum().orElseThrow());
    }
    @Test void changingMetadataKeepsTheControlValueAndDoesNotReportAnEdit(){
        var slider=new GLUnitSlider<>("Length",1.,0.,2.,.01,false,GLSlider.Presentation.BAR,formats(),Unit.CENTIMETRE);
        var edits=new AtomicInteger();slider.setStoredValueListener(v->edits.incrementAndGet());
        slider.setStoredMetadata(2.,0.,4.,.1);assertEquals(1.,slider.getStoredValue());assertEquals(400.,slider.getValueMaximum().orElseThrow());assertEquals(0,edits.get());
        slider.reset();assertEquals(2.,slider.getStoredValue());assertEquals(1,edits.get());
    }
    @Test void freeAndIntegralStoredValuesRemainConstrained(){
        var slider=new GLUnitSlider<>("Length",1.,0.,null,1,true,GLSlider.Presentation.BUTTONS,formats(),Unit.CENTIMETRE);
        assertEquals(GLSlider.Mode.FREE,slider.getMode());assertTrue(slider.getValueMaximum().isEmpty());
        slider.setValue(151);assertEquals(2.,slider.getStoredValue());slider.setValue(-100);assertEquals(0.,slider.getStoredValue());
    }
    @Test void rejectsIncompatibleChoicesAndKeepsFixedUnitsWithoutMenu(){
        assertThrows(IllegalArgumentException.class,()->new GLUnitFormats<>(Unit.METRE,Unit.SECOND,List.of(Unit.SECOND)));
        var slider=new GLUnitSlider<>("Length",1.,null,null,.01,false,GLSlider.Presentation.BUTTONS,GLUnitFormats.fixed(Unit.METRE),Unit.METRE);
        assertThrows(IllegalArgumentException.class,()->slider.setDisplayUnit(Unit.SECOND));
        assertTrue(slider.getContextMenu().getItems().stream().noneMatch(i->i.getText().equals("Unit")));
    }
}
