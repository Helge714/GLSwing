# Node-System

[UI-Übersicht](../ui-framework.md) · [Controls](controls.md) ·
[Themes](themes-localization.md) · [History](history.md) ·
[Grafikpipeline: Picking](../graphics-pipeline/picking.md)

## Inhalt

- [Minimaler Graph](#minimaler-graph)
- [`GLNode`](#glnode)
- [Ports und Verbindungsregeln](#ports-und-verbindungsregeln)
- [Verbindungen](#verbindungen)
- [Auswahl und Interaktion](#auswahl-und-interaktion)
- [Ansicht](#ansicht)
- [Verbindungsgeometrie und Einheiten](#verbindungsgeometrie-und-einheiten)
- [Styles](#styles)
- [History und Modellgrenze](#history-und-modellgrenze)

Das Node-System ist eine spezialisierte retained-mode UI auf Basis derselben
`GLContainer`-, Picking-, Theme- und History-Infrastruktur. `GLNodeEditor`
verwaltet Ansicht, Auswahl und Verbindungen; `GLNode` ordnet Inhalte und Ports
an; das fachliche Graphmodell bleibt in der Anwendung.

## Minimaler Graph

```java
GLNodeEditor editor = new GLNodeEditor();

GLNode source = new GLNode("Quelle");
GLNodePort colorOut = source.addOutput("color", new GLLabel("Farbe"));
source.setResizeMode(GLNodeResizeMode.HORIZONTAL);

GLNode target = new GLNode("Ausgabe");
GLNodePort colorIn = target.addInput("color", new GLComboBox<ColorMode>());

editor.addNode(source, 80, 100);
editor.addNode(target, 420, 140);
editor.connect(colorOut, colorIn);
```

Node-Positionen sind Weltkoordinaten des Editors. Zoom und Pan verändern nur die
Ansicht und nicht die gespeicherten Bounds der Nodes.

## `GLNode`

### Titel, Auswahl und Größe

```java
node.setTitle("Multiply");
node.setTitleEditable(true);
node.setTitleGradientEnabled(true);
node.setResizeMode(GLNodeResizeMode.HORIZONTAL);
node.setDragAnywhere(false);
```

Ein editierbarer Titel wird direkt in der Titelfläche bearbeitet; zusätzlich
enthält das Kontextmenü den lokalisierten Bearbeiten-Eintrag. Der Node kann über
die Titelzeile verschoben werden. Mit `dragAnywhere=true` gilt das auch für freie,
nicht von einem pickbaren Kind belegte Inhaltsfläche.

`GLNodeResizeMode` erlaubt `NONE`, `HORIZONTAL`, `VERTICAL` oder `BOTH`.
`isSelected()` und `isActiveSelected()` unterscheiden normale Mehrfachauswahl
vom zuletzt aktiv gewählten Node. Es kann höchstens einen aktiven Node geben.

### Inhalte und Zeilen

```java
node.addContent(new GLLabel("Nur Anzeige"));
GLNodePort in = node.addInput("factor", slider);
GLNodePort out = node.addOutput(combo, GLNodePort.output("result"));
GLNodePorts pair = node.addInputOutput("min", minSlider, "max");
```

`addContent(...)`, `addInput(...)`, `addOutput(...)` und
`addInputOutput(...)` bilden intern die notwendigen Zeilen, ohne dass die
Anwendung einen öffentlichen `GLNodeRow` verwalten muss. Padding, vertikaler
Abstand und Portpositionen kommen aus `GLNodeStyle`. Inputs liegen am linken,
Outputs am rechten Rand.

`getContents()`, `getPorts()` und `findPort(id)` dienen zur Navigation.
`removeContent(...)` entfernt die zugehörige Zeile und ihre Ports; bestehende
Verbindungen müssen über den Editor konsistent entfernt werden.

Alle `GLContainer` besitzen einen semantischen Namen. Ist
`input.setContentHiddenWhenConnected(true)` aktiv, ersetzt der Node einen durch
eine Verbindung verdeckten Editor durch ein Label dieses Namens. Controls mit
Text liefern bereits sinnvolle Standardnamen; für rein grafische eigene Inhalte
sollte `setName(...)` gesetzt werden.

## Ports und Verbindungsregeln

```java
GLNodePort input = GLNodePort.input("roughness");
input.setMaximumConnections(1);
input.setColor(new Color(110, 170, 230));
input.setContentHiddenWhenConnected(true);
input.setConnectionValidator((own, other) -> isCompatible(own, other));
```

Inputs erlauben standardmäßig eine Verbindung, Outputs beliebig viele.
`setMaximumConnections(...)` passt das Limit an. `getConnectionCount()` und
`isContentHiddenByConnection()` liefern den aktuellen Editorzustand.

Eine Verbindung ist nur zulässig, wenn:

- genau ein Input und ein Output beteiligt sind,
- beide Ports zu Nodes desselben Editors gehören,
- die Verbindung nicht bereits existiert,
- beide Kapazitätsregeln erfüllt sind; ein Input mit Maximum `1` ersetzt beim
  Hinzufügen seine vorhandene Verbindung,
- die Validatoren beider Ports zustimmen.

`canConnectTo(...)` prüft die lokale Portregel; `editor.canConnect(...)` prüft
den vollständigen Graphkontext. `clearColor()` stellt die vom Node-Style
geerbte Input-/Outputfarbe wieder her.

## Verbindungen

`GLNodeConnection` verbindet immer `getOutput()` mit `getInput()`. Sie kann
ausgewählt werden und optional mit `setWidth(...)` eine eigene Breite besitzen;
`clearWidth()` stellt die Editor-Stylebreite wieder her. Die Linie erhält einen
Farbverlauf von der Output- zur Inputfarbe. Standardmäßig interpoliert das Theme
in sRGB; ein anderer `GLColorInterpolator` kann am Editor gesetzt werden.

Beim Ziehen zeigt Snapping einen kompatiblen Port in der Nähe an. Wird eine
bestehende Verbindung gelöst und außerhalb eines gültigen Ziels losgelassen,
wird sie gelöscht. Kontextmenü und Entf/Delete löschen die ausgewählte
Verbindung beziehungsweise Node-Auswahl.

```java
if (editor.canConnect(output, input)) {
    GLNodeConnection connection = editor.connect(output, input);
}
editor.removeConnection(connection);
```

`addConnection(...)` nimmt ein bereits konstruiertes Verbindungsobjekt auf.
`GLNodeEditorListener` meldet hinzugefügte/entfernte Verbindungen und
Auswahländerungen. Das Hinzufügen und Entfernen der Nodes selbst stößt die
Anwendung direkt über ihre eigenen Modelloperationen an.

## Auswahl und Interaktion

- Einfacher Klick selektiert genau dieses Element und löscht die übrige Auswahl.
- Shift fügt einen Node hinzu. Ein bereits normal selektierter wird aktiv; ein
  erneut mit Shift angeklickter aktiver Node wird entfernt.
- Ziehen auf leerem Hintergrund erzeugt eine Marquee-Auswahl.
- Es gibt maximal einen aktiven Node innerhalb der Mehrfachauswahl.
- Entf/Delete löscht Nodes oder die ausgewählte Verbindung.
- Mausrad zoomt um die Cursorposition.
- Strg/Ctrl+Mausrad pannt horizontal, Shift+Mausrad vertikal.
- Mittlere Maustaste pannt die Ansicht direkt.

`setSelectedNode(...)`, `setSelectedNodes(...)`,
`setNodeSelected(...)`, `setSelectedConnection(...)` und `clearSelection()`
stellen dieselben Zustände programmatisch her. `getSelectedNodes()` liefert die
gesamte Auswahl, `getActiveNode()` das aktive Element.

## Ansicht

```java
editor.setZoomRange(0.25f, 4f);
editor.setZoom(1f);
editor.zoomByAtScreenPosition(1.2f, mouseX, mouseY);
editor.setPan(0, 0);
editor.panBy(24, -12);
editor.setWheelPanDistance(36);
editor.resetView();
```

Zoom ist ein dimensionsloser Faktor. Pan und Wheel-Pan-Distanz verwenden
logische Bildschirm-Pixel. `zoomByAtScreenPosition(...)` hält den Weltpunkt
unter dem angegebenen Screenpunkt fest.

## Verbindungsgeometrie und Einheiten

Alle Werte kommen standardmäßig aus `GLNodeEditorStyle` und lassen sich lokal
mit `set...` überschreiben; die jeweilige `clear...`-Methode stellt das Theme
wieder her.

| Eigenschaft | Einheit | Wirkung |
|---|---|---|
| `connectionSnapDistance` | Bildschirm-Pixel | Maximaler Fangabstand zu einem kompatiblen Port. |
| `connectionCurveTolerance` | Bildschirm-Pixel-Abweichung | Tessellierungsfehler der Bezierkurve; kleiner erzeugt mehr Segmente. |
| `connectionHandleFactor` | dimensionslos | Horizontaler Kontrollpunktabstand relativ zur Portdistanz. |
| `minimumConnectionHandleLength` | logische Welt-Pixel | Untergrenze des Kontrollpunktabstands. |
| `connectionWidth` | Bildschirm-Pixel | Sichtbare Linienbreite unabhängig vom Zoom. |
| `connectionBorderWidth` | Bildschirm-Pixel | Zusätzliche Outlinebreite je Seite. |

Für vollständig gerade Verbindungen werden sowohl
`connectionHandleFactor=0` als auch `minimumConnectionHandleLength=0` gesetzt.
Nur den Faktor auf null zu setzen lässt die Mindestlänge weiterhin wirken.

```java
editor.setConnectionSnapDistance(24);
editor.setConnectionCurveTolerance(0.5f);
editor.setConnectionHandleFactor(0.55f);
editor.setMinimumConnectionHandleLength(50);
editor.setConnectionWidth(3);
editor.setConnectionBorder(border);
editor.setSelectedConnectionBorder(selectedBorder);
editor.setConnectionBorderWidth(1);
```

Die Borderfarben werden über `setConnectionBorder(...)` und
`setSelectedConnectionBorder(...)` gesetzt; die Breite über
`setConnectionBorderWidth(...)`. `setConnectionColorInterpolator(...)` wählt
die Farbinterpolation.

## Styles

`GLNodeStyle` steuert Node-Hintergrund, Titelverlauf, Text, normale/selektierte/
aktiv-selektierte Border, analytischen Schatten, Portfarben, Rundung, Abstände
und Resize-Griffe. Der Schatten wird direkt im spezialisierten UI-Rendering
berechnet und benötigt keinen separaten Layer pro Node; überlappende Nodes
werden deshalb weiterhin korrekt in Z-Reihenfolge komponiert.

`GLNodeEditorStyle` steuert Editorhintergrund, Verbindungslinien, Snapping,
Kurvenqualität sowie Marquee. Beide Styles können lokal gesetzt und mit
`clearGLStyle()` wieder aus dem Theme bezogen werden. Alle zugehörigen
Dateischlüssel sind im Standard-Theme unter `node.*` und `nodeEditor.*`
aufgeführt.

## History und Modellgrenze

Bei einem am Host gesetzten `GLSaveManager` werden Titelbearbeitung, Bewegung,
Resize, Verbindungen und Löschvorgänge als sinnvolle zusammengefasste Schritte
aufgezeichnet. Mehrfachbewegungen bilden eine Transaktion statt eines Eintrags
pro Node.

Das UI speichert jedoch nicht automatisch das fachliche Graphmodell. IDs,
Node-Typen, Parameterwerte und Serialisierung bleiben Aufgabe der Anwendung.
Empfohlen ist eine klare Zuordnung zwischen Modellobjekt und `GLNode`; Listener
übertragen strukturelle UI-Änderungen ins Modell, und Laden baut Editor und
Verbindungen aus diesem Modell neu auf.
