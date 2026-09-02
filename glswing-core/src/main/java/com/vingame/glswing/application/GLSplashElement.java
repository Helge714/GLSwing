package com.vingame.glswing.application;

import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.function.Supplier;

import javax.swing.JComponent;

/** One ordered element in the standard splash content recipe. */
public sealed interface GLSplashElement {
	/** Application heading. An empty value resolves to the application title. */
	record Title(String text) implements GLSplashElement {
		public Title {
			if (text == null) throw new IllegalArgumentException("Title is required");
		}
	}

	/** Secondary application or startup text. */
	record Subtitle(String text) implements GLSplashElement {
		public Subtitle {
			if (text == null) throw new IllegalArgumentException("Subtitle is required");
		}
	}

	/** Aspect-preserving image inside a fixed bounding box. */
	record Logo(Path path, int width, int height) implements GLSplashElement {
		public Logo {
			if (path == null || width <= 0 || height <= 0)
				throw new IllegalArgumentException(
						"Logo path and positive dimensions are required");
		}
	}

	/** Dedicated line bound to the active task and its detail text. */
	record CurrentTask() implements GLSplashElement { }

	/** Fixed history window around the active task. */
	record TaskHistory(OptionalInt width, int pastTasks, int upcomingTasks,
			double fadeTargetOpacity, boolean showStatusMarkers)
			implements GLSplashElement {
		public TaskHistory {
			if (width == null || width.isPresent() && width.getAsInt() <= 0)
				throw new IllegalArgumentException("Task-history width must be positive");
			if (pastTasks < 0 || upcomingTasks < 0)
				throw new IllegalArgumentException(
						"Visible task counts must not be negative");
			if (!Double.isFinite(fadeTargetOpacity)
					|| fadeTargetOpacity < 0 || fadeTargetOpacity > 1)
				throw new IllegalArgumentException(
						"Fade target opacity must be between zero and one");
		}
	}

	/** Aggregate startup progress, optionally outside the content padding. */
	record ProgressBar(OptionalInt width, boolean usePadding)
			implements GLSplashElement {
		public ProgressBar {
			if (width == null || width.isPresent() && width.getAsInt() <= 0)
				throw new IllegalArgumentException("Progress-bar width must be positive");
			if (!usePadding && width.isPresent())
				throw new IllegalArgumentException(
						"An unpadded progress bar always fills the window width");
		}
	}

	/** Fixed vertical separation between adjacent elements. */
	record Gap(int height) implements GLSplashElement {
		public Gap {
			if (height < 0)
				throw new IllegalArgumentException("Splash gap must not be negative");
		}
	}

	/** Application-defined Swing component created on the AWT thread. */
	record Custom(Supplier<? extends JComponent> factory)
			implements GLSplashElement {
		public Custom {
			if (factory == null)
				throw new IllegalArgumentException("Component factory is required");
		}
	}
}
