package com.vingame.glswing.rendering.internal;

import java.util.Arrays;

import com.vingame.glswing.input.GLMouseTarget;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.rendering.GLTransform;

/** Reusable frame-local mapping from compact GPU IDs to application pick data. */
public final class PickFrameRecords {

	private static final int INITIAL_CAPACITY = 256;
	private int[] ids = new int[INITIAL_CAPACITY];
	private GLMouseTarget[] targets = new GLMouseTarget[INITIAL_CAPACITY];
	private float[] inverses = new float[INITIAL_CAPACITY * 6];
	private int count = 1;
	private int lastRecord;
	private int lastId;
	private GLMouseTarget lastTarget;
	private final float[] lastTransform = new float[6];

	private PickFrameRecords(PickFrameRecords source) {
		ids = Arrays.copyOf(source.ids, source.count);
		targets = Arrays.copyOf(source.targets, source.count);
		inverses = Arrays.copyOf(source.inverses, source.count * 6);
		count = source.count;
		lastRecord = 0;
		lastId = 0;
		lastTarget = null;
	}

	public PickFrameRecords() {
	}

	public void beginFrame() {
		Arrays.fill(targets, 1, count, null);
		count = 1;
		lastRecord = 0;
		lastTarget = null;
		lastId = 0;
	}

	public int add(int id, GLMouseTarget target, GLTransform localToScreen) {
		if (id != 0 && target != null)
			throw new IllegalArgumentException(
					"Pick ID and mouse target are mutually exclusive");
		if (id == 0 && target == null)
			return 0;
		if (localToScreen == null)
			throw new IllegalArgumentException("Pick transform must not be null");

		float m00 = localToScreen.getScaleX();
		float m10 = localToScreen.getShearY();
		float m01 = localToScreen.getShearX();
		float m11 = localToScreen.getScaleY();
		float m02 = localToScreen.getTranslateX();
		float m12 = localToScreen.getTranslateY();
		if (lastRecord != 0 && id == lastId && target == lastTarget
				&& sameTransform(m00, m10, m01, m11, m02, m12))
			return lastRecord;

		ensureCapacity(count + 1);
		int record = count++;
		ids[record] = id;
		targets[record] = target;
		int offset = record * 6;
		float determinant = m00 * m11 - m01 * m10;
		if (determinant == 0 || !Float.isFinite(determinant)) {
			Arrays.fill(inverses, offset, offset + 6, Float.NaN);
		} else {
			inverses[offset] = m11 / determinant;
			inverses[offset + 1] = -m10 / determinant;
			inverses[offset + 2] = -m01 / determinant;
			inverses[offset + 3] = m00 / determinant;
			inverses[offset + 4] = (m01 * m12 - m11 * m02) / determinant;
			inverses[offset + 5] = (m10 * m02 - m00 * m12) / determinant;
		}
		lastRecord = record;
		lastId = id;
		lastTarget = target;
		lastTransform[0] = m00;
		lastTransform[1] = m10;
		lastTransform[2] = m01;
		lastTransform[3] = m11;
		lastTransform[4] = m02;
		lastTransform[5] = m12;
		return record;
	}

	public GLPickResult resolve(int record, float screenX, float screenY) {
		if (record <= 0 || record >= count)
			return GLPickResult.none();
		int offset = record * 6;
		float inverseM00 = inverses[offset];
		float inverseM10 = inverses[offset + 1];
		float inverseM01 = inverses[offset + 2];
		float inverseM11 = inverses[offset + 3];
		float inverseM02 = inverses[offset + 4];
		float inverseM12 = inverses[offset + 5];
		float localX = inverseM00 * screenX + inverseM01 * screenY + inverseM02;
		float localY = inverseM10 * screenX + inverseM11 * screenY + inverseM12;
		return new GLPickResult(ids[record], targets[record], screenX, screenY,
				localX, localY, inverseM00, inverseM10, inverseM01, inverseM11,
				inverseM02, inverseM12);
	}

	/** Immutable-by-convention copy paired with one completed GPU ID image. */
	public PickFrameRecords snapshot() {
		return new PickFrameRecords(this);
	}

	private boolean sameTransform(float m00, float m10, float m01,
			float m11, float m02, float m12) {
		return Float.compare(lastTransform[0], m00) == 0
				&& Float.compare(lastTransform[1], m10) == 0
				&& Float.compare(lastTransform[2], m01) == 0
				&& Float.compare(lastTransform[3], m11) == 0
				&& Float.compare(lastTransform[4], m02) == 0
				&& Float.compare(lastTransform[5], m12) == 0;
	}

	private void ensureCapacity(int required) {
		if (required <= ids.length)
			return;
		int capacity = Math.max(required, ids.length * 2);
		ids = Arrays.copyOf(ids, capacity);
		targets = Arrays.copyOf(targets, capacity);
		inverses = Arrays.copyOf(inverses, capacity * 6);
	}
}
