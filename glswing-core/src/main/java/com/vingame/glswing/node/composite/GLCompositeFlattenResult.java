package com.vingame.glswing.node.composite;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vingame.glswing.node.model.GLGraphDiagnostic;
import com.vingame.glswing.node.model.GLNodeGraphSnapshot;
import com.vingame.glswing.node.model.GLNodeId;

/** Flattened graph, origin map, and diagnostics from one transactional expansion. */
public record GLCompositeFlattenResult(Optional<GLNodeGraphSnapshot> graph,
		Map<GLNodeId, GLCompositeOrigin> origins,
		List<GLGraphDiagnostic> diagnostics) {
	public GLCompositeFlattenResult {
		if (graph == null || origins == null || diagnostics == null)
			throw new IllegalArgumentException("Complete flatten result required");
		origins = Collections.unmodifiableMap(new LinkedHashMap<>(origins));
		diagnostics = List.copyOf(diagnostics);
		if (graph.isEmpty() && !origins.isEmpty())
			throw new IllegalArgumentException("Failed expansion cannot expose partial origins");
	}
	public boolean isValid() { return graph.isPresent(); }
}
