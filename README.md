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
`scrollPane.*` und `tabbedPane.*`. Eine interaktive Kombination ist ueber
`Scroll Tabs Demo.launch` startbar. Zurueckgestellte Vorhaben stehen zentral in
[`TODO.md`](TODO.md).

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

Canvas-Dialoge benoetigen dieselben `--add-opens`-Argumente wie GLCanvas und
sind ueber `Canvas Dialog Demo.launch` direkt testbar.

## Kernbeispiele

- `GuiShowcaseDemo` – erweiterbare Gesamtdemo aller GUI-Controls und Zustaende
- `DrawingApiDemo` – gefuellte und gezeichnete Formen sowie Farben pro Punkt
- `CustomShaderDemo` – getrennte Filled-, Texture- und Text-Shader mit Uniforms
- `LayerDemo` – Gruppen-Alpha und verschachtelte Layer
- `RecordingParameterDemo` – wiederverwendbare Recordings mit Laufzeitparametern
- `PickingDemo` – Picking fuer Formen, Texturen und Text
- `DraggableZOrderDemo` – Dragging und Zeichenreihenfolge
- `LayoutDemo` – leichtgewichtiges Grid- und Free-Layout
- `SingleButtonDemo` – Buttons, Checkbox und beide Slider-Modi
- `ComboBoxDemo` – Popup-Platzierung, Submenus, Scrolling und Zoom
- `MenuDemo` – Menueleiste, Kontextmenues und verschachtelte Submenus
- `CanvasMenuDemo` – Menues auf einer direkten `GLCanvas`
- `CanvasSliderPerformanceDemo` – Benchmark fuer grosse Slider-Szenen
- `GLFrameDemo` – verwaltetes Standalone-Fenster mit Renderkonfiguration
- `ScrollTabsDemo` – verschachtelte Scrollbereiche, Scrollbars und Tabs

Die beiden Canvas-Demos werden unter Windows am einfachsten ueber ihre
mitgelieferten Eclipse-Launch-Konfigurationen gestartet, da JOGL dafuer
zusaetzliche `--add-opens`-Argumente benoetigt.
Die vollstaendige Vergleichsszene ist sowohl als `GLSwingPanel` als auch als
direkte `GLCanvas` vorhanden; ihre Performancewerte stehen im Fenstertitel.

## Tests

Die Klassen unter `com.vingame.glswing.test` sind eigenstaendig startbare
CPU-, GPU- und Robot-Regressionstests. Sie bleiben bewusst getrennt von den
Demos, damit Fehler in Rendering, Recording und Eingabeverarbeitung gezielt
reproduzierbar sind.

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
#   G L S w i n g N e x t  
 