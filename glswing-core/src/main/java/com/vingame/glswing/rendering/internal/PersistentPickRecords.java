package com.vingame.glswing.rendering.internal;

import java.lang.ref.WeakReference;
import java.util.Arrays;

import com.vingame.glswing.input.GLMouseTarget;
import com.vingame.glswing.rendering.GLTransform;

/** Target-local pick records whose GPU indices may survive across frames. */
public final class PersistentPickRecords {

	private static final int INITIAL_CAPACITY = 64;
	private int[] ids = new int[INITIAL_CAPACITY];
	@SuppressWarnings("unchecked")
	private WeakReference<GLMouseTarget>[] targets = new WeakReference[INITIAL_CAPACITY];
	private float[] transforms = new float[INITIAL_CAPACITY * 6];
	private int count = 1;
	private int lastRecord;

	public int add(int id, GLMouseTarget target, GLTransform localToTarget) {
		validate(id, target, localToTarget);
		if (id == 0 && target == null)
			return 0;
		if (lastRecord != 0 && ids[lastRecord] == id
				&& targetAt(lastRecord) == target
				&& sameTransform(lastRecord, localToTarget))
			return lastRecord;
		int record = appendExact(id, target, localToTarget);
		lastRecord = record;
		return record;
	}

	/** Removes all persistent mappings after the complete ID surface was cleared. */
	public void reset() {
		Arrays.fill(targets, 1, count, null);
		count = 1;
		lastRecord = 0;
	}

	public int size() {
		return count - 1;
	}

	/** Appends a contiguous presentation mapping to one frame-local registry. */
	public int appendTo(PickFrameRecords destination,
			GLTransform targetToScreen) {
		if (destination == null || targetToScreen == null)
			throw new IllegalArgumentException(
					"Pick destination and presentation transform are required");
		int base = destination.nextRecord();
		appendRecords((id, target, transform) ->
				destination.appendExact(id, target, transform), targetToScreen);
		return base;
	}

	/** Appends a contiguous mapping while one target is drawn into another. */
	public int appendTo(PersistentPickRecords destination,
			GLTransform targetToTarget) {
		if (destination == null || targetToTarget == null)
			throw new IllegalArgumentException(
					"Pick destination and presentation transform are required");
		if (destination == this)
			throw new IllegalArgumentException(
					"A picking target cannot be mapped into itself");
		int base = destination.count;
		appendRecords(destination::appendExact, targetToTarget);
		return base;
	}

	private void appendRecords(RecordAppender destination,
			GLTransform targetToDestination) {
		GLTransform local = new GLTransform();
		GLTransform combined = new GLTransform();
		for (int record = 1; record < count; record++) {
			int offset = record * 6;
			local.setTransform(transforms[offset], transforms[offset + 1],
					transforms[offset + 2], transforms[offset + 3],
					transforms[offset + 4], transforms[offset + 5]);
			combined.setTransform(targetToDestination).concatenate(local);
			destination.append(ids[record], targetAt(record), combined);
		}
	}

	private int appendExact(int id, GLMouseTarget target,
			GLTransform localToTarget) {
		ensureCapacity(count + 1);
		int record = count++;
		ids[record] = id;
		targets[record] = target == null ? null : new WeakReference<>(target);
		int offset = record * 6;
		transforms[offset] = localToTarget.getScaleX();
		transforms[offset + 1] = localToTarget.getShearY();
		transforms[offset + 2] = localToTarget.getShearX();
		transforms[offset + 3] = localToTarget.getScaleY();
		transforms[offset + 4] = localToTarget.getTranslateX();
		transforms[offset + 5] = localToTarget.getTranslateY();
		return record;
	}

	private GLMouseTarget targetAt(int record) {
		WeakReference<GLMouseTarget> reference = targets[record];
		return reference == null ? null : reference.get();
	}

	private boolean sameTransform(int record, GLTransform transform) {
		int offset = record * 6;
		return Float.compare(transforms[offset], transform.getScaleX()) == 0
				&& Float.compare(transforms[offset + 1], transform.getShearY()) == 0
				&& Float.compare(transforms[offset + 2], transform.getShearX()) == 0
				&& Float.compare(transforms[offset + 3], transform.getScaleY()) == 0
				&& Float.compare(transforms[offset + 4], transform.getTranslateX()) == 0
				&& Float.compare(transforms[offset + 5], transform.getTranslateY()) == 0;
	}

	private void ensureCapacity(int required) {
		if (required <= ids.length)
			return;
		int capacity = Math.max(required, ids.length * 2);
		ids = Arrays.copyOf(ids, capacity);
		targets = Arrays.copyOf(targets, capacity);
		transforms = Arrays.copyOf(transforms, capacity * 6);
	}

	private static void validate(int id, GLMouseTarget target,
			GLTransform transform) {
		if (id != 0 && target != null)
			throw new IllegalArgumentException(
					"Pick ID and mouse target are mutually exclusive");
		if (transform == null)
			throw new IllegalArgumentException("Pick transform must not be null");
	}

	@FunctionalInterface
	private interface RecordAppender {
		void append(int id, GLMouseTarget target, GLTransform transform);
	}
}
