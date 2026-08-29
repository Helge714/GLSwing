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
}
