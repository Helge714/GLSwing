# UI-Framework

Dieses Handbuch beschreibt die retained-mode GUI oberhalb von `GLContext2D`.
Ein `GLContainer` besitzt Zustand und Bounds, wird in einen `GLParent`
eingehängt und zeichnet seine unveränderten Anteile über ein gecachtes
Recording. Controls, Popups und Nodes bauen auf demselben Komponentenbaum auf.

Für unmittelbares Zeichnen, Shader, Layer, RenderTargets und Picking ist das
[Handbuch der Grafikpipeline](graphics-pipeline.md) zuständig.

## Inhalt

- [API-Landkarte](#api-landkarte)
- [Koordinaten und Einheiten](#koordinaten-und-einheiten)
- [Minimalbeispiel](#minimalbeispiel)
- [Komponenten und Hosts](ui-framework/components.md)
- [Layouts und Größen](ui-framework/layout.md)
- [Controls](ui-framework/controls.md)
- [Themes und Sprachen](ui-framework/themes-localization.md)
- [Undo/Redo mit `GLSaveManager`](ui-framework/history.md)
- [Node-System](ui-framework/nodes.md)

## API-Landkarte

| Bereich | Zentrale Typen | Aufgabe |
|---|---|---|
| Hosts | `GLFrame`, `GLSwingContainerCanvas`, `GLSwingPanel`, `GLDialog` | OpenGL-Oberfläche, Animator, Root-Hierarchie und Fenster |
| Komponentenbaum | `GLParent`, `GLContainer` | Kinder, Bounds, Z-Order, Culling, Damage und gecachtes Rendering |
| Layout | `GLLayout`, `GLLayouts`, `GLGridLayout`, `GLConstraints`, `GLSize`, `GLInsets` | Messen und Anordnen |
| Basis-Controls | `GLControl`, `GLLabel`, `GLButton`, `GLCheckBox`, `GLLine` | Zustand, Fokus und einfache Inhalte |
| Werteingabe | `GLSlider`, `GLSliderGroup`, `GLTextEditor`, `GLTextInputFormats`, `GLComboBox<T>` | Werte wählen, validieren und editieren |
| Navigation | `GLMenuBar`, `GLPopupMenu`, `GLMenuItem`, `GLTabbedPane` | Menüs, Popups und Ansichten |
| Scrollen | `GLScrollPane`, `GLScrollBar` | Geclippte Viewports und Scrollmodelle |
| Darstellung | `GLTheme`, `GLThemeValues`, Style-Records, `GLThemeLoader`, `GLThemeWatcher` | Globale und lokale Looks |
| Sprache | `GLLocalizer`, `GLUiKeys` | UTF-8-Texte, Locale-Fallback und Anwendungsüberschreibungen |
| History | `GLSaveManager`, `GLUndoableEdit` | Undo/Redo, Transaktionen und Speicherstatus |
| Nodes | `GLNodeEditor`, `GLNode`, `GLNodePort`, `GLNodeConnection` | Zoombarer Node-Graph mit Verbindungen und Auswahl |

## Koordinaten und Einheiten

Die UI verwendet logische Pixel. `x`, `y`, Breite, Höhe, Padding, Margins,
Layout-Gaps und die meisten Style-Maße beziehen sich deshalb auf denselben
lokalen Koordinatenraum wie die Bounds der Komponente. Betriebssystemskalierung
und Renderauflösung werden vom Host behandelt.

Jedes Kind startet lokal bei `(0, 0)`. Seine `x`-/`y`-Position wird erst beim
Rendern relativ zum Parent angewendet. Recordings bleiben dadurch ortsunabhängig
und können nach Bewegung ohne erneute Tessellierung abgespielt werden.

Abweichende Einheiten werden an der jeweiligen API genannt. Besonders wichtig:

- Node-Verbindungsbreiten, Borders und Snapping sind bildschirmbezogen, damit
  sie beim Zoomen bedienbar und optisch stabil bleiben.
- Der Pan-Wert des Node-Editors ist in logischen Bildschirm-Pixeln angegeben.
- `GLFrame#setRenderResolutionScale(float)` skaliert nur die interne
  Renderauflösung, nicht Layout oder Komponentenkoordinaten.

## Minimalbeispiel

```java
GLContainer root = new GLContainer() {
    @Override
    protected void record(GLContext2D g) {
        // Der Parent selbst hat hier keinen Hintergrund.
    }
};
root.setGLLayout(new GLGridLayout(1).withGap(8));
root.setGLPadding(12);

GLLabel label = new GLLabel("Bereit");
GLButton button = new GLButton("Start");
button.addActionListener(event -> label.setText("Läuft"));

root.addGL(label, GLConstraints.auto().fillHorizontal());
root.addGL(button, GLConstraints.auto().fillHorizontal());

GLFrame frame = new GLFrame("GLSwing");
frame.addGL(root);
frame.packGL();
frame.setVisible(true);
```

`record(...)` beschreibt nur die eigene, cachebare Darstellung eines
Containers. Kinder werden automatisch danach gerendert und müssen dort nicht
manuell aufgerufen werden.

