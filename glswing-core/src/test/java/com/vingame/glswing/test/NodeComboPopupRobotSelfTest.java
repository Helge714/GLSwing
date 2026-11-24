package com.vingame.glswing.test;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import com.vingame.glswing.component.GLFrame;
import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.layout.GLGridLayout;
import com.vingame.glswing.node.GLNode;
import com.vingame.glswing.node.GLNodeEditor;
import com.vingame.glswing.ui.GLComboBox;
import com.vingame.glswing.ui.GLLabel;

/** Visual regression probe for a ComboBox popup nested inside a node editor. */
public final class NodeComboPopupRobotSelfTest {
	private NodeComboPopupRobotSelfTest() {
	}

	public static void main(String[] args) throws Exception {
		Holder holder = new Holder();
		SwingUtilities.invokeAndWait(() -> {
			holder.frame = new GLFrame("Node combo popup regression",
					GLFrame.RenderingSettings.DEFAULT.withTargetFps(120));
			holder.frame.setMinimumSize(new Dimension(700, 480));
			holder.frame.setSize(900, 620);
			holder.frame.setLocationRelativeTo(null);
			holder.frame.setAlwaysOnTop(true);
			holder.frame.setGLClearColor(new Color(25, 28, 35));
			holder.frame.setGLLayout(new GLGridLayout());
			GLNodeEditor editor = new GLNodeEditor();
			editor.setZoom(1.35f);
			holder.frame.addGL(editor,
					GLConstraints.auto().weight(1, 1).fillBoth());
			GLNode node = new GLNode("Popup probe");
			node.addContent(new GLLabel("The popup must remain tightly bounded"));
			holder.combo = new GLComboBox<>(
					List.of("Linear", "Multiply", "Screen"));
			node.addInputOutput("input", holder.combo, "output");
			editor.addNode(node, 100, 100);
			node.setSize(300, node.getPreferredGLSize().height());
			holder.frame.setVisible(true);
		});

		try {
			Thread.sleep(700);
			SwingUtilities.invokeAndWait(holder.combo::showPopup);
			Thread.sleep(500);
			float popupHeight = holder.combo.getPopupMenu().getHeight();
			if (popupHeight <= 0 || popupHeight > 140)
				throw new AssertionError(
						"Three-row combo popup has an implausible height: "
								+ popupHeight);
			float[] comboBounds = holder.combo.getLastRenderedScreenBounds();
			float[] popupBounds = holder.combo.getPopupMenu()
					.getLastRenderedScreenBounds();
			if (comboBounds == null || popupBounds == null
					|| Math.abs(popupBounds[1]
							- (comboBounds[1] + comboBounds[3])) > 3)
				throw new AssertionError(
						"Zoomed combo popup was not attached below its owner");
			if (args.length > 0) {
				Rectangle bounds = holder.frame.getBounds();
				BufferedImage image = new Robot().createScreenCapture(bounds);
				ImageIO.write(image, "png", Path.of(args[0]).toFile());
			}
			System.out.println("Node combo popup regression self-test passed (height "
					+ popupHeight + ").");
		} finally {
			SwingUtilities.invokeAndWait(holder.frame::dispose);
		}
	}

	private static final class Holder {
		private GLFrame frame;
		private GLComboBox<String> combo;
	}
}
