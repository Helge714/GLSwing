package com.vingame.glswing.demo;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import com.jogamp.opengl.GLAnimatorControl;

/** Console and file diagnostics for interactive demos. */
final class GLDemoErrorReporter {
	private static final Path LOG = Path.of("glswing-error.log")
			.toAbsolutePath().normalize();
	private static volatile String demo = "GLSwing demo";
	private static final long STALL_NANOS = 10_000_000_000L;

	private GLDemoErrorReporter() {
	}

	static void install(String demoName) {
		demo = demoName;
		Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
				report("Uncaught exception on " + thread.getName(), error));
	}

	static void attach(GLAnimatorControl animator) {
		animator.setUncaughtExceptionHandler((control, drawable, error) ->
				report("JOGL animator exception", error));
		Thread watchdog = new Thread(() -> watch(animator),
				"glswing-demo-stall-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
	}

	private static void watch(GLAnimatorControl animator) {
		int previousFrames = 0;
		long lastProgress = 0;
		boolean observedFrameProgress = false;
		boolean reported = false;
		while (!animator.isStarted())
			sleep();
		while (animator.isStarted()) {
			int frames = animator.getTotalFPSFrames();
			if (frames != previousFrames) {
				previousFrames = frames;
				lastProgress = System.nanoTime();
				observedFrameProgress = true;
				reported = false;
			} else if (animator.isPaused() || !animator.isAnimating()) {
				lastProgress = System.nanoTime();
				reported = false;
			} else if (observedFrameProgress && !reported
					&& System.nanoTime() - lastProgress >= STALL_NANOS) {
				reportThreadDump("Possible animator stall: no completed frame for 10 seconds");
				reported = true;
			}
			sleep();
		}
	}

	private static void sleep() {
		try {
			Thread.sleep(250);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	private static synchronized void reportThreadDump(String source) {
		ThreadMXBean threads = ManagementFactory.getThreadMXBean();
		ThreadInfo[] information = threads.dumpAllThreads(true, true);
		StringBuilder dump = new StringBuilder(System.lineSeparator())
				.append("=== ").append(Instant.now()).append(" | ").append(demo)
				.append(" | ").append(source).append(" ===")
				.append(System.lineSeparator());
		for (ThreadInfo thread : information)
			dump.append(thread).append(System.lineSeparator());
		String message = dump.toString();
		System.err.print(message);
		System.err.println("Diagnostic log: " + LOG);
		try {
			Files.writeString(LOG, message, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Exception loggingFailure) {
			System.err.println("Could not write diagnostic log: " + loggingFailure);
		}
	}

	private static synchronized void report(String source, Throwable error) {
		StringWriter stack = new StringWriter();
		error.printStackTrace(new PrintWriter(stack));
		String message = System.lineSeparator()
				+ "=== " + Instant.now() + " | " + demo + " | " + source + " ==="
				+ System.lineSeparator() + stack;
		System.err.print(message);
		System.err.println("Diagnostic log: " + LOG);
		try {
			Files.writeString(LOG, message, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Exception loggingFailure) {
			System.err.println("Could not write diagnostic log: " + loggingFailure);
		}
	}
}
