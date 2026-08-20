package com.vingame.glswing.demo;

import java.awt.Color;
import java.awt.image.BufferedImage;

import com.vingame.glswing.rendering.GLAlphaMode;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLDepthState;
import com.vingame.glswing.rendering.GLImage;
import com.vingame.glswing.rendering.GLLighting2D;
import com.vingame.glswing.rendering.GLSprite;

/** Shared deterministic workload used by the interactive and offscreen benchmark. */
final class SpriteBenchmarkWorkload {
    enum Channels {
        COLOR("Farbe"),
        DEPTH("Farbe + Tiefe"),
        LIT("Farbe + Tiefe + Normal/Licht");

        final String label;

        Channels(String label) {
            this.label = label;
        }

        Channels next() {
            Channels[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private static final int SOURCE_SIZE = 96;
    private static final Color[] COLORS = {
            new Color(236, 76, 92),
            new Color(68, 155, 245),
            new Color(68, 210, 151),
            new Color(246, 184, 70)
    };
    private static final GLLighting2D LIGHTING = new GLLighting2D(
            new Color(225, 235, 255), 0.25f,
            new GLLighting2D.DirectionalLight(-0.42f, -0.36f, 0.83f,
                    Color.WHITE, 1.05f));

    private final GLSprite[][][] sprites =
            new GLSprite[Channels.values().length][GLAlphaMode.values().length][COLORS.length];
    private float[] x = new float[0];
    private float[] y = new float[0];
    private float[] depth = new float[0];
    private float cellWidth;
    private float cellHeight;
    private float spriteWidth;
    private float spriteHeight;
    private int preparedCount = -1;
    private float preparedX;
    private float preparedY;
    private float preparedWidth;
    private float preparedHeight;

    SpriteBenchmarkWorkload() {
        buildSprites();
    }

    void draw(GLContext2D context, int objectCount, Channels channels,
            GLAlphaMode alphaMode, boolean moving, boolean picking,
            float seconds, float areaX, float areaY, float areaWidth,
            float areaHeight, boolean restoreState) {
        prepare(objectCount, areaX, areaY, areaWidth, areaHeight);

        boolean previousColorOutput = restoreState
                && context.isColorRenderingEnabled();
        boolean previousPickingOutput = restoreState
                && context.isPickingRenderingEnabled();
        GLDepthState previousDepthState = restoreState
                ? context.getDepthState() : null;
        float previousDepth = restoreState ? context.getDepth() : 0;
        GLLighting2D previousLighting = restoreState
                ? context.getLighting() : null;
        context.setOutputMask(true, picking);
        context.clearDepth();
        context.setLighting(LIGHTING);

        GLSprite[] variants = sprites[channels.ordinal()][alphaMode.ordinal()];
        float movementX = cellWidth * 0.18f;
        float movementY = cellHeight * 0.18f;
        for (int index = 0; index < objectCount; index++) {
            float drawX = x[index];
            float drawY = y[index];
            if (moving) {
                float phase = seconds * 2.1f + index * 0.173f;
                drawX += (float) Math.sin(phase) * movementX;
                drawY += (float) Math.cos(phase * 0.73f) * movementY;
            }
            if (picking)
                context.setPickId(index + 1);
            context.drawSprite(variants[index & 3], drawX, drawY,
                    spriteWidth, spriteHeight, Color.WHITE, depth[index]);
        }

        context.clearPickId();
        if (restoreState) {
            context.setDepthState(previousDepthState);
            context.setDepth(previousDepth);
            context.setLighting(previousLighting);
            context.setOutputMask(previousColorOutput, previousPickingOutput);
        }
    }

    private void prepare(int objectCount, float areaX, float areaY,
            float areaWidth, float areaHeight) {
        if (objectCount == preparedCount && areaX == preparedX
                && areaY == preparedY && areaWidth == preparedWidth
                && areaHeight == preparedHeight)
            return;
        if (x.length < objectCount) {
            x = new float[objectCount];
            y = new float[objectCount];
            depth = new float[objectCount];
        }
        double aspect = Math.max(0.1, areaWidth / Math.max(1, areaHeight));
        int columns = Math.max(1,
                (int) Math.ceil(Math.sqrt(objectCount * aspect)));
        int rows = Math.max(1, (objectCount + columns - 1) / columns);
        cellWidth = areaWidth / columns;
        cellHeight = areaHeight / rows;
        spriteWidth = Math.max(0.5f, cellWidth * 0.84f);
        spriteHeight = Math.max(0.5f, cellHeight * 0.84f);
        float insetX = (cellWidth - spriteWidth) * 0.5f;
        float insetY = (cellHeight - spriteHeight) * 0.5f;
        for (int index = 0; index < objectCount; index++) {
            x[index] = areaX + (index % columns) * cellWidth + insetX;
            y[index] = areaY + (index / columns) * cellHeight + insetY;
            depth[index] = 0.13f + ((index * 37) % 700) / 1000f;
        }
        preparedCount = objectCount;
        preparedX = areaX;
        preparedY = areaY;
        preparedWidth = areaWidth;
        preparedHeight = areaHeight;
    }

    private void buildSprites() {
        GLImage depthMap = GLImage.of(createDepthMap());
        GLImage normalMap = GLImage.of(createNormalMap());
        for (GLAlphaMode alphaMode : GLAlphaMode.values())
            for (int colorIndex = 0; colorIndex < COLORS.length; colorIndex++) {
                GLImage colorMap = GLImage.of(createColorMap(
                        COLORS[colorIndex], alphaMode));
                sprites[Channels.COLOR.ordinal()][alphaMode.ordinal()][colorIndex] =
                        base(colorMap, alphaMode)
                                .depthState(GLDepthState.DISABLED)
                                .build();
                sprites[Channels.DEPTH.ordinal()][alphaMode.ordinal()][colorIndex] =
                        base(colorMap, alphaMode)
                                .depthMap(depthMap)
                                .depthRange(-0.025f, 0.075f)
                                .build();
                sprites[Channels.LIT.ordinal()][alphaMode.ordinal()][colorIndex] =
                        base(colorMap, alphaMode)
                                .depthMap(depthMap)
                                .normalMap(normalMap)
                                .depthRange(-0.025f, 0.075f)
                                .lit(true)
                                .build();
            }
    }

    private static GLSprite.Builder base(GLImage colorMap,
            GLAlphaMode alphaMode) {
        return GLSprite.builder(colorMap)
                .alphaMode(alphaMode)
                .alphaCutoff(0.35f);
    }

    private static BufferedImage createColorMap(Color color,
            GLAlphaMode alphaMode) {
        BufferedImage image = new BufferedImage(SOURCE_SIZE, SOURCE_SIZE,
                BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < SOURCE_SIZE; py++)
            for (int px = 0; px < SOURCE_SIZE; px++) {
                float nx = normalized(px);
                float ny = normalized(py);
                float radius = (float) Math.sqrt(nx * nx + ny * ny);
                int alpha;
                if (alphaMode == GLAlphaMode.OPAQUE)
                    alpha = 255;
                else {
                    float edgePixels = (1 - radius) * SOURCE_SIZE * 0.5f;
                    alpha = Math.round(clamp01(edgePixels) * 255);
                }
                float shade = 0.78f + clamp01(1 - radius) * 0.22f;
                image.setRGB(px, py, new Color(
                        clampByte(Math.round(color.getRed() * shade)),
                        clampByte(Math.round(color.getGreen() * shade)),
                        clampByte(Math.round(color.getBlue() * shade)), alpha).getRGB());
            }
        return image;
    }

    private static BufferedImage createDepthMap() {
        BufferedImage image = new BufferedImage(SOURCE_SIZE, SOURCE_SIZE,
                BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < SOURCE_SIZE; py++)
            for (int px = 0; px < SOURCE_SIZE; px++) {
                float nx = normalized(px);
                float ny = normalized(py);
                float nz = (float) Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny));
                int value = Math.round(nz * 255);
                image.setRGB(px, py, new Color(value, value, value).getRGB());
            }
        return image;
    }

    private static BufferedImage createNormalMap() {
        BufferedImage image = new BufferedImage(SOURCE_SIZE, SOURCE_SIZE,
                BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < SOURCE_SIZE; py++)
            for (int px = 0; px < SOURCE_SIZE; px++) {
                float nx = normalized(px);
                float ny = normalized(py);
                float lengthSquared = nx * nx + ny * ny;
                float nz = (float) Math.sqrt(Math.max(0, 1 - lengthSquared));
                float inverseLength = 1f / (float) Math.sqrt(
                        Math.max(0.0001f, lengthSquared + nz * nz));
                image.setRGB(px, py, new Color(
                        clampByte(Math.round((nx * inverseLength * 0.5f + 0.5f) * 255)),
                        clampByte(Math.round((ny * inverseLength * 0.5f + 0.5f) * 255)),
                        clampByte(Math.round(nz * inverseLength * 255))).getRGB());
            }
        return image;
    }

    private static float normalized(int coordinate) {
        return (coordinate + 0.5f) / SOURCE_SIZE * 2 - 1;
    }

    private static float clamp01(float value) {
        return Math.max(0, Math.min(1, value));
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
