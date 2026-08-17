package com.vingame.glswing.component;

/** Accumulates conservative screen-space repaint damage between frames. */
final class GLDamageTracker {

	private static final float FULL_REDRAW_AREA = 0.55f;
	private boolean fullRedraw = true;
	private boolean pickingDirty = true;
	private float minimumX;
	private float minimumY;
	private float maximumX;
	private float maximumY;
	private boolean hasDamage;

	synchronized void repaintAll(boolean rebuildPicking) {
		fullRedraw = true;
		hasDamage = true;
		pickingDirty |= rebuildPicking;
	}

	synchronized void repaint(GLContainer source, boolean rebuildPicking) {
		if (source == null) {
			repaintAll(rebuildPicking);
			return;
		}
		float[] previous = source.lastScreenVisualBounds();
		float[] current = source.predictedScreenVisualBounds();
		if (previous == null || current == null) {
			repaintAll(rebuildPicking);
			return;
		}
		add(previous);
		add(current);
		pickingDirty |= rebuildPicking;
	}

	synchronized Damage consume(float width, float height) {
		if (!hasDamage)
			return Damage.IDLE;
		boolean full = fullRedraw || pickingDirty;
		float[] bounds = null;
		if (!full) {
			float x0 = clamp(minimumX, 0, width);
			float y0 = clamp(minimumY, 0, height);
			float x1 = clamp(maximumX, 0, width);
			float y1 = clamp(maximumY, 0, height);
			float damagedArea = Math.max(0, x1 - x0) * Math.max(0, y1 - y0);
			full = width <= 0 || height <= 0
					|| damagedArea >= width * height * FULL_REDRAW_AREA;
			if (!full)
				bounds = new float[] { x0, y0,
						Math.max(0, x1 - x0), Math.max(0, y1 - y0) };
		}
		// A complete color repaint does not imply changed picking geometry.
		// Picking requests still force a complete submission, but color-only
		// interaction frames retain the existing ID surface and CPU snapshot.
		Damage result = new Damage(true, full, pickingDirty, bounds);
		fullRedraw = false;
		pickingDirty = false;
		hasDamage = false;
		return result;
	}

	synchronized boolean isPickingDirty() {
		return pickingDirty;
	}

	private void add(float[] bounds) {
		if (bounds[2] <= 0 || bounds[3] <= 0)
			return;
		float x0 = bounds[0] - 2;
		float y0 = bounds[1] - 2;
		float x1 = bounds[0] + bounds[2] + 2;
		float y1 = bounds[1] + bounds[3] + 2;
		if (!hasDamage) {
			minimumX = x0;
			minimumY = y0;
			maximumX = x1;
			maximumY = y1;
			hasDamage = true;
		} else {
			minimumX = Math.min(minimumX, x0);
			minimumY = Math.min(minimumY, y0);
			maximumX = Math.max(maximumX, x1);
			maximumY = Math.max(maximumY, y1);
		}
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	record Damage(boolean redraw, boolean full, boolean rebuildPicking,
			float[] bounds) {
		private static final Damage IDLE = new Damage(false, false, false, null);
	}
}
