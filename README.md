# GLSwing

GLSwing is a GPU-accelerated 2D rendering and user-interface library for Java.
It combines a `Graphics2D`-inspired drawing API with JOGL-based batching,
high-quality text, retained UI components, GPU picking, off-screen rendering,
layer effects, and a node-editor toolkit.

The library can run as a standalone OpenGL application, inside a conventional
`JFrame`, or as an OpenGL-backed component in an existing Swing interface.

> **Project status:** GLSwing 0.1.0 is an early-access release. The library is
> usable, but its API may change between releases while the public surface is
> refined.

## Highlights

- batched shape, stroke, image, text, UI, and 2.5D sprite rendering;
- logical-pixel coordinates with HiDPI-aware output;
- paths, cubic Bézier curves, SVG import, dashed strokes, caps, and joins;
- crisp MSDF text and icons plus automatic RGBA texture atlases;
- model transforms, projections, cameras, depth testing, normal maps, and light;
- color-ID and object-target picking with pointer capture;
- reusable recordings and GPU-resident sprite buffers;
- nested layers with opacity, blend modes, blur, shadows, outlines, and clips;
- persistent render targets and ping-pong feedback for GPU simulations;
- retained UI controls, virtualized lists, layouts, themes, localization,
  dialogs, and undo/redo;
- an interactive node-editor foundation with ports and Bézier connections.

## Requirements

- Java 17 or newer;
- a desktop OpenGL implementation supported by JOGL 2.6.0;
- Maven 3.9.11 or the included Maven Wrapper when building from source.

GLSwing targets Java 17 bytecode. A newer JDK can build the project because the
Maven compiler is configured with `--release 17`.

## Installation

### Build and install from source

Clone the repository and install the library into the local Maven repository:

```bash
./mvnw clean install
```

On Windows:

```powershell
mvnw.cmd clean install
```

Then add GLSwing to the application POM:

```xml
<dependency>
    <groupId>com.vingame</groupId>
    <artifactId>glswing</artifactId>
    <version>0.1.0</version>
</dependency>
```

JOGL, GlueGen, and their platform-specific native libraries are declared as
transitive Maven dependencies. No manually managed `jogamp-fat.jar` is needed.

### Use the release JAR directly

`glswing-0.1.0.jar` is the runtime library and can be added to a Java project's
classpath like any other JAR. When Maven is not used, the JOGL and GlueGen
runtime JARs, including the native JARs for the target platform, must also be
present.

The optional artifacts improve IDE integration:

- `glswing-0.1.0-sources.jar` provides navigable source code;
- `glswing-0.1.0-javadoc.jar` provides offline API documentation.

Language Server Protocol support is kept in a separate optional artifact so
applications that do not embed a code editor do not carry its dependencies:

```xml
<dependency>
    <groupId>com.vingame</groupId>
    <artifactId>glswing-lsp</artifactId>
    <version>0.1.0</version>
</dependency>
```

Native operating-system file dialogs are likewise optional. Add
`glswing-filedialogs-swt` to use SWT-native Windows, macOS, or Linux dialogs;
without it, `GLFileDialogs.auto()` uses the themed GLSwing file browser.

The main artifact declares
`Automatic-Module-Name: com.vingame.glswing` and works on both the classpath and
the module path as an automatic module.

## Quick start

`GLApplication` provides a managed standalone window, startup splash, shared
configuration loading, root component hierarchy, first-frame handoff, and
orderly shutdown:

```java
import java.awt.Color;

import com.vingame.glswing.application.GLApplication;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.layout.GLGridLayout;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLButton;
import com.vingame.glswing.ui.GLLabel;

public final class HelloGLSwing {
    public static void main(String[] args) {
        GLApplication.builder()
                .title("GLSwing")
                .size(900, 600)
                .clearColor(new Color(20, 24, 32))
                .surface(context -> {
                    GLContainer root = new GLContainer() {
                        @Override protected void record(GLContext2D g) { }
                    };
                    root.setGLLayout(new GLGridLayout(1).withGap(8));
                    root.setGLPadding(16);

                    GLLabel status = new GLLabel("Ready");
                    GLButton button = new GLButton("Start");
                    button.addActionListener(event -> status.setText("Running"));
                    root.addGL(status, GLConstraints.auto().fillHorizontal());
                    root.addGL(button, GLConstraints.auto().fillHorizontal());
                    return root;
                })
                .launch();
    }
}
```

For immediate-mode rendering, subclass `GLSwingCanvas` or `GLSwingPanel` and
implement its rendering callback with `GLContext2D`. For a retained interface,
compose `GLContainer` and the standard controls, then attach the root container
with `addGL(...)`.

## Documentation

The [user guide](docs/README.md) is the entry point for the complete
documentation:

- [Rendering guide and `GLContext2D` reference](docs/graphics-pipeline.md)
- [UI framework and component reference](docs/ui-framework.md)
- [Application startup and splash screens](docs/ui-framework/application-startup.md)
- [Examples and recommended learning paths](docs/README.md#examples)
- [Release notes](CHANGELOG.md)

The generated Javadoc is the authoritative member-level API reference. The
guides explain concepts, units, lifecycle rules, interactions between systems,
and recommended usage patterns.

## Examples

The `glswing-examples` module contains complete applications for drawing,
recording, custom shaders, layers, image atlases, render targets, picking,
2.5D sprites, UI controls, and node editing. Build it together with the library:

```bash
./mvnw -pl glswing-examples -am package
```

Useful starting points include:

| Example | Demonstrates |
|---|---|
| `GLFrameDemo` | Managed startup, splash screen, and standalone window |
| `DrawingApiDemo` | Shapes, strokes, and per-vertex colors |
| `GuiShowcaseDemo` | Managed startup plus controls, layouts, dialogs, and a selectable external color/metrics/font bundle |
| `CustomShaderDemo` | Custom rendering programs and uniforms |
| `RecordingParameterDemo` | Reusable recorded drawing commands |
| `LayerDemo` | Group compositing and effects |
| `ImageAtlasDemo` | Images, icons, atlases, and adaptive SVG rasterization |
| `DepthSpriteDemo` | Sprite depth, normal maps, lighting, and picking |
| `NodeEditorDemo` | Nodes, ports, selection, and connections |
| `GameOfLifeDemo` | Render-target feedback entirely on the GPU |

## Building release artifacts

The project is a Maven reactor with four modules:

| Module | Artifact |
|---|---|
| `glswing-core` | Runtime library, resources, and tests |
| `glswing-lsp` | Optional Language Server Protocol integration |
| `glswing-filedialogs-swt` | Optional native file-dialog backend |
| `glswing-examples` | Demonstrations and benchmarks |

Run the release profile to create the binary, source, and Javadoc JARs:

```bash
./mvnw -Prelease clean verify
```

Each module writes its artifacts to its own `target` directory. Applications
need only `glswing-core`; add the LSP or SWT artifact when that integration is
required. The examples module is not a runtime dependency.

## License

GLSwing is available under the [Apache License 2.0](LICENSE).
