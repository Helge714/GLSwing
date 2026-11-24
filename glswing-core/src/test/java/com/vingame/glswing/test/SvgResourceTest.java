package com.vingame.glswing.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Exercises the original demo SVG as vector geometry and through both GPU icon paths. */
class SvgResourceTest {
    @Test void parsesDemoArtwork() { GLSvgDocumentSelfTest.main(new String[0]); }

    @Test @EnabledIfSystemProperty(named="glswing.test.gl", matches="true")
    void rendersClippedGradientAndPaths() { PathGpuSelfTest.main(new String[0]); }

    @Test @EnabledIfSystemProperty(named="glswing.test.gl", matches="true")
    void rendersRgbaAndMsdfAtlasIcons() { ImageAtlasGpuSelfTest.main(new String[0]); }
}
