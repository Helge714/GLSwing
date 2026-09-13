package com.vingame.glswing.node.execution.java;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vingame.glswing.node.execution.GLExecutionInput;
import com.vingame.glswing.node.execution.GLExecutionOperation;
import com.vingame.glswing.node.execution.GLExecutionPlan;
import com.vingame.glswing.node.execution.GLExecutionStorage;
import com.vingame.glswing.node.execution.GLExecutionValue;
import com.vingame.glswing.node.execution.GLExecutionValueRef;
import com.vingame.glswing.node.model.GLNodeValue;
import com.vingame.glswing.node.model.GLPortTiming;

/** Prepared, state-owning instance of a plan for the reference Java backend. */
public final class GLJavaExecutablePlan {
	private final GLExecutionPlan plan;
	private final Map<String, GLJavaOperation> implementations;
	private final Map<GLExecutionValueRef, GLNodeValue> previousState =
			new LinkedHashMap<>();
	private Map<GLExecutionValueRef, GLNodeValue> workingValues;
	private List<GLJavaExecutionTrace> workingTraces;
	private int nextOperationIndex;
	private long nextStep;

	GLJavaExecutablePlan(GLExecutionPlan plan,
			Map<String, GLJavaOperation> implementations) {
		this.plan = plan;
		this.implementations = Map.copyOf(implementations);
	}

	public GLExecutionPlan plan() { return plan; }
	public long nextStep() { return nextStep; }

	/** Evaluates exactly one complete step and updates previous-step state atomically. */
	public synchronized GLJavaExecutionResult evaluate() throws GLJavaExecutionException {
		while (true) {
			GLJavaExecutionProgress progress = executeNext();
			if (progress.completedStep().isPresent())
				return progress.completedStep().orElseThrow();
		}
	}

	/** Operation that would be executed by the next {@link #executeNext()} call. */
	public synchronized Optional<GLExecutionOperation> nextOperation() {
		if (plan.operations().isEmpty()) return Optional.empty();
		return Optional.of(plan.operations().get(workingValues == null
				? 0 : nextOperationIndex));
	}

	public synchronized boolean isStepInProgress() { return workingValues != null; }

	/**
	 * Advances the current graph step by one operation. State becomes visible to
	 * the next graph step only after the final operation succeeds.
	 */
	public synchronized GLJavaExecutionProgress executeNext()
			throws GLJavaExecutionException {
		if (workingValues == null) beginStep();
		if (plan.operations().isEmpty())
			return new GLJavaExecutionProgress(Optional.empty(),
					Optional.of(finishStep()));
		GLExecutionOperation operation = plan.operations().get(nextOperationIndex);
		try {
			Map<String, List<GLNodeValue>> inputs = new LinkedHashMap<>();
			for (GLExecutionInput input : operation.inputs()) {
				GLNodeValue value = input.timing() == GLPortTiming.PREVIOUS_STEP
						? previousState.getOrDefault(input.value(), GLNodeValue.NullValue.INSTANCE)
						: workingValues.get(input.value());
				if (value == null) throw failure(operation,
						"Current-step input " + input.portId() + " has no value", null);
				inputs.computeIfAbsent(input.portId(), ignored -> new ArrayList<>()).add(value);
			}
			Map<String, GLNodeValue> produced;
			long started = System.nanoTime();
			try {
				produced = implementations.get(operation.operationTypeId()).execute(
						new GLJavaExecutionContext(nextStep, operation), immutable(inputs),
						operation.properties());
			} catch (Exception exception) {
				throw failure(operation, "Operation failed: " + operation.operationTypeId(),
						exception);
			}
			if (produced == null) throw failure(operation,
					"Operation returned no output map", null);
			for (Map.Entry<String, GLExecutionValueRef> output : operation.outputs().entrySet()) {
				GLNodeValue value = produced.get(output.getKey());
				if (value == null) throw failure(operation,
						"Operation did not produce output " + output.getKey(), null);
				workingValues.put(output.getValue(), value);
			}
			for (String output : produced.keySet())
				if (!operation.outputs().containsKey(output)) throw failure(operation,
						"Operation produced unknown output " + output, null);
			long duration = Math.max(0, System.nanoTime() - started);
			GLJavaExecutionTrace trace = new GLJavaExecutionTrace(nextStep, operation,
					immutable(inputs), produced, duration);
			workingTraces.add(trace);
			nextOperationIndex++;
			Optional<GLJavaExecutionResult> completed = nextOperationIndex
					== plan.operations().size() ? Optional.of(finishStep()) : Optional.empty();
			return new GLJavaExecutionProgress(Optional.of(trace), completed);
		} catch (GLJavaExecutionException failure) {
			abandonStep();
			throw failure;
		}
	}

	private void beginStep() {
		workingValues = new LinkedHashMap<>();
		for (GLExecutionValue value : plan.values())
			value.constant().ifPresent(constant ->
					workingValues.put(value.ref(), constant));
		workingTraces = new ArrayList<>();
		nextOperationIndex = 0;
	}

	private GLJavaExecutionResult finishStep() {
		Map<GLExecutionValueRef, GLNodeValue> nextState = new LinkedHashMap<>();
		for (GLExecutionValue value : plan.values())
			if (value.storage() == GLExecutionStorage.STATE
					&& workingValues.containsKey(value.ref()))
				nextState.put(value.ref(), workingValues.get(value.ref()));
		previousState.clear();
		previousState.putAll(nextState);
		GLJavaExecutionResult result = new GLJavaExecutionResult(nextStep++,
				workingValues, workingTraces);
		abandonStep();
		return result;
	}

	private void abandonStep() {
		workingValues = null;
		workingTraces = null;
		nextOperationIndex = 0;
	}

	/** Clears persistent previous-step values and restarts the step counter. */
	public synchronized void reset() {
		previousState.clear();
		abandonStep();
		nextStep = 0;
	}

	private static Map<String, List<GLNodeValue>> immutable(
			Map<String, List<GLNodeValue>> source) {
		Map<String, List<GLNodeValue>> result = new LinkedHashMap<>();
		source.forEach((key, value) -> result.put(key, List.copyOf(value)));
		return Map.copyOf(result);
	}
	private static GLJavaExecutionException failure(GLExecutionOperation operation,
			String message, Throwable cause) {
		return new GLJavaExecutionException(operation, message, cause);
	}
}
