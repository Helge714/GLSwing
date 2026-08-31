package com.vingame.glswing.test;

import org.junit.jupiter.api.Test;

/**
 * Makes the fast, platform-independent main-based self-tests part of the
 * standard Maven lifecycle. GPU and Robot tests remain explicit because they
 * require a suitable graphics/display environment.
 */
final class MavenSmokeTest {
	private static final String[] NO_ARGS = {};

	@Test
	void transforms() {
		GLTransformSelfTest.main(NO_ARGS);
	}

	@Test
	void colorInterpolation() {
		GLColorInterpolationSelfTest.main(NO_ARGS);
	}

	@Test
	void paths() {
		GLPath2DSelfTest.main(NO_ARGS);
	}

	@Test
	void polygonTessellation() {
		PolygonTessellatorSelfTest.main(NO_ARGS);
	}

	@Test
	void strokeTessellation() {
		StrokeTessellatorSelfTest.main(NO_ARGS);
	}

	@Test
	void layouts() {
		GLLayoutSelfTest.main(NO_ARGS);
	}

	@Test
	void localizationAndThemes() throws Exception {
		GLLocalizationThemeSelfTest.main(NO_ARGS);
	}

	@Test
	void historyManager() {
		GLHistoryManagerSelfTest.main(NO_ARGS);
	}

	@Test
	void inputMaps() throws Exception {
		GLInputMapSelfTest.main(NO_ARGS);
	}

	@Test
	void configurationEditors() throws Exception {
		GLConfigurationEditorSelfTest.main(NO_ARGS);
	}

	@Test
	void textEditor() {
		GLTextEditorSelfTest.main(NO_ARGS);
	}

	@Test
	void textArea() {
		GLTextAreaSelfTest.main(NO_ARGS);
	}

	@Test
	void sliderGroup() {
		GLSliderGroupSelfTest.main(NO_ARGS);
	}

	@Test
	void controlsAndFocus() {
		GLControlsSelfTest.main(NO_ARGS);
	}

	@Test
	void menus() {
		GLMenuSelfTest.main(NO_ARGS);
	}

	@Test
	void scrollingAndTabs() {
		GLScrollTabsSelfTest.main(NO_ARGS);
	}

	@Test
	void nodes() {
		GLNodeSelfTest.main(NO_ARGS);
	}
}
