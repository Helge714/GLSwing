package com.vingame.glswing.component;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GLDialogSurfacePolicyTest {
    @Test void linuxUsesOpaqueSurfaceUnlessExplicitlyEnabled() {
        assertFalse(GLDialog.translucentDialogsEnabled("Linux", null));
        assertFalse(GLDialog.translucentDialogsEnabled("linux", null));
        assertTrue(GLDialog.translucentDialogsEnabled("Linux", "true"));
    }
    @Test void otherPlatformsKeepTranslucencyAndCanDisableIt() {
        assertTrue(GLDialog.translucentDialogsEnabled("Windows 11", null));
        assertTrue(GLDialog.translucentDialogsEnabled("Mac OS X", null));
        assertFalse(GLDialog.translucentDialogsEnabled("Windows 11", "false"));
    }
}
