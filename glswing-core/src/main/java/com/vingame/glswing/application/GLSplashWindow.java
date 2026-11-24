package com.vingame.glswing.application;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

final class GLSplashWindow {
	private final GLSplashConfiguration configuration;
	private final JFrame dialog;
	private final JPanel shell = new JPanel(new BorderLayout());
	private final GLSplashViewContext viewContext;
	private long shownAt;

	GLSplashWindow(GLSplashConfiguration configuration, String applicationTitle,
			GLSplashViewFactory viewFactory, Window owner) {
		this.configuration = configuration;
		dialog = new JFrame(applicationTitle);
		if (owner != null) dialog.setIconImages(owner.getIconImages());
		viewContext = new GLSplashViewContext(configuration, applicationTitle);
		JComponent content = viewFactory == null
				? createStandardView() : viewFactory.create(viewContext);
		if (content == null)
			throw new IllegalStateException("Splash view factory returned null");

		shell.setBackground(configuration.background());
		shell.add(content, BorderLayout.CENTER);
		dialog.setUndecorated(true);
		dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		dialog.setAlwaysOnTop(configuration.alwaysOnTop());
		dialog.setContentPane(shell);
		dialog.setSize(configuration.width(), configuration.height());
		dialog.setLocationRelativeTo(null);
	}

	void show() {
		shownAt = System.nanoTime();
		dialog.setVisible(true);
	}

	void update(GLStartupSnapshot snapshot) {
		viewContext.update(snapshot);
	}

	void closeAfterMinimumDuration() {
		long elapsed = (System.nanoTime() - shownAt) / 1_000_000;
		long remaining = Math.max(configuration.minimumDisplayMillis() - elapsed,
				configuration.handoffDelayMillis());
		if (remaining > 0) {
			try { Thread.sleep(remaining); }
			catch (InterruptedException error) { Thread.currentThread().interrupt(); }
		}
		SwingUtilities.invokeLater(dialog::dispose);
	}

	void showFailure(Throwable failure, Runnable closeAction) {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(() -> showFailure(failure, closeAction));
			return;
		}
		viewContext.showFailure(failure);
		String message = failure.getMessage();
		JLabel explanation = new JLabel("Startup failed"
				+ (message == null ? "" : " — " + message));
		explanation.setForeground(com.vingame.glswing.ui.GLTheme.DEFAULT.values().getColor("splash.error"));
		JButton close = new JButton("Close");
		close.addActionListener(event -> {
			dialog.dispose();
			closeAction.run();
		});
		JPanel failureBar = new JPanel(new BorderLayout(16, 0));
		failureBar.setBackground(configuration.background());
		failureBar.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		failureBar.add(explanation, BorderLayout.CENTER);
		failureBar.add(close, BorderLayout.EAST);
		shell.add(failureBar, BorderLayout.SOUTH);
		shell.revalidate();
		shell.repaint();
		dialog.addWindowListener(new WindowAdapter() {
			@Override public void windowClosing(WindowEvent event) { close.doClick(); }
		});
	}

	private JComponent createStandardView() {
		Color background = configuration.background();
		JPanel root = viewContext.backgroundPanel(new BorderLayout());
		root.setBorder(BorderFactory.createLineBorder(background.brighter(), 1));

		JPanel alignmentArea = new JPanel(new GridBagLayout());
		alignmentArea.setOpaque(false);
		alignmentArea.setBorder(BorderFactory.createEmptyBorder(
				configuration.padding(), configuration.padding(),
				configuration.padding(), configuration.padding()));
		JPanel information = new JPanel();
		information.setOpaque(false);
		information.setAlignmentX(0f);
		information.setLayout(new BoxLayout(information, BoxLayout.Y_AXIS));
		JComponent unpaddedProgress = null;
		for (GLSplashElement element : configuration.elements()) {
			JComponent component = viewContext.create(element);
			if (element instanceof GLSplashElement.ProgressBar progress
					&& !progress.usePadding()) {
				if (unpaddedProgress != null)
					throw new IllegalStateException(
							"Only one unpadded progress bar is supported");
				unpaddedProgress = component;
			} else {
				component.setAlignmentX(0f);
				information.add(component);
			}
		}

		alignmentArea.add(information,
				alignmentConstraints(configuration.contentAlignment()));
		root.add(alignmentArea, BorderLayout.CENTER);
		if (unpaddedProgress != null)
			root.add(unpaddedProgress, BorderLayout.SOUTH);
		return root;
	}

	private static GridBagConstraints alignmentConstraints(
			GLSplashAlignment alignment) {
		GridBagConstraints result = new GridBagConstraints();
		result.weightx = 1;
		result.weighty = 1;
		result.fill = GridBagConstraints.NONE;
		result.anchor = switch (alignment) {
		case TOP_LEFT -> GridBagConstraints.NORTHWEST;
		case TOP_CENTER -> GridBagConstraints.NORTH;
		case TOP_RIGHT -> GridBagConstraints.NORTHEAST;
		case CENTER_LEFT -> GridBagConstraints.WEST;
		case CENTER -> GridBagConstraints.CENTER;
		case CENTER_RIGHT -> GridBagConstraints.EAST;
		case BOTTOM_LEFT -> GridBagConstraints.SOUTHWEST;
		case BOTTOM_CENTER -> GridBagConstraints.SOUTH;
		case BOTTOM_RIGHT -> GridBagConstraints.SOUTHEAST;
		};
		return result;
	}

}
