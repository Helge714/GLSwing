package com.vingame.glswing.node.execution.java;

import java.util.List;
import java.util.Map;

import com.vingame.glswing.node.model.GLNodeValue;

/** Runtime implementation of one operation type for the reference Java backend. */
@FunctionalInterface
public interface GLJavaOperation {
	Map<String, GLNodeValue> execute(GLJavaExecutionContext context,
			Map<String, List<GLNodeValue>> inputs,
			Map<String, GLNodeValue> properties) throws Exception;
}
