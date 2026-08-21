# Themes und Sprachen

[UI-Übersicht](../ui-framework.md) · [Controls](controls.md) ·
[Node-System](nodes.md)

## Inhalt

- [Theme-Vererbung](#theme-vererbung)
- [Eingebaute Themes und externe Dateien](#eingebaute-themes-und-externe-dateien)
- [Farbformate](#farbformate)
- [Style-System](#style-system)
- [Sprachen](#sprachen)

## Theme-Vererbung

Ein `GLTheme` wird am Host oder an einem beliebigen `GLContainer` gesetzt und
an dessen Unterbaum vererbt:

```java
frame.setGLTheme(GLThemes.DARK);
settingsPanel.setGLTheme(GLThemes.LIGHT); // nur dieser Unterbaum
settingsPanel.clearGLTheme();             // wieder vom Parent erben
```

Ein Control kann zusätzlich seinen typisierten Style lokal überschreiben:

```java
button.setGLStyle(button.getGLStyle().withCornerRadius(10));
button.clearGLStyle();
```

Theme und Style-Records sind unveränderlich. Ein Wechsel invalidiert die
betroffenen Recordings und Layouts automatisch.

## Eingebaute Themes und externe Dateien

`GLThemes.DEFAULT`, `DARK` und `LIGHT` laden die Ressourcen unter
`glswing-core/src/main/resources/com/vingame/glswing/themes`.
`default.gltheme` ist vollständig;
abgeleitete Themes enthalten mit `theme.extends=...` nur ihre Abweichungen.

```properties
theme.name=My Dark
theme.extends=dark

palette.accent=rgba(255, 118, 72, 255)
state.pressed=${palette.accent}
metrics.cornerRadius=7
nodeEditor.marqueeFill=derive(${palette.accent}, alpha=35%)
```

Referenzen `${key}` werden rekursiv aufgelöst. Freie, nicht von einem
eingebauten Style verwendete Schlüssel bleiben in `theme.values()` erhalten
und können von eigenen Controls ausgewertet werden. So lässt sich ein
Anwendungstheme erweitern, ohne `GLTheme` zu unterklassen.

### Laden und Fallback

```java
GLTheme theme = GLThemeLoader.load(Path.of("themes/my.gltheme"));
frame.setGLTheme(theme);

GLTheme safe = GLThemeLoader.loadOrDefault(
        Path.of("themes/optional.gltheme"), GLThemes.DARK,
        error -> error.printStackTrace());
```

`load(Path)` meldet fehlende Dateien, Referenzzyklen, unbekannte Basisthemes und
ungültige Werte als `GLThemeLoadException`. `loadOrDefault(...)` ist die
bewusste Variante für optionale Anwendungsthemes. Pflichtressourcen der
Bibliothek sollten dagegen nicht still ersetzt werden.

`loadResource(String)` lädt ein Theme aus dem Classpath.

### Live-Reload

```java
GLThemeWatcher watcher = GLThemeWatcher.watch(themePath,
        frame::setGLTheme,
        error -> showThemeError(error.getMessage()));

// Beim Beenden:
watcher.close();
```

Der Watcher lädt sofort einmal, entprellt Dateisystemereignisse und ruft beide
Callbacks auf dem AWT-Event-Thread auf. Ist eine Datei während des Speicherns
kurz unvollständig, bleibt das letzte gültige Theme aktiv. `reloadNow()` stößt
einen manuellen Versuch an.

## Farbformate

Unterstützt werden:

```properties
a=#336699
b=#336699CC
c=rgb(51, 102, 153)
d=rgba(51, 102, 153, 204)
e=rgba(0.2f, 0.4f, 0.6f, 0.8f)
f=rgba(20%, 40%, 60%, 80%)
```

Ganzzahlen verwenden `0..255`, Werte mit `f` den Bereich `0..1`, Prozentwerte
`0%..100%`.

### `derive(...)`

`derive(base, ...)` übernimmt eine Farbe und verändert Alpha oder HSV-Kanäle.
Kanäle heißen `alpha`/`a`, `hue`/`h`, `saturation`/`s` und
`value`/`v`/`brightness`. Pro Ausdruck darf jeder Kanal einmal vorkommen.

| Operator | Bedeutung | Beispiel |
|---|---|---|
| `=` | Absoluten Kanalwert setzen | `alpha=40%` |
| `*=` | Vorhandenen Wert multiplizieren | `alpha*=0.5` |
| `+=` | Betrag addieren | `value+=10%` |
| `-=` | Betrag subtrahieren | `saturation-=0.15f` |

```properties
transparentSelection=derive(${palette.selection}, alpha*=50%)
brighter=derive(${palette.accent}, value+=12%, saturation-=5%)
rotatedHue=derive(${palette.accent}, hue+=30deg)
opaque=derive(rgba(10, 20, 30, 80), alpha=255)
```

Hue wird zyklisch gewrappt; Alpha, Sättigung und Helligkeit werden auf
`0..1` begrenzt. Hue akzeptiert Grad mit `deg`, Prozent oder normalisierte
Floatwerte.

## Style-System

Häufig verwendete Control-Familien liegen als typisierte Records direkt im
`GLTheme`: `GLLabelStyle`, `GLButtonStyle`, `GLCheckBoxStyle`, `GLSliderStyle`,
`GLTextEditorStyle`, `GLMenuStyle`, `GLMenuBarStyle` und `GLComboBoxStyle`.

Weitere Styles wie `GLScrollBarStyle`, `GLScrollPaneStyle`,
`GLTabbedPaneStyle`, `GLNodeStyle` und `GLNodeEditorStyle` werden mit
`fromTheme(theme)` aus `GLThemeValues` erzeugt. Damit bleibt das Theme für neue
Komponenten erweiterbar, ohne seinen Java-Record bei jedem Typ zu vergrößern.

`theme.withCornerRadius(radius)` setzt die Rundung konsistent für alle
eingebauten gerundeten Familien einschließlich Scroll-, Tab- und Node-Werten.

Style-Geometrie verwendet logische Pixel, soweit die jeweilige Control-API
nichts anderes nennt. Farben sind normales, nicht-prämultipliziertes
`java.awt.Color`; die Renderpipeline übernimmt die passende GPU-Darstellung.

## Sprachen

`GLLocalizer` ist ein unveränderlicher UTF-8-Resolver auf Basis von
`ResourceBundle`. Die Bibliothek liefert Root-, englische und deutsche
Properties unter
`glswing-core/src/main/resources/com/vingame/glswing/languages`.

```java
GLLocalizer texts = GLLocalizer.forLocale(Locale.GERMANY)
        .withBundle("com.example.languages.Messages")
        .with("app.documentCount", "{0} Dokumente");

frame.setGLLocalizer(texts);
String title = texts.text("app.documentCount", documentCount);
```

Die Auflösung erfolgt in dieser Priorität:

1. direkte Overrides aus `with(...)` und `withOverrides(...)`,
2. zuletzt ergänzte Anwendungsbundles,
3. GLSwing-Bundle,
4. der Schlüssel selbst als finaler Missing-Value-Fallback.

Innerhalb eines Bundles gilt die normale Locale-Kette, zum Beispiel
`de_AT -> de -> Root`. Es gibt keinen überraschenden Fallback auf die
System-Locale. Parameter werden mit `MessageFormat` und der gewählten Locale
eingesetzt.

Eingebaute Schlüssel stehen stabil in `GLUiKeys`, derzeit unter anderem für
Slider-Reset, Node-Titelbearbeitung, Node-Löschen und Verbindung-Löschen. Eigene
Anwendungen sollten einen eigenen Namespace wie `myapp.editor.save` verwenden.
