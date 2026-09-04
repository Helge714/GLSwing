# Application Startup

[UI guide](../ui-framework.md) · [Next: Components](components.md)

`GLApplication` is the standard entry point for standalone GLSwing programs.
It creates the selected OpenGL host, presents a startup window, loads shared
configuration, builds the retained component tree, and replaces the startup
window only after the first application frame has been presented.

## Contents

- [Minimal application](#minimal-application)
- [Automatic startup work](#automatic-startup-work)
- [Splash configuration](#splash-configuration)
- [Custom splash layout](#custom-splash-layout)
- [Custom startup tasks](#custom-startup-tasks)
- [Using prepared results](#using-prepared-results)
- [Thread affinity](#thread-affinity)
- [Progress and task weights](#progress-and-task-weights)
- [Failures and fallbacks](#failures-and-fallbacks)
- [Application lifetime](#application-lifetime)
- [Close confirmation](#close-confirmation)
- [Choosing a backend](#choosing-a-backend)

## Minimal application

The surface factory returns one root `GLContainer`. GLSwing sizes that root to
the complete rendering area and keeps it synchronized with window resizing.

```java
GLApplicationHandle application = GLApplication.builder()
        .title("Media Library")
        .backend(GLBackend.GLJPANEL)
        .size(1100, 720)
        .surface(context -> new LibrarySurface(context))
        .launch();
```

`launch()` returns immediately. Application setup continues on the appropriate
threads while the startup window remains responsive.

## Automatic startup work

Every launch includes the following ordered stages:

1. load the selected theme, including imported fragments and external fonts;
2. load the semantic input map;
3. create the localizer and add application resource bundles;
4. execute application-defined preparation tasks;
5. build and attach the retained interface on the AWT event-dispatch thread;
6. render and confirm the first complete frame while the native window remains
   hidden;
7. show the prepared application window.

These stages are published to the splash screen automatically. Applications do
not need to manually increment progress for standard GLSwing initialization.
Resources created by individual controls while the tree is built are covered by
the interface-building stage.

```java
GLApplication.builder()
        .title("Media Library")
        .theme(Path.of("config", "application.gltheme"), GLThemes.DARK)
        .inputMap(Path.of("config", "application.glinput"), GLInputMaps.DEFAULT)
        .locale(Locale.GERMAN)
        .localizationBundle("com.example.media.Messages")
        .surface(LibrarySurface::new)
        .launch();
```

The second argument of `theme(...)` and `inputMap(...)` is the fallback used if
an external file cannot be read or parsed. Omitting the paths uses the default
theme and default input map.

## Splash configuration

The standard splash can show branding, application text, aggregate progress,
and task states. Its layout is independent of the application theme because it
is displayed before that theme has necessarily been loaded.

```java
.splash(splash -> splash
        .backgroundImage(Path.of("assets", "splash.png"))
        .backgroundImageFit(GLSplashImageFit.COVER)
        .backgroundOverlay(new Color(0, 0, 0, 96))
        .size(720, 420)
        .padding(32)
        .contentAlignment(GLSplashAlignment.BOTTOM_LEFT)
        .addLogo(Path.of("assets", "logo.png"), 96, 96)
        .addTitle("Media Library")
        .addSubtitle("Preparing your workspace")
        .addGap(24)
        .addTaskHistory(400, 4, 2, 0.25, true)
        .addProgressBar(false)
        .minimumDisplayMillis(500)
        .handoffDelayMillis(1000))
```

The standard splash is an ordered content recipe. The first explicit
`add...()` call replaces the built-in recipe, and subsequent calls appear in
declaration order. If no elements are added, GLSwing supplies a complete
default containing the application title, subtitle, current task, progress bar,
and recent history. `useDefaultElements()` discards an explicit recipe and
restores that default.

| Method | Element |
|---|---|
| `addTitle(text)` | Primary heading |
| `addSubtitle(text)` | Secondary text |
| `addLogo(path, width, height)` | Aspect-preserving image in a fixed box |
| `addCurrentTask()` | Active task and its detail text |
| `addTaskHistory(...)` | Fixed past/current/upcoming task window |
| `addProgressBar()` | Aggregate progress filling the content width |
| `addProgressBar(width)` | Aggregate progress with a fixed width |
| `addProgressBar(usePadding)` | Aggregate progress inside the content stack, or edge-to-edge at the window bottom when `false` |
| `addGap(height)` | Fixed vertical separation |
| `addComponent(factory)` | Application-defined Swing component |

`backgroundImageFit(...)` controls image scaling:

| Value | Behavior |
|---|---|
| `COVER` | Preserves the aspect ratio, fills the splash, and crops excess image area |
| `CONTAIN` | Preserves the aspect ratio and keeps the complete image visible |
| `STRETCH` | Fills the splash without preserving the aspect ratio |

The configured background color remains visible when no background image is
available and supplies the letterbox area in `CONTAIN` mode. A translucent
`backgroundOverlay(...)` is painted above the image and below the content; it
is useful for maintaining text contrast across detailed artwork.

`contentAlignment(...)` accepts all nine combinations from `TOP_LEFT` through
`BOTTOM_RIGHT`. Window dimensions and internal padding are configured with
`size(...)` and `padding(...)`. Logo dimensions belong to `addLogo(...)`, and
progress width belongs to `addProgressBar(...)`. `addProgressBar(false)` is
the explicit exception to recipe flow: it docks at the unpadded lower window
edge while all other elements remain in declaration order.

Task history is configured directly when it is added:

```java
.addTaskHistory(
        400,  // width
        4,    // completed tasks above the active task
        2,    // pending tasks below the active task
        0.25, // fade target immediately beyond the visible range
        true  // show status markers
)
```

Omit the width to let the history fill the content width. Empty slots are
retained near the beginning and end of startup, so the active task never
changes its visual position. Setting both task counts to zero produces a
current-task-only history.

The fade argument controls symmetric fading away from the active row. Its value
belongs to the first task immediately beyond each visible range; the outermost
visible task therefore remains slightly more opaque. With seven past rows and
a target of `0.0`, for example, the seventh row retains one eighth opacity and
the omitted eighth row would be fully transparent. Intermediate rows are
interpolated linearly. `1.0` disables fading and `0.5` fades toward half opacity.
This bounded presentation requires no scrollbar and keeps the startup layout
stable regardless of the total number of tasks.

Add `addCurrentTask()` only when a dedicated status line is useful in addition
to the active history row. The final `addTaskHistory(...)` argument controls
pending, active, completed, and failed symbols without changing colors or
fading. When enabled, markers occupy a fixed-width centered column, so every
task label shares the same horizontal origin regardless of glyph width.

The minimum duration prevents a very short launch from producing a distracting
flash. It does not delay work and is measured from the moment the splash becomes
visible. The handoff delay starts after the prepared application window is
revealed and keeps the completed splash above it for a deliberate transition.
It is disabled by default. `GLSplashConfiguration.Builder#colors(...)` can
set the background, primary text, secondary text, and accent colors before the
normal application theme is available.

## Custom splash layout

Use `splashView(...)` when application branding requires a layout beyond the
standard preset. The factory runs on the AWT event-dispatch thread and returns
an ordinary Swing component, so standard Swing layout managers, custom painting,
and application-specific components remain available:

```java
.splash(splash -> splash
        .size(720, 420))
.splashView(splash -> {
    JPanel root = splash.backgroundPanel(new BorderLayout(24, 16));
    root.setBorder(BorderFactory.createEmptyBorder(32, 32, 0, 32));

    JPanel information = new JPanel();
    information.setOpaque(false);
    information.setLayout(new BoxLayout(information, BoxLayout.Y_AXIS));
    information.add(splash.title("Media Library"));
    information.add(splash.subtitle("Preparing your workspace"));
    information.add(Box.createVerticalStrut(24));
    information.add(splash.currentTask());
    information.add(Box.createVerticalStrut(12));
    information.add(splash.taskHistory(400, 3, 1, 0.35, true));

    root.add(splash.logo(Path.of("assets", "logo.png"), 96, 96),
            BorderLayout.WEST);
    root.add(information, BorderLayout.CENTER);
    root.add(splash.progressBar(), BorderLayout.SOUTH);
    return root;
})
```

The context creates titles, subtitles, logos, current-task labels, histories,
and progress bars as ready-to-place components. Stateful elements remain bound
to startup snapshots without application listeners or manual thread switching.
As with all Swing components, each supplied instance can belong to only one
parent. Multiple histories or progress bars may be created and are updated
together. `snapshot()` exposes the latest immutable progress state for
additional custom presentation.

For a custom view, simply omit any element that is not required. History
markers, visible ranges, fade behavior, and fixed width are arguments of the
created history component rather than global splash flags.

`backgroundPanel(layout)` is optional. It creates a panel that paints the
configured background color, background image, scaling mode, and overlay before
painting its children. A custom view may instead return any other `JComponent`.
Window creation, task execution, progress publication, visible failure handling,
minimum display time, and first-frame handoff remain owned by `GLApplication`.
The standard splash itself is assembled from these same bound components.

## Custom startup tasks

Use `prepare(...)` for application work that must complete before the surface is
created. Tasks are executed in declaration order.

```java
.prepare(startup -> {
    startup.provide("catalog", "Loading catalog", task ->
            Catalog.load(Path.of("data", "catalog.json")));

    startup.background("Preparing thumbnails", 2.0, task -> {
        for (int index = 0; index < files.size(); index++) {
            thumbnails.prepare(files.get(index));
            task.progress(index + 1, files.size());
            task.detail(files.get(index).getFileName().toString());
        }
        return null;
    });
})
```

Use `background(...)` for work that does not produce a shared value. Use
`provide(key, ...)` when later startup tasks or the surface need the result.
Keys must be unique within an application launch.
Values produced by `provide(...)` must be non-null.

## Using prepared results

A later task reads an earlier result through its `GLStartupTaskContext`:

```java
startup.provide("index", "Building search index", task -> {
    Catalog catalog = task.require("catalog", Catalog.class);
    return SearchIndex.create(catalog);
});
```

The surface receives the same typed results through `GLApplicationContext`:

```java
.surface(context -> {
    Catalog catalog = context.require("catalog", Catalog.class);
    SearchIndex index = context.require("index", SearchIndex.class);
    return new LibrarySurface(catalog, index);
})
```

The application context also exposes the `JFrame`, root `GLParent`, resolved
theme, input map, and localizer.

## Thread affinity

Each task declares where it must execute:

| Method | Execution context | Suitable work |
|---|---|---|
| `background(...)` and `provide(...)` | Startup worker | Files, parsing, network clients, indexes, and CPU preparation |
| `awt(...)` | AWT event-dispatch thread | Swing state and operating-system window integration |
| `gl(...)` | Active OpenGL context | Explicit GPU resource creation that cannot remain lazy |

```java
startup.awt("Registering desktop integration", task -> {
    installDesktopHandlers();
    return null;
});

startup.gl("Uploading fixed atlas", task -> {
    GL3 gl = task.requireGL();
    atlas.upload(gl);
    return null;
});
```

Do not perform file or network I/O in AWT or GL tasks. GLSwing realizes the
native host without showing it so explicit GL tasks have a valid context while
the splash remains the only visible window.

## Progress and task weights

Every task contributes its declared weight to aggregate progress. The default
weight is `1.0`; use a larger weight for work that normally occupies a larger
share of startup time. Weights are relative rather than durations.

A task is indeterminate until it calls `progress(...)`. It becomes complete
when its operation returns, even if its last explicit progress value was below
one. The standard splash still shows the aggregate completed weight while a
later atomic task is running; only the first unresolved task uses an
indeterminate animation. `detail(...)` changes the optional status text without
changing progress.

Published `GLStartupSnapshot` values are immutable and contain aggregate
progress plus the label, state, detail, and local progress of every task.

## Failures and fallbacks

Invalid external theme and input-map files use their configured fallbacks. The
splash identifies that fallback in the task detail, and startup continues.

Other task failures stop subsequent work, keep the error visible in the splash,
and complete `GLApplicationHandle#readyFuture()` exceptionally. The default
failure handler also writes the exception to the diagnostic stream. An
application can add logging or telemetry without replacing the visible error:

```java
.onFailure(error -> applicationLog.error("Startup failed", error))
```

The failed window can be closed from the splash. Partially initialized custom
resources should either be immutable managed values or be closed by the custom
failure handler.

## Application lifetime

`GLApplicationHandle` separates launch from readiness:

```java
GLApplicationHandle handle = builder.launch();
handle.readyFuture().thenAccept(context -> startExternalEvents());
handle.closedFuture().thenRun(backgroundExecutor::shutdown);
```

Closing the native application window disposes the rendering host and completes
`closedFuture()`. Calling `handle.close()` performs the same orderly shutdown.
The splash closes only after the root tree has rendered and the host has
confirmed its first successful frame. The native application window remains
visually hidden during that render and becomes visible only when the prepared
frame is available; surface construction alone is not treated as readiness.
GLSwing temporarily uses window opacity where the platform supports it and an
off-screen native position as the compatibility fallback, because JOGL does not
guarantee presentation callbacks for a window that has never been shown.

Use `onClose(...)` for application resources that must be released while the
rendering host is still valid:

```java
.onClose(context -> {
    themeWatcher.close();
    languageServer.close();
})
```

## Close confirmation

`onCloseRequest(...)` installs an asynchronous close guard for both the native
window close control and `GLApplicationHandle.close()`. Complete with `true` to
continue into `onClose(...)`, or `false` to keep the fully initialized window
open. Repeated close requests share the pending decision and do not create
duplicate dialogs.

```java
.onCloseRequest(context -> documentLifecycle.prepareCloseAll())
.onClose(context -> resources.close())
```

This supports themed confirmations and asynchronous save-as dialogs without
blocking the AWT event-dispatch thread. A failed or `null` decision also leaves
the application open.

## Choosing a backend

- `GLBackend.GLJPANEL` integrates naturally with Swing's lightweight component
  hierarchy and is the default.
- `GLBackend.CANVAS` uses direct, event-driven presentation. On current
  Java runtimes it requires the VM module-opening arguments documented in
  [Components and hosts](components.md#glframe).

Both backends use the same startup plan, application context, retained root
surface, progress model, and lifetime handle.

`GuiShowcaseDemo` and `CanvasGuiShowcaseDemo` use this launcher with the same
eagerly constructed showcase surface. They demonstrate the automatic resource
stages, application preparation, shutdown hooks, and first-frame handoff on
both backends without lazy tab construction.
