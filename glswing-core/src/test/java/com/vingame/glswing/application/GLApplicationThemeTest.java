package com.vingame.glswing.application;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.vingame.glswing.ui.*;
import java.awt.Color;
class GLApplicationThemeTest {
    @Test void clearColorFollowsActiveThemeUnlessExplicitlyOverridden(){
        var theme=GLTheme.DEFAULT.withValues(GLTheme.DEFAULT.values().with("application.clearColor","#123456"));
        assertEquals(new Color(0x123456),GLApplicationWindow.resolveClearColor(null,theme));
        assertEquals(Color.RED,GLApplicationWindow.resolveClearColor(Color.RED,theme));
        var changed=theme.withValues(theme.values().with("application.clearColor","#654321"));
        assertEquals(new Color(0x654321),GLApplicationWindow.resolveClearColor(null,changed));
    }
    @Test void auxiliaryColorsAreDeclaredInTheTheme(){
        for(String key:new String[]{"compositeInterface.background","nodeWorkspace.background","nodeBreadcrumb.hoverText","splash.error","codeEditor.diagnostic.error","line.color","colorPicker.checkerLight"})
            assertNotNull(GLTheme.DEFAULT.values().getColor(key));
        var overridden=GLTheme.DEFAULT.values().with("compositeInterface.background","#010203");
        assertEquals(new Color(1,2,3),overridden.getColor("compositeInterface.background"));
    }
}
