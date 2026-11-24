# GLSwing

GLSwing is a small JOGL-based, GPU-accelerated 2D drawing surface for Swing. It
currently supports batched filled polygons, lines and textures, transforms,
framebuffers, stencil operations and an integer ID buffer for GPU picking.

## Interactive demo

In Eclipse, run
`com.vingame.glswing.example.GLSwingDemo` as a Java application. Move the mouse
over the three shapes to verify color rendering, resizing and ID picking. Resize
the window and verify that picking remains aligned with the shapes.

## Core smoke tests

Run `com.vingame.glswing.test.CoreSelfTest` as a Java application. The tests do
not require JUnit or an OpenGL window and print
`GLSwing core self-tests passed.` on success.

Run `com.vingame.glswing.test.RenderingSmokeTest` to open a small JOGL window
and verify a real pixel in both the color buffer and GPU picking buffer. The
window closes automatically when the check finishes.

When launching manually as a module, use assertions for the tests:

```text
java -ea -m GLSwing/com.vingame.glswing.test.CoreSelfTest
java -m GLSwing/com.vingame.glswing.example.GLSwingDemo
```

## Minimal drawing API

Subclass `GLSwingPanel` and implement `setupDisplay(GLContext)`. Call
`ortho(getWidth(), getHeight())` for pixel coordinates and
`enableIdRendering()` when shapes should be selectable through `lastID`.
