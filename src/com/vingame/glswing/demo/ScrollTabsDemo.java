package com.vingame.glswing.demo;

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.SwingUtilities;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLFrame;
import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.layout.GLGridLayout;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLLabel;
import com.vingame.glswing.ui.GLScrollPane;
import com.vingame.glswing.ui.GLTabbedPane;

/** Interactive showcase for general scroll panes, scrollbars and tabs. */
public final class ScrollTabsDemo {
	private ScrollTabsDemo() {
	}

	public static void main(String[] args) {
		GLDemoErrorReporter.install("Scroll & Tabs Demo");
		SwingUtilities.invokeLater(ScrollTabsDemo::show);
	}

	private static void show() {
		GLFrame frame = new GLFrame("GLSwingNext – ScrollPane & Tabs",
				GLFrame.RenderingSettings.DEFAULT.withTargetFps(144));
		frame.setMinimumSize(new Dimension(620, 420));
		frame.setSize(920, 640);
		frame.setLocationRelativeTo(null);
		frame.setGLClearColor(new Color(19, 24, 34));
		frame.setGLLayout(new GLGridLayout());
		frame.setGLPadding(new GLInsets(20, 20, 20, 20));

		GLTabbedPane tabs = new GLTabbedPane();
		tabs.addTab("Vertikale Liste", new GLScrollPane(buttonList()));
		tabs.addTab("Beide Richtungen", new GLScrollPane(wideContent()));
		tabs.addTab("Verschachtelt", nestedScrollPane());
		for (int index = 4; index <= 10; index++)
			tabs.addTab("Tab " + index, simplePage(
					"Zusätzlicher Inhalt von Tab " + index));
		tabs.setEnabledAt(4, false);
		tabs.addSelectionListener((source, oldIndex, newIndex) ->
				frame.setTitle("GLSwingNext – ausgewählter Tab " + (newIndex + 1)));
		frame.addGL(tabs, GLConstraints.auto().weight(1, 1).fillBoth());
		frame.setVisible(true);
	}

	private static GLContainer buttonList() {
		GLContainer list = emptyContainer();
		list.setPreferredGLSize(720, 920);
		GLLabel explanation = new GLLabel(
				"Mausrad: vertikal · Shift+Mausrad: horizontal");
		explanation.setBounds(18, 16, 500, 24);
		list.addGL(explanation);
		for (int index = 0; index < 28; index++) {
			GLButton button = new GLButton("Listeneintrag " + (index + 1));
			button.setBounds(18 + index % 2 * 342,
					54 + index / 2 * 58, 320, 34);
			list.addGL(button);
		}
		return list;
	}

	private static GLContainer wideContent() {
		GLContainer content = new GLContainer() {
			@Override
			protected void record(GLContext2D context) {
				for (int row = 0; row < 5; row++)
					for (int column = 0; column < 8; column++) {
						int red = 55 + column * 18;
						int green = 70 + row * 24;
						context.fillRoundRect(24 + column * 160,
								24 + row * 105, 136, 80, 10,
								new Color(Math.min(220, red),
										Math.min(210, green), 145));
					}
			}
		};
		content.setPreferredGLSize(1340, 580);
		return content;
	}

	private static GLContainer nestedScrollPane() {
		GLContainer outerContent = emptyContainer();
		outerContent.setPreferredGLSize(760, 720);
		GLLabel title = new GLLabel("Ein ScrollPane kann selbst wieder ScrollPanes enthalten");
		title.setBounds(18, 14, 580, 24);
		outerContent.addGL(title);
		GLScrollPane inner = new GLScrollPane(wideContent());
		inner.setBounds(18, 54, 690, 360);
		outerContent.addGL(inner);
		for (int index = 0; index < 5; index++) {
			GLButton button = new GLButton("Außen liegender Button " + (index + 1));
			button.setBounds(18, 450 + index * 48, 300, 30);
			outerContent.addGL(button);
		}
		return new GLScrollPane(outerContent);
	}

	private static GLContainer simplePage(String text) {
		GLContainer page = emptyContainer();
		GLLabel label = new GLLabel(text);
		label.setBounds(20, 20, 420, 28);
		page.addGL(label);
		return page;
	}

	private static GLContainer emptyContainer() {
		return new GLContainer() {
			@Override protected void record(GLContext2D context) { }
		};
	}
}
