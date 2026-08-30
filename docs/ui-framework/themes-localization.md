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
- [Color values](#color-values)
- [External fonts](#external-fonts)
- [Typed styles](#typed-styles)
- [Localization](#localization)

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

## Color values

Theme colors accept hexadecimal, integer RGB/RGBA, normalized float channels,
percentages, named references, and `derive(...)` expressions. Integer channels
use `0..255`; values suffixed with `f` use `0..1`.

`derive(base, ...)` adjusts `alpha`/`a`, `hue`/`h`, `saturation`/`s`, and
`brightness`/`value`/`v`. Operators include assignment, addition, subtraction,
and multiplication—for example `saturation-=0.15f`. Hue wraps cyclically;
alpha, saturation, and brightness clamp to `0..1`. Hue accepts degrees with
`deg`, percentages, or normalized turns.

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

Popup geometry distinguishes the outer menu radius from the highlight drawn
behind an active item. Configure the latter with
`menu.itemCornerRadius` in a `.gltheme` file (the bundled default is `2` logical
pixels), or derive a typed style with
`theme.menuStyle().withItemCornerRadius(radius)`. The item radius is applied to
all four corners and does not change how a ComboBox popup joins its owner.

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
