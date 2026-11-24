package com.vingame.glswing.node;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.vingame.glswing.node.model.*;
import com.vingame.glswing.ui.GLComboBox;
import java.util.*;
class GLPropertyProvenanceTest {
    @Test void automaticAndExplicitPropertiesHavePersistentPortableMetadata(){
        var original=new GLGraphNode(GLNodeId.random(),"test.node");
        assertTrue(original.isPropertyAutomatic("value"));
        var automatic=original.withAutomaticProperty("value",GLNodeValue.of(1));assertTrue(automatic.isPropertyAutomatic("value"));
        var reconstructed=new GLGraphNode(automatic.id(),automatic.typeId(),automatic.typeVersion(),automatic.properties());assertTrue(reconstructed.isPropertyAutomatic("value"));
        assertFalse(reconstructed.withExplicitProperty("value",GLNodeValue.of(1)).isPropertyAutomatic("value"));
        assertFalse(reconstructed.withProperty("value",GLNodeValue.of(2)).isPropertyAutomatic("value"));
        assertFalse(original.withProperty("value",GLNodeValue.of(1)).isPropertyAutomatic("value"));
        assertTrue(reconstructed.withExplicitProperty("value",GLNodeValue.of(2)).withAutomaticProperty("value",GLNodeValue.of(3)).isPropertyAutomatic("value"));
    }
    @Test void comboPresentationRowsAreNeverValues(){
        var combo=new GLComboBox<String>(List.of("a"));combo.addSeparator();
        combo.addSubMenu("Group").addItem("b").addSeparator().addItem("c");combo.addSeparator();
        assertEquals(List.of("a","b","c"),combo.getItems());assertEquals(3,combo.getItemCount());
        combo.setSelectedIndex(2);assertEquals("c",combo.getSelectedItem());
        combo.sortItems(Comparator.naturalOrder());assertEquals("c",combo.getSelectedItem());assertEquals(3,combo.getItemCount());
    }
}
