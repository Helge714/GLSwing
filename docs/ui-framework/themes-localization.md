# Themes and Localization

[UI guide](../ui-framework.md) · [Controls](controls.md) · [Components](components.md)

Themes provide immutable typed styles and extensible values. `GLLocalizer`
resolves UTF-8 resource bundles through an explicit locale and deterministic
fallback chain.

## Contents

- [Theme inheritance](#theme-inheritance)
- [Built-in and external themes](#built-in-and-external-themes)
- [Loading and live reload](#loading-and-live-reload)
- [Color values](#color-values)
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

## Loading and live reload

```java
GLTheme theme = GLThemes.load(path);
GLTheme optional = GLThemes.loadOrDefault(path, GLThemes.DEFAULT);
GLTheme packaged = GLThemes.loadResource("/themes/application.gltheme");
```

`load(Path)` reports missing files, cycles, unknown base themes, and invalid
values as `GLThemeLoadException`. Use `loadOrDefault(...)` only for genuinely
optional application themes; required resources should fail visibly.

The file watcher performs an initial load, debounces filesystem events, and
invokes success/failure callbacks on the AWT event thread. An incomplete write
does not replace the last valid theme. `reloadNow()` requests an immediate
reload. Close the watcher when its host is disposed.

## Color values

Theme colors accept hexadecimal, integer RGB/RGBA, normalized float channels,
percentages, named references, and `derive(...)` expressions. Integer channels
use `0..255`; values suffixed with `f` use `0..1`.

`derive(base, ...)` adjusts `alpha`/`a`, `hue`/`h`, `saturation`/`s`, and
`brightness`/`value`/`v`. Operators include assignment, addition, subtraction,
and multiplication—for example `saturation-=0.15f`. Hue wraps cyclically;
alpha, saturation, and brightness clamp to `0..1`. Hue accepts degrees with
`deg`, percentages, or normalized turns.

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
