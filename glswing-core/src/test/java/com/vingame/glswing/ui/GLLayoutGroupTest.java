package com.vingame.glswing.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.layout.GLConstraints.Connection;
import com.vingame.glswing.history.GLHistoryManager;

class GLLayoutGroupTest {
    private GLLayoutGroup group() {
        GLLayoutGroup group = new GLLayoutGroup();
        group.setGLLayout(com.vingame.glswing.layout.GLLayouts.FREE);
        group.setBounds(0, 0, 100, 100);
        return group;
    }
    private GLButton cell(GLLayoutGroup group, float x, float y, float w, float h) {
        GLButton button = new GLButton("Cell");
        button.setBounds(x,y,w,h);
        group.addGL(button,GLConstraints.auto().connectAll());
        return button;
    }
    @Test void partialEdgeConnectionsAndRemoval() {
        GLLayoutGroup group=group();
        GLButton field=cell(group,0,0,70,60);
        GLButton up=cell(group,70,0,30,30);
        GLButton down=cell(group,70,30,30,30);
        group.refreshConnections();
        assertEquals(0,field.getConnectedShape().topRight());
        assertEquals(0,up.getConnectedShape().bottomLeft());
        assertEquals(0,down.getConnectedShape().topLeft());
        group.removeGL(up);
        assertNull(up.getConnectedShape());
        group.refreshConnections();
        assertTrue(group.getOutlineShape().contains(10,10));
        assertFalse(group.getOutlineShape().contains(85,10));
    }
    @Test void nestedLShapePreservesEmptySpace() {
        GLLayoutGroup root=group(), nested=group();
        nested.setBounds(0,0,60,60);
        cell(nested,0,0,60,30);
        cell(nested,0,30,30,30);
        root.addGL(nested,GLConstraints.auto().connectAll());
        GLButton other=cell(root,60,0,30,30);
        root.refreshConnections();
        assertEquals(0,other.getConnectedShape().topLeft());
        assertFalse(root.getOutlineShape().contains(45,45));
        assertTrue(root.getOutlineShape().contains(15,45));
    }
    @Test void connectionPermissionsSurviveConstraintCopies() {
        var constraints=GLConstraints.at(0,0).connect(Connection.RIGHT).span(1,2).weight(1,1).fillBoth();
        assertEquals(java.util.Set.of(Connection.RIGHT),constraints.connections());
        assertTrue(GLConstraints.DEFAULT.connections().isEmpty());
    }
    @Test void inputHistoryIsIndependentOfLayout() {
        GLHistoryManager history=new GLHistoryManager();
        int[] values={1,2};
        GLInputGroup<Integer> group=new GLInputGroup<>(()->history);
        group.add(()->values[0],v->values[0]=v);
        group.add(()->values[1],v->values[1]=v);
        group.begin("Change values"); group.update(v->v+1);group.update(v->v+1);group.commit();
        assertArrayEquals(new int[]{3,4},values);
        assertTrue(history.undo()); assertArrayEquals(new int[]{1,2},values);assertFalse(history.canUndo());
        assertTrue(history.redo());assertArrayEquals(new int[]{3,4},values);
        group.begin("Cancel");group.update(v->99);group.cancel();assertArrayEquals(new int[]{3,4},values);
    }
}
