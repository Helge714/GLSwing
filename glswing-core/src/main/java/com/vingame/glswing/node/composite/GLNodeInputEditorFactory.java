package com.vingame.glswing.node.composite;

import com.vingame.glswing.component.GLContainer;

/** Creates the runtime control for portable composite input-editor metadata. */
@FunctionalInterface
public interface GLNodeInputEditorFactory {
	GLContainer createEditor(GLNodeInputEditorContext context);
}
