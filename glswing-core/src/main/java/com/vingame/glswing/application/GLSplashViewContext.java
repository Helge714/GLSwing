package com.vingame.glswing.application;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.vingame.glswing.application.GLStartupSnapshot.Status;

/**
 * Factory for automatically updated Swing elements used by custom splash
 * views. Creation and updates run on the AWT event-dispatch thread.
 */
public final class GLSplashViewContext {
	private static final int TASK_ROW_HEIGHT = 18;
	private static final int TASK_MARKER_WIDTH = 16;
	private static final int TASK_MARKER_GAP = 6;

	private final GLSplashConfiguration configuration;
	private final String applicationTitle;
	private final List<JLabel> currentTaskLabels = new ArrayList<>();
	private final List<JProgressBar> progressBars = new ArrayList<>();
	private final List<TaskHistoryBinding> taskHistories = new ArrayList<>();
	private volatile GLStartupSnapshot snapshot =
			new GLStartupSnapshot(0, true, List.of());

	GLSplashViewContext(GLSplashConfiguration configuration,
			String applicationTitle) {
		this.configuration = configuration;
		this.applicationTitle = applicationTitle;
	}

	public GLSplashConfiguration configuration() { return configuration; }
	public GLStartupSnapshot snapshot() { return snapshot; }

	/** Creates a heading; an empty value resolves to the application title. */
	public JLabel title(String text) {
		if (text == null) throw new IllegalArgumentException("Title is required");
		return label(text.isEmpty() ? applicationTitle : text,
				configuration.foreground(), Font.BOLD, 24);
	}

	/** Creates a heading containing the application title. */
	public JLabel title() { return title(""); }

	public JLabel subtitle(String text) {
		if (text == null) throw new IllegalArgumentException("Subtitle is required");
		return label(text, configuration.mutedForeground(), Font.PLAIN, 13);
	}

	public JLabel logo(Path path, int width, int height) {
		if (path == null || width <= 0 || height <= 0)
			throw new IllegalArgumentException(
					"Logo path and positive dimensions are required");
		if (!Files.isRegularFile(path)) return sizedLabel(width, height);
		ImageIcon source = new ImageIcon(path.toString());
		if (source.getIconWidth() <= 0 || source.getIconHeight() <= 0)
			return sizedLabel(width, height);
		double scale = Math.min((double) width / source.getIconWidth(),
				(double) height / source.getIconHeight());
		int scaledWidth = Math.max(1,
				(int) Math.round(source.getIconWidth() * scale));
		int scaledHeight = Math.max(1,
				(int) Math.round(source.getIconHeight() * scale));
		JLabel result = new JLabel(new ImageIcon(source.getImage().getScaledInstance(
				scaledWidth, scaledHeight, Image.SCALE_SMOOTH)));
		result.setPreferredSize(new Dimension(width, height));
		result.setMaximumSize(new Dimension(width, height));
		result.setAlignmentX(0f);
		return result;
	}

	/** Creates a line bound to the active task and its detail text. */
	public JLabel currentTask() {
		JLabel result = label("Preparing application",
				configuration.foreground(), Font.PLAIN, 13);
		currentTaskLabels.add(result);
		applyCurrentTask(result, snapshot);
		return result;
	}

	public JComponent taskHistory(int pastTasks, int upcomingTasks,
			double fadeTargetOpacity, boolean showStatusMarkers) {
		return taskHistory(OptionalInt.empty(), pastTasks, upcomingTasks,
				fadeTargetOpacity, showStatusMarkers);
	}

	public JComponent taskHistory(int width, int pastTasks, int upcomingTasks,
			double fadeTargetOpacity, boolean showStatusMarkers) {
		return taskHistory(OptionalInt.of(width), pastTasks, upcomingTasks,
				fadeTargetOpacity, showStatusMarkers);
	}

	private JComponent taskHistory(OptionalInt width, int pastTasks,
			int upcomingTasks, double fadeTargetOpacity,
			boolean showStatusMarkers) {
		GLSplashElement.TaskHistory specification =
				new GLSplashElement.TaskHistory(width, pastTasks, upcomingTasks,
						fadeTargetOpacity, showStatusMarkers);
		TaskHistoryBinding binding = new TaskHistoryBinding(specification);
		taskHistories.add(binding);
		binding.update(snapshot.tasks());
		return binding.panel;
	}

	/** Creates an aggregate progress bar that fills its content width. */
	public JProgressBar progressBar() {
		return progressBar(OptionalInt.empty());
	}

	/** Creates an aggregate progress bar with a fixed width. */
	public JProgressBar progressBar(int width) {
		return progressBar(OptionalInt.of(width));
	}

	private JProgressBar progressBar(OptionalInt width) {
		if (width.isPresent() && width.getAsInt() <= 0)
			throw new IllegalArgumentException("Progress-bar width must be positive");
		JProgressBar result = new JProgressBar(0, 1000);
		result.setForeground(configuration.accent());
		result.setBackground(configuration.background().brighter());
		result.setBorderPainted(false);
		int preferredWidth = width.orElse(400);
		result.setPreferredSize(new Dimension(preferredWidth, 8));
		result.setMaximumSize(new Dimension(
				width.isPresent() ? preferredWidth : Integer.MAX_VALUE, 8));
		result.setAlignmentX(0f);
		progressBars.add(result);
		applyProgress(result, snapshot);
		return result;
	}

	/**
	 * Creates an image-aware panel using the configured background, artwork,
	 * scaling mode and overlay.
	 */
	public JPanel backgroundPanel(LayoutManager layout) {
		return new BackgroundPanel(configuration, layout);
	}

	JComponent create(GLSplashElement element) {
		if (element instanceof GLSplashElement.Title title)
			return title(title.text());
		if (element instanceof GLSplashElement.Subtitle subtitle)
			return subtitle(subtitle.text());
		if (element instanceof GLSplashElement.Logo logo)
			return logo(logo.path(), logo.width(), logo.height());
		if (element instanceof GLSplashElement.CurrentTask)
			return currentTask();
		if (element instanceof GLSplashElement.TaskHistory history)
			return taskHistory(history.width(), history.pastTasks(),
					history.upcomingTasks(), history.fadeTargetOpacity(),
					history.showStatusMarkers());
		if (element instanceof GLSplashElement.ProgressBar progress)
			return progressBar(progress.width());
		if (element instanceof GLSplashElement.Gap gap)
			return (JComponent) Box.createVerticalStrut(gap.height());
		if (element instanceof GLSplashElement.Custom custom) {
			JComponent component = custom.factory().get();
			if (component == null)
				throw new IllegalStateException(
						"Splash component factory returned null");
			return component;
		}
		throw new IllegalArgumentException("Unsupported splash element: " + element);
	}

	void update(GLStartupSnapshot next) {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(() -> update(next));
			return;
		}
		snapshot = next;
		currentTaskLabels.forEach(label -> applyCurrentTask(label, next));
		progressBars.forEach(bar -> applyProgress(bar, next));
		taskHistories.forEach(history -> history.update(next.tasks()));
	}

	void showFailure(Throwable failure) {
		String message = failure.getMessage();
		currentTaskLabels.forEach(label -> label.setText("Startup failed"
				+ (message == null ? "" : " — " + message)));
		progressBars.forEach(bar -> {
			bar.setIndeterminate(false);
			bar.setForeground(com.vingame.glswing.ui.GLTheme.DEFAULT.values().getColor("splash.error"));
		});
	}

	private static void applyCurrentTask(JLabel label,
			GLStartupSnapshot snapshot) {
		GLStartupSnapshot.Task active = snapshot.currentTask();
		label.setText(active == null ? "Ready" : active.label()
				+ (active.detail().isBlank() ? "" : " — " + active.detail()));
	}

	private static void applyProgress(JProgressBar bar,
			GLStartupSnapshot snapshot) {
		bar.setIndeterminate(snapshot.indeterminate() && snapshot.progress() == 0);
		if (!bar.isIndeterminate())
			bar.setValue((int) Math.round(snapshot.progress() * 1000));
	}

	private static int findCurrentOrLatest(List<GLStartupSnapshot.Task> tasks) {
		for (int index = 0; index < tasks.size(); index++)
			if (tasks.get(index).status() == Status.RUNNING) return index;
		for (int index = tasks.size() - 1; index >= 0; index--)
			if (tasks.get(index).status() != Status.PENDING) return index;
		return tasks.isEmpty() ? -1 : 0;
	}

	private JPanel taskRow(String marker, String text, Color color,
			boolean showMarker) {
		JPanel row = new JPanel(new BorderLayout(TASK_MARKER_GAP, 0));
		row.setOpaque(false);
		row.setAlignmentX(0f);
		if (showMarker) {
			JLabel markerLabel = label(marker, color, Font.PLAIN, 12);
			markerLabel.setHorizontalAlignment(SwingConstants.CENTER);
			markerLabel.setPreferredSize(new Dimension(
					TASK_MARKER_WIDTH, TASK_ROW_HEIGHT));
			row.add(markerLabel, BorderLayout.WEST);
		}
		row.add(label(text, color, Font.PLAIN, 12), BorderLayout.CENTER);
		return row;
	}

	private static Color faded(Color color, int offset, int pastTasks,
			int upcomingTasks, double targetOpacity) {
		if (offset == 0 || targetOpacity == 1) return color;
		int distance = Math.abs(offset);
		int extent = offset < 0 ? pastTasks : upcomingTasks;
		double fraction = (double) distance / (extent + 1);
		double opacity = 1 - fraction * (1 - targetOpacity);
		int alpha = (int) Math.round(color.getAlpha() * opacity);
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
	}

	private static JLabel label(String text, Color color, int style, int size) {
		JLabel result = new JLabel(text);
		result.setForeground(color);
		result.setFont(result.getFont().deriveFont(style, size));
		result.setAlignmentX(0f);
		return result;
	}

	private static JLabel sizedLabel(int width, int height) {
		JLabel result = new JLabel();
		result.setPreferredSize(new Dimension(width, height));
		result.setMaximumSize(new Dimension(width, height));
		result.setAlignmentX(0f);
		return result;
	}

	private final class TaskHistoryBinding {
		private final GLSplashElement.TaskHistory specification;
		private final JPanel panel;

		TaskHistoryBinding(GLSplashElement.TaskHistory specification) {
			this.specification = specification;
			int rows = specification.pastTasks() + 1
					+ specification.upcomingTasks();
			panel = new JPanel(new GridLayout(rows, 1));
			panel.setOpaque(false);
			panel.setAlignmentX(0f);
			int width = specification.width().orElse(400);
			Dimension size = new Dimension(width, rows * TASK_ROW_HEIGHT);
			panel.setPreferredSize(size);
			panel.setMaximumSize(new Dimension(specification.width().isPresent()
					? width : Integer.MAX_VALUE, size.height));
		}

		void update(List<GLStartupSnapshot.Task> tasks) {
			panel.removeAll();
			int selected = findCurrentOrLatest(tasks);
			for (int offset = -specification.pastTasks();
					offset <= specification.upcomingTasks(); offset++) {
				int index = selected + offset;
				if (selected < 0 || index < 0 || index >= tasks.size()) {
					panel.add(new JLabel());
					continue;
				}
				GLStartupSnapshot.Task task = tasks.get(index);
				String marker = switch (task.status()) {
				case COMPLETE -> "✓";
				case FAILED -> "!";
				case RUNNING -> "›";
				case PENDING -> "·";
				};
				Color color = task.status() == Status.RUNNING
						? configuration.foreground()
						: configuration.mutedForeground();
				color = faded(color, offset, specification.pastTasks(),
						specification.upcomingTasks(),
						specification.fadeTargetOpacity());
				panel.add(taskRow(marker, task.label(), color,
						specification.showStatusMarkers()));
			}
			panel.revalidate();
			panel.repaint();
		}
	}

	private static final class BackgroundPanel extends JPanel {
		private static final long serialVersionUID = 1L;
		private final GLSplashConfiguration configuration;
		private final Image image;

		BackgroundPanel(GLSplashConfiguration configuration, LayoutManager layout) {
			super(layout);
			this.configuration = configuration;
			setOpaque(true);
			setBackground(configuration.background());
			image = load(configuration.backgroundImage());
		}

		@Override protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D copy = (Graphics2D) graphics.create();
			try {
				if (image != null) paintImage(copy);
				Color overlay = configuration.backgroundOverlay();
				if (overlay.getAlpha() > 0) {
					copy.setColor(overlay);
					copy.fillRect(0, 0, getWidth(), getHeight());
				}
			} finally {
				copy.dispose();
			}
		}

		private void paintImage(Graphics2D graphics) {
			int sourceWidth = image.getWidth(this);
			int sourceHeight = image.getHeight(this);
			if (sourceWidth <= 0 || sourceHeight <= 0) return;
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			if (configuration.backgroundImageFit() == GLSplashImageFit.STRETCH) {
				graphics.drawImage(image, 0, 0, getWidth(), getHeight(), this);
				return;
			}
			double widthScale = (double) getWidth() / sourceWidth;
			double heightScale = (double) getHeight() / sourceHeight;
			double scale = configuration.backgroundImageFit() == GLSplashImageFit.COVER
					? Math.max(widthScale, heightScale)
					: Math.min(widthScale, heightScale);
			int width = (int) Math.round(sourceWidth * scale);
			int height = (int) Math.round(sourceHeight * scale);
			graphics.drawImage(image, (getWidth() - width) / 2,
					(getHeight() - height) / 2, width, height, this);
		}

		private static Image load(Path path) {
			if (path == null || !Files.isRegularFile(path)) return null;
			Image result = new ImageIcon(path.toString()).getImage();
			return result.getWidth(null) <= 0 ? null : result;
		}
	}
}
