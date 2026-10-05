package com.vingame.glswing.node.composite;

import java.util.*;
import com.vingame.glswing.node.model.GLNodeValue;

/** Neutral editor contract supplied by the application, including portable presentation options.
 * Core interprets eligibility and editor kind; rate and unit semantics remain with the provider. */
public record GLNodeEditorCapabilities(Kind kind, boolean copyable, boolean standalone,
        GLNodeValue defaultValue, Map<String,GLNodeValue> options) {
    public enum Kind { SLIDER, CHOICE, CHECKBOX, TRIGGER, CUSTOM }
    public GLNodeEditorCapabilities {
        Objects.requireNonNull(kind); Objects.requireNonNull(defaultValue); options = Map.copyOf(options);
    }
    public boolean linkable() { return copyable && standalone && kind != Kind.CUSTOM; }
}
