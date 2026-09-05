package com.vingame.glswing.demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.util.FPSAnimator;
import com.vingame.glswing.component.GLSwingCanvas;
import com.vingame.glswing.component.GLSwingContainerCanvas;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFeedbackBuffer;
import com.vingame.glswing.rendering.GLRenderTargetOptions;
import com.vingame.glswing.rendering.ShaderProgram;

/** Binary cellular automata entirely updated through two persistent GPU targets. */
public final class GameOfLifeDemo {

	private static final int GRID_SIZE = 256;
	private static final int MAX_FPS = 60;
	private static final long SIMULATION_STEP_NANOS = 125_000_000L;

	private GameOfLifeDemo() {
	}

	public static void main(String[] args) {
		GLDemoErrorReporter.install("GPU Game of Life Demo");
		SwingUtilities.invokeLater(GameOfLifeDemo::showWindow);
	}

	private static void showWindow() {
		if (!GLSwingCanvas.hasRequiredAwtModuleAccess()) {
			System.err.println("GameOfLifeDemo benoetigt diese VM-Argumente:");
			System.err.println(GLSwingCanvas.REQUIRED_WINDOWS_VM_ARGUMENTS);
			return;
		}

		LifeCanvas canvas = new LifeCanvas();
		canvas.setPreferredSize(new Dimension(900, 720));
		canvas.setFocusable(true);
		JLabel status = new JLabel("", SwingConstants.CENTER);
		status.setPreferredSize(new Dimension(900, 28));

		JFrame frame = new JFrame("GLSwing - GPU Game of Life");
		frame.setLayout(new BorderLayout());
		frame.add(canvas, BorderLayout.CENTER);
		frame.add(status, BorderLayout.SOUTH);
		frame.pack();
		frame.setLocationRelativeTo(null);

		Timer repaintTimer = new Timer(1000 / MAX_FPS, event -> canvas.repaintGL());
		Timer statusTimer = new Timer(250, event -> status.setText(String.format(
				"%.0f/%d FPS | %d Drawcalls | %s (%dx%d) | Zoom %.0f%% | 1/2 Regel, R neu, C leer, Leertaste: %s",
				canvas.getFramesPerSecond(), MAX_FPS,
				canvas.getLastDrawCallCount(), canvas.getRule().name(),
				canvas.getRule().kernelSize(), canvas.getRule().kernelSize(),
				canvas.getZoom() * 100f,
				canvas.isPaused() ? "Weiter" : "Pause")));
		FPSAnimator animator = new FPSAnimator(canvas, MAX_FPS, true);
		GLDemoErrorReporter.attach(animator);
		AtomicBoolean closing = new AtomicBoolean();
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent event) {
				if (!closing.compareAndSet(false, true))
					return;
				repaintTimer.stop();
				statusTimer.stop();
				Thread shutdown = new Thread(() -> {
					animator.stop();
					SwingUtilities.invokeLater(frame::dispose);
				}, "game-of-life-stop");
				shutdown.setDaemon(true);
				shutdown.start();
			}
		});
		MouseAdapter mouse = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				canvas.requestFocusInWindow();
				if (SwingUtilities.isLeftMouseButton(event)) {
					canvas.queueCellAt(event.getX(), event.getY());
				}
			}

			@Override
			public void mouseDragged(MouseEvent event) {
				if ((event.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0)
					canvas.queueCellAt(event.getX(), event.getY());
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent event) {
				canvas.zoomAt(event.getX(), event.getY(), event.getPreciseWheelRotation());
			}
		};
		canvas.addMouseListener(mouse);
		canvas.addMouseMotionListener(mouse);
		canvas.addMouseWheelListener(mouse);
		canvas.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent event) {
				switch (event.getKeyCode()) {
					case KeyEvent.VK_SPACE -> canvas.togglePaused();
					case KeyEvent.VK_1 -> canvas.selectRule(CellularRule.CONWAY);
					case KeyEvent.VK_2 -> canvas.selectRule(CellularRule.BOSCO);
					case KeyEvent.VK_R -> canvas.randomize();
					case KeyEvent.VK_C -> canvas.clearCells();
					default -> { }
				}
			}
		});
		frame.setVisible(true);
		repaintTimer.start();
		statusTimer.start();
		animator.start();
	}

	private static final class LifeCanvas extends GLSwingContainerCanvas {
		private static final long serialVersionUID = 1L;
		private final GLFeedbackBuffer life = new GLFeedbackBuffer(
				GRID_SIZE, GRID_SIZE,
				GLRenderTargetOptions.repeatingPixelArt());
		private ShaderProgram lifeShader;
		private ShaderProgram largerLifeShader;
		private volatile CellularRule selectedRule = CellularRule.CONWAY;
		private CellularRule activeRule = CellularRule.CONWAY;
		private final AtomicReference<ResetRequest> pendingReset =
				new AtomicReference<>(new ResetRequest(
						CellularRule.CONWAY, 0x474c5357494e47L));
		private final AtomicBoolean clearRequested = new AtomicBoolean();
		private volatile boolean paused;
		private final ConcurrentLinkedQueue<Integer> pendingCells = new ConcurrentLinkedQueue<>();
		private volatile float displayX;
		private volatile float displayY;
		private volatile float displaySize;
		private volatile float zoom = 1f;
		private volatile float viewCenterX = 0.5f;
		private volatile float viewCenterY = 0.5f;
		private long lastStepNanos;

		LifeCanvas() {
			super(4, 1);
		}

		void togglePaused() {
			paused = !paused;
			repaintGL();
		}

		boolean isPaused() {
			return paused;
		}

		CellularRule getRule() {
			return selectedRule;
		}

		void selectRule(CellularRule rule) {
			selectedRule = rule;
			pendingReset.set(new ResetRequest(rule, System.nanoTime()));
			repaintGL();
		}

		void randomize() {
			pendingReset.set(new ResetRequest(selectedRule, System.nanoTime()));
			repaintGL();
		}

		void clearCells() {
			clearRequested.set(true);
			repaintGL();
		}

		float getZoom() {
			return zoom;
		}

		void zoomAt(int mouseX, int mouseY, double wheelRotation) {
			float oldSize = displaySize;
			float oldZoom = zoom;
			if (oldSize <= 0f || oldZoom <= 0f)
				return;
			float nextZoom = (float) Math.max(1d, Math.min(32d,
					oldZoom * Math.pow(1.18d, -wheelRotation)));
			if (Math.abs(nextZoom - oldZoom) < 0.0001f)
				return;

			float cellX = (mouseX - displayX) / oldSize;
			float cellY = (mouseY - displayY) / oldSize;
			float nextSize = oldSize * nextZoom / oldZoom;
			float nextCenterX = cellX - (mouseX - getWidth() * 0.5f) / nextSize;
			float nextCenterY = cellY - (mouseY - getHeight() * 0.5f) / nextSize;
			float halfVisible = 0.5f / nextZoom;
			viewCenterX = Math.max(halfVisible, Math.min(1f - halfVisible, nextCenterX));
			viewCenterY = Math.max(halfVisible, Math.min(1f - halfVisible, nextCenterY));
			zoom = nextZoom;
			repaintGL();
		}

		void queueCellAt(int mouseX, int mouseY) {
			float x = displayX;
			float y = displayY;
			float size = displaySize;
			if (size <= 0f || mouseX < x || mouseY < y
					|| mouseX >= x + size || mouseY >= y + size)
				return;
			int cellX = Math.min(GRID_SIZE - 1,
					Math.max(0, (int) ((mouseX - x) * GRID_SIZE / size)));
			int cellY = Math.min(GRID_SIZE - 1,
					Math.max(0, (int) ((mouseY - y) * GRID_SIZE / size)));
			pendingCells.offer(cellY * GRID_SIZE + cellX);
			repaintGL();
		}

		@Override
		protected void initializeContainer(GL3 gl) {
			lifeShader = ShaderProgram.fromResources(gl,
					"/com/vingame/glswing/shaders/texture.vert",
					"/com/vingame/glswing/demo/shaders/game_of_life.frag");
			largerLifeShader = ShaderProgram.fromResources(gl,
					"/com/vingame/glswing/shaders/texture.vert",
					"/com/vingame/glswing/demo/shaders/larger_than_life.frag");
		}

		@Override
		protected void renderContainer(GLContext2D context) {
			context.clear(new Color(14, 18, 27));
			ResetRequest reset = pendingReset.getAndSet(null);
			if (reset != null) {
				activeRule = reset.rule();
				seedRandomCells(context, reset.seed());
				clearRequested.set(false);
				pendingCells.clear();
				lastStepNanos = System.nanoTime();
			} else if (clearRequested.getAndSet(false)) {
				life.clear(context, Color.BLACK);
				pendingCells.clear();
				lastStepNanos = System.nanoTime();
			}

			long now = System.nanoTime();
			if (!paused && now - lastStepNanos >= SIMULATION_STEP_NANOS) {
				life.update(context, () -> {
					if (activeRule == CellularRule.CONWAY) {
						context.setTextureShader(lifeShader);
						context.setTextureUniform("uTexelSize",
								1f / GRID_SIZE, 1f / GRID_SIZE);
					} else {
						context.setTextureShader(largerLifeShader);
						activeRule.applyUniforms(context, GRID_SIZE, GRID_SIZE);
					}
					context.drawRenderTarget(life.current(),
							0, 0, GRID_SIZE, GRID_SIZE);
				});
				lastStepNanos = now;
			}
			Integer firstCell = pendingCells.poll();
			if (firstCell != null) {
				context.beginRenderTarget(life.current());
				try {
					Integer cell = firstCell;
					do {
						int cellX = cell % GRID_SIZE;
						int cellY = cell / GRID_SIZE;
						context.fillRect(cellX, cellY, 1f, 1f, Color.WHITE);
					} while ((cell = pendingCells.poll()) != null);
				} finally {
					context.endRenderTarget();
				}
			}

			float availableWidth = Math.max(1, getWidth() - 48);
			float availableHeight = Math.max(1, getHeight() - 48);
			float size = Math.min(availableWidth, availableHeight) * zoom;
			float x = getWidth() * 0.5f - viewCenterX * size;
			float y = getHeight() * 0.5f - viewCenterY * size;
			displayX = x;
			displayY = y;
			displaySize = size;
			context.fillUIRoundRect(x - 8, y - 8,
					size + 16, size + 16, 12, new Color(31, 40, 56));
			context.drawRenderTarget(life.current(), x, y, size, size,
					new Color(105, 225, 174));
			context.drawUIRoundRect(x - 8, y - 8,
					size + 16, size + 16, 12, 2, new Color(77, 96, 124));
		}

		@Override
		protected void disposeContainerResources(GL3 gl) {
			life.close();
			if (lifeShader != null) {
				lifeShader.dispose(gl);
				lifeShader = null;
			}
			if (largerLifeShader != null) {
				largerLifeShader.dispose(gl);
				largerLifeShader = null;
			}
		}

		private void seedRandomCells(GLContext2D context, long seed) {
			life.clear(context, Color.BLACK);
			float density = activeRule == CellularRule.BOSCO ? 0.30f : 0.29f;
			Random random = new Random(seed);
			context.withRenderTarget(life.current(), () -> {
				for (int y = 0; y < GRID_SIZE; y++) {
					for (int x = 0; x < GRID_SIZE; x++) {
						if (random.nextFloat() < density)
							context.fillRect(x, y, 1, 1, Color.WHITE);
					}
				}
			});
		}

		private record ResetRequest(CellularRule rule, long seed) {
		}
	}
}
