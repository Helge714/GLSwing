# GLSwing code map

Developer navigation index. Use the relevant row before repository-wide searches;
verify current code before editing. Update affected rows when files move or
subsystem ownership changes. This is not a complete inventory or an API contract.

## Modules and ownership

- `glswing-core/`: reusable UI, rendering, node graph and application infrastructure.
- `glswing-lsp/`: language-server integration, separate from application languages.
- `glswing-filedialogs-swt/`: optional SWT file-dialog backend.
- `glswing-examples/`: examples and demos.
- `glswing-consumer-smoke/`: external-consumer build validation.
- `pom.xml`: parent build and module/dependency setup.

Core must remain independent of EasyKnack, audio node IDs, physical-unit policy,
DSP protocols and application migrations. Consumer applications use Maven
artifacts. The sibling checkout layout is a development convenience only.

## Entry points

Paths below are relative to
`glswing-core/src/main/java/com/vingame/glswing/`.

| Task | Start here | Responsibility |
| --- | --- | --- |
| Startup / windows / splash | `application/GLApplication.java`, `application/GLStartupEngine.java`, `application/GLStartupPlan.java` | Application surfaces and startup execution/progress. |
| Graph editor / gestures | `node/GLNodeEditor.java`, `node/GLNode.java`, `node/GLNodePort.java`, `node/GLNodeConnection.java`, `node/GLNodeConnectionCurves.java` | Visual nodes, independent trailing severity badges (`GLNodeTitleBadgeTest`), connections, selection, gestures, replacement routing and picking. `GLNodeAutoLayout` aligns port anchors, lays out strongly connected feedback regions and packs independent components. Workspace Arrange preserves branch order and rough component placement with flow-based compaction through `arrangePreservingOrder`; `arrange` retains compact packing. `GLNode` layout roles keep group boundaries outside; `GLCompactAutoLayoutTest` covers geometry, stability and bounded work. `GLNodeViewport` and `GLNodeConnectionCurves` prune offscreen node traversal and retain built-in gradients; `GLGraphVisualPerformanceTest` includes GPU crossing-wire coverage. |
| Workspace / commands / inspector | `node/GLNodeWorkspace.java`, `node/GLNodeEditingActions.java`, `node/GLNodePropertyInspector.java`, `node/GLNodeWorkspaceViews.java` | Model/view workspace, camera-center preservation on window resize (including zoom/zero-size; `GLCameraLayoutRoutingTest`), opt-in per-graph history scopes and retained graph documents, navigation, atomic boundary-wire insertion/body-drop port creation, editor actions and projected/read-only property UI. |
| Graph model / catalog | `node/model/GLNodeGraph.java`, `node/model/GLNodeTypeRegistry.java`, `node/model/GLNodeCatalog.java`, `node/model/GLNodeValueTypeRegistry.java` | Model snapshots/mutations, node and value types, catalog entries. |
| Binding | `node/binding/` | Synchronize model nodes with views, capture immutable connection history and editor state; projection notifications let the workspace restore composite boundary wires. Failed node/connection projections log graph/element identities and stack traces, deduplicated until recovery (`ProjectionFailureLoggingTest`). `GLNodeSizingTest` covers synchronous post-child-theme measurement and saved bounds respecting content minimums. |
| Group interface / validation | `node/composite/GLCompositeNodeDescriptor.java`, `node/composite/GLCompositeInterfaceEditor.java`, `node/composite/GLCompositeInterfaceTransactions.java`, `node/composite/GLCompositeGraphRegistry.java` | Reusable group contracts, interface controls, undoable edits and asset registry. |
| Group execution / forwarding / ungroup | `node/composite/GLCompositeGraphFlattener.java`, `node/composite/GLCompositeExpansionAnalyzer.java`, `node/composite/GLCompositeForwarding.java`, `node/composite/GLCompositeUngroup.java` | Expand groups, validate limits, resolve direct boundary routes and unpack instances. |
| Graph documents / asset copies | `node/document/`, `node/document/GLNodeGraphAssetCopy.java` | Graph document/asset data and structural dependency copying. Schema 10 stores graph-local camera centers (`GLNodeCameraState`); zoom remains shared. `GLCameraLayoutRoutingTest` covers persistence, routing and layout. |
| Editor templates / capabilities | `node/composite/GLNodeEditorTemplateLibrary.java`, `node/composite/GLNodeEditorCapabilities.java`, `node/composite/GLNodeEditorSourceCatalog.java` | Generic editor-template storage and copy/link capability descriptions. |
| Execution / mute / history | `node/execution/`, `history/` | Generic graph execution/mute mechanisms and scoped undo/redo with one document save checkpoint; no audio DSP policy. Regression: `GLScopedHistoryTest`. |
| Remote editor drivers | `ui/GLDriverTarget.java`, `ui/GLDriverMapping.java`, `ui/GLControl.java` | Transport-neutral targets, absolute mapping/soft takeover, driver context menus and theme-colored indicators. Applications own protocols and persistent assignments. |
| Node projection state transfer | `node/binding/GLNodeViewFactory.java`, `node/binding/GLNodeEditorBinding.java` | Transfer transient application view state across port/type replacements; regression in `GLNodeEditorBindingSelfTest`. |
| Controls / shared editing | `ui/GLLabel.java`, `ui/GLTextArea.java`, `ui/GLTextDocument.java`, `ui/GLSlider.java`, `ui/GLUnitSlider.java`, `ui/GLNumberSpinner.java`, `ui/GLInputGroup.java`, `ui/GLSliderGroup.java`, `ui/GLNumberSpinnerGroup.java`, `ui/GLTabbedPane.java` | Text, sliders/spinners, grouped editing and tab controls. |
| Code editor / theme | `ui/GLCodeEditor.java`, `ui/GLCodeTextSupport.java`, `ui/GLCodeLanguage.java`, `ui/GLTheme.java`, `ui/GLThemeLoader.java`, `ui/GLThemeDefaults.java` | Code editor UI/language integration and theme parsing/defaults. Language hooks provide optional identifier completion, Enter indentation, formatting and transient semantic font emphasis (`GLCodeSemanticStyleTest`); `GLCodeEditorLanguageHooksTest` covers dispatch. |
| Component image capture | `rendering/GLComponentImageCapture.java`, `component/GLContainer.java` | Per-capture font textures; preserve source hierarchy, geometry and surface fonts. Regression: `GLComponentImageCaptureTest`. |
| Curve controls | `ui/GLCurveEditor.java`, `ui/GLFunctionCurve.java` | Hover/focus reveals editing points and Bezier handles; fixed point positions lock X and topology for phase editors. Points use mode-specific glyphs; phase grids support explicit column counts and edge-aligned plots. Regressions: `GLCurveEditorTest`. |
| Rendering / events / layout | `rendering/`, `component/`, `input/`, `layout/` | Rendering implementation, component tree, event dispatch and layout infrastructure. `component/GLKeyboardManager` shares focus/hover/default command routing across both hosts. `ui/GLToolTipManager` validates hidden/detached owners and pointer transitions (`GLTooltipLifecycleTest`). |
| Application documents / recovery / dialogs | `document/`, `document/GLDocumentAutosaveCoordinator.java`, `file/` | Generic document lifecycle, asynchronous autosave and file-dialog infrastructure. `GLFileBrowser` uses actual tree clicks rather than selection-restoration events; `GLFileNavigationTest` checks independent navigation. |

## Tests, resources and supporting documentation

- `glswing-core/src/test/java/`: focused JUnit tests; find the nearest feature/package.
- `glswing-core/src/main/resources/`: bundled themes, schemas and other toolkit resources.
- `docs/node-wire-insertion.md`: node insertion behavior.
- `docs/ui-framework.md`, `docs/graphics-pipeline.md`: deeper UI/rendering references.
- `docs/application-services.md`: reusable application services.
- `CHANGELOG.md`: public changes; `TODO.md`: pending work; `AGENTS.md`: working agreements.
- `target/` directories: generated output, not authoritative source.

For a small change, inspect the owning class, relevant callers and nearest tests.
Assemble the change before broader builds; repeat only for a new failure, change
or unresolved concern. Documentation-only navigation changes need path/link and
diff checks, not a full build.

- Markdown: `ui/GLMarkdown` renders escaped offline HTML; `ui/GLMarkdownView` owns themed layout, tables, anchors and link hit-testing. Text, code and Markdown share `GLTextSearchTarget`, the `GLTextSearch` matcher and `GLTextSearchDialog`; Markdown maps matches across spans/wrapping/tables and exposes scroll callbacks (`GLMarkdownSearchTest`). Applications own document discovery/navigation and caching policy. Tests: `GLMarkdownTest`; consumer presentation tests remain in EasyKnack.
- External showcase assets live in `glswing-examples/resources`, deliberately outside the classpath.
