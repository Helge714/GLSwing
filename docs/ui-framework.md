# UI Framework Guide

GLSwing's retained UI framework is built on top of `GLContext2D`. A
`GLContainer` owns state and bounds, participates in a `GLParent` hierarchy,
and caches stable visuals in a recording. Standard controls, popup overlays,
dialogs, and node editors use the same component model.

For shapes, shaders, layers, render targets, resources, and custom picking,
see the [rendering guide](graphics-pipeline.md).

## Contents

- [API map](#api-map)
- [Coordinates and units](#coordinates-and-units)
- [Minimal application](#minimal-application)
- [Detailed guides](#detailed-guides)

## API map

| Area | Main types | Responsibility |
|---|---|---|
| Application startup | `GLApplication`, `GLStartupPlan`, `GLApplicationContext`, `GLApplicationHandle` | Splash, resources, startup work, first-frame handoff, and lifetime |
| Hosts | `GLFrame`, `GLSwingContainerCanvas`, `GLSwingPanel`, `GLDialog` | OpenGL surface, animation, root hierarchy, and windows |
| Component tree | `GLParent`, `GLContainer` | Children, bounds, z-order, culling, damage, and cached rendering |
| Layout | `GLLayout`, `GLLayouts`, `GLGridLayout`, `GLConstraints`, `GLSize`, `GLInsets` | Measurement and arrangement |
| Basic controls | `GLControl`, `GLLabel`, `GLButton`, `GLCheckBox`, `GLLine` | State, focus, actions, and simple content |
| Value input | `GLSlider`, `GLSliderGroup`, `GLTextEditor`, `GLTextInputFormats`, `GLComboBox<T>` | Selection, validation, and editing |
| Navigation | `GLMenuBar`, `GLPopupMenu`, `GLMenuItem`, `GLTabbedPane` | Menus, overlays, and view selection |
| File access | `GLFileDialogs`, `GLFileDialogRequest` | Native SWT dialogs with a themed GLSwing fallback |
| Documents | `GLDocument<T>`, `GLDocumentManager`, `GLDocumentLifecycle<T>`, `GLDocumentBundle`, `GLDocumentWorkspace` | Typed models, commands, active history routing, codecs, migration, and atomic persistence |
| Scrolling | `GLScrollPane`, `GLScrollBar` | Clipped viewports and scroll models |
| Data views | `GLList<T>`, `GLListModel<T>`, `GLListSelectionModel` | Virtualized rows, renderers, and selection |
| Appearance | `GLTheme`, style records, `GLThemeValues`, `GLThemeLoader`, `GLThemeWatcher` | Global and local presentation |
| Localization | `GLLocalizer`, `GLUiKeys` | UTF-8 bundles, locale fallback, and overrides |
| History | `GLHistoryManager`, `GLUndoableEdit` | Scoped undo, redo, transactions, and persisted-state tracking |
| Node editing | `GLNodeEditor`, `GLNode`, `GLNodePort`, `GLNodeConnection` | Zoomable graphs, connections, and selection |

## Coordinates and units

The UI uses logical pixels. Bounds, positions, preferred sizes, padding,
margins, layout gaps, and most style measurements share the same local
coordinate system. Hosts apply operating-system scaling and the configured
render-resolution scale.

Every child starts at local `(0, 0)`. Its position is applied relative to the
parent during rendering. Cached recordings therefore remain position
independent and can be replayed after movement without retessellating geometry.

Important exceptions:

- node connection widths, borders, and snapping distances use screen space so
  that interaction remains stable while zooming;
- node-editor pan is expressed in logical screen pixels;
- `GLFrame#setRenderResolutionScale(float)` changes the internal framebuffer
  resolution, not component layout or input coordinates.

## Minimal application

```java
GLApplication.builder()
        .title("GLSwing")
        .size(900, 600)
        .surface(context -> {
            GLContainer root = new GLContainer() {
                @Override protected void record(GLContext2D g) { }
            };
            root.setGLLayout(new GLGridLayout(1).withGap(8));
            root.setGLPadding(12);

            GLLabel status = new GLLabel("Ready");
            GLButton start = new GLButton("Start");
            start.addActionListener(event -> status.setText("Running"));
            root.addGL(status, GLConstraints.auto().fillHorizontal());
            root.addGL(start, GLConstraints.auto().fillHorizontal());
            return root;
        })
        .launch();
```

`record(...)` describes only the cacheable appearance owned by the container.
The framework renders children afterward; applications do not call child
render methods manually.

## Detailed guides

1. [Application startup](ui-framework/application-startup.md)
2. [Components and hosts](ui-framework/components.md)
3. [Layouts and sizing](ui-framework/layout.md)
4. [Standard controls](ui-framework/controls.md)
5. [Themes and localization](ui-framework/themes-localization.md)
6. [Input configuration](ui-framework/input-configuration.md)
7. [Undo and redo](ui-framework/history.md)
8. [Documents and serialization](ui-framework/documents.md)
9. [Node editor](ui-framework/nodes.md)
