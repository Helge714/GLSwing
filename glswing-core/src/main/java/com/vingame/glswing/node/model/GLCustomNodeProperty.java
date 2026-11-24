package com.vingame.glswing.node.model;

import java.util.Objects;
import java.util.function.*;
import com.vingame.glswing.component.GLContainer;

/** Application-owned inspector control, with in-place model synchronization. */
public record GLCustomNodeProperty(String id, String displayName,
        BiFunction<GLGraphNode, Consumer<GLNodeValue>, GLContainer> create,
        BiConsumer<GLContainer, GLGraphNode> update) implements GLNodePropertyDescriptor {
    public GLCustomNodeProperty { Objects.requireNonNull(id); Objects.requireNonNull(displayName); Objects.requireNonNull(create); Objects.requireNonNull(update); }
}
