package com.vingame.glswing.rendering;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class GLFontMathSymbolsTest {
    @Test void defaultAtlasContainsScientificTextWithoutAContext(){
        for(String family:new String[]{GLFont.SANS_SERIF,GLFont.MONOSPACED}){
            var font=new GLFont(family,GLFont.PLAIN,16);
            "∞Σθφμσπ≈≠≤≥∈√∑∫⊙⁴₀•\u1d62\u2019\u201c\u201d\u201e\u202f\u2212\u2500".codePoints().forEach(cp->assertTrue(font.canRender(cp),family+" missing U+"+Integer.toHexString(cp)));
            assertFalse(font.isInitialized());font.dispose(null);
        }
    }
    @Test void explicitRepertoireRemainsRestricted(){
        var font=new GLFont(GLFont.SANS_SERIF,GLFont.PLAIN,16,"ABC");
        assertTrue(font.canRender('A'));assertFalse(font.canRender('∞'));font.dispose(null);
    }
}