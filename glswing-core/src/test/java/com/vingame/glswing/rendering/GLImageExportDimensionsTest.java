package com.vingame.glswing.rendering;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GLImageExportDimensionsTest {
    @Test void coupledDimensionsPaddingAndReset(){
        var d=new GLImageExportDimensions(600,400,32);assertEquals(664,d.width());assertEquals(464,d.height());
        d.width(1328);assertEquals(928,d.height());assertEquals(64,d.padding());
        d.padding(100);assertEquals(1328,d.width());assertEquals(928,d.height());
        d.linked(false);d.height(500);assertEquals(1328,d.width());assertEquals(500,d.height());
        d.addPadding(true);assertEquals(1528,d.outputWidth());assertEquals(700,d.outputHeight());
        d.addPadding(false);assertEquals(1328,d.outputWidth());assertEquals(500,d.outputHeight());
        d.reset();assertEquals(664,d.width());assertEquals(464,d.height());assertEquals(32,d.padding());
        assertTrue(GLImageExportDimensions.valid(10000,10000),"The old 32 MP policy must not apply");
        d.linked(true);d.size(800,800);assertEquals(800,d.width());assertEquals(800,d.height());
        d.width(400);assertEquals(400,d.height());
    }
    @Test void everyInputIsBoundedBeforeExport(){
        var random=new java.util.Random(51);
        for(int i=0;i<2000;i++){
            var d=new GLImageExportDimensions(1+random.nextDouble()*100000,1+random.nextDouble()*100000,32);
            d.linked(random.nextBoolean());d.addPadding(random.nextBoolean());
            for(int n=0;n<8;n++){
                if(n%4==0)d.width(random.nextInt(20000));else if(n%4==1)d.height(random.nextInt(20000));else if(n%4==2)d.padding(random.nextInt(20000));else d.size(random.nextInt(100000),random.nextInt(100000));
                assertTrue(GLImageExportDimensions.valid(d.width(),d.height()),d.width()+" Ã— "+d.height());
                assertTrue(GLImageExportDimensions.valid(d.outputWidth(),d.outputHeight()));
                assertTrue(d.padding()*2<Math.min(d.outputWidth(),d.outputHeight()));
            }
        }
    }
}
