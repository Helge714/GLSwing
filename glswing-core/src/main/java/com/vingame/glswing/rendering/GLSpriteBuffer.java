package com.vingame.glswing.rendering;

import java.awt.Color;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.vingame.glswing.input.GLMouseTarget;

/**
 * Immutable, renderer-independent recording of sprite instances.
 * <p>
 * A sprite buffer keeps its compact CPU description so that it can be uploaded
 * lazily into every renderer/context in which it is used. The uploaded instance
 * data stays GPU-resident until this buffer or the renderer is closed.
 * </p>
 */
public final class GLSpriteBuffer implements AutoCloseable {
    static final class Entry {
        final GLMaterial2D material;
        final float x;
        final float y;
        final float width;
        final float height;
        final Color tint;
        final float depth;
        final GLTransform localTransform;
        final int pickId;
        final WeakReference<GLMouseTarget> mouseTarget;
        final float mouseOriginX;
        final float mouseOriginY;
        final int sectionBarrier;

        Entry(GLMaterial2D material,
                float x, float y, float width, float height,
                Color tint, float depth,
                GLTransform localTransform,
                int pickId, GLMouseTarget mouseTarget,
                float mouseOriginX, float mouseOriginY, int sectionBarrier) {
            this.material = Objects.requireNonNull(material, "material");
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.tint = Objects.requireNonNull(tint, "tint");
            this.depth = depth;
            this.localTransform = Objects.requireNonNull(localTransform, "localTransform").copy();
            this.pickId = pickId;
            this.mouseTarget = mouseTarget != null ? new WeakReference<>(mouseTarget) : null;
            this.mouseOriginX = mouseOriginX;
            this.mouseOriginY = mouseOriginY;
            this.sectionBarrier = sectionBarrier;
        }

        GLMouseTarget mouseTarget() {
            return mouseTarget != null ? mouseTarget.get() : null;
        }
    }

    static final class Builder {
        private final List<Entry> entries = new ArrayList<>();
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private boolean finished;
        private int sectionBarrier;

        void add(GLMaterial2D material,
                float x, float y, float width, float height,
                Color tint, float depth,
                GLTransform localTransform,
                int pickId, GLMouseTarget mouseTarget,
                float mouseOriginX, float mouseOriginY) {
            requireOpen();
			requireStatic(material.getColorMap(), "color");
			if (material.hasDepthMap())
				requireStatic(material.getDepthMap(), "depth");
			if (material.hasNormalMap())
				requireStatic(material.getNormalMap(), "normal");
            Entry entry = new Entry(material, x, y, width, height, tint, depth,
                    localTransform, pickId, mouseTarget, mouseOriginX, mouseOriginY,
                    sectionBarrier);
            entries.add(entry);
            include(localTransform.transformX(x, y), localTransform.transformY(x, y));
            include(localTransform.transformX(x + width, y), localTransform.transformY(x + width, y));
            include(localTransform.transformX(x + width, y + height),
                    localTransform.transformY(x + width, y + height));
            include(localTransform.transformX(x, y + height), localTransform.transformY(x, y + height));
        }

        void barrier() {
            requireOpen();
            sectionBarrier++;
        }

        GLSpriteBuffer finish() {
            requireOpen();
            finished = true;
            if (entries.isEmpty()) {
                return new GLSpriteBuffer(List.of(), 0f, 0f, 0f, 0f);
            }
            return new GLSpriteBuffer(List.copyOf(entries), minX, minY, maxX, maxY);
        }

        void cancel() {
            finished = true;
            entries.clear();
        }

        private void include(float x, float y) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        private void requireOpen() {
            if (finished) {
                throw new IllegalStateException("Sprite-buffer recording has already ended");
            }
        }

		private static void requireStatic(GLImage image, String channel) {
			if (image.getOptions().usage() == GLImageOptions.Usage.DYNAMIC)
				throw new IllegalArgumentException("A GPU-resident sprite buffer cannot "
						+ "contain a dynamic " + channel + " image");
		}
    }

    private final List<Entry> entries;
    private final float minX;
    private final float minY;
    private final float maxX;
    private final float maxY;
    private volatile boolean closed;

    private GLSpriteBuffer(List<Entry> entries, float minX, float minY, float maxX, float maxY) {
        this.entries = entries;
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
    }

    /** Number of recorded sprite instances. */
    public int getInstanceCount() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean isClosed() {
        return closed;
    }

    public float getMinX() {
        return minX;
    }

    public float getMinY() {
        return minY;
    }

    public float getMaxX() {
        return maxX;
    }

    public float getMaxY() {
        return maxY;
    }

    List<Entry> entries() {
        return entries;
    }

    @Override
    public void close() {
        closed = true;
    }
}
