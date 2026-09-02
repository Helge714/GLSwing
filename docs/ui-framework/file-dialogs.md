# File dialogs

- [Artifacts and backend selection](#artifacts-and-backend-selection)
- [Creating a request](#creating-a-request)
- [Native SWT backend](#native-swt-backend)
- [Themed fallback](#themed-fallback)
- [Lifecycle and failures](#lifecycle-and-failures)

GLSwing exposes one asynchronous file-dialog model for native operating-system
dialogs and its theme-aware GPU fallback. Neither backend blocks the AWT event
dispatch thread or the OpenGL render thread.

## Artifacts and backend selection

The request, result, filter, and themed-backend APIs are part of `glswing`.
Native dialogs are supplied by the optional platform-aware artifact:

```xml
<dependency>
    <groupId>com.vingame</groupId>
    <artifactId>glswing-filedialogs-swt</artifactId>
    <version>0.1.0</version>
</dependency>
```

Choose a policy when creating the service:

```java
GLFileDialogs dialogs = GLFileDialogs.auto();
GLFileDialogs nativeOnly = GLFileDialogs.nativeDialogs();
GLFileDialogs themedOnly = GLFileDialogs.themed();
```

`auto()` discovers native backends through `ServiceLoader`. A technical native
backend failure opens the themed dialog instead. User cancellation is a normal
result and never triggers fallback. `nativeDialogs()` reports an exceptional
completion if no native backend is installed; it never substitutes AWT or
Swing file choosers.

## Creating a request

```java
GLFileDialogRequest request = GLFileDialogRequest.open()
        .title("Open texture")
        .initialDirectory(projectAssets)
        .selectionMode(GLFileSelectionMode.FILES)
        .multiple(true)
        .filter("Images", "png", "jpg", "webp")
        .build();

dialogs.show(request, owner).thenAccept(result -> {
    if (result.approved())
        result.selectedPaths().forEach(this::openTexture);
});
```

Save requests support a suggested filename, extension filters, and native or
themed overwrite confirmation:

```java
GLFileDialogRequest request = GLFileDialogRequest.save()
        .title("Export scene")
        .suggestedFileName("scene.json")
        .filter("JSON", "json")
        .confirmOverwrite(true)
        .build();
```

All returned paths are absolute and normalized. Cancellation produces
`approved() == false` and an empty path list.

## Native SWT backend

`glswing-filedialogs-swt` uses the matching SWT Windows, Cocoa, or GTK artifact
and delegates navigation, shell locations, platform previews, and native
overwrite behavior to the operating system. A single managed SWT event thread
and an `SWT_AWT` owner bridge are shared by the service instance. The bridge
keeps native modality associated with the GLSwing application window.

SWT exposes separate file and directory dialogs. A request that combines files
and directories is therefore unsupported by the native backend; `auto()` uses
the themed fallback for that request.

## Themed fallback

The built-in fallback is a `GLDialog` composed entirely from GLSwing controls.
It inherits the owner's theme and supports:

- file and directory navigation;
- Open, Save, and Save As request semantics;
- extension filters and default extensions;
- multiple-file result collection;
- current-directory selection;
- overwrite confirmation;
- asynchronous directory enumeration;
- search, hidden-file visibility, and new-folder creation;
- system-derived common places and filesystem roots;
- contextual open, copy, path-copy, rename, and recycle-bin actions.

Filesystem enumeration never runs on the render thread. Generation tokens
discard obsolete directory results after rapid navigation or search changes.
Selecting an entry and clicking its visible name again starts inline renaming.
Clicking outside the editor commits the name; changing a file extension first
opens a confirmation dialog. Clicking an unused area of the file list clears
the current entry selection. The fallback's custom text uses the
`fileDialog.textShadow.*` theme prefix and inherits the global text shadow when
no file-dialog-specific override is present.

When hidden entries are visible, the themed backend renders their names,
metadata, and icons with `fileDialog.hiddenText` and uses italic text by
default. Set `fileDialog.hiddenItalic=false` to retain the normal font style.
Themes that omit `fileDialog.hiddenText` receive a translucent variant of the
normal label color.

## Lifecycle and failures

Keep a service for the lifetime of the owning application and close it during
shutdown:

```java
try (GLFileDialogs dialogs = GLFileDialogs.auto()) {
    // Show any number of dialogs.
}
```

Native initialization, filesystem access, and decoding errors complete the
returned stage exceptionally. In `AUTO` mode only native backend failures are
converted into a themed fallback. Application code should still observe the
returned stage and report unrecoverable themed-dialog errors.
