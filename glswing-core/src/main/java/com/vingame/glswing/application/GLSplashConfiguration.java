package com.vingame.glswing.application;

import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Supplier;

import javax.swing.JComponent;

/** Immutable appearance, content recipe and behavior of the startup window. */
public record GLSplashConfiguration(Path backgroundImage,
		GLSplashImageFit backgroundImageFit, Color backgroundOverlay,
		GLSplashAlignment contentAlignment, int width, int height, int padding,
		long minimumDisplayMillis, long handoffDelayMillis, Color background,
		Color foreground, Color mutedForeground, Color accent,
		List<GLSplashElement> elements) {

	public GLSplashConfiguration {
		if (backgroundImageFit == null || contentAlignment == null)
			throw new IllegalArgumentException("Splash options must not be null");
		if (width <= 0 || height <= 0)
			throw new IllegalArgumentException("Splash dimensions must be positive");
		if (padding < 0)
			throw new IllegalArgumentException("Splash padding must not be negative");
		if (minimumDisplayMillis < 0 || handoffDelayMillis < 0)
			throw new IllegalArgumentException(
					"Splash durations must not be negative");
		if (background == null || backgroundOverlay == null || foreground == null
				|| mutedForeground == null || accent == null)
			throw new IllegalArgumentException("Splash colors must not be null");
		if (elements == null)
			throw new IllegalArgumentException("Splash elements are required");
		elements = List.copyOf(elements);
	}

	public static Builder builder() { return new Builder(); }

	/** Builder with a complete standard recipe unless elements are added explicitly. */
	public static final class Builder {
		private Path backgroundImage;
		private GLSplashImageFit backgroundImageFit = GLSplashImageFit.COVER;
		private Color backgroundOverlay = new Color(0, 0, 0, 0);
		private GLSplashAlignment contentAlignment = GLSplashAlignment.CENTER;
		private int width = 560;
		private int height = 340;
		private int padding = 32;
		private long minimumDisplayMillis = 450;
		private long handoffDelayMillis;
		private Color background = new Color(24, 27, 32);
		private Color foreground = new Color(242, 244, 247);
		private Color mutedForeground = new Color(166, 173, 184);
		private Color accent = new Color(45, 137, 239);
		private final List<GLSplashElement> elements = new ArrayList<>();
		private boolean explicitElements;

		public Builder backgroundImage(Path image) {
			backgroundImage = image;
			return this;
		}
		public Builder backgroundImageFit(GLSplashImageFit fit) {
			backgroundImageFit = fit;
			return this;
		}
		/** Applies a translucent color above the background image. */
		public Builder backgroundOverlay(Color overlay) {
			backgroundOverlay = overlay;
			return this;
		}
		public Builder contentAlignment(GLSplashAlignment alignment) {
			contentAlignment = alignment;
			return this;
		}
		public Builder size(int width, int height) {
			this.width = width;
			this.height = height;
			return this;
		}
		public Builder padding(int padding) {
			this.padding = padding;
			return this;
		}
		public Builder minimumDisplayMillis(long millis) {
			minimumDisplayMillis = millis;
			return this;
		}
		/** Keeps the completed splash above the revealed application window. */
		public Builder handoffDelayMillis(long millis) {
			handoffDelayMillis = millis;
			return this;
		}
		public Builder colors(Color background, Color foreground,
				Color mutedForeground, Color accent) {
			this.background = background;
			this.foreground = foreground;
			this.mutedForeground = mutedForeground;
			this.accent = accent;
			return this;
		}

		public Builder addTitle(String title) {
			return add(new GLSplashElement.Title(title));
		}
		public Builder addSubtitle(String subtitle) {
			return add(new GLSplashElement.Subtitle(subtitle));
		}
		public Builder addLogo(Path path) { return addLogo(path, 72, 72); }
		public Builder addLogo(Path path, int width, int height) {
			return add(new GLSplashElement.Logo(path, width, height));
		}
		public Builder addCurrentTask() {
			return add(new GLSplashElement.CurrentTask());
		}
		public Builder addTaskHistory() {
			return addTaskHistory(OptionalInt.empty(), 5, 0, 1, true);
		}
		public Builder addTaskHistory(int width) {
			return addTaskHistory(OptionalInt.of(width), 5, 0, 1, true);
		}
		public Builder addTaskHistory(int pastTasks, int upcomingTasks,
				double fadeTargetOpacity) {
			return addTaskHistory(OptionalInt.empty(), pastTasks, upcomingTasks,
					fadeTargetOpacity, true);
		}
		public Builder addTaskHistory(int pastTasks, int upcomingTasks,
				double fadeTargetOpacity, boolean showStatusMarkers) {
			return addTaskHistory(OptionalInt.empty(), pastTasks, upcomingTasks,
					fadeTargetOpacity, showStatusMarkers);
		}
		public Builder addTaskHistory(int width, int pastTasks,
				int upcomingTasks, double fadeTargetOpacity,
				boolean showStatusMarkers) {
			return addTaskHistory(OptionalInt.of(width), pastTasks, upcomingTasks,
					fadeTargetOpacity, showStatusMarkers);
		}
		private Builder addTaskHistory(OptionalInt width, int pastTasks,
				int upcomingTasks, double fadeTargetOpacity,
				boolean showStatusMarkers) {
			return add(new GLSplashElement.TaskHistory(width, pastTasks,
					upcomingTasks, fadeTargetOpacity, showStatusMarkers));
		}
		/** Adds a progress bar that fills the ordered content width. */
		public Builder addProgressBar() {
			return addProgressBar(OptionalInt.empty(), true);
		}
		public Builder addProgressBar(int width) {
			return addProgressBar(OptionalInt.of(width), true);
		}
		/**
		 * Adds a progress bar. Without padding, it spans the window bottom edge.
		 */
		public Builder addProgressBar(boolean usePadding) {
			return addProgressBar(OptionalInt.empty(), usePadding);
		}
		private Builder addProgressBar(OptionalInt width, boolean usePadding) {
			return add(new GLSplashElement.ProgressBar(width, usePadding));
		}
		public Builder addGap(int height) {
			return add(new GLSplashElement.Gap(height));
		}
		public Builder addComponent(Supplier<? extends JComponent> factory) {
			return add(new GLSplashElement.Custom(factory));
		}
		/** Discards explicit elements and restores the standard recipe. */
		public Builder useDefaultElements() {
			elements.clear();
			explicitElements = false;
			return this;
		}

		private Builder add(GLSplashElement element) {
			if (!explicitElements) {
				elements.clear();
				explicitElements = true;
			}
			elements.add(element);
			return this;
		}

		public GLSplashConfiguration build() {
			List<GLSplashElement> resolved = explicitElements
					? List.copyOf(elements) : defaultElements();
			return new GLSplashConfiguration(backgroundImage, backgroundImageFit,
					backgroundOverlay, contentAlignment, width, height, padding,
					minimumDisplayMillis, handoffDelayMillis, background, foreground,
					mutedForeground, accent, resolved);
		}

		private static List<GLSplashElement> defaultElements() {
			return List.of(
					new GLSplashElement.Title(""),
					new GLSplashElement.Gap(4),
					new GLSplashElement.Subtitle("Starting application"),
					new GLSplashElement.Gap(22),
					new GLSplashElement.CurrentTask(),
					new GLSplashElement.Gap(8),
					new GLSplashElement.ProgressBar(OptionalInt.of(400), true),
					new GLSplashElement.Gap(16),
					new GLSplashElement.TaskHistory(OptionalInt.of(400),
							5, 0, 1, true));
		}
	}
}
