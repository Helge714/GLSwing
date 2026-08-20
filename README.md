# GLSwingNext

GLSwingNext ist eine leichtgewichtige, JOGL-basierte 2D-Grafik- und
GUI-Bibliothek für Java. Sie verbindet eine an `Graphics2D` angelehnte API mit
GPU-Batching, scharfem MSDF-Text, Picking, wiederverwendbaren Recordings,
Layer-Effekten und eigenen GPU-gerenderten Bedienelementen.

Das Projekt befindet sich in aktiver Entwicklung. Die aktuelle API ist die
einzige unterstützte Fassung; veraltete Zwischenstände werden nicht durch eine
Kompatibilitätsschicht konserviert.

## Java-Voraussetzungen

GLSwingNext wird mit Java 17 als Mindest- und API-Ziel kompiliert. Anwendungen
können die Bibliothek daher unter Java 17 oder jeder neueren Java-Version
verwenden. Für die Entwicklung empfiehlt sich ein aktuelles LTS-JDK; Eclipse
Temurin 25 ist eine freie, Java-SE-kompatible OpenJDK-Distribution und eine
sinnvolle Standardwahl. Das neuere Entwicklungs-JDK ändert die erzeugte
Java-17-Kompatibilität nicht, solange der Compiler mit Release 17 arbeitet.

In Eclipse verwendet das Projekt deshalb die Execution Environment
`JavaSE-17`. Ein konfiguriertes Temurin 25 kann diese bedienen; es muss kein
separates JDK 17 installiert werden.

## Dokumentation

- [Dokumentationsübersicht](docs/README.md)
- [Grafikpipeline und `GLContext2D`](docs/graphics-pipeline.md) – vollständige
  Befehls- und Konfigurationsreferenz mit Beispielen für Shader, Atlanten,
  Recording, Picking, Layer und RenderTargets
- [UI-Framework](docs/ui-framework.md) – Komponenten, Hosts, Layouts, alle
  Controls, Themes, Sprachen, Undo/Redo und das Node-System
- [Offene Vorhaben](TODO.md)

Die README bleibt bewusst kurz. Ausführliche Anleitungen gehören nach `docs`,
vollständig startbare Szenen nach `src/com/vingame/glswing/demo` und gezielte
Regressionstests nach `src/com/vingame/glswing/test`.

## Schnellstart mit `GLFrame`

`GLFrame` kombiniert `JFrame`, direkte `GLSwingContainerCanvas`, Animator und
sicheren Shutdown. Der manuelle Aufbau mit `JFrame` und Canvas sowie die
Einbettung per `GLSwingPanel` bleiben ebenfalls unterstützt.

```java
GLFrame.RenderingSettings rendering = GLFrame.RenderingSettings.DEFAULT
        .withAntialiasingSamples(4)
        .withVSync(true)
        .withTargetFps(144);

GLFrame frame = new GLFrame("Anwendung", rendering);
frame.setSize(1280, 800);
frame.setRenderResolutionScale(1f);
frame.setGLClearColor(new Color(20, 25, 35));
frame.addGL(root);
frame.setVisible(true);
```

Geerbte Swing-Inhaltsmethoden wie `add(Component)`, `setContentPane(...)` und
`setJMenuBar(...)` funktionieren technisch, sind auf `GLFrame` aber deprecated.
Für garantiertes GLSwing-Verhalten dienen `addGL(...)` und die GPU-gerenderte
Menüleiste. `getGLCanvas()` gibt bei Bedarf Zugriff auf den direkten Canvas.

Unter Windows benötigt der direkte Canvas zusätzliche `--add-opens`-Argumente.
Die mitgelieferten Eclipse-Launch-Konfigurationen enthalten sie bereits.

## Funktionsumfang

### Grafikpipeline

- gebatchte Filled-, Texture-, Text- und 2.5D-Sprite-Pipelines
- universeller instanzierter UI-Batch für Controls, MSDF-Text und Icons
- Linien, Dashes, Caps, Joins, Offset und World-/Screen-Space-Breiten
- Polygone, Beziers, `GLPath2D` und SVG-Import
- Model-Transforms, Projektionen und `GLCamera2D`
- numerisches Picking oder direkte `GLMouseTarget`-Ziele
- Recordings mit verschachtelbaren Laufzeitparametern
- GPU-residente statische Sprite-Puffer mit relativem Draw-Transform
- verschachtelte, begrenzte Layer mit Blendmodi und Effektketten
- persistente RenderTargets und Ping-Pong-Feedback ohne CPU-Readback
- optionaler Z-Buffer sowie Sprite-Materialien mit Color-, Depth- und Normalmap
- Cutout-/Blend-Alpha, depth-korrektes Picking und Ambient-/Richtungslicht
- eigene Filled-, Texture-, Text- und Layer-Shader mit typgeprüften Uniforms
- automatische RGBA-Atlanten, Mipmapping, MSDF-Icons und adaptive SVG-Rasterung

### GUI

- leichte `GLContainer`-Hierarchie mit Layout, Culling und Damage-Tracking
- Buttons, Checkboxen, Slider, ComboBoxen, Menüs und Menüleisten
- Texteditoren, Tabs, Scrollbereiche und Dialoge
- gruppierte Slider und interaktive Node-Editor-Grundlagen
- Z-Order, Themes, Sprachen und Undo/Redo

## Wichtige Demos

| Demo | Zweck |
|---|---|
| `GuiShowcaseDemo` | Gesamtdemo der GUI-Controls und Zustände |
| `CanvasGuiShowcaseDemo` | Gleiche GUI über den direkten Canvas |
| `GLFrameDemo` | Verwaltetes Standalone-Fenster |
| `DrawingApiDemo` | Formen, Linien und Farben pro Punkt |
| `PathDemo`, `SvgImportDemo` | Pfade, Strokes und SVG-Dokumente |
| `CustomShaderDemo` | Eigene Filled-, Texture- und Textshader |
| `RecordingParameterDemo` | Recordings und Laufzeitparameter |
| `LayerDemo` | Gruppen-Alpha, Blendmodi und Effekte |
| `ImageAtlasDemo` | Bilder, Icons, Atlanten und adaptives Rasterisieren |
| `DepthSpriteDemo` | Pixelgenaue Sprite-Tiefe, Normalmaps, Licht und Picking |
| `SpritePipelineBenchmarkDemo` | Interaktiver End-to-End-Vergleich von Kanälen, Alpha, Bewegung und Picking |
| `SpritePipelineBenchmark` | Reproduzierbare Offscreen-CPU-/GPU-Messung ohne Präsentation |
| `ResidentSpriteBenchmark` | Direkter A/B-Test von dynamischen und GPU-residenten Sprite-Instanzen |
| `PickingDemo` | Picking für Formen, Texturen und Text |
| `NodeEditorDemo` | Interaktiver Node-Editor |
| `GameOfLifeDemo` | RenderTargets und GPU-Ping-Pong |

Die zugehörigen `.launch`-Dateien starten die wichtigsten Szenen direkt in
Eclipse. Performancewerte stehen in den entsprechenden Fenstertiteln oder
Statusanzeigen.

## GUI-Kurzbeispiel

```java
GLScrollPane scrollPane = new GLScrollPane(largeContent);

GLTabbedPane tabs = new GLTabbedPane();
tabs.addTab("Liste", scrollPane);
tabs.addTab("Einstellungen", settings);
tabs.setEnabledAt(1, false);

frame.addGL(tabs);
```

`GLScrollPane` zeigt Scrollbars bei Bedarf und unterstützt verschachteltes
Clipping. `GLTabbedPane` kürzt zu breite Titel und scrollt seine Tab-Leiste,
wenn selbst die Mindestbreiten nicht mehr passen.

## Dialoge

`GLDialog` zeigt einen `GLContainer` als frei bewegliches, an seinen Owner
gekoppeltes Betriebssystemfenster. Verschachtelte Dialoge werden an den direkt
aufrufenden Dialog gekoppelt.

```java
GLDialog dialog = new GLDialog("Einstellungen", settingsContainer);
dialog.setSize(520, 360);
dialog.setModality(GLDialog.Modality.OWNER_MODAL);
dialog.show(owner);
dialog.closedFuture().thenRun(() -> System.out.println("geschlossen"));
```

## Themes und Sprachen

Das vollständige Standard-Theme liegt ausschließlich unter
`resources/com/vingame/glswing/themes/default.gltheme`. `dark.gltheme` und
`light.gltheme` überschreiben nur ihre Abweichungen. Eigene Dateien werden mit
`GLThemeLoader.load(Path)` geladen; `GLThemeWatcher` kann sie während der
Entwicklung live neu laden.

```properties
theme.extends=dark
palette.accent=rgba(1f, 0.5f, 0.5f, 1f)
state.pressed=${palette.accent}
metrics.cornerRadius=7
```

Farben unterstützen `#RRGGBB`, `#RRGGBBAA`, `rgb(...)` und `rgba(...)` mit
0–255-Werten, Floatwerten `0f..1f` oder Prozenten. Freie Tokens bleiben über
`theme.values()` für eigene Controls zugänglich.

`GLLocalizer` liest UTF-8-Properties mit der üblichen Locale-Kette, zum
Beispiel `de_AT -> de -> Root`. Anwendungstexte können ein eigenes Bundle und
direkte Overrides vor das Bibliotheksbundle setzen:

```java
GLLocalizer texts = GLLocalizer.forLocale(Locale.GERMAN)
        .withBundle("com.example.languages.Messages")
        .with("myapp.special", "Spezial");
frame.setGLLocalizer(texts);
```

## Tests

Die Klassen unter `com.vingame.glswing.test` sind eigenständig startbare CPU-,
GPU- und Robot-Regressionstests. Sie bleiben getrennt von Demos, damit Fehler in
Rendering, Recording und Eingabe gezielt reproduzierbar sind.

## Ressourcenstruktur

```text
resources/com/vingame/glswing/
  languages/   UTF-8-Properties und Locale-Fallbacks
  themes/      default.gltheme, dark.gltheme und light.gltheme
  shaders/     interne Vertex- und Fragmentshader
```

Pflichtressourcen wie Root-Sprache, Standard-Theme und interne Shader gehören
zur Bibliothek. Fehlen sie, meldet GLSwing den Fehler klar, statt still eine
abweichende Ersatzdarstellung zu verwenden.
