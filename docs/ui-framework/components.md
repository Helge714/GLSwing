# Komponenten und Hosts

[UI-Übersicht](../ui-framework.md) · [Layouts](layout.md) ·
[Controls](controls.md) · [History](history.md)

## Inhalt

- [Das Komponentenmodell](#das-komponentenmodell)
- [Bounds, Sichtbarkeit und Namen](#bounds-sichtbarkeit-und-namen)
- [Kinder und Z-Order](#kinder-und-z-order)
- [Culling, Damage und visueller Überstand](#culling-damage-und-visueller-überstand)
- [Theme, Sprache, Fokus und History](#theme-sprache-fokus-und-history)
- [Hosts](#hosts)

## Das Komponentenmodell

`GLParent` ist die gemeinsame Parent-Schnittstelle von Hosts und
`GLContainer`. Sie bündelt Kinderverwaltung, Layout, Theme, Sprache, Fokus,
History und Repaint-Anforderungen. `GLContainer` ist die Basisklasse für alle
retained-mode UI-Elemente. Eine zusätzliche Klasse namens `GLComponent` gibt es
bewusst nicht: Auch ein Blatt-Control kann Kinder aufnehmen und dieselbe
leichte Container-Infrastruktur nutzen.

Ein eigener Container implementiert mindestens `record(...)`:

```java
public final class StatusLamp extends GLContainer {
    private Color color = Color.GREEN;

    public StatusLamp() {
        setPreferredGLSize(new GLSize(18, 18));
    }

    @Override
    protected void record(GLContext2D g) {
        g.setColor(color);
        g.fillCircle(getWidth() / 2f, getHeight() / 2f,
                Math.min(getWidth(), getHeight()) / 2f);
    }

    public void setColor(Color color) {
        this.color = Objects.requireNonNull(color);
        invalidateRecording();
        repaintGL();
    }
}
```

### Render-Lifecycle

| Hook/Methode | Bedeutung |
|---|---|
| `record(GLContext2D)` | Baut die cachebare Darstellung in lokalen Koordinaten auf. |
| `render(GLContext2D)` | Spielt standardmäßig das Recording ab; kann für dynamische Präsentation überschrieben werden. |
| `renderChildren(GLContext2D)` | Rendert die Kinder in Paint-Reihenfolge. |
| `transformComponent(GLContext2D)` | Erweiterungspunkt für eine lokale Transformation. |
| `invalidateRecording()` | Markiert nur die eigene Aufnahme als veraltet. |
| `resized(float width,float height)` | Wird nach einer Größenänderung aufgerufen. |
| `attached(GLParent)` / `detached(GLParent)` | Lebenszyklus beim Einhängen beziehungsweise Entfernen. |
| `themeChanged()` / `localizerChanged()` | Reaktion auf geerbte Darstellungs- oder Sprachänderungen. |

`renderComponent(...)` koordiniert intern Transformation, Picking, Layout,
Culling, Damage, Recording und Kinder. Die Methode wird nicht manuell ersetzt.
Eigene State-Änderungen müssen die passende Kombination aus
`invalidateRecording()`, `invalidateGLLayout()` und `repaintGL()` auslösen.

Der Parent und jedes Kind besitzen getrennte Recordings. Ändert sich ein Button,
muss deshalb nicht der komplette übergeordnete Dialog neu aufgenommen werden.
Beim Replay kann der Renderer kompatible Befehle trotzdem batchen.

## Bounds, Sichtbarkeit und Namen

| API | Wirkung |
|---|---|
| `setBounds(x,y,w,h)` | Position relativ zum Parent und Größe in logischen Pixeln. |
| `setLocation(x,y)` / `setSize(w,h)` | Ändert nur Position beziehungsweise Größe. |
| `getX/Y/Width/Height()` | Aktuelle lokale Bounds. |
| `setVisible(boolean)` | Nimmt die Komponente aus Rendering, Layout und Eingabe. |
| `setName(String)` / `clearName()` | Semantischer Name für UI-Inhalte, Fallbacks und Werkzeuge. |
| `getLastRenderedScreenBounds()` | Zuletzt ermittelte Bildschirm-Bounds, etwa zur Popup-Platzierung. |

Controls setzen häufig einen sinnvollen Standardnamen aus ihrem Text. Ein
explizites `setName(...)` bleibt davon unabhängig und lässt sich mit
`hasExplicitName()` unterscheiden.

## Kinder und Z-Order

```java
parent.addGL(child);
parent.addGL(child, GLConstraints.at(0, 0).fillBoth());
parent.removeGL(child);
child.removeFromGLParent();
```

`getGLChildren()` liefert die stabile Layout-/Einfügereihenfolge.
`getGLChildrenInPaintOrder()` liefert die Reihenfolge von hinten nach vorn.
`bringToFront()`, `sendToBack()`, `moveForward()` und `moveBackward()` ändern
nur die Z-Order, nicht die Layoutreihenfolge.

Z-Indizes sind Positionen im Bereich `0 .. childCount - 1`. Beim Verschieben
werden sie normalisiert; es existiert kein ständig wachsender globaler Index,
der irgendwann überlaufen könnte.

## Culling, Damage und visueller Überstand

Der Host merkt sich beschädigte Bereiche und kann unveränderte Teile
wiederverwenden. Komponenten außerhalb des sichtbaren Bereichs werden nicht
unnötig gerendert. Zeichnet eine Komponente außerhalb ihrer Bounds, muss sie
diesen Bereich deklarieren:

```java
card.setVisualOverflow(new GLInsets(12, 12, 16, 12));
// oder aus Alpha, Schatten, Outline usw. ableiten:
card.setVisualOverflow(composite);
```

Das ist bei Schatten, weichen Outlines und ähnlichen Layer-Effekten wichtig.
Andernfalls kann Damage-Tracking den Überstand beim Bewegen oder Überdecken zu
knapp invalidieren.

`repaintGL()` fordert eine Darstellung an. `repaintGL(source)` ermöglicht dem
Host eine präzisere Damage-Berechnung. Wenn sich auch die pickbare Geometrie
ändert, dient `repaintAndRebuildPicking(source)` zur gleichzeitigen
Invalidierung des Picking-Snapshots.

## Theme, Sprache, Fokus und History

Theme und Localizer werden vom nächsten Parent geerbt. Ein Unterbaum kann sie
lokal überschreiben und später wieder auf die Vererbung zurückschalten:

```java
panel.setGLTheme(GLThemes.LIGHT);
panel.clearGLTheme();

panel.setGLLocalizer(GLLocalizer.GERMAN);
panel.clearGLLocalizer();
```

`getGLFont(GLFontSpec)` greift auf den Font-Cache des Hosts zu.
`getGLSaveManager()` liefert die gemeinsame History. Fokusfähige Controls
fordern über `requestGLFocus(...)` Tastatureingabe an; ein Parent kann mit
`clearGLFocus(subtree)` den Fokus innerhalb eines entfernten Unterbaums lösen.

## Hosts

### `GLFrame`

`GLFrame` ist der empfohlene Standalone-Host. Er kombiniert `JFrame`, direkten
`GLSwingContainerCanvas`, `FPSAnimator` und geordneten Shutdown.

```java
GLFrame.RenderingSettings settings = GLFrame.RenderingSettings.DEFAULT
        .withAntialiasingSamples(4)
        .withVSync(true)
        .withTargetFps(144);

GLFrame frame = new GLFrame("Editor", settings);
frame.setGLClearColor(new Color(22, 25, 31));
frame.setRenderResolutionScale(1f);
frame.addGL(root);
frame.setSize(1280, 800);
frame.setVisible(true);
```

Die Antialiasing-Samples müssen vor Erzeugung des nativen GL-Kontexts bekannt
sein. Ziel-FPS und Renderauflösung lassen sich später ändern. Messwerte liefern
unter anderem `getFramesPerSecond()`, `getAverageFrameTimeMillis()`,
`getLastDrawCallCount()` und `getFrameProfile()`.

Geerbte Swing-Inhaltsmethoden (`add(Component)`, `setContentPane(...)`,
`setJMenuBar(...)` usw.) sind absichtlich deprecated. Für die verwaltete
GL-Hierarchie werden `addGL(...)` und `removeGL(...)` verwendet.

### `GLSwingContainerCanvas`

Der direkte, heavyweight AWT-Canvas ist die performante Wahl für eine vollständig
GPU-gerenderte Oberfläche. Eine Unterklasse implementiert
`renderContainer(GLContext2D)` für zusätzliche unmittelbare Root-Zeichnung.
Fenster, Animator und Lebensdauer verwaltet die Anwendung selbst, wenn sie nicht
`GLFrame` benutzt.

### `GLSwingPanel`

`GLSwingPanel` basiert auf `GLJPanel` und lässt sich in normale Swing-Layouts
einbetten. Das ist sinnvoll, wenn GLSwing neben klassischen Swing-Komponenten
stehen soll. Die zusätzliche Swing-Compositing-Stufe kann die maximal erreichbare
Framerate gegenüber dem direkten Canvas begrenzen.

### `GLDialog`

`GLDialog` ist ein frei bewegliches, nativ an den Owner gekoppeltes Fenster mit
eigener GL-Oberfläche. Eine Instanz ist single-use und öffnet asynchron:

```java
GLDialog dialog = new GLDialog("Einstellungen", content);
dialog.setSize(520, 360);
dialog.setResizable(true);
dialog.setModality(GLDialog.Modality.OWNER_MODAL);
dialog.show(frame);
dialog.closedFuture().thenRun(() -> savePreferences());
```

`MODELESS` lässt den Owner bedienbar, `OWNER_MODAL` blockiert dessen Eingabe.
`toFront()` aktiviert den Dialog zusammen mit seiner Owner-Kette; `close()` ist
idempotent.
