# GLSwingNext

GLSwingNext ist ein leichtgewichtiger JOGL-basierter 2D-Renderer mit direkter
`GLCanvas`- und `GLSwingPanel`-Integration. Der aktuelle Stand umfasst gebatchte Formen,
Texturen und MSDF-Text, Recording, Picking, Layer-Effekte, Layouts und eigene
GPU-gerenderte Bedienelemente.

## Standalone-Fenster

`GLFrame` ist die optionale Komfortschicht fuer eigenstaendige Anwendungen. Es
erbt die ueblichen Fensterfunktionen von `JFrame`, besitzt bereits eine direkte
`GLSwingContainerCanvas` und verwaltet deren `FPSAnimator` samt sicherem
Shutdown. Der manuelle Aufbau aus `JFrame` und `GLSwingContainerCanvas` bleibt
weiterhin voll unterstuetzt.

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

`getGLCanvas()` stellt den direkten Zugriff fuer fortgeschrittene Anpassungen
bereit. Geerbte Inhaltsmethoden wie `add(Component)`, `setContentPane(...)` und
`setJMenuBar(...)` bleiben kompatibel, sind auf `GLFrame` aber als deprecated
markiert: Fuer garantiertes GLSwing-Verhalten sollen `addGL(...)` und die
GPU-gerenderte Menueleiste verwendet werden.

Canvas-Inhalte werden auch beim interaktiven Aendern der Fenstergroesse weiter
vom Animator aktualisiert. GLSwing unterdrueckt dazu standardmaessig das
vorzeitige weisse Loeschen des AWT-Canvas. Mit
`-Dsun.awt.noerasebackground=false` kann dieses globale AWT-Verhalten vor dem
Start ausdruecklich wieder aktiviert werden.

## Tabs und Scrollbereiche

`GLScrollPane` nimmt genau einen frei gestaltbaren `GLContainer` als Inhalt
auf. Vertikale und horizontale Scrollbars erscheinen standardmaessig nur bei
Bedarf; `Shift` + Mausrad scrollt horizontal. Verschachtelte ScrollPanes werden
durch weitergereichte, konsumierbare Mausradereignisse unterstuetzt. Mit
`scrollRectToVisible(...)` kann ein Inhaltsbereich programmatisch eingeblendet
werden.

`GLTabbedPane` verwaltet Titel, Enabled-Zustand, Auswahl und Inhalt seiner Tabs.
Zu breite Titel werden gekuerzt; passt selbst die minimale Tab-Leiste nicht
mehr in die Breite, erscheint darunter eine horizontale Scrollbar. Der Inhalt
des gewaehlten Tabs darf wiederum ein `GLScrollPane` sein.

```java
GLScrollPane scrollPane = new GLScrollPane(largeContent);
GLTabbedPane tabs = new GLTabbedPane();
tabs.addTab("Liste", scrollPane);
tabs.addTab("Einstellungen", settings);
tabs.setEnabledAt(1, false);
```

Farben und Geometrie liegen in `default.gltheme` unter `scrollBar.*`,
`scrollPane.*` und `tabbedPane.*`. Der allgemeine GUI-Showcase demonstriert
Tabs samt Overflow-Scrollbar; der noch ausstehende umfassende ScrollPane-
Showcase steht zentral in [`TODO.md`](TODO.md).

## Native GLSwing-Dialoge

`GLDialog` zeigt denselben `GLContainer`-Inhalt als frei bewegliches, an sein
Hauptfenster gekoppeltes Betriebssystemfenster. GLJPanel- und GLCanvas-Owner
verwenden dafuer ein owned `JDialog` mit direktem GLCanvas. Verschachtelte
Dialoge werden jeweils an den unmittelbar aufrufenden Dialog gekoppelt.

```java
GLDialog dialog = new GLDialog("Einstellungen", settingsContainer);
dialog.setSize(520, 360);
dialog.setModality(GLDialog.Modality.OWNER_MODAL);
dialog.show(owner);
dialog.closedFuture().thenRun(() -> System.out.println("geschlossen"));
```

Canvas-Dialoge benoetigen dieselben `--add-opens`-Argumente wie GLCanvas.
Dialoge und verschachtelte Dialoge sind in beiden GUI-Showcases enthalten.

## Kernbeispiele

- `GuiShowcaseDemo` – erweiterbare Gesamtdemo aller GUI-Controls und Zustaende
- `DrawingApiDemo` – gefuellte und gezeichnete Formen sowie Farben pro Punkt
- `CustomShaderDemo` – getrennte Filled-, Texture- und Text-Shader mit Uniforms
- `LayerDemo` – Gruppen-Alpha und verschachtelte Layer
- `RecordingParameterDemo` – wiederverwendbare Recordings mit Laufzeitparametern
- `PickingDemo` – Picking fuer Formen, Texturen und Text
- `GLFrameDemo` – verwaltetes Standalone-Fenster mit Renderkonfiguration
- `PathDemo` und `SvgImportDemo` – Pfade, Strokes und vollstaendige SVGs
- `ImageAtlasDemo` – Bilder, adaptive SVG-Icons und Texturatlanten
- `NodeEditorDemo` – interaktiver Node-Editor
- `GameOfLifeDemo` – persistente RenderTargets und GPU-Ping-Pong-Feedback

Die vollstaendige GUI-Szene ist sowohl als `GLSwingPanel` als auch als direkte
`GLCanvas` vorhanden. Unter Windows wird die Canvas-Variante am einfachsten
ueber ihre Eclipse-Launch-Konfiguration gestartet, da JOGL dort zusaetzliche
`--add-opens`-Argumente benoetigt. Performancewerte stehen im Fenstertitel.

## Tests

Die Klassen unter `com.vingame.glswing.test` sind eigenstaendig startbare
CPU-, GPU- und Robot-Regressionstests. Sie bleiben bewusst getrennt von den
Demos, damit Fehler in Rendering, Recording und Eingabeverarbeitung gezielt
reproduzierbar sind.

## Bilder, Icons und Texturatlanten

`GLImage` ist die empfohlene High-Level-Ressource fuer normale Bilder. Sie kann
ohne aktuellen OpenGL-Kontext geladen werden. Beim ersten Zeichnen entscheidet
der jeweilige Renderer automatisch: kleine statische Clamp-Bilder werden in
RGBA-Atlasseiten gepackt, grosse, wiederholte oder dynamische Bilder erhalten
eine eigene Textur. `GLTexture` bleibt als explizite Low-Level-API erhalten.

```java
GLImage photo = GLImage.loadResource(MyApp.class, "/images/photo.png");
GLImage pixels = GLImage.load(path, GLImageOptions.pixelArt());

context.drawImage(photo, 20, 20, 320, 180);       // normaler TextureBatch
context.drawUIImage(pixels, 360, 20, 32, 32);     // gemeinsamer UI-Batch
```

Mit `GLImageOptions.atlas()`, `standalone()`, `pixelArt()`, `dynamic()` und
`repeating()` kann die Automatik gezielt ueberschrieben werden. Statische,
linear gefilterte RGBA-Bilder verwenden standardmaessig atlas-sichere Mipmaps
mit trilinearem Filtering. Die Auswahl kann explizit angepasst werden:

```java
GLImageOptions noMipmaps = GLImageOptions.DEFAULT
        .withMipmapping(GLImageOptions.Mipmapping.NONE);
GLImageOptions forcedMipmaps = GLImageOptions.standalone()
        .withMipmapping(GLImageOptions.Mipmapping.ENABLED);
```

Mip-Atlanten besitzen groessere extrudierte Raender und nur so viele Stufen,
wie Bildgroesse und Rand sicher erlauben. MSDF-Icons, dynamische Bilder und
Pixel-Art verwenden keine gewoehnlichen Mipmaps. Ein Batch kann
bis zu 16 unterschiedliche Texturen gleichzeitig binden; weitere Texturen
erzeugen automatisch den naechsten Drawcall. Picking nutzt dieselben Slots.

`GLIcon` importiert SVGs als Icon-Ressource. `AUTO` verwendet fuer einfarbige
SVGs ein frei einfaerbbares MSDF und fuer mehrfarbige SVGs adaptive
RGBA-Aufloesungsstufen:

```java
GLIcon link = GLIcon.fromSvgResource("com/example/icons/link.svg");
GLIcon logo = GLIcon.fromSvgResource("com/example/icons/logo.svg",
        GLIcon.Format.RGBA);

context.drawIcon(link, 20, 220, 24, 24, accentColor);
context.drawIcon(logo, 60, 210, 96, 96);
```

Mehrfarbige SVGs behalten ihr Vektordokument und rasterisieren bei Bedarf eine
passende, endliche Aufloesungsstufe in einem gemeinsamen Hintergrund-Worker.
Bis sie bereitsteht, bleibt die beste vorhandene Stufe sichtbar. Schnelle
Zoomschritte werden zusammengefasst; der kurze GPU-Upload erfolgt spaeter mit
hoechstens einem adaptiven Icon-Upgrade pro Renderer-Frame. Die Auswahl
beruecksichtigt Projektion, Transform und HiDPI-Skalierung. Sie geschieht auch
beim Abspielen eines bereits vorhandenen Recordings neu; Zoom erfordert daher
weder eine neue Tessellierung noch eine neue Aufnahme. Aufloesungsbereich,
Stufung, Oversampling und Wartezeit lassen sich konfigurieren:

```java
GLIconOptions options = GLIconOptions.DEFAULT
        .withResolutionRange(32, 1024)
        .withResolutionStep(2)
        .withOversampling(1.25f)
        .withRasterizationDelayMillis(150);
GLIcon adaptiveLogo = GLIcon.fromSvgResource(
        "com/example/icons/logo.svg", GLIcon.Format.RGBA, options);

// Optionales Vorwaermen erledigt nur die CPU-Rasterisierung.
adaptiveLogo.prepareAsync(512).thenRun(frame::repaintGL);
```

Die Rastervarianten landen weiterhin in den RGBA-Atlanten und verwenden deren
Mipmaps zum sauberen Verkleinern. Fuer bewusst feste Rastergroessen steht
`GLIconOptions.fixed(resolution)` bereit; MSDF-Icons bleiben prinzipbedingt
auf einer festen Quellaufloesung.

UI-Flächen, MSDF-Text, RGBA-Icons und MSDF-Icons bleiben dabei im selben
universellen UI-Batch. `Image Atlas Demo.launch` zeigt alle Varianten samt Zoom
und Drawcall-Anzeige, ohne die Szene beim Zoomen neu aufzunehmen;
`ImageAtlasGpuSelfTest` prueft Atlaswahl, adaptive Recording-Wiedergabe,
Slotgrenze und Picking auf einer Offscreen-GL3-Instanz.

## Persistente RenderTargets und Feedback

`GLRenderTarget` ist ein lazy erzeugter, persistenter RGBA8-FBO mit eigener
Aufloesung. Innerhalb eines Targets verwendet der Context Top-Left-Pixelmasse;
Picking ist deaktiviert. Target-, Viewport-, Transform-, Projektions-, Shader-
und Uniformzustand werden beim Beenden vollstaendig restauriert:

```java
GLRenderTarget target = new GLRenderTarget(512, 512,
        GLRenderTargetOptions.pixelArt());

context.withRenderTarget(target, () -> {
    context.clear(Color.TRANSPARENT);
    context.fillCircle(256, 256, 80, Color.WHITE);
});
context.drawRenderTarget(target, 20, 20, 400, 400);
```

Fuer Simulationen kapselt `GLFeedbackBuffer` zwei Targets. `current()` bleibt
waehrend eines Updates lesbar, waehrend in `next()` geschrieben wird; nach
erfolgreichem Abschluss werden beide atomar getauscht:

```java
GLFeedbackBuffer feedback = new GLFeedbackBuffer(512, 512,
        GLRenderTargetOptions.repeatingPixelArt());

feedback.update(context, () -> {
    context.setTextureShader(simulationShader);
    context.drawRenderTarget(feedback.current(), 0, 0, 512, 512);
});
feedback.drawCurrent(context, 20, 20, 700, 700);
```

Gleichzeitiges Lesen und Schreiben desselben Targets wird abgewiesen. Targets
sind dynamische Ressourcen und werden daher bewusst nicht in `GLRecording`
aufgenommen. `GPU Game of Life Demo.launch` zeigt eine 256x256-Simulation ohne
GPU-zu-CPU-Readback; `RenderTargetGpuSelfTest` prueft Persistenz, Orientierung,
State-Restoration und Ping-Pong-Rendering.

## Gruppierte Slider

Kompatible Slider koennen als vertikaler Block mit gemeinsamem Rahmen angelegt
werden. FIXED-Slider muessen denselben Wertebereich besitzen; FREE-Slider
koennen ebenfalls gruppiert werden. Beim vertikalen Ueberstreichen waehrend
eines Drags werden weitere Zeilen fuer diese Geste gekoppelt:

```java
GLSliderGroup group = new GLSliderGroup(
        GLSlider.free("Rot", 128),
        GLSlider.free("Gruen", 96),
        GLSlider.free("Blau", 160));
```

Die Kopplung endet beim Loslassen der Maustaste. Ein gemeinsamer FIXED-Bereich
kann spaeter atomar ueber `group.setRange(minimum, maximum)` geaendert werden.

## Sprachen

`GLLocalizer` liest UTF-8-Properties-Bundles mit der ueblichen Locale-Kette
(`de_AT -> de -> Root`). Das GLSwing-Root-Bundle ist Englisch; danach dient der
Schluessel selbst als letzter Fallback. Anwendungstexte koennen als zusaetzliches
Bundle oder als direkte Overrides vor die Bibliothek geschaltet werden:

```java
GLLocalizer texts = GLLocalizer.forLocale(Locale.GERMAN)
        .withBundle("com.example.languages.Messages")
        .with("myapp.special", "Spezial");
panel.setGLLocalizer(texts);

String status = texts.text("myapp.items", itemCount);
```

## Externe Themes

`GLThemeLoader.loadResource(...)` laedt mitgelieferte oder anwendungseigene
`.gltheme`-Ressourcen. `GLThemeLoader.load(Path)` liest austauschbare Dateien.
Fehlende Werte stammen aus `theme.extends`, anschliessend aus `GLTheme.DEFAULT`.
Das vollstaendige Standard-Theme liegt in
`resources/com/vingame/glswing/themes/default.gltheme`; es ist die einzige
Quelle fuer die mitgelieferte Standardoptik. Die Java-`DEFAULT`-Konstanten
werden daraus aufgebaut und enthalten keine zweite Farbliste.

```properties
theme.extends=dark
palette.accent=rgba(1f, 0.5f, 0.5f, 1f)
state.pressed=${palette.accent}
metrics.cornerRadius=7
button.cornerRadius=10
custom.timelineMarker=${palette.accent}
```

Unterstuetzte Farben sind `#RRGGBB`, `#RRGGBBAA`, `rgb(...)` und `rgba(...)`
mit 0-255-Werten, konsistenten `f`-Werten von 0 bis 1 oder Prozenten. Freie
Tokens bleiben ueber `theme.values()` fuer eigene Controls erhalten. Die
mitgelieferten Presets stehen als `GLThemes.DARK` und `GLThemes.LIGHT` bereit.

`GLThemeLoader.load(...)` und `loadResource(...)` sind strikt und melden eine
fehlende oder fehlerhafte Datei als `GLThemeLoadException`. Fuer optionale
Benutzerthemes gibt es einen expliziten Fallback, bei dem der Fehler weiterhin
protokolliert werden kann:

```java
GLTheme theme = GLThemeLoader.loadOrDefault(path, GLThemes.DARK,
        error -> logger.warning(error.getMessage()));
```

Zum Entwickeln kann eine Datei automatisch neu geladen werden. Der Watcher
beobachtet den ganzen Theme-Ordner, damit auch Aenderungen an einem geerbten
`default.gltheme` das aktuell ausgewaehlte Theme aktualisieren. Die Handler
laufen auf dem AWT-Thread; bei einem Ladefehler bleibt das letzte gueltige Theme
aktiv:

```java
GLThemeWatcher watcher = GLThemeWatcher.watch(themePath,
        panel::setGLTheme,
        error -> logger.warning(error.getMessage()));

// Beim Schliessen des Fensters:
watcher.close();
```

## Ressourcen

Mitgelieferte Dateien liegen getrennt vom Java-Code unter `resources`:

```text
resources/com/vingame/glswing/
  languages/   UTF-8-Properties und Locale-Fallbacks
  themes/      default.gltheme, dark.gltheme und light.gltheme
  shaders/     interne Vertex- und Fragment-Shader
```

Das englische Root-Sprachbundle ist der Fallback fuer nicht unterstuetzte
Locales. Fehlt diese Pflichtressource oder das Standard-Theme bzw. ein interner
Shader, bricht GLSwing mit einer klaren Fehlermeldung ab; solche Dateien sind
Bestandteil der Bibliothek und kein optionaler Laufzeit-Fallback.
#
