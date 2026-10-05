package com.vingame.glswing.ui;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class GLLabelTooltipTest {
    @Test void tooltipEnablesPickingButNeverSelectionOrFocus(){
        var label=new GLLabel("Value");assertFalse(label.isMouseTargetEnabled());
        label.setToolTipText("Meaning");assertTrue(label.isMouseTargetEnabled());assertFalse(label.isFocusTraversable());assertFalse(label.isSelectable());
        label.setToolTipText(" ");assertFalse(label.isMouseTargetEnabled());
        label.setSelectable(true);label.setToolTipText("Meaning");label.setToolTipText(null);
        assertTrue(label.isMouseTargetEnabled());assertTrue(label.isFocusTraversable());
    }
}
