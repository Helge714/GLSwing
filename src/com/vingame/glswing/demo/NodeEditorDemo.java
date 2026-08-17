package com.vingame.glswing.demo;

import java.awt.Color;
import java.awt.Dimension;
import java.util.List;

import javax.swing.SwingUtilities;

import com.vingame.glswing.component.GLFrame;
import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.layout.GLGridLayout;
import com.vingame.glswing.node.GLNode;
import com.vingame.glswing.node.GLNodeEditor;
import com.vingame.glswing.node.GLNodePort;
import com.vingame.glswing.node.GLNodePorts;
import com.vingame.glswing.node.GLNodeResizeMode;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLComboBox;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLSlider;

/** First interactive vertical slice of the reusable node-editor controls. */
public final class NodeEditorDemo {
	private NodeEditorDemo() {
	}

	public static void main(String[] args) {
		GLDemoErrorReporter.install("Node Editor Demo");
		SwingUtilities.invokeLater(NodeEditorDemo::show);
	}

	private static void show() {
		GLFrame frame = new GLFrame("GLSwingNext - Node Editor",
				GLFrame.RenderingSettings.DEFAULT.withTargetFps(144));
		frame.setMinimumSize(new Dimension(760, 520));
		frame.setSize(1080, 720);
		frame.setLocationRelativeTo(null);
		frame.setGLClearColor(new Color(25, 28, 35));
		frame.setGLLayout(new GLGridLayout());

		GLNodeEditor editor = new GLNodeEditor();
		frame.addGL(editor, GLConstraints.auto().weight(1, 1).fillBoth());

		GLNode material = materialNode();
		material.setResizeMode(GLNodeResizeMode.HORIZONTAL);
		material.setDragAnywhere(true);
		material.setGLStyle(material.getGLStyle()
				.withTitleBackground(new Color(54, 83, 126))
				.withTitleGradient(new Color(82, 126, 184),
						new Color(42, 68, 108)));
		material.setTitleGradientEnabled(true);
		editor.addNode(material, 90, 80);
		material.setSize(300, material.getPreferredGLSize().height());

		GLNode texture = textureNode();
		texture.setResizeMode(GLNodeResizeMode.BOTH);
		texture.setGLStyle(texture.getGLStyle()
				.withTitleBackground(new Color(126, 72, 42))
				.withTitleGradient(new Color(184, 112, 67),
						new Color(101, 52, 31)));
		texture.setTitleGradientEnabled(true);
		editor.addNode(texture, 470, 120);
		texture.setSize(270, texture.getPreferredGLSize().height() + 55);

		GLNode output = outputNode();
		output.setResizeMode(GLNodeResizeMode.NONE);
		output.setGLStyle(output.getGLStyle()
				.withTitleBackground(new Color(59, 105, 77)));
		editor.addNode(output, 730, 390);

		editor.connect(texture.findPort("color"), material.findPort("base"));
		GLNodePort surface = output.findPort("surface");
		surface.setConnectionValidator((own, other) -> !"uv".equals(other.getId()));
		editor.connect(material.findPort("result"), surface);

		frame.setVisible(true);
	}

	private static GLNode materialNode() {
		GLNode node = new GLNode("Material Properties");
		node.addContent(new GLLabel(
				"Ziehen: Auswahlrahmen | Mitte: Pan | Mausrad: Zoom"));
		node.addContent(new GLLabel(
				"Shift: Auswahl | Strg/Shift+Rad: horizontal/vertikal"));

		GLSlider roughness = GLSlider.fixed("Roughness", 0, 1, 0.42);
		roughness.setStep(0.01);
		node.addInput("roughness", roughness);

		GLComboBox<String> blend = new GLComboBox<>(
				List.of("Linear", "Multiply", "Screen"));
		blend.setName("Base Color");
		GLNodePorts base = node.addInputOutput("base", blend, "result");
		base.input().setContentHiddenWhenConnected(true);

		node.addContent(new GLButton("Normaler Node-Inhalt"));
		return node;
	}

	private static GLNode textureNode() {
		GLNode node = new GLNode("Texture Mapping");
		node.addInput("texture", new GLLabel("Image Texture"));
		node.addInputOutput(GLNodePort.input("scale"),
				GLSlider.fixed("Scale", 0.1, 8, 1),
				GLNodePort.output("uv"));
		node.addOutput("color", new GLLabel("Mapped Color"));
		return node;
	}

	private static GLNode outputNode() {
		GLNode node = new GLNode("Output");
		node.addInput("surface", new GLLabel("Surface"));
		node.addInput("alpha", GLSlider.fixed("Alpha", 0, 1, 1));
		return node;
	}
}
