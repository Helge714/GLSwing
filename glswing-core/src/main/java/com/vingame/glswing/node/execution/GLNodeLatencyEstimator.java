package com.vingame.glswing.node.execution;

import java.util.Map;

import com.vingame.glswing.node.model.GLNodeValue;

/** Calculates property-dependent processing latency in the plan's named unit. */
@FunctionalInterface
public interface GLNodeLatencyEstimator {
	long estimate(Map<String, GLNodeValue> properties) throws Exception;
}
