# Text Editing

[UI guide](../ui-framework.md) · [Controls](controls.md) ·
[Documents](documents.md) · [Input configuration](input-configuration.md)

## Contents

- [Choosing a control](#choosing-a-control)
- [Documents and text areas](#documents-and-text-areas)
- [Selection and navigation](#selection-and-navigation)
- [Wrapping, tabs, and scrolling](#wrapping-tabs-and-scrolling)
- [Alignment](#alignment)
- [Character formatting](#character-formatting)
- [Code editing](#code-editing)
- [Editing history](#editing-history)

## Choosing a control

Use `GLTextEditor` for a single-line value that is committed or cancelled as
one control value. It supports filters, final validation, and integration with
the enclosing history scope when a value is committed.

Use `GLTextArea` for multiline documents. Enter inserts a newline, Tab inserts
an indentation character, and undo/redo operate on the active document-editing
session rather than committing the complete text as one value.

## Documents and text areas

`GLTextDocument` stores normalized text and maintains an indexed logical-line
structure. CRLF and CR input is normalized to LF. Applications can share or
replace a document independently of the view.

Text is stored as Java Unicode strings. Caret movement, deletion, mouse hit
testing, and wrapping follow locale-aware Unicode character boundaries rather
than splitting UTF-16 surrogate pairs. Rendering still depends on the selected
font containing the required glyphs.

```java
GLTextDocument document = new GLTextDocument(initialText);
GLTextArea area = new GLTextArea(document);
area.setLineWrap(true);
area.setTabSize(4);

document.addDocumentListener((source, change) -> saveDraft(source.getText()));
```

`Change` reports the replacement offset plus removed and inserted text.
Methods such as `getLineStart`, `getLineEnd`, `getLineText`, and
`getLineOfOffset` provide logical-line lookup without requiring callers to
rescan the complete document.

## Selection and navigation

Mouse dragging selects across visual and logical lines. The standard input map
provides character, word, line, document, clipboard, and selection commands.
Home and End address the current logical line; the platform command modifier
combines with them for document boundaries and with Left/Right for word
navigation. Up and Down preserve the logical column where possible; Page Up
and Page Down move by the visible line count.

Selection endpoints remain stored when the text area loses focus, but the
selection highlight is hidden while the control is inactive. Focusing the area
again restores the visual highlight for the unchanged selection.

`setEditable(false)` retains navigation, selection, and copying while blocking
document changes.

## Wrapping, tabs, and scrolling

Soft wrapping is enabled by default and does not insert newline characters.
Wrapping prefers the latest whitespace boundary that fits and falls back to a
Unicode character boundary only when a single word is wider than the viewport.
An ordinary hyphen is also a preferred wrap boundary, so authors can provide
manual hyphenation without adding document metadata. The hyphen remains visible
at the end of the row.
The first separator that exceeds the remaining width acts as the visual wrap
delimiter: the fitting word stays in place and the caret moves to the start of
the continuation row. Further spaces are ordinary indentation on that row.
`setLineWrap(false)` enables horizontal scrolling for long logical lines.
`setTabSize(...)` controls the visual width of tab characters without changing
document content. The caret is scrolled into view automatically, and the mouse
wheel scrolls vertically by font line height. With wrapping disabled, the
configured horizontal-scroll modifier scrolls horizontally. Applications may
also inspect or change both offsets through `getHorizontalScrollOffset()`,
`getVerticalScrollOffset()`, and `setScrollOffset(...)`.

When the visual rows exceed the viewport, `GLTextArea` displays an integrated,
theme-aware vertical scrollbar. Its thumb can be dragged or repositioned by
clicking the track. `isVerticalScrollBarVisible()` reports the current overflow
state. The text viewport reserves the themed scrollbar thickness so wrapping
does not change when the bar appears or disappears.

Only rows intersecting the viewport are submitted for text drawing. The
document remains one model object; GLSwing does not create a child control for
each line.

## Alignment

`setTextAlignment(...)` applies `LEFT`, `CENTER`, `RIGHT`, or `JUSTIFIED`
alignment to the text area. A secondary-button press opens the built-in context
menu, whose **Alignment** submenu contains these commands. Retrieve the root
menu through `getContextMenu()` to adjust its items or presentation for an
application. Center and right alignment also affect caret placement, selection,
pointer hit testing, and vertical navigation.
Alignment commands selected from this menu are recorded in the enclosing
history. Direct calls to `setTextAlignment(...)` are ordinary programmatic
state updates and do not create history entries.

Justification expands spaces only on rows produced by soft wrapping. The final
row of a logical paragraph remains naturally spaced, as is customary in text
editors. Tabs retain their configured width and are not justification points.
Automatic hyphenation is intentionally not inferred: correct results require a
language-specific hyphenation dictionary and an explicit document language.

## Character formatting

The context menu's **Formatting** submenu provides **Bold** and **Italic**
commands. With a non-empty selection, a command changes that range and records
an operation in the enclosing document history. Any pending typing session is
committed first, keeping formatting and text insertion as distinct undo steps.
With only a caret, a command changes the typing style for text inserted
afterward. Bold and italic may be combined.

Formatting belongs to `GLTextDocument`, not to its view. Use `setStyle(...)`,
`getStyleAt(...)`, and `getStyleRuns(...)` when loading, saving, or inspecting
formatted content. `GLTextStyle` is immutable and currently contains the bold
and italic attributes. `snapshot()` and `restore(...)` preserve text and
formatting together; completed focus sessions therefore also restore both
through the enclosing history.

The default input map assigns `Ctrl/Cmd+B` and `Ctrl/Cmd+I` to bold and italic.
Override `text.toggle-bold` and `text.toggle-italic` in an external `.glinput`
file to change them.

All built-in context-menu labels use `GLUiKeys` and GLSwing's resource bundles.
Changing the host `GLLocalizer` refreshes existing menus. Applications can
override individual labels or supply their own localized bundle without
rebuilding the editor menu.

## Code editing

`GLCodeEditor` specializes the multiline editor for source text. It uses a
monospaced font, disables soft wrapping, displays line numbers, continues the
current indentation on Enter, adds one indentation level after an opening
brace, and inserts matching brackets and quotes around the caret.
Typing a closing bracket or quote when the same automatically generated
character is already at the caret advances over it instead of inserting a
duplicate. Pressing Enter between a generated `{}` pair expands it into a
three-line block: the caret is placed on the indented inner line and the closing
brace is aligned with the line containing the opening brace.

Syntax highlighting is transient presentation data: lexer tokens never modify
the text or the persistent rich-text runs in `GLTextDocument`. Select a built-in
lexer with `GLCodeLanguages.JAVA` or `GLCodeLanguages.GLSL`, or implement
`GLCodeLanguage` for an application language. When the connected tooling
provides semantic tokens, those classifications take precedence over lexer
colors; unsupported and temporarily unavailable semantic categories retain the
lexer result. While a new semantic snapshot is pending after an edit, tokens
outside the changed source range remain visible and their offsets follow the
edit. Only intersecting classifications are discarded; the lexer immediately
colors that source until the provider responds.

```java
GLCodeEditor editor = new GLCodeEditor(source);
editor.setLanguage(GLCodeLanguages.GLSL);
editor.setIndentationSize(4);
```

For `.gltheme` documents, the color picker and the built-in formatter share a
configurable expression policy:

```java
editor.setPreferredColorOutputModel(GLColorModel.OKLCH); // null preserves the source model
editor.setColorExpressionFormat(new GLColorExpressionFormat(
		GLColorExpressionFormat.ChannelNotation.PERCENT,
		3,     // maximum fractional digits
		true,  // remove trailing zeros
		true)); // omit fully opaque alpha
```

Formatting then rounds direct functional color literals to the requested
precision, removes redundant trailing zeros, and can omit a fully opaque alpha
channel (`1`, `255`, or `100%`, depending on the notation). Hex colors,
references, and derived expressions are preserved. The same policy is used when a color is
replaced through the editor's color preview.

The `.gltheme` context menu exposes **Color formatting…**, which opens a
theme-aware settings dialog. It provides the output model and notation as
combo boxes, a 0–12 decimal-place slider, controls for trailing zeros and
opaque alpha, and a live expression example. Applications may open it directly with
`editor.showColorFormattingDialog()`. The reusable
`GLColorExpressionFormatDialog.show(...)` API is also available when the
settings are managed outside a code editor.

The code editor replaces the rich-text context menu with code-specific
commands. Its contents are capability-driven: a language only exposes
formatting, line comments, import organization, definition navigation, inlay
hints, or color formatting when the corresponding `GLCodeLanguage` capability
reports support and, where required, a code-intelligence session is active.
Consequently, `.gltheme` files do not show import organization, while a Java
editor connected to a suitable language server does. Custom languages can
override the `supports...()` methods on `GLCodeLanguage`.

A selection expands comment commands to all touched logical lines. Without a
selection, formatting processes the complete document, while commenting and
uncommenting affect the caret's logical line. The corresponding API methods are `formatCode()`,
`commentSelection()`, and `uncommentSelection()`. Each command is recorded as
one document-level undo/redo operation. All ordinary selection, scrolling,
Unicode navigation, focus-session history, and document APIs remain available.

A double-click selects the Unicode word below the pointer in both `GLTextArea`
and `GLCodeEditor`.

Formatting normalizes indentation and common lexical spacing such as control
statement parentheses, commas, and the space before an opening block brace.
Quoted strings and trailing line comments are preserved. Syntax colors are
read from the active theme's `codeEditor.keyword`, `codeEditor.string`,
`codeEditor.number`, `codeEditor.comment`, and `codeEditor.type` values; the
built-in light and dark themes provide separate palettes. `codeEditor.text` and
`codeEditor.background` set the code-specific base foreground and surface.
Semantic colors use
`codeEditor.semantic.<token-type>`, including `class`, `interface`, `parameter`,
`variable`, `property`, `method`, and the other standard LSP token types.
`codeEditor.navigationLink` controls the Ctrl/Cmd-hover link color.
Themes can also override a server-specific type or a modifier combination with
`codeEditor.semantic.<token-type>.<modifier>`; for example,
`codeEditor.semantic.variable.readonly`. A wildcard such as
`codeEditor.semantic.*.deprecated` applies to that modifier across token types.

`GLCodeLanguage.diagnose(String)` supplies optional language-specific
diagnostics. `GLCodeEditor` runs this check 300 milliseconds after the most
recent document change, exposes the immutable result through `getDiagnostics()`,
and draws error or warning ranges with a wavy underline. Hovering an underlined
range displays its message through the standard tooltip overlay. The built-in
Java and GLSL implementations perform inexpensive structural checks for
unbalanced delimiters and unclosed literals or block comments; compiler-level
name and type analysis remains the responsibility of an external language
implementation or tooling adapter.

The built-in Java language also reports conservative, unambiguous cases:
missing semicolons on recognizable statements, a value returned from a
clearly declared `void` method, an empty return in a clearly declared non-void
method, a simple branch-free non-void method without a value return,
`continue` when no loop exists, `break` when no loop or switch exists, and an
`if` statement without a parenthesized condition. It deliberately does not
attempt type resolution or complete control-flow analysis. Applications that
need compiler-equivalent results should use the optional language-server
integration.

Built-in profiles are available as `GLCodeLanguages.JAVA`, `C`, `CPP`,
`CSHARP`, `GLSL`, `JAVASCRIPT`, `TYPESCRIPT`, `PYTHON`, `RUBY`, `JSON`,
`HTML`, `XML`, and `CSS`.
Each profile supplies its common keywords, comment and literal conventions,
structural delimiter checks, and only those additional lightweight rules that
are unambiguous for that language. For example, JavaScript and TypeScript do
not report omitted semicolons because automatic semicolon insertion is part of
those languages; Python checks block colons and mixed indentation; Ruby checks
`end`-delimited blocks; and JSON rejects single-quoted strings and trailing
commas. These profiles are editing aids rather than replacements for their
respective compilers or interpreters.

HTML and XML validate nested, closing, self-closing, and unclosed tags while
ignoring markup comments; HTML additionally recognizes standard void elements.
CSS validates declaration colons and definite missing separators between
consecutive declarations. Java also rejects a bare identifier such as `test`
as a standalone line because it is neither a declaration nor a legal statement.
Repeated semicolons outside parentheses are diagnosed as redundant empty
statements in Java, C, C++, C#, GLSL, JavaScript, and TypeScript. They are
warnings because these languages permit empty statements. CSS reports the same
pattern as an error. Semicolons in constructs such as `for (;;)` and semicolons
inside comments or string literals are ignored.
Determining whether otherwise valid statements occur in a legal class, method,
initializer, lambda, or nested-type scope requires a real grammar and remains a
language-server responsibility.

### Native configuration-language support

GLSwing provides schema-aware editing for its configuration formats directly
in `glswing-core`: `GLCodeLanguages.GLTHEME` for `.gltheme`,
`GLCodeLanguages.GLINPUT` for `.glinput`, and
`GLCodeLanguages.LOCALIZATION` for UTF-8 localization `.properties` files.
None starts a process or requires a language-server installation. Construct the
editor with a real document URI to select the language and enable its in-process
code intelligence automatically:

```java
Path themeFile = Path.of("themes", "application.gltheme");
GLCodeEditor editor = new GLCodeEditor(
        Files.readString(themeFile), themeFile.toUri());
```

For an existing editor, call `configureDocument(uri)`. File-name recognition is
also available through `GLCodeLanguages.forUri(...)` and
`GLCodeLanguages.forFileName(...)`.

The native providers offer format-specific property and value completion,
validation, hover documentation, semantic classifications, duplicate-key
diagnostics, and cross-file navigation. Themes additionally resolve property
references and fragments; input maps validate inheritance and binding grammar;
localized bundles compare their keys and `MessageFormat` arguments with the
closest root bundle. Relative paths are resolved from the document URI;
therefore an artificial URI cannot provide file completion or cross-file
navigation.

Resolved color values are accompanied by a compact swatch after the source
value. The preview supports hexadecimal, RGB, HSV, HSL, OKLab, OKLCH,
references, and `derive(...)` expressions, including colors declared in
imported theme fragments. Its checkerboard exposes transparency; hovering shows
hexadecimal and RGBA values. In an editable theme document, clicking opens the
[color picker](controls.md#color-controls). Apply replaces the expression as one
undoable edit; Cancel leaves it unchanged. Hex literals remain hex literals and
functional colors initially use their original model. Changing a reference or
`derive(...)` expression replaces it with a literal. Read-only documents and
non-theme providers retain the selection-only behavior. Live previews remain
inside the picker until Apply; external theme watchers update after saving.
Other providers can expose the same presentation through
`GLCodeDiagnosticProvider.Session.documentColors(...)`, which returns
`GLCodeColor` ranges. The default implementation returns no colors, preserving
compatibility with diagnostic-only and existing language-server adapters.
Existing swatches and their source ranges are translated incrementally during
the provider debounce. A normal edit therefore does not briefly remove every
color preview; an invalid expression is removed once the refreshed provider
snapshot confirms that it no longer resolves to a color.

Parameter and type inlay hints are visual-only editor adornments. They occupy
layout space before their source offset but never become part of the document,
selection, clipboard contents, or saved file. The code editor exposes the
current immutable snapshot through `getInlayHints()`. Applications can toggle
the presentation with `setInlayHintsVisible(boolean)`; the standard code-editor
context menu provides the same command and displays its selected state.

```java
editor.setInlayHintsVisible(true);
```

Providers supply hints through
`GLCodeDiagnosticProvider.Session.inlayHints(GLCodeDocument)`. The built-in
configuration provider labels channels in `rgb`, `rgba`, `hsv`, `hsl`,
`oklab`, and `oklch` expressions and labels the base and adjustments of
`derive(...)`. This makes unfamiliar color spaces self-describing in theme
files without requiring an external process. Other languages can return
parameter, type, or general `GLCodeInlayHint` instances through the same API.

Inlay labels use compact rounded boxes modeled after conventional IDE inline
hints. Their appearance is theme-controlled through
`codeEditor.inlay.background`, `.border`, `.text`, `.fontScale`, `.padding`,
`.gap`, `.radius`, and `.borderWidth`. While a provider computes a new snapshot,
the editor keeps unaffected labels visible and translates their anchors across
insertions and deletions. Only labels whose anchors are removed by the current
edit disappear immediately. Built-in configuration languages derive parameter
slots from their surrounding function and commas, so a temporarily empty color
channel retains its label while the user enters a replacement value.

For provider-backed languages, signature help can supply a provisional label
before the first argument character exists. The editor displays it only when a
single signature is available or every reported overload uses the same name at
the active parameter position. Differing overloads remain unlabeled until the
provider can resolve the call; this avoids presenting a guessed contract as
fact. A later native inlay hint replaces the provisional label automatically.

Applications can use the same infrastructure for their own UTF-8
`key=value` formats. Define properties with `GLCodePropertySchema`, construct a
`GLPropertiesCodeLanguage`, and return or assign that language to an editor.
The schema controls exact and prefix-matched keys, value types, enum choices,
unknown-key policy, and format-level validators.
`GLCodeLanguage.codeIntelligenceProvider()` is the extension point for any
other language that can provide in-process completion, hover, definitions,
semantic tokens, inlay hints, and diagnostics.

### Language Server Protocol

The optional `glswing-lsp` artifact connects a code editor to language servers
that use standard input and output. Profiles keep discovery, installation,
initialization options, language IDs, file extensions, and virtual-source
handling outside the editor:

```java
Path workspace = Path.of("/projects/example");
Path sourceFile = workspace.resolve("src/main/java/example/App.java");
GLLanguageServerRegistry registry = GLLanguageServerRegistry.standard();
GLLanguageServerProfile profile = registry.forPath(sourceFile).orElseThrow();
Path installation = profile.findInstallation().orElseThrow();
GLLanguageServer server = profile.start(installation, workspace);

GLCodeEditor editor = new GLCodeEditor(source);
editor.setLanguage(GLCodeLanguages.JAVA);
editor.setDiagnosticProvider(server, sourceFile.toUri());

// When the editor no longer represents this document:
editor.clearDiagnosticProvider();

// When the application no longer needs the shared server:
server.close();
```

The standard registry includes `GLJdtLanguageServerProfile` for Java. An
application can implement `GLLanguageServerProfile` and call `register(...)` to
add clangd, Roslyn, Pyright, TypeScript Language Server, or another LSP
implementation. `forLanguage(...)` and `forPath(...)` perform ordered profile
selection. Completion, hover, signatures, diagnostics, definitions,
implementations, call hierarchies, semantic highlighting, and editor tabs remain
independent of the selected server.

A profile may override `loadSource(...)` for non-file definition URIs. The JDT
profile uses this hook for `jdt:` class-file sources; ordinary profiles inherit
the default `file:` loader. Managed installation is optional and advertised by
`supportsManagedInstallation()`.

`GLLanguageServer` starts the process, performs the LSP initialize handshake,
and sends `textDocument/didOpen`, full-document `didChange`, and `didClose`
notifications. Published UTF-16 LSP ranges are converted to editor offsets.
Server diagnostics are merged with the built-in lightweight diagnostics, and
responses carrying an obsolete document version are discarded. One server may
be shared by multiple editors, but each document URI may be open only once on
that server. Clearing an editor connection closes only its document session;
closing the server sends the LSP shutdown and exit sequence and releases the
process.

The code editor displays errors and warnings in an overview ruler beside the
vertical scrollbar. Marker positions represent source lines across the complete
document rather than only the visible viewport. Hovering a marker shows the
normal diagnostic tooltip; clicking the ruler moves the caret to the nearest
diagnostic and scrolls it into view. Themes can customize
`codeEditor.overviewRulerWidth`, `codeEditor.overviewRulerGap`,
`codeEditor.overviewMarkerHeight`, and `codeEditor.overviewRulerBackground`.

When the pointer rests over source text, `GLCodeEditor` requests
`textDocument/hover` after a short debounce. Plain-text and Markdown hover
responses are normalized and displayed through the standard tooltip overlay;
multiline signatures and documentation are supported. Diagnostic messages take
precedence when the pointer is over an error range. Moving the pointer, editing
the document, disconnecting the provider, or leaving the editor cancels or
invalidates the pending request, so a stale response cannot replace current
information. Providers that implement only diagnostics inherit an empty hover
implementation and remain source-compatible.

The same document session provides asynchronous code completion through
`textDocument/completion`. Press `Ctrl/Cmd+Space` to request proposals at the
caret, or type `.` to invoke member completion automatically. The result opens
as a theme-aware GLSwing popup: use Up and Down to select an entry, Enter or Tab
to insert it, and Escape to dismiss the popup. Mouse selection is also
supported. Responses are discarded if the document version or caret position
has changed while the request was running.

When an explicit `Ctrl/Cmd+Space` request produces exactly one proposal, the
editor inserts it immediately. Automatic completion after `.` never inserts a
proposal without confirmation. Call `setInsertSingleCompletion(false)` when an
application should display the popup even for one manually requested result.

Applications that provide their own tooling can implement
`GLCodeDiagnosticProvider.Session.complete(...)` and return
`GLCodeCompletion` values. Each value carries its visible label, optional
detail text, primary replacement, caret offset, and optional additional
`GLCodeTextEdit` operations. Primary and additional edits are applied as one
transaction, so an inserted symbol and its automatically added import are
reverted by one Undo command. Providers that implement only diagnostics retain
the default empty completion implementation.

The LSP adapter honors `TextEdit` and `InsertReplaceEdit` ranges as well as
`additionalTextEdits`. LSP snippets are converted into ordinary document text;
the caret is placed at the first numbered tab stop, or at `$0`/the insertion
end when no numbered stop exists. GLSwing does not expose an interactive
multi-tab-stop snippet session yet, so subsequent placeholders are inserted
using their default text without Tab navigation.

When a provider implements `Session.signatureHelp(...)`, the code editor
requests callable signatures after `(` and refreshes them after `,`. The popup
lists available overloads, visibly delimits the active parameter, and includes
the provider's concise signature documentation. Typing `)` closes the popup.
The LSP adapter uses `textDocument/signatureHelp` for this feature.

The code editor resolves definitions through
`GLCodeDiagnosticProvider.Session.definitions(...)`. Press `F3`, choose **Go
to definition** from the context menu, or Ctrl-click a symbol. Locations in
the current document move the caret directly. For another URI, configure a
`GLCodeNavigationHandler` with `setNavigationHandler(...)`; the application
can then activate an existing editor, open a tab, or delegate navigation to an
external tool. Multiple targets are presented in a selection popup. The LSP
adapter maps both `Location` and `LocationLink` definition responses.

Holding Ctrl, or Cmd on macOS, while hovering a resolvable symbol turns its
source range into a blue underlined link, changes the pointer to a hand, and
opens an interactive action popup. **Open Declaration**, **Open
Implementation**, and **Open Call Hierarchy** appear only when the active
provider returns a corresponding result for that symbol. The first two actions
navigate through the normal `GLCodeNavigationHandler`. The hierarchy popup
shows the first level of incoming and outgoing calls and lets the user navigate
to each location. A short dismissal delay bridges the distance between the
source link and its popup; entering the popup cancels that delay, so clamped
popups near the host edges remain reachable. Releasing the modifier, editing
the document, or dismissing the popup removes the transient link.

Custom tooling implements `Session.implementations(...)`,
`canOpenCallHierarchy(...)`, and `callHierarchy(...)` to participate in these
actions. `GLLanguageServer` maps them to the standard
`textDocument/implementation` and call-hierarchy requests. The Core does not
assume Java symbol rules, so any LSP-backed language can expose the same UI.

When the server advertises full semantic-token support, the editor requests a
versioned document snapshot after changes and applies the returned UTF-16
ranges without modifying the document. `getSemanticTokens()` exposes the latest
immutable snapshot. Servers without semantic tokens continue to use the
built-in lexer exclusively; providers can supply the same feature directly via
`Session.semanticTokens(...)` without depending on `glswing-lsp`.
`setSemanticStatusListener(...)` reports whether a request is pending, supplied
classifications, returned an empty result, or failed.
The lexical palette remains visible while a newly started language server is
still importing the project; semantic colors replace it when that asynchronous
analysis completes. Diagnostics from the obsolete document version are removed
immediately rather than being translated to potentially incorrect lines.

The code-editor context menu also provides **Organize imports**. It requests
the standard `source.organizeImports` code action from the active provider;
JDT LS can add resolvable missing imports, remove unused imports, and sort the
result according to its project settings. The API equivalent is
`organizeImports()` or the default `Ctrl+Shift+O` shortcut. All returned edits
form one undoable transaction. With no
connected provider, or when the provider offers no such source action, the
command leaves the document unchanged.

The language-server executable is not bundled. Install and configure the
server appropriate for the selected language, and pass its command-line
arguments as separate list entries. A real file URI should be used whenever a
server resolves imports, project settings, or related files from the workspace.

The Code Editor tab in `GuiShowcaseDemo` displays the Core and LSP connection
status. It runs without an external server by default. If JDT LS is missing,
the tab offers **Install Java LSP** and **Select installation** actions. The
installer downloads the pinned Eclipse JDT LS milestone, obtains and verifies
its published SHA-256 checksum, extracts it into the user's application-data
directory, and stores the selected installation in user preferences. Later
showcase runs find and start it automatically.

The separate `GUI Showcase Demo with Java LSP` Eclipse launch configuration
uses this managed path and therefore needs no manually edited server path. A
custom server command can still be supplied after `--lsp` as program arguments:

```bash
java com.vingame.glswing.demo.GuiShowcaseDemo --lsp jdtls
```

For a server executable that needs no additional arguments, the JVM property
`-Dglswing.demo.lsp.command=/path/to/server` is also supported. Explicit
commands take precedence over managed discovery. The sample contains unresolved
Java symbols intentionally: the built-in profile accepts their syntax, while a
connected Java language server can report their semantic errors.

All controls inherit the general `setToolTipText(String)` API. Tooltips appear
after a short hover delay, use the active theme's popup typography and colors,
remain inside the host surface, and do not participate in mouse picking.

The default code commands are `Ctrl/Cmd+Shift+F` for formatting,
`Ctrl/Cmd+Shift+C` for commenting, and `Ctrl/Cmd+Alt+C` for uncommenting. Their
semantic input-map names are `code.format`, `code.comment`,
`code.uncomment`, and `code.organizeImports`; completion uses `code.complete`. Menu accelerator labels
reflect these bindings. Override any of them in the active `.glinput` file.

Inside a text area, `Tab` inserts a tab character. Focus traversal uses
`Ctrl/Cmd+Tab` and `Ctrl/Cmd+Shift+Tab` by default; these semantic bindings are
configured as `focus.next` and `focus.previous` in the input map.

## Editing history

Each `GLTextArea` owns a `GLHistoryManager` returned by
`getEditingHistoryManager()`. Undo and redo shortcuts are handled inside the
focused area through the common `GLEditingHistoryTarget` contract, before the
host history sees the shortcut. Replacing the document or applying unrelated programmatic
changes resets this local editing history, preventing commands from crossing
document boundaries.

When focus leaves the area, the complete editing session is recorded as one
entry in the enclosing host history. `setHistoryEnabled(false)` disables this
outer commit without disabling local editing history. The enclosing history
therefore remains suitable for application-level operations while character
edits stay granular only during active text entry.
