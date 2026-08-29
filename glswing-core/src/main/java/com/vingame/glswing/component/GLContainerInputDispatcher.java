package com.vingame.glswing.component;

import java.awt.AWTException;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLInputActions;
import com.vingame.glswing.input.GLInputMap;
import com.vingame.glswing.input.GLKeyTarget;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLMouseTarget;
import com.vingame.glswing.input.GLMouseWheelEvent;
import com.vingame.glswing.input.GLMouseWheelCaptureTarget;
import com.vingame.glswing.input.GLPickResult;
import com.vingame.glswing.input.GLImmediateHoverRegistry;
import com.vingame.glswing.input.GLPointerController;
import com.vingame.glswing.input.GLPointerControllers;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.ui.GLTextEditor;
import com.vingame.glswing.ui.GLMenuMnemonics;

/** Input bridge shared by retained controls hosted on a direct GL canvas. */
final class GLContainerInputDispatcher {

	private final GLSwingContainerCanvas host;
	private final GLPointerController pointerController;
	private GLPickResult hoverPick = GLPickResult.none();
	private GLPickResult capturedPick = GLPickResult.none();
	private GLPickResult clickCandidate = GLPickResult.none();
	private MouseEvent pendingHoverEvent;
	private boolean pendingHoverMoved;
	private boolean hoverReadScheduled;
	private long hoverReadGeneration;
	private volatile MouseEvent postWheelHoverEvent;
	private volatile long postWheelHoverGeneration;
	private int pointerTasksPending;
	private int pressPicksPending;
	private MouseEvent pendingDragEvent;
	private int lastDragX;
	private int lastDragY;
	private boolean pointerWarpRequested;
	private float pointerWarpX;
	private float pointerWarpY;
	private boolean pointerWarpEventPending;
	private int expectedPointerWarpX;
	private int expectedPointerWarpY;
	private Robot pointerRobot;

	GLContainerInputDispatcher(GLSwingContainerCanvas host) {
		this.host = host;
		pointerController = GLPointerControllers.forAwt(host);
		installMouseDispatch();
		installKeyboardDispatch();
	}

	void reset() {
		hoverReadGeneration++;
		pendingHoverEvent = null;
		pendingDragEvent = null;
		hoverReadScheduled = false;
		hoverPick = GLPickResult.none();
		capturedPick = GLPickResult.none();
		clickCandidate = GLPickResult.none();
		pointerWarpEventPending = false;
	}

	private void installMouseDispatch() {
		MouseAdapter dispatcher = new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent event) {
				queueHoverRead(event, false);
			}

			@Override
			public void mouseExited(MouseEvent event) {
				cancelHoverRead();
				if (!hasPressedMouseButton(event))
					cancelPointerCapture(event);
				leaveHover(event);
			}

			@Override
			public void mouseMoved(MouseEvent event) {
				if (!consumePointerWarpEvent(event))
					queueHoverRead(event, true);
			}

			@Override
			public void mousePressed(MouseEvent event) {
				pointerWarpEventPending = false;
				cancelHoverRead();
				clickCandidate = GLPickResult.none();
				host.requestFocusInWindow();
				pressPicksPending++;
				readPointerPick(event, result -> {
					pressPicksPending--;
					dispatchMousePressed(event, result);
					if (pressPicksPending == 0) {
						MouseEvent drag = pendingDragEvent;
						pendingDragEvent = null;
						if (drag != null)
							dispatchMouseDragged(drag);
					}
				});
			}

			@Override
			public void mouseDragged(MouseEvent event) {
				if (consumePointerWarpEvent(event))
					return;
				if (pressPicksPending > 0)
					pendingDragEvent = event;
				else
					dispatchMouseDragged(event);
			}

			@Override
			public void mouseReleased(MouseEvent event) {
				cancelHoverRead();
				// The captured press target must always receive release, but resolving
				// the object below the pointer need not force a synchronous GL frame.
				// display() from the AWT event thread can race the Canvas animator and,
				// depending on the native driver, deadlock or terminate the process.
				readPointerPick(event,
						releaseHit -> dispatchMouseReleased(event, releaseHit));
			}

			@Override
			public void mouseClicked(MouseEvent event) {
				cancelHoverRead();
				queuePointerBarrier(() -> dispatchMouseClicked(event));
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent event) {
				cancelHoverRead();
				readPointerPick(event, result -> {
					if (result.hasTarget()) {
						dispatchMouseWheel(event, result);
						host.repaintColorOnly();
					}
					javax.swing.SwingUtilities.invokeLater(
							() -> requestPostWheelHover(event));
				});
			}
		};
		host.addMouseListener(dispatcher);
		host.addMouseMotionListener(dispatcher);
		host.addMouseWheelListener(dispatcher);
	}

	private static boolean hasPressedMouseButton(MouseEvent event) {
		int buttons = MouseEvent.BUTTON1_DOWN_MASK
				| MouseEvent.BUTTON2_DOWN_MASK | MouseEvent.BUTTON3_DOWN_MASK;
		return (event.getModifiersEx() & buttons) != 0;
	}

	private void dispatchMouseWheel(MouseWheelEvent event, GLPickResult result) {
		GLMouseTarget capture = wheelCaptureTarget(result.getTarget());
		if (capture != null && capture != result.getTarget()) {
			GLMouseWheelEvent captured = new GLMouseWheelEvent(event,
					result.retarget(capture));
			capture.mouseWheelMoved(captured);
			if (captured.isConsumed())
				return;
		}
		GLMouseWheelEvent translated = new GLMouseWheelEvent(event, result);
		GLMouseTarget target = result.getTarget();
		while (target != null) {
			target.mouseWheelMoved(translated);
			if (translated.isConsumed() || !(target instanceof GLContainer child))
				return;
			GLParent parent = child.getGLParent();
			target = parent instanceof GLMouseTarget mouseTarget
					? mouseTarget : null;
		}
	}

	private static GLMouseTarget wheelCaptureTarget(GLMouseTarget target) {
		for (GLMouseTarget current = target; current instanceof GLContainer child;) {
			if (current instanceof GLMouseWheelCaptureTarget)
				return current;
			GLParent parent = child.getGLParent();
			current = parent instanceof GLMouseTarget mouseTarget
					? mouseTarget : null;
		}
		return target instanceof GLMouseWheelCaptureTarget ? target : null;
	}

	private void dispatchMousePressed(MouseEvent event, GLPickResult result) {
		result = attached(result);
		GLMouseTarget hitTarget = result.getTarget();
		host.requestGLFocus(hitTarget instanceof GLKeyTarget keyTarget
				&& keyTarget.isFocusTraversable() ? keyTarget : null);
		updateHover(event, result, false);
		lastDragX = event.getX();
		lastDragY = event.getY();
		capturedPick = result.hasTarget() ? dispatchPress(event, result)
				: GLPickResult.none();
		if (capturedPick.hasTarget())
			host.repaintColorOnly();
	}

	private GLPickResult dispatchPress(MouseEvent event, GLPickResult initial) {
		GLPickResult current = initial;
		GLMouseTarget target = current.getTarget();
		GLMouseEvent translated = mouseEvent(event, current, 0, 0);
		target.mousePressed(translated);
		if (event.getButton() != MouseEvent.BUTTON2)
			return current;
		while (!translated.isConsumed() && target instanceof GLContainer child) {
			GLParent parent = child.getGLParent();
			if (!(parent instanceof GLMouseTarget parentTarget))
				break;
			target = parentTarget;
			current = current.retarget(target);
			translated = mouseEvent(event, current, 0, 0);
			target.mousePressed(translated);
		}
		return current;
	}

	private void dispatchMouseDragged(MouseEvent event) {
		if (!capturedPick.hasTarget())
			return;
		if (!host.isAttachedTarget(capturedPick.getTarget())) {
			capturedPick = GLPickResult.none();
			return;
		}
		GLPickResult current = capturedPick.at(event.getX(), event.getY());
		pointerWarpRequested = false;
		current.getTarget().mouseDragged(mouseEvent(event, current,
				event.getX() - lastDragX, event.getY() - lastDragY));
		if (pointerWarpRequested) {
			float x = Math.max(0, Math.min(host.getWidth() - 1, pointerWarpX));
			float y = Math.max(0, Math.min(host.getHeight() - 1, pointerWarpY));
			capturedPick = capturedPick.at(x, y);
			lastDragX = Math.round(x);
			lastDragY = Math.round(y);
			warpPointer(x, y);
		} else {
			capturedPick = current;
			lastDragX = event.getX();
			lastDragY = event.getY();
		}
		host.repaintColorOnly();
	}

	private void requestPointerWarp(float x, float y) {
		pointerWarpRequested = true;
		pointerWarpX = x;
		pointerWarpY = y;
	}

	private boolean consumePointerWarpEvent(MouseEvent event) {
		if (!pointerWarpEventPending)
			return false;
		pointerWarpEventPending = false;
		if (Math.abs(event.getX() - expectedPointerWarpX) > 2
				|| Math.abs(event.getY() - expectedPointerWarpY) > 2)
			return false;
		lastDragX = event.getX();
		lastDragY = event.getY();
		if (capturedPick.hasTarget())
			capturedPick = capturedPick.at(event.getX(), event.getY());
		return true;
	}

	private void warpPointer(float x, float y) {
		if (!host.isShowing())
			return;
		try {
			Point origin = host.getLocationOnScreen();
			Robot robot = pointerRobot;
			if (robot == null) {
				robot = host.getGraphicsConfiguration() == null
						? new Robot()
						: new Robot(host.getGraphicsConfiguration().getDevice());
				pointerRobot = robot;
			}
			expectedPointerWarpX = Math.round(x);
			expectedPointerWarpY = Math.round(y);
			pointerWarpEventPending = true;
			robot.mouseMove(origin.x + expectedPointerWarpX,
					origin.y + expectedPointerWarpY);
		} catch (AWTException | IllegalComponentStateException
				| SecurityException ignored) {
			pointerWarpEventPending = false;
		}
	}

	private void dispatchMouseReleased(MouseEvent event,
			GLPickResult releaseHit) {
		releaseHit = attached(releaseHit);
		if (!capturedPick.hasTarget()) {
			updateHover(event, releaseHit, false);
			return;
		}
		GLPickResult capturedAtRelease = capturedPick.at(
				event.getX(), event.getY());
		if (host.isAttachedTarget(capturedAtRelease.getTarget())) {
			capturedAtRelease.getTarget().mouseReleased(
					mouseEvent(event, capturedAtRelease, 0, 0));
			clickCandidate = releaseHit.getTarget()
					== capturedAtRelease.getTarget()
							? releaseHit : GLPickResult.none();
		} else {
			clickCandidate = GLPickResult.none();
		}
		capturedPick = GLPickResult.none();
		updateHover(event, releaseHit, false);
		host.repaintColorOnly();
	}

	private void cancelPointerCapture(MouseEvent event) {
		if (!capturedPick.hasTarget())
			return;
		GLPickResult captured = capturedPick.at(event.getX(), event.getY());
		capturedPick = GLPickResult.none();
		clickCandidate = GLPickResult.none();
		pointerWarpRequested = false;
		pointerWarpEventPending = false;
		if (host.isAttachedTarget(captured.getTarget()))
			captured.getTarget().mouseCaptureLost(
					mouseEvent(event, captured, 0, 0));
		host.repaintColorOnly();
	}

	private void dispatchMouseClicked(MouseEvent event) {
		if (clickCandidate.hasTarget()
				&& host.isAttachedTarget(clickCandidate.getTarget())) {
			GLPickResult current = clickCandidate.at(
					event.getX(), event.getY());
			current.getTarget().mouseClicked(mouseEvent(event, current, 0, 0));
		}
		clickCandidate = GLPickResult.none();
		host.repaintColorOnly();
	}

	private void readPointerPick(MouseEvent event,
			Consumer<GLPickResult> completion) {
		pointerTasksPending++;
		GLPickResult immediate = GLImmediateHoverRegistry.resolve(
				host, event.getX(), event.getY());
		if (immediate != null) {
			try {
				completion.accept(attached(immediate));
			} finally {
				pointerTasksPending--;
			}
			return;
		}
		// Never call display()/invoke() from the AWT input thread. The Canvas
		// animator owns the native OpenGL context; a synchronous input-side frame
		// can deadlock with JAWT or the graphics driver. A completed snapshot may
		// trail geometry by one frame, which is preferable and deterministic.
		GLPickResult result = host.latestPickResult(event.getX(), event.getY());
		try {
			completion.accept(result == null
					? GLPickResult.none() : attached(result));
		} finally {
			pointerTasksPending--;
		}
		if (result == null)
			host.repaintColorOnly();
	}

	private void queuePointerBarrier(Runnable completion) {
		completion.run();
	}

	private void queueHoverRead(MouseEvent event, boolean sendMoved) {
		postWheelHoverEvent = null;
		postWheelHoverGeneration++;
		GLPickResult immediate = GLImmediateHoverRegistry.resolve(
				host, event.getX(), event.getY());
		if (immediate != null) {
			hoverReadGeneration++;
			pendingHoverEvent = null;
			pendingHoverMoved = false;
			updateHover(event, attached(immediate), sendMoved);
			return;
		}
		pendingHoverEvent = event;
		pendingHoverMoved = sendMoved;
		if (!hoverReadScheduled)
			startHoverRead();
	}

	private void startHoverRead() {
		MouseEvent event = pendingHoverEvent;
		if (event == null) {
			hoverReadScheduled = false;
			return;
		}
		pendingHoverEvent = null;
		boolean sendMoved = pendingHoverMoved;
		pendingHoverMoved = false;
		hoverReadScheduled = true;
		long generation = hoverReadGeneration;
		GLPickResult result = host.latestPickResult(event.getX(), event.getY());
		completeHoverRead(event, result, sendMoved, generation);
		if (result == null)
			host.repaintColorOnly();
	}

	private void completeHoverRead(MouseEvent event, GLPickResult result,
			boolean sendMoved, long generation) {
		if (result != null && generation == hoverReadGeneration
				&& host.isDisplayable())
			updateHover(event, attached(result), sendMoved);
		hoverReadScheduled = false;
		if (pendingHoverEvent != null)
			startHoverRead();
	}

	private void cancelHoverRead() {
		hoverReadGeneration++;
		pendingHoverEvent = null;
		pendingHoverMoved = false;
		postWheelHoverEvent = null;
		postWheelHoverGeneration++;
	}

	private void requestPostWheelHover(MouseEvent event) {
		postWheelHoverGeneration++;
		postWheelHoverEvent = event;
		host.repaint();
	}

	void pickSnapshotsUpdated() {
		MouseEvent event = postWheelHoverEvent;
		if (event == null)
			return;
		if (host.isPickingDirty() || !host.isLatestPickSnapshotReady()) {
			javax.swing.SwingUtilities.invokeLater(() -> {
				if (postWheelHoverEvent != null && host.isDisplayable())
					host.repaint();
			});
			return;
		}
		long generation = postWheelHoverGeneration;
		GLPickResult result = host.latestPickResult(event.getX(), event.getY());
		if (result == null)
			return;
		postWheelHoverEvent = null;
		javax.swing.SwingUtilities.invokeLater(() -> {
			if (generation == postWheelHoverGeneration && host.isDisplayable())
				updateHover(event, attached(result), false);
		});
	}

	private void installKeyboardDispatch() {
		host.setFocusable(true);
		host.setFocusTraversalKeysEnabled(false);
			host.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent event) {
				GLInputMap inputMap = host.focusedGLTarget() instanceof GLContainer container
						? container.getGLInputMap() : host.getGLInputMap();
				if (GLMenuMnemonics.handleKeyPressed(host, event, inputMap)) {
					host.repaintColorOnly();
					return;
				}
				if (host.focusedGLTarget() instanceof GLTextEditor editor
						&& editor.handleEditingHistoryKeyPressed(event, inputMap)) {
					host.repaintColorOnly();
					return;
				}
				GLHistoryManager history = host.focusedGLTarget()
						instanceof GLContainer container
								? container.getGLHistoryManager()
								: host.getGLHistoryManager();
				if (history != null && history.handleKeyPressed(event, inputMap)) {
					host.repaintColorOnly();
					return;
				}
				if (inputMap.matches(GLInputActions.FOCUS_NEXT, event)
						|| inputMap.matches(GLInputActions.FOCUS_PREVIOUS, event)) {
					focusNext(inputMap.matches(GLInputActions.FOCUS_PREVIOUS, event));
					event.consume();
					return;
				}
				dispatchKey(event, KeyDispatch.PRESSED);
			}

			@Override
			public void keyReleased(KeyEvent event) {
				dispatchKey(event, KeyDispatch.RELEASED);
			}

			@Override
			public void keyTyped(KeyEvent event) {
				dispatchKey(event, KeyDispatch.TYPED);
			}
		});
		host.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent event) {
				host.requestGLFocus(null);
			}
		});
	}

	private void dispatchKey(KeyEvent event, KeyDispatch dispatch) {
		GLKeyTarget target = host.focusedGLTarget();
		if (target == null)
			return;
		if (!(target instanceof GLContainer container)
				|| !host.belongsToCanvas(container)
				|| !target.isFocusTraversable()) {
			host.requestGLFocus(null);
			return;
		}
		GLKeyEvent glEvent = new GLKeyEvent(event);
		switch (dispatch) {
		case PRESSED -> dispatchKeyPressed(target, glEvent);
		case RELEASED -> target.keyReleased(glEvent);
		case TYPED -> target.keyTyped(glEvent);
		}
		host.repaintColorOnly();
	}

	private static void dispatchKeyPressed(GLKeyTarget target,
			GLKeyEvent event) {
		target.keyPressed(event);
		if (event.isConsumed() || !(target instanceof GLContainer container))
			return;
		for (GLParent parent = container.getGLParent();
				parent instanceof GLContainer ancestor;
				parent = ancestor.getGLParent()) {
			if (ancestor instanceof GLKeyTarget keyTarget)
				keyTarget.keyPressed(event);
			if (event.isConsumed())
				return;
		}
	}

	private void focusNext(boolean backwards) {
		List<GLKeyTarget> targets = new ArrayList<>();
		collectFocusable(host, targets);
		if (targets.isEmpty()) {
			host.requestGLFocus(null);
			return;
		}
		int current = targets.indexOf(host.focusedGLTarget());
		int next = backwards
				? (current <= 0 ? targets.size() - 1 : current - 1)
				: (current < 0 || current + 1 == targets.size()
						? 0 : current + 1);
		host.requestGLFocus(targets.get(next));
	}

	private static void collectFocusable(GLParent parent,
			List<GLKeyTarget> result) {
		for (GLContainer child : parent.getGLChildren()) {
			if (!child.isVisible())
				continue;
			if (child instanceof GLKeyTarget target
					&& target.isFocusTraversable())
				result.add(target);
			collectFocusable(child, result);
		}
	}

	private void updateHover(MouseEvent event, GLPickResult next,
			boolean sendMoved) {
		GLMouseTarget previousTarget = hoverPick.getTarget();
		GLMouseTarget nextTarget = next.getTarget();
		if (previousTarget != nextTarget) {
			if (previousTarget != null)
				previousTarget.mouseExited(mouseEvent(event,
						hoverPick.at(event.getX(), event.getY()), 0, 0));
			hoverPick = next;
			if (nextTarget != null)
				nextTarget.mouseEntered(mouseEvent(event, next, 0, 0));
		} else {
			hoverPick = next;
		}
		if (sendMoved && nextTarget != null)
			nextTarget.mouseMoved(mouseEvent(event, next, 0, 0));
	}

	private void leaveHover(MouseEvent event) {
		if (!hoverPick.hasTarget())
			return;
		hoverPick.getTarget().mouseExited(mouseEvent(event,
				hoverPick.at(event.getX(), event.getY()), 0, 0));
		hoverPick = GLPickResult.none();
	}

	private GLMouseEvent mouseEvent(MouseEvent event, GLPickResult pick,
			float deltaX, float deltaY) {
		return new GLMouseEvent(event, pick, deltaX, deltaY,
				this::requestPointerWarp, pointerController);
	}

	private GLPickResult attached(GLPickResult result) {
		GLMouseTarget target = result.getTarget();
		return target == null || host.isAttachedTarget(target)
				? result : GLPickResult.none();
	}

	private enum KeyDispatch {
		PRESSED,
		RELEASED,
		TYPED
	}
}
