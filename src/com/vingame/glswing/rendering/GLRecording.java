package com.vingame.glswing.rendering;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.vingame.glswing.input.GLMouseTarget;

/**
 * Immutable sequence of normalized rendering operations captured by a
 * {@link GLContext2D}. A recording belongs to the renderer that created it and
 * does not own referenced textures, fonts or shaders.
 */
public final class GLRecording {

	private final GLRenderer2D owner;
	private final GLRenderer2D.StateSnapshot initialState;
	private final List<Command> commands;

	private GLRecording(GLRenderer2D owner,
			GLRenderer2D.StateSnapshot initialState, List<Command> commands) {
		this.owner = owner;
		this.initialState = initialState;
		this.commands = List.copyOf(commands);
	}

	/** Returns the number of coalesced replay commands, excluding the initial state snapshot. */
	public int getCommandCount() {
		return commands.size();
	}

	public boolean isEmpty() {
		return commands.isEmpty();
	}

	void replay(GLRenderer2D renderer) {
		replay(renderer, null);
	}

	void replay(GLRenderer2D renderer, GLRenderParameters parameters) {
		if (renderer != owner)
			throw new IllegalArgumentException(
					"A recording can only be drawn by the renderer that created it");
		if (commands.isEmpty())
			return;
		GLRenderer2D.StateSnapshot outerState = renderer.captureState();
		renderer.beginRecordingReplay(initialState, outerState, parameters);
		try {
			for (Command command : commands)
				command.replay(renderer);
		} finally {
			try {
				renderer.endRecordingReplay();
			} finally {
				renderer.applyState(outerState);
			}
		}
	}

	static Builder builder(GLRenderer2D owner) {
		return new Builder(owner, owner.captureState());
	}

	@FunctionalInterface
	interface Command {
		void replay(GLRenderer2D renderer);
	}

	static final class Builder {
		private final GLRenderer2D owner;
		private final GLRenderer2D.StateSnapshot initialState;
		private final List<Command> commands = new ArrayList<>();
		private PendingType pendingType;
		private VertexAccumulator pendingVertices;
		private GLTexture pendingTexture;
		private float pendingPixelRange;
		private int pendingPickId;
		private GLMouseTarget pendingTarget;
		private float pendingOriginX;
		private float pendingOriginY;
		private int openLayers;
		private int openClips;
		private int openParameterRegions;

		private Builder(GLRenderer2D owner, GLRenderer2D.StateSnapshot initialState) {
			this.owner = owner;
			this.initialState = initialState;
		}

		void add(Command command) {
			flushPending();
			commands.add(command);
		}

		void addFilled(float[] vertices, int pickId, GLMouseTarget mouseTarget,
				float originX, float originY) {
			preparePending(PendingType.FILLED, null, 0,
					pickId, mouseTarget, originX, originY);
			pendingVertices.append(vertices);
		}

		void addFilledQuad(
				float x1, float y1, float x2, float y2,
				float x3, float y3, float x4, float y4,
				float red, float green, float blue, float alpha,
				int pickId, GLMouseTarget mouseTarget,
				float originX, float originY) {
			preparePending(PendingType.FILLED, null, 0,
					pickId, mouseTarget, originX, originY);
			pendingVertices.appendFilledQuad(
					x1, y1, x2, y2, x3, y3, x4, y4,
					red, green, blue, alpha);
		}

		void addTextured(GLTexture texture,
				float x1, float y1, float x2, float y2,
				float x3, float y3, float x4, float y4,
				float u0, float v0, float u1, float v1,
				float red, float green, float blue, float alpha,
				int pickId, GLMouseTarget mouseTarget,
				float originX, float originY) {
			preparePending(PendingType.TEXTURE, texture, 0,
					pickId, mouseTarget, originX, originY);
			pendingVertices.appendTexturedQuad(
					x1, y1, x2, y2, x3, y3, x4, y4,
					u0, v0, u1, v1, red, green, blue, alpha);
		}

		void addText(GLTexture atlas, float pixelRange, float[] vertices,
				int pickId, GLMouseTarget mouseTarget,
				float originX, float originY) {
			preparePending(PendingType.TEXT, atlas, pixelRange,
					pickId, mouseTarget, originX, originY);
			pendingVertices.append(vertices);
		}

		void addUI(GLTexture texture, float[] instances,
				int pickId, GLMouseTarget mouseTarget,
				float originX, float originY) {
			if (instances == null || instances.length == 0)
				return;
			TargetReference target = TargetReference.of(mouseTarget);
			add(renderer -> renderer.addUI(
					texture, instances, pickId, target.get(), originX, originY));
		}

		void beginLayer(Command command) {
			add(command);
			openLayers++;
		}

		void endLayer(Command command) {
			if (openLayers == 0)
				throw new IllegalStateException(
						"A recording cannot close a layer opened outside the recording");
			add(command);
			openLayers--;
		}

		void beginClip(Command command) {
			add(command);
			openClips++;
		}

		void endClip(Command command) {
			if (openClips == 0)
				throw new IllegalStateException(
						"A recording cannot close a clip opened outside the recording");
			add(command);
			openClips--;
		}

		void beginParameters(GLRenderParameters parameters) {
			add(renderer -> renderer.pushRecordingParameters(parameters));
			openParameterRegions++;
		}

		void endParameters() {
			if (openParameterRegions == 0)
				throw new IllegalStateException("No recording parameter region is open");
			add(GLRenderer2D::popRecordingParameters);
			openParameterRegions--;
		}

		int openLayers() {
			return openLayers;
		}

		int openClips() {
			return openClips;
		}

		GLRecording finish() {
			if (openLayers != 0)
				throw new IllegalStateException("Recording ended with " + openLayers
						+ " unclosed layer" + (openLayers == 1 ? "" : "s"));
			if (openClips != 0)
				throw new IllegalStateException("Recording ended with " + openClips
						+ " unclosed clip" + (openClips == 1 ? "" : "s"));
			if (openParameterRegions != 0)
				throw new IllegalStateException("Recording ended with "
						+ openParameterRegions + " unclosed parameter region"
						+ (openParameterRegions == 1 ? "" : "s"));
			flushPending();
			return new GLRecording(owner, initialState, commands);
		}

		private void preparePending(PendingType type, GLTexture texture,
				float pixelRange, int pickId, GLMouseTarget target,
				float originX, float originY) {
			if (!isCompatible(type, texture, pixelRange,
					pickId, target, originX, originY)) {
				flushPending();
				pendingType = type;
				pendingVertices = new VertexAccumulator();
				pendingTexture = texture;
				pendingPixelRange = pixelRange;
				pendingPickId = pickId;
				pendingTarget = target;
				pendingOriginX = originX;
				pendingOriginY = originY;
			}
		}

		private boolean isCompatible(PendingType type, GLTexture texture,
				float pixelRange, int pickId, GLMouseTarget target,
				float originX, float originY) {
			if (pendingType != type || pendingTexture != texture
					|| (type == PendingType.TEXT
							&& Float.compare(pendingPixelRange, pixelRange) != 0)
					|| pendingPickId != pickId || pendingTarget != target)
				return false;
			if (pickId == GLContext2D.NO_PICK_ID && target == null)
				return true;
			return Float.compare(pendingOriginX, originX) == 0
					&& Float.compare(pendingOriginY, originY) == 0;
		}

		private void flushPending() {
			if (pendingType == null)
				return;
			float[] vertices = pendingVertices.toArray();
			GLTexture texture = pendingTexture;
			float pixelRange = pendingPixelRange;
			int pickId = pendingPickId;
			TargetReference target = TargetReference.of(pendingTarget);
			float originX = pendingOriginX;
			float originY = pendingOriginY;
			switch (pendingType) {
			case FILLED -> commands.add(renderer -> renderer.addFilled(
					vertices, pickId, target.get(), originX, originY));
			case TEXTURE -> commands.add(renderer -> renderer.addTextured(
					texture, vertices, pickId, target.get(), originX, originY));
			case TEXT -> commands.add(renderer -> renderer.addText(
					texture, pixelRange, vertices,
					pickId, target.get(), originX, originY));
			}
			pendingType = null;
			pendingVertices = null;
			pendingTexture = null;
			pendingTarget = null;
		}
	}

	private static final class TargetReference {
		private static final TargetReference NONE = new TargetReference(null);
		private final WeakReference<GLMouseTarget> reference;

		private TargetReference(GLMouseTarget target) {
			reference = target == null ? null : new WeakReference<>(target);
		}

		static TargetReference of(GLMouseTarget target) {
			return target == null ? NONE : new TargetReference(target);
		}

		GLMouseTarget get() {
			return reference == null ? null : reference.get();
		}
	}

	private enum PendingType {
		FILLED,
		TEXTURE,
		TEXT
	}

	private static final class VertexAccumulator {
		private float[] values = new float[256];
		private int size;

		void append(float[] vertices) {
			ensureCapacity(vertices.length);
			System.arraycopy(vertices, 0, values, size, vertices.length);
			size += vertices.length;
		}

		void appendFilledQuad(
				float x1, float y1, float x2, float y2,
				float x3, float y3, float x4, float y4,
				float red, float green, float blue, float alpha) {
			ensureCapacity(36);
			putFilledVertex(x1, y1, red, green, blue, alpha);
			putFilledVertex(x2, y2, red, green, blue, alpha);
			putFilledVertex(x3, y3, red, green, blue, alpha);
			putFilledVertex(x1, y1, red, green, blue, alpha);
			putFilledVertex(x3, y3, red, green, blue, alpha);
			putFilledVertex(x4, y4, red, green, blue, alpha);
		}

		void appendTexturedQuad(
				float x1, float y1, float x2, float y2,
				float x3, float y3, float x4, float y4,
				float u0, float v0, float u1, float v1,
				float red, float green, float blue, float alpha) {
			ensureCapacity(48);
			putTexturedVertex(x1, y1, u0, v0, red, green, blue, alpha);
			putTexturedVertex(x2, y2, u1, v0, red, green, blue, alpha);
			putTexturedVertex(x3, y3, u1, v1, red, green, blue, alpha);
			putTexturedVertex(x1, y1, u0, v0, red, green, blue, alpha);
			putTexturedVertex(x3, y3, u1, v1, red, green, blue, alpha);
			putTexturedVertex(x4, y4, u0, v1, red, green, blue, alpha);
		}

		float[] toArray() {
			return Arrays.copyOf(values, size);
		}

		private void putFilledVertex(float x, float y,
				float red, float green, float blue, float alpha) {
			values[size++] = x;
			values[size++] = y;
			values[size++] = red;
			values[size++] = green;
			values[size++] = blue;
			values[size++] = alpha;
		}

		private void putTexturedVertex(float x, float y, float u, float v,
				float red, float green, float blue, float alpha) {
			values[size++] = x;
			values[size++] = y;
			values[size++] = u;
			values[size++] = v;
			values[size++] = red;
			values[size++] = green;
			values[size++] = blue;
			values[size++] = alpha;
		}

		private void ensureCapacity(int additionalValues) {
			int required = size + additionalValues;
			if (required <= values.length)
				return;
			int capacity = values.length;
			while (capacity < required)
				capacity = Math.max(capacity * 2, required);
			values = Arrays.copyOf(values, capacity);
		}
	}
}
