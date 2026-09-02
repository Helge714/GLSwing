package com.vingame.glswing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.EventQueue;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import org.junit.jupiter.api.Test;

final class GLStartupEngineTest {
	@Test void customSplashElementsFollowStartupSnapshots() throws Exception {
		AtomicReference<GLSplashViewContext> reference = new AtomicReference<>();
		AtomicReference<JLabel> headingReference = new AtomicReference<>();
		AtomicReference<JLabel> currentReference = new AtomicReference<>();
		AtomicReference<JProgressBar> progressReference = new AtomicReference<>();
		AtomicReference<JPanel> historyReference = new AtomicReference<>();
		EventQueue.invokeAndWait(() -> {
			GLSplashViewContext context = new GLSplashViewContext(
					GLSplashConfiguration.builder().build(), "Fallback title");
			reference.set(context);
			headingReference.set(context.title("Test application"));
			currentReference.set(context.currentTask());
			progressReference.set(context.progressBar());
			historyReference.set((JPanel) context.taskHistory(1, 1, 0.5, true));
		});
		GLSplashViewContext context = reference.get();
		GLStartupSnapshot snapshot = new GLStartupSnapshot(0.5, false, List.of(
				new GLStartupSnapshot.Task("Configuration",
						GLStartupSnapshot.Status.COMPLETE, 1, ""),
				new GLStartupSnapshot.Task("Interface",
						GLStartupSnapshot.Status.RUNNING, 0, "Creating controls"),
				new GLStartupSnapshot.Task("Renderer",
						GLStartupSnapshot.Status.PENDING, 0, "")));

		EventQueue.invokeAndWait(() -> context.update(snapshot));

		assertEquals("Test application", headingReference.get().getText());
		assertEquals("Interface — Creating controls",
				currentReference.get().getText());
		assertEquals(500, progressReference.get().getValue());
		assertEquals(snapshot, context.snapshot());
		JPanel history = historyReference.get();
		assertEquals(3, history.getComponentCount());
		JPanel completed = (JPanel) history.getComponent(0);
		JPanel active = (JPanel) history.getComponent(1);
		JPanel pending = (JPanel) history.getComponent(2);
		assertEquals("✓", ((JLabel) completed.getComponent(0)).getText());
		assertEquals("Configuration",
				((JLabel) completed.getComponent(1)).getText());
		assertEquals("›", ((JLabel) active.getComponent(0)).getText());
		assertEquals("Interface", ((JLabel) active.getComponent(1)).getText());
		assertEquals("·", ((JLabel) pending.getComponent(0)).getText());
		assertEquals("Renderer", ((JLabel) pending.getComponent(1)).getText());
		assertEquals(((JLabel) completed.getComponent(0)).getPreferredSize().width,
				((JLabel) pending.getComponent(0)).getPreferredSize().width);
		assertEquals(191,
				((JLabel) completed.getComponent(1)).getForeground().getAlpha());
		assertEquals(191,
				((JLabel) pending.getComponent(1)).getForeground().getAlpha());
	}

	@Test void buildsCustomSplashConfiguration() {
		Path background = Path.of("branding", "splash.png");
		GLSplashConfiguration configuration = GLSplashConfiguration.builder()
				.backgroundImage(background)
				.backgroundImageFit(GLSplashImageFit.CONTAIN)
				.backgroundOverlay(new Color(0, 0, 0, 96))
				.contentAlignment(GLSplashAlignment.BOTTOM_LEFT)
				.size(720, 420)
				.padding(28)
				.addLogo(Path.of("branding", "logo.png"), 96, 80)
				.addTitle("Test")
				.addTaskHistory(320, 4, 2, 0.35, false)
				.addProgressBar(240)
				.addProgressBar(false)
				.build();

		assertEquals(background, configuration.backgroundImage());
		assertEquals(GLSplashImageFit.CONTAIN,
				configuration.backgroundImageFit());
		assertEquals(GLSplashAlignment.BOTTOM_LEFT,
				configuration.contentAlignment());
		assertEquals(720, configuration.width());
		assertEquals(5, configuration.elements().size());
		GLSplashElement.Logo logo =
				(GLSplashElement.Logo) configuration.elements().get(0);
		assertEquals(96, logo.width());
		GLSplashElement.TaskHistory history =
				(GLSplashElement.TaskHistory) configuration.elements().get(2);
		assertEquals(320, history.width().orElseThrow());
		assertEquals(4, history.pastTasks());
		assertEquals(2, history.upcomingTasks());
		assertEquals(0.35, history.fadeTargetOpacity());
		assertFalse(history.showStatusMarkers());
		GLSplashElement.ProgressBar unpaddedProgress =
				(GLSplashElement.ProgressBar) configuration.elements().get(4);
		assertFalse(unpaddedProgress.usePadding());
	}

	@Test void rejectsInvalidSplashDimensions() {
		assertThrows(IllegalArgumentException.class,
				() -> GLSplashConfiguration.builder().size(0, 420).build());
		assertThrows(IllegalArgumentException.class,
				() -> GLSplashConfiguration.builder()
						.addTaskHistory(-1, 1, 0.5));
		assertThrows(IllegalArgumentException.class,
				() -> GLSplashConfiguration.builder()
						.addTaskHistory(1, 1, 1.1));
	}

	@Test void executesInOrderAndPublishesWeightedProgress() throws Exception {
		List<String> order = new ArrayList<>();
		List<GLStartupSnapshot> snapshots = new ArrayList<>();
		AtomicBoolean onAwt = new AtomicBoolean();
		GLStartupPlan plan = GLStartupPlan.builder()
				.provide("configuration", "Load configuration", 2, task -> {
					task.progress(1, 2);
					order.add("load");
					return "configured";
				})
				.awt("Build interface", 1, task -> {
					onAwt.set(EventQueue.isDispatchThread());
					order.add(task.require("configuration", String.class));
					return null;
				})
				.build();

		var results = new GLStartupEngine(plan, snapshots::add).run(null);
		assertEquals(List.of("load", "configured"), order);
		assertTrue(onAwt.get());
		assertEquals("configured", results.get("configuration"));
		assertEquals(1, snapshots.get(snapshots.size() - 1).progress());
		assertFalse(snapshots.get(snapshots.size() - 1).indeterminate());
	}

	@Test void reportsFailureAndStopsFollowingTasks() {
		AtomicBoolean followingTask = new AtomicBoolean();
		List<GLStartupSnapshot> snapshots = new ArrayList<>();
		GLStartupPlan plan = GLStartupPlan.builder()
				.background("Fail", () -> { throw new IllegalStateException("broken"); })
				.background("Must not run", () -> followingTask.set(true))
				.build();

		assertThrows(IllegalStateException.class,
				() -> new GLStartupEngine(plan, snapshots::add).run(null));
		assertFalse(followingTask.get());
		assertEquals(GLStartupSnapshot.Status.FAILED,
				snapshots.get(snapshots.size() - 1).tasks().get(0).status());
	}
}
