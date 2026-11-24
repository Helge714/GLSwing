package com.vingame.glswing.node;

import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.vingame.glswing.node.binding.*;
import com.vingame.glswing.node.model.*;
import com.vingame.glswing.ui.GLLabel;

class GLHiddenNodeViewStateTest {
    @Test void persistedBoundsAreRestoredAfterHiddenPresentation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var types = new GLNodeTypeRegistry();
            types.register(new GLNodeTypeDescriptor("test.view"));
            var id = GLNodeId.random();
            var graph = new GLNodeGraph();
            graph.edit("Create hidden node", edit -> edit.addNode(new GLGraphNode(
                    id, "test.view", Map.of(GLNodeState.HIDDEN, GLNodeValue.of(true)))));
            var saved = new GLNodeViewState(30, 50, 443, 577);
            var state = new GLNodeEditorViewState(Map.of(id, saved));
            GLNodeViewFactory factory = (model, type) -> {
                var node = new GLNode("View");
                node.addContent(new GLLabel("Content"));
                return node;
            };
            // Recreate the binding as workspace navigation does, retaining captured dimensions.
            for (int i = 0; i < 3; i++) {
                try (var binding = new GLNodeEditorBinding(graph, types, new GLNodeEditor(),
                        factory, GLGraphCyclePolicy.ACYCLIC, state)) {
                    var view = binding.findNodeView(id).orElseThrow();
                    assertTrue(view.isContentHidden());
                    assertEquals(saved, GLNodeViewState.capture(view));
                    state = binding.captureViewState();
                }
            }
        });
    }
}
