package com.vingame.glswing.node.execution;

import java.util.Map;

import com.vingame.glswing.node.model.GLNodeValue;

/** Optional compile-time evaluator for a pure operation. */
@FunctionalInterface
public interface GLNodeConstantEvaluator {
	/** Returns values keyed by output port ID, or an empty map when folding is unavailable. */
	Map<String, GLNodeValue> evaluate(Map<String, GLNodeValue> properties,
			Map<String, java.util.List<GLNodeValue>> inputs) throws Exception;
}
