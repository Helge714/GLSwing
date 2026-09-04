package com.vingame.glswing.demo;

/** Source-root fixture used by the showcase's live language-server editor. */
public final class HelloGLSwing {
	private HelloGLSwing() { }

	public static void main(String[] args) {
		Greeting greeting = new DefaultGreeting();
		System.out.println(greeting.message());
		// Hold Ctrl over a symbol to inspect its available navigation actions.
		GuiShowcaseDemo.main(args);
		// Place the caret after the dot and press Ctrl+Space.
		System.out.println();
	}

	private interface Greeting {
		String message();
	}

	private static final class DefaultGreeting implements Greeting {
		@Override public String message() {
			return "Hello, GLSwing";
		}
	}
}
