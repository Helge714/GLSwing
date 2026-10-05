# Reusable application services

These APIs belong to `glswing-core`; they do not depend on an application graph,
audio runtime or preference namespace.

## Labels

`GLLabel.setToolTipText(...)` enables picking across the label rectangle when
non-empty. It does not enable selection or keyboard focus. Clearing the tooltip
removes this hover target unless selection is enabled. Rendering, ellipsis,
vertical/horizontal alignment and theme shadows use the normal label renderer.
A specialized row may override `textPadding(GLFont)` without drawing its own text.

## Images

Prepare a component's logical bounds on its owning UI thread, then call
`GLComponentImageCapture.capture(component, logicalWidth, logicalHeight,
pixelWidth, pixelHeight, transparent)`. The method creates/disposes an offscreen
GL context and invalidates renderer-owned recordings before and after capture.
It does not clone a live document, configure graph navigation or resize the
component. Do not mutate the component concurrently with capture. Use a detached
presentation tree if the live component has context-specific resources.

`SizeLimitException.maximumSide()` identifies a GPU limit; an application may
translate it into its own dialog. JOGL may wrap it in a GL exception.

`GLImageExport.encode(...)` writes to a caller-owned stream.
`GLImageExport.write(...)` uses a temporary sibling and replaces the destination
only after encoding succeeds. PNG preserves alpha. JPEG composites alpha over
white and accepts quality 0.01 through 1. Unknown formats produce
`EncoderUnavailableException`. `GLImagePreview.setImage(...)` provides a bounded
preview texture with theme-derived background/checkerboard colors.

## Remembered dialogs

Wrap `GLFileDialogs` in `GLRememberingFileDialogs` and inject a `DirectoryStore`.
Pass a stable purpose string such as `document.open` to `show`; do not use a
translated title. A missing remembered directory falls back to the request's
initial directory. Cancellation does not save. File selection saves its parent;
directory selection saves the selected directory. Persistence failure does not
turn an approved result into a failed dialog. Close the decorator when its owner
is disposed; this closes the wrapped dialogs.

## Group editors

`GLNodeEditorCapabilities` expresses kind, copy eligibility, standalone status,
default value and portable options. Applications provide descriptors derived from
their node definitions. `GLNodeEditorSourceCatalog.collect(...)` selects linkable
standalone editors and groups instances by node type/property; its predicate is
where application-specific restrictions belong. The generic payload preserves
the application's editor factory without depending on its type in Core.

`GLNodeEditorTemplate` is an immutable choice definition with separate entry IDs,
labels and emitted values. Its value representation is portable document data.
`GLNodeEditorTemplateLibrary.load(...)` validates an entire snapshot before
replacing the library. Registering templates for particular signal types remains
application policy.

## Graph import

`GLNodeGraphAssetCopy.plan(...)` collects a selected asset and its nested composite
dependencies and allocates fresh asset/type IDs. `copy(...)` creates fresh graph,
node and connection IDs, remaps port fanout and property targets, and retains view
positions. Plans describe one import; create a new plan for a second independent
import. A project root can become a composite using the supplied root type ID.

Implement `Policy` to rewrite application metadata, node properties, editor
options and property values. The `Context` supplies global and local ID maps;
Core deliberately does not guess which arbitrary user strings are references.
Use extra ID mappings for application-owned objects such as editor templates.
Conflicting overrides of structural IDs are rejected.
