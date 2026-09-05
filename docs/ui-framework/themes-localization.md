# Themes and Localization

[UI guide](../ui-framework.md) · [Controls](controls.md) · [Components](components.md)

Themes provide immutable typed styles and extensible values. `GLLocalizer`
resolves UTF-8 resource bundles through an explicit locale and deterministic
fallback chain.

## Contents

- [Theme inheritance](#theme-inheritance)
- [Built-in and external themes](#built-in-and-external-themes)
- [Modular color and metrics themes](#modular-color-and-metrics-themes)
- [Loading and live reload](#loading-and-live-reload)
- [Editable user overrides](#editable-user-overrides)
- [Editing theme source](#editing-theme-source)
- [Color values](#color-values)
- [External fonts](#external-fonts)
- [Typed styles](#typed-styles)
- [Text shadows](#text-shadows)
- [Localization](#localization)
- [Editing localization bundles](#editing-localization-bundles)

## Theme inheritance

Set a `GLTheme` on the host or any `GLContainer`. Descendants inherit the
nearest theme unless a control has a local typed style.

```java
host.setGLTheme(GLThemes.DARK);
panel.setGLTheme(customTheme);
button.setGLStyle(buttonStyle);
button.clearGLStyle();
```

Themes and style records are immutable. Changing an inherited theme invalidates
affected recordings and layout automatically.

## Built-in and external themes

`GLThemes.DEFAULT`, `DARK`, and `LIGHT` load packaged theme resources. External
`.gltheme` files use key/value properties. A theme can inherit another file or
built-in theme with `theme.extends=...`; child keys override parent values.

References use `${key}` and resolve recursively. Custom keys remain available
through `theme.values()` so application controls can share the same theme file.

## Modular color and metrics themes

Applications can combine one color theme and one metrics theme in a bundle.
Fragments contain only explicit values; they do not independently inherit a
complete theme.

```properties
# colors/dark.gltheme
theme.type=colors
palette.surface=#181818
palette.text=#f0f0f0
palette.accent=#4d90fe
```

```properties
# metrics/compact-rounded.gltheme
theme.type=metrics
metrics.cornerRadius=6
metrics.padding=4,6,4,6
menu.itemCornerRadius=2
menu.itemHeight=24
```

```properties
# themes/dark-compact.gltheme
theme.type=bundle
theme.colors=../colors/dark.gltheme
theme.metrics=../metrics/compact-rounded.gltheme

# Bundle-local values have the highest theme-file priority.
comboBox.cornerRadius=8
```

The loader applies the base theme, the color fragment, the metrics fragment,
and finally bundle-local values. A bundle has at most one slot of each type;
duplicate declarations are rejected instead of silently replacing one another.
Color fragments reject geometry keys, metrics fragments reject color and font
keys, fragment type mismatches are errors, and fragments cannot import or
inherit other themes. Relative references resolve from the bundle directory.
Existing themes without `theme.type` remain supported through
`theme.extends`.

## Loading and live reload

```java
GLTheme theme = GLThemeLoader.load(path);
GLTheme optional = GLThemeLoader.loadOrDefault(path, GLThemes.DEFAULT, null);
GLTheme packaged = GLThemeLoader.loadResource("/themes/application.gltheme");
```

`load(Path)` reports missing files, cycles, unknown base themes, and invalid
values as `GLThemeLoadException`. Use `loadOrDefault(...)` only for genuinely
optional application themes; required resources should fail visibly.

The file watcher performs an initial load, debounces filesystem events, and
invokes success/failure callbacks on the AWT event thread. An incomplete write
does not replace the last valid theme. `reloadNow()` requests an immediate
reload. Close the watcher when its host is disposed.

## Editable user overrides

`GLThemeEditor` supports preference dialogs without making `GLTheme` mutable.
It keeps a base theme and explicit overrides separate, rebuilds all typed styles
for previews, and writes only changed properties.

```java
GLThemeEditor editor = GLThemeEditor.loadOrCreate(
        userThemeFile, GLThemes.DARK);
editor.setColor("palette.accent", selectedColor);
editor.set("button.cornerRadius", 6);

host.setGLTheme(editor.build()); // preview
editor.save(userThemeFile);      // persist
```

`getEffective(...)` reads a resolved value. `getOverride(...)` and
`isOverridden(...)` distinguish user choices from inherited values.
`unset(key)` restores inheritance for one property, while `reset()` removes all
overrides. Saving uses atomic replacement and creates missing parent
directories. For a custom base, use
`GLThemeEditor.from(base, "base.gltheme")` so the saved `theme.extends`
reference remains portable.

## Editing theme source

`GLCodeEditor` recognizes `.gltheme` document URIs and enables built-in,
schema-aware assistance without an external language server:

```java
Path file = Path.of("themes", "application.gltheme");
GLCodeEditor sourceEditor = new GLCodeEditor(
        Files.readString(file), file.toUri());
```

The editor highlights property declarations, references, functions, numbers,
strings, and directives. `Ctrl/Cmd+Space` completes known theme properties,
enum and boolean values, built-in base themes, sibling theme fragments, and
font files. Hovering a known property shows its type, purpose, permitted values,
and default where available. Ctrl/Cmd-click and **Go to definition** navigate
to local `${property}` declarations, imported fragment declarations, and theme
files named by `theme.extends`, `theme.colors`, or `theme.metrics`. The built-in
`default`, `dark`, and `light` inheritance targets resolve to their packaged,
read-only `.gltheme` sources as well.

Diagnostics cover malformed or duplicate declarations, invalid typed values,
unknown references, missing imported files, local reference cycles, import
cycles, and invalid color/metrics fragment contents. Custom application keys
are intentionally accepted because `GLThemeValues` is extensible; their local
declarations and references still receive navigation and completion support.
Use a real file URI when cross-file features are required.

Every resolvable color expression receives an inline preview swatch. This also
applies to `${...}` references and `derive(...)` expressions after resolving
local declarations, imported fragments, and built-in base-theme values. A
checkerboard makes partial transparency visible. Hovering the swatch reports
hexadecimal and RGBA representations; clicking it selects the corresponding
source value. Preview size, spacing, border, and checker colors use the
`codeEditor.colorPreview*` theme properties.

## Color values

Theme colors accept hexadecimal, RGB, HSV, HSL, OKLab, OKLCH, named
references, and `derive(...)` expressions. Functional forms accept an optional
final alpha component:

```properties
palette.rgb=rgb(255, 0.5, 32, 75%)
palette.hsv=hsv(210deg, 60%, 80%, 192)
palette.hsl=hsl(210deg, 60%, 50%)
palette.oklab=oklab(0.65, -0.08, 0.12, 0.75f)
palette.oklch=oklch(65%, 0.14, 145deg)
```

`rgba(r, g, b, a)` remains a compatibility alias; new themes can consistently
use `rgb(r, g, b[, a])`. Every RGB and alpha component is interpreted
independently: an integer without a suffix uses `0..255`, a value containing a
decimal point or ending in `f` uses normalized `0..1`, and `%` uses `0..100`.
Formats may be mixed in one color. Consequently, an unsuffixed `1` means
`1/255`; write `1.0`, `1f`, or `100%` for full intensity or opacity.

HSV/HSL saturation, value, and lightness require a percentage or normalized
decimal/`f` value. Hue accepts integer degrees, an explicit `deg` value,
percentages, or normalized decimal/`f` turns. OKLab lightness uses a percentage
or normalized value; its `a` and `b` axes are signed decimals. OKLCH uses the
same lightness, a nonnegative decimal chroma, and a hue. OKLab and OKLCH values
outside the displayable sRGB gamut are clipped when converted to
`java.awt.Color`.

`derive(base, ...)` adjusts `alpha`/`a`, `hue`/`h`, `saturation`/`s`, and
`brightness`/`value`/`v`. Operators include assignment, addition, subtraction,
and multiplication—for example `saturation-=0.15f`. Absolute, additive, and
subtractive unit-channel amounts follow the same integer, decimal/`f`, and
percentage convention as functional colors. Multipliers remain ratios, so
`alpha*=0.5` halves alpha and `alpha*=1` leaves it unchanged. Hue wraps
cyclically and accepts degrees with `deg`, percentages, or normalized turns;
alpha, saturation, and brightness clamp to `0..1`.

## External fonts

Themes can load TrueType/OpenType (`.ttf`, `.otf`) and Type 1 (`.pfa`, `.pfb`)
font files. Paths in filesystem themes are relative to the declaring
`.gltheme` file; packaged theme resources resolve relative to their classpath
location.

```properties
font.source=../fonts/Inter-Regular.ttf
font.size=14
```

For a family with separate faces, declare each available file:

```properties
font.regular=../fonts/Inter-Regular.ttf
font.bold=../fonts/Inter-Bold.ttf
font.italic=../fonts/Inter-Italic.ttf
font.boldItalic=../fonts/Inter-BoldItalic.ttf
font.style=plain
```

The loaded Java font family is selected automatically unless `font.family` is
set explicitly. Control-specific declarations use the same suffixes, for
example `comboBox.font.source` and `comboBox.font.family`. Font sources are
valid in a `theme.type=colors` fragment, so one bundle can combine typography
and color with an independent metrics profile. Missing files, unsupported
extensions, and invalid font data fail loading with `GLThemeLoadException`.

`GuiShowcaseDemo` and `CanvasGuiShowcaseDemo` include an executable example.
Under **Theme → Colors** and **Theme → Metrics**, each category can be changed
without resetting the other. This demonstrates combinations such as a light
palette with extreme geometry or the external VT323 typography with standard
geometry. The fragment files are intentionally stored outside the module
classpath to demonstrate filesystem loading and relative resource resolution.

Applications that expose the same independent choices can compose the selected
files directly:

```java
GLTheme theme = GLThemeLoader.loadBundle(
        GLThemes.DEFAULT,
        Path.of("themes", "colors", "light.gltheme"),
        Path.of("themes", "metrics", "compact.gltheme"));
container.setGLTheme(theme);
```

The first file must declare `theme.type=colors`; the second must declare
`theme.type=metrics`. Either path may be `null` when only one category is
overridden.

## Typed styles

Common families are exposed as immutable records, including `GLLabelStyle`,
`GLButtonStyle`, `GLCheckBoxStyle`, `GLSliderStyle`, `GLTextEditorStyle`,
`GLMenuStyle`, `GLMenuBarStyle`, and `GLComboBoxStyle`.

Extended families such as `GLScrollBarStyle`, `GLScrollPaneStyle`,
`GLTabbedPaneStyle`, `GLNodeStyle`, and `GLNodeEditorStyle` are produced from
`GLThemeValues` with `fromTheme(theme)`. `theme.withCornerRadius(radius)` updates
rounded built-in families consistently.

Style geometry uses logical pixels unless its API states otherwise. Colors are
ordinary non-premultiplied `java.awt.Color`; the renderer performs GPU alpha
conversion.

Fonts can be configured directly in every `.gltheme` file. Global defaults use
`font.family`, `font.style`, and `font.size`; a control family can override them
with keys such as `comboBox.font.family`, `comboBox.font.style`, and
`comboBox.font.size`. Supported styles are `plain`, `bold`, `italic`, and
`bold-italic`. Font sizes use logical pixels. A family without a declared font
source must already be available to Java; normal Java fallback applies
otherwise.

## Text shadows

Built-in controls can draw an offset shadow behind their text. Shadows are
disabled by default through a transparent color. Configure the global shadow
with three theme values:

```properties
textShadow.color=rgba(0, 0, 0, 140)
textShadow.offsetX=1
textShadow.offsetY=1
textShadow.softness=1.25
textShadow.offsetSpace=screen
```

Any control family can override individual values by prefixing them, for
example `button.textShadow.color`, `menu.textShadow.offsetX`, or
`node.textShadow.offsetY`. Supported prefixes include `label`, `button`,
`checkBox`, `slider`, `textEditor`, `menu`, `menuBar`, `comboBox`,
`tabbedPane`, `node`, and `fileDialog`. Menu mnemonic underlines inherit the
same shadow as their label. Missing control-specific values inherit the global
shadow. Offsets use logical surface pixels in the default screen-space mode.
Softness is a non-negative MSDF spread factor: `0` retains the normal crisp
MSDF edge, and larger values progressively widen the transition. It does not
draw multiple offset copies or require an offscreen blur pass.

`textShadow.offsetSpace` accepts `screen` or `local`. The default `screen`
applies the offset after the current transform, so zooming, scaling, or rotating
content does not change the shadow's direction or distance on the surface.
`local` applies the offset before transformation for effects that should follow
the text's object space. Control-specific overrides such as
`node.textShadow.offsetSpace=local` are supported. Mnemonic underlines retain
the configured shadow color and offset, but bound softness to the scale of their
one-pixel decoration so extreme text softness does not create an oversized
rectangular blur.

Custom controls can obtain the resolved immutable value through
`theme.textShadow(prefix)`. `GLTextShadow.NONE` represents a disabled shadow.
Draw shadows before their corresponding text:

```java
GLTextShadow shadow = theme.textShadow("customControl");
context.drawUITextShadow(font, text, x, baseline, shadow);
context.drawUIText(font, text, x, baseline, textColor);

context.drawTextShadow(font, caption, x, baseline, shadow);
context.drawText(font, caption, x, baseline, textColor);
```

Popup geometry distinguishes the outer menu radius from the highlight drawn
behind an active item. Configure the latter with
`menu.itemCornerRadius` in a `.gltheme` file (the bundled default is `2` logical
pixels), or derive a typed style with
`theme.menuStyle().withItemCornerRadius(radius)`. The item radius is applied to
all four corners and does not change how a ComboBox popup joins its owner.

Popup elevation uses an analytic rounded-rectangle shadow in the shared UI
batch. Configure it independently from menu colors and geometry:

```properties
menu.shadow.color=rgba(0, 0, 0, 105)
menu.shadow.softness=9
menu.shadow.offsetX=0
menu.shadow.offsetY=3
```

Set the shadow color alpha or softness to `0` to disable it. Shadow offsets and
softness are logical pixels; GLSwing automatically declares the required visual
overflow so damage tracking and clipping include the complete shadow.

ComboBox press feedback uses `comboBox.pressedLight` and
`comboBox.pressedDark`. The same gradient remains active while its popup is
open and follows the flattened attachment edge. In typed code, use
`GLComboBoxStyle.withPressedGradient(light, dark)`.

## Localization

`GLLocalizer` resolves immutable UTF-8 `ResourceBundle` content. GLSwing ships
root, English, and German strings. Applications can add bundles and direct
overrides:

```java
GLLocalizer localizer = GLLocalizer.forLocale(Locale.ENGLISH)
        .withBundle("com.example.messages")
        .with("myapp.editor.save", "Save");
host.setGLLocalizer(localizer);
```

Resolution order is:

1. direct `with(...)` / `withOverrides(...)` values;
2. application bundles, most recently added first;
3. GLSwing's built-in bundle;
4. the key itself.

Each bundle follows its normal locale chain, such as `de_AT -> de -> root`,
without an implicit fallback to the system locale. Parameters use
`MessageFormat` with the selected locale. Stable built-in keys are defined in
`GLUiKeys`; application keys should use an application-specific namespace.

## Editing localization bundles

`GLCodeEditor` recognizes `.properties` document URIs as
`GLCodeLanguages.LOCALIZATION`. It provides UTF-8 properties highlighting,
completion and hover documentation for stable GLSwing keys, duplicate-key and
`MessageFormat` validation, and support for arbitrary application keys.

When a locale file such as `Messages_de.properties` has a sibling
`Messages.properties`, the editor also:

- reports translations missing from the root bundle;
- marks keys not declared by the root bundle;
- compares the set of `MessageFormat` arguments used by each translation;
- completes untranslated root keys; and
- navigates from a localized key to its root declaration.

The same fallback convention works for more specific files such as
`Messages_de_AT.properties`: the closest existing parent bundle is used for
editing assistance. Cross-file features require a real document URI.
