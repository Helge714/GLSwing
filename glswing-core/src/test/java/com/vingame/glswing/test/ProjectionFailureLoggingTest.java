package com.vingame.glswing.test;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.node.*;
import com.vingame.glswing.node.binding.*;
import com.vingame.glswing.node.model.*;
import com.vingame.glswing.ui.GLLabel;

class ProjectionFailureLoggingTest {
    @Test void missingPortLogsIdentityAndStackOnceUntilRecovery() throws Exception {
        var logger = Logger.getLogger(GLNodeEditorBinding.class.getName());
        var records = new ArrayList<LogRecord>();
        var handler = new Handler() {
            public void publish(LogRecord record) { records.add(record); }
            public void flush() {}
            public void close() {}
        };
        boolean parents = logger.getUseParentHandlers();
        Level level = logger.getLevel();
        logger.setUseParentHandlers(false); logger.setLevel(Level.ALL); logger.addHandler(handler);
        try {
            SwingUtilities.invokeAndWait(() -> {
                var graph = new GLNodeGraph(); var id = GLNodeId.random(); var healthy = GLNodeId.random();
                graph.edit("Create", edit -> {
                    edit.addNode(new GLGraphNode(id, "test.output"));
                    edit.addNode(new GLGraphNode(healthy, "test.output"));
                });
                var types = new GLNodeTypeRegistry();
                types.register(new GLNodeTypeDescriptor("test.output",GLNodePortDescriptor.output("out", "number")));
                var broken = new AtomicBoolean(true);
                var factory = new GLNodeViewFactory() {
                    public GLNode createNode(GLGraphNode node, GLNodeTypeDescriptor type) {
                        var view = new GLNode("Test");
                        if (!broken.get() || node.id().equals(healthy)) view.addOutput("out", new GLLabel("Out"));
                        return view;
                    }
                    public boolean isViewCurrent(GLNode view, GLGraphNode node, GLNodeTypeDescriptor type) { return false; }
                };
                try (var binding = new GLNodeEditorBinding(graph, types, new GLNodeEditor(), factory, GLGraphCyclePolicy.DELAYED_FEEDBACK)) {
                    assertEquals(1, records.size());
                    var record = records.get(0);
                    assertEquals(Level.WARNING, record.getLevel());
                    assertTrue(record.getMessage().contains(id.toString()));
                    assertTrue(record.getMessage().contains("test.output"));
                    assertTrue(record.getMessage().contains(graph.snapshot().graphId().toString()));
                    assertSame(binding.lastProjectionFailure().orElseThrow(), record.getThrown());
                    assertTrue(record.getThrown().getMessage().contains("out"));
                    assertTrue(binding.findNodeView(healthy).isPresent());
                    binding.refresh(); binding.refresh(); assertEquals(1, records.size());
                    broken.set(false); binding.refresh();
                    assertTrue(binding.lastProjectionFailure().isEmpty());
                    assertTrue(binding.findNodeView(id).isPresent());
                    broken.set(true); binding.refresh(); assertEquals(2, records.size());
                }
            });
        } finally {
            logger.removeHandler(handler); logger.setUseParentHandlers(parents); logger.setLevel(level);
        }
    }
}
