package com.vingame.glswing.demo;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Locale;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLFrame;
import com.vingame.glswing.component.GLFrameProfile;
import com.vingame.glswing.rendering.GLAlphaMode;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLCheckBox;
import com.vingame.glswing.ui.GLLabel;

/** Interactive end-to-end benchmark for the 2.5D sprite pipeline. */
public final class SpritePipelineBenchmarkDemo {
    private static final int[] COUNTS = { 100, 1_000, 10_000, 50_000 };

    private SpritePipelineBenchmarkDemo() { }

    public static void main(String[] args) {
        GLDemoErrorReporter.install("Sprite Pipeline Benchmark");
        SwingUtilities.invokeLater(SpritePipelineBenchmarkDemo::show);
    }

    private static void show() {
        GLFrame.RenderingSettings settings = GLFrame.RenderingSettings.DEFAULT
                .withVSync(false).withTargetFps(1_000);
        GLFrame frame = new GLFrame("GLSwingNext - Sprite Pipeline Benchmark",
                settings);
        frame.setGLClearColor(new Color(12, 17, 27));
        frame.setMinimumSize(new Dimension(900, 560));
        frame.setSize(1280, 760);
        frame.setLocationRelativeTo(null);

        BenchmarkSurface surface = new BenchmarkSurface();
        surface.setBounds(0, 0, 1280, 720);
        frame.addGL(surface);

        GLButton count = new GLButton(countText(surface.objectCount));
        count.setBounds(18, 16, 142, 28);
        count.addActionListener(event -> {
            surface.objectCount = nextCount(surface.objectCount);
            count.setText(countText(surface.objectCount));
        });
        frame.addGL(count);

        GLButton channels = new GLButton(surface.channels.label);
        channels.setBounds(170, 16, 260, 28);
        channels.addActionListener(event -> {
            surface.channels = surface.channels.next();
            channels.setText(surface.channels.label);
        });
        frame.addGL(channels);

        GLButton alpha = new GLButton("Alpha: " + surface.alphaMode);
        alpha.setBounds(440, 16, 154, 28);
        alpha.addActionListener(event -> {
            surface.alphaMode = nextAlpha(surface.alphaMode);
            alpha.setText("Alpha: " + surface.alphaMode);
        });
        frame.addGL(alpha);

        GLCheckBox movement = new GLCheckBox("Bewegung", surface.moving);
        movement.setBounds(610, 16, 120, 28);
        movement.addActionListener(event -> surface.moving = movement.isSelected());
        frame.addGL(movement);

        GLCheckBox picking = new GLCheckBox("Picking", surface.picking);
        picking.setBounds(740, 16, 105, 28);
        picking.addActionListener(event -> surface.picking = picking.isSelected());
        frame.addGL(picking);

        GLLabel statusPrimary = new GLLabel("Performance-Messung startet ...");
        statusPrimary.setBounds(18, 50, 1220, 20);
        frame.addGL(statusPrimary);
        GLLabel statusSecondary = new GLLabel(
                "CPU- und GPU-Timer werden ohne blockierendes Readback gesammelt.");
        statusSecondary.setBounds(18, 72, 1220, 20);
        frame.addGL(statusSecondary);

        frame.getGLCanvas().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                surface.setSize(Math.max(1, frame.getGLCanvas().getWidth()),
                        Math.max(1, frame.getGLCanvas().getHeight()));
            }
        });

        Timer statusTimer = new Timer(250, event -> updateStatus(
                frame, surface, statusPrimary, statusSecondary));
        statusTimer.start();
        frame.closedFuture().whenComplete((unused, error) -> statusTimer.stop());
        frame.setVisible(true);
    }

    private static void updateStatus(GLFrame frame, BenchmarkSurface surface,
            GLLabel primary, GLLabel secondary) {
        GLFrameProfile profile = frame.getFrameProfile();
        primary.setText(String.format(Locale.ROOT,
                "%,d · %s · %s · %s · %s | %.0f FPS | E2E %.2f ms | CPU Szene %.2f ms | Sprite-API %.2f ms",
                surface.objectCount, surface.channels.label, surface.alphaMode,
                surface.moving ? "bewegt" : "statisch",
                surface.picking ? "Picking an" : "Picking aus",
                frame.getFramesPerSecond(), profile.endToEndMillis(),
                profile.sceneCpuMillis(), surface.lastApiMillis));
        secondary.setText(String.format(Locale.ROOT,
                "GPU Szene %.2f ms | Present CPU/GPU %.2f/%.2f ms | Backend %.2f ms | %d Drawcalls | MSAA %dx | Render %.0f%%",
                profile.sceneGpuMillis(), profile.presentCpuMillis(),
                profile.presentGpuMillis(), profile.backendRemainderMillis(),
                frame.getLastDrawCallCount(), frame.getActualAntialiasingSamples(),
                frame.getRenderResolutionScale() * 100));
    }

    private static int nextCount(int current) {
        for (int index = 0; index < COUNTS.length; index++)
            if (COUNTS[index] == current)
                return COUNTS[(index + 1) % COUNTS.length];
        return COUNTS[0];
    }

    private static String countText(int count) {
        return String.format(Locale.ROOT, "Anzahl: %,d", count);
    }

    private static GLAlphaMode nextAlpha(GLAlphaMode current) {
        GLAlphaMode[] values = GLAlphaMode.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private static final class BenchmarkSurface extends GLContainer {
        private static final float CONTENT_TOP = 102;
        private final SpriteBenchmarkWorkload workload =
                new SpriteBenchmarkWorkload();
        private final long started = System.nanoTime();
        private volatile int objectCount = 1_000;
        private volatile SpriteBenchmarkWorkload.Channels channels =
                SpriteBenchmarkWorkload.Channels.LIT;
        private volatile GLAlphaMode alphaMode = GLAlphaMode.CUTOUT;
        private volatile boolean moving;
        private volatile boolean picking;
        private volatile double lastApiMillis;

        @Override
        protected void record(GLContext2D context) {
            // The workload is intentionally submitted in render(), not cached.
        }

        @Override
        protected void render(GLContext2D context) {
            long start = System.nanoTime();
            float seconds = (System.nanoTime() - started) / 1_000_000_000f;
            workload.draw(context, objectCount, channels, alphaMode,
                    moving, picking, seconds, 0, CONTENT_TOP, getWidth(),
                    Math.max(1, getHeight() - CONTENT_TOP), true);
            lastApiMillis = (System.nanoTime() - start) / 1_000_000.0;
            repaintGL();
        }

        @Override
        protected boolean isMouseTargetEnabled() {
            return false;
        }
    }
}
