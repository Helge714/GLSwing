package com.vingame.glswing.test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import com.vingame.glswing.rendering.GLSvgDocument;

/** Dependency-free checks for SVG resources, view boxes, definitions and clips. */
public final class GLSvgDocumentSelfTest {

	private static final String ICON_RESOURCE =
			"com/vingame/glswing/icons/test_icon.svg";

	private GLSvgDocumentSelfTest() {
	}

	public static void main(String[] args) {
		GLSvgDocument icon = GLSvgDocument.loadResource(ICON_RESOURCE);
		assertNear("icon viewBox width", 256f, icon.getViewBoxWidth());
		assertNear("icon viewBox height", 256f, icon.getViewBoxHeight());
		if (icon.getShapeCount() != 3)
			throw new AssertionError("Expected three compiled icon shapes, got "
					+ icon.getShapeCount());

		String inline = """
				<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 50">
				  <defs>
				    <clipPath id="half"><path d="M0 0H50V50H0Z"/></clipPath>
				  </defs>
				  <g transform="translate(5 0)" clip-path="url(#half)">
				    <path style="fill:#ff8040;fill-rule:evenodd" d="M0 0H100V50H0Z"/>
				  </g>
				</svg>
				""";
		GLSvgDocument parsed = GLSvgDocument.parse(new ByteArrayInputStream(
				inline.getBytes(StandardCharsets.UTF_8)));
		if (parsed.getShapeCount() != 1)
			throw new AssertionError("Inline clipped SVG did not compile");
		System.out.println("GLSvgDocument self-test passed.");
	}

	private static void assertNear(String name, float expected, float actual) {
		if (Math.abs(expected - actual) > 0.01f)
			throw new AssertionError(name + ": expected " + expected + ", got " + actual);
	}
}
