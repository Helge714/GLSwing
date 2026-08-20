# Recording und Laufzeitparameter

[Zur API-Landkarte](../graphics-pipeline.md) · [Zeichenbefehle](drawing.md) · [Weiter: Shader](shaders.md)

Ein `GLRecording` ist eine unveränderliche Folge normalisierter
Renderanweisungen. Es ist keine isolierte OpenGL-Displaylist und kein einzelner
Drawcall: Beim Replay werden die gespeicherten Anweisungen wieder in die gerade
offenen Rendererbatches eingespeist. Dadurch können mehrere Recordings und
Immediate-Befehle weiterhin gemeinsam gebatcht werden.

## Inhalt

- [Was Recording spart](#was-recording-spart)
- [Manuelle und automatische Aufnahme](#manuelle-aufnahme)
- [Replay und äußerer Zustand](#replay-und-äußerer-zustand)
- [Laufzeitparameter](#parameter-für-ein-komplettes-recording)
- [Gespeicherte und nicht recordbare Inhalte](#was-genau-gespeichert-wird)
- [Lebensdauer und Diagnose](#referenzen-und-lebensdauer)

## Was Recording spart

- Polygon- und Path-Tessellierung
- Stroke-, Dash-, Join- und Cap-Geometrie
- Textvertex-Erzeugung
- viele kleine öffentliche Context-Aufrufe
- wiederholtes Normalisieren kompatibler Befehle

Es spart nicht automatisch Fragmentarbeit, Layerpasses oder die Anzahl der
sichtbar notwendigen Shader-/Typwechsel.

## Manuelle Aufnahme

```java
GLRecording card;

g.beginRecording();
try {
    g.fillRoundRect(0, 0, 240, 110, 14, new Color(45, 80, 135));
    g.drawLine(20, 76, 220, 76, new Color(100, 220, 255));
    card = g.endRecording();
} catch (RuntimeException error) {
    if (g.isRecording())
        g.cancelRecording();
    throw error;
}

g.translate(300, 120);
g.draw(card);
```

`beginRecording` sichert den äußeren Renderer- und Contextzustand und startet
mit einem Identitäts-Modeltransform. `endRecording` oder `cancelRecording`
restauriert den äußeren Zustand. Ein Frame darf nicht mit offener Aufnahme
enden.

## Automatisches Recording in `GLContainer`

`GLContainer.record(g)` wird lazy aufgerufen, sobald sein Cache dirty ist.
`render(g)` spielt standardmäßig das Ergebnis. Kinder sind nicht Teil des
Parent-Recordings, sondern besitzen unabhängige Caches.

```java
final class Badge extends GLContainer {
    private Color background = Color.BLUE;

    void setBackground(Color value) {
        background = value;
        invalidateRecording();
    }

    @Override
    protected void record(GLContext2D g) {
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, background);
    }
}
```

`invalidateRecording()` ist richtig, wenn sich gespeicherte Geometrie, Text,
Ressource oder nichtparametrisierte Farbe ändert. Ein reines Verschieben über
Component-Bounds oder Parent-Transform benötigt keine neue Aufnahme.

## Replay und äußerer Zustand

```java
g.draw(recording);
g.draw(recording, parameters);
```

Ein Recording wird unterhalb des aktuellen Modeltransforms abgespielt. Sein
initialer Zustand und seine internen Zustandswechsel gelten nur während des
Replays; danach wird der äußere Rendererzustand restauriert. Eine Aufnahme kann
nur auf dem Renderer abgespielt werden, der sie erzeugt hat.

Playback während einer anderen aktiven Aufnahme ist derzeit nicht unterstützt.

## Parameter für ein komplettes Recording

```java
GLRenderParameters state = new GLRenderParameters()
        .setTranslation(300, 120)
        .setTint(new Color(210, 235, 255))
        .setOpacity(0.8f);

g.draw(card, state);

state.setTransform(new GLTransform()
        .translate(420, 180)
        .rotate(0.1f)
        .scale(1.15f));
```

| Parameter | Bedeutung |
|---|---|
| `setTransform`, `resetTransform` | Vollständiger Replay-Transform; Matrixeinheiten wie im [Konfigurationskapitel](context-configuration.md#gltransform-referenz) |
| `setTranslation` | Transform durch Translation in lokalen Benutzerkoordinaten ersetzen |
| `translate`, `rotate`, `scale` | Benutzerkoordinaten, Radiant beziehungsweise dimensionslose Faktoren |
| `setTint`, `resetTint` | Aufgezeichnete RGBA-Farben multiplizieren |
| `setColor`, `clearColor` | Aufgezeichnete RGB-/Vertexfarben ersetzen/zurücksetzen |
| `setOpacity` | Vertexalpha mit einem dimensionslosen Faktor `0..1` multiplizieren |
| `reset` | Alle Werte auf Identität/Weiß/Original/1 setzen |

`getTransform` liefert eine Kopie. Setter validieren Null, Endlichkeit und den
Opacitybereich `0..1`. Weitere Abfragen sind `getTint`, `hasColor`, `getColor`
und `getOpacity`.

Parameter-Opacity ist keine Gruppen-Opacity. Überlappen sich zwei Formen im
Recording, wird Alpha pro Form angewandt und die Überlappung bleibt sichtbar.
Für einmaliges Alpha auf das fertige Ergebnis dient ein Layer.

## Parameterregionen

Eine Aufnahme kann Referenzen auf mehrere mutable Parametersätze enthalten:

```java
GLRenderParameters surface = new GLRenderParameters();
GLRenderParameters accent = new GLRenderParameters();

g.beginRecording();
g.withParameters(surface, () ->
        g.fillRect(0, 0, 260, 150, Color.WHITE));
g.withParameters(accent, () -> {
    g.fillCircle(35, 35, 12, Color.WHITE);
    g.drawLine(60, 35, 220, 35, Color.WHITE);
});
GLRecording panel = g.endRecording();

surface.setColor(new Color(48, 89, 150));
accent.setColor(new Color(95, 220, 255));
g.draw(panel);
```

`beginParameters`/`endParameters` dürfen verschachtelt werden, aber nur während
einer Aufnahme. `withParameters` schließt die Region auch bei einer Exception.
Änderungen am Parameterobjekt wirken beim nächsten Replay ohne Neuaufnahme.

## Was genau gespeichert wird

| Inhalt | Recordingverhalten |
|---|---|
| Filled-/Texture-/Textgeometrie | Kompatible Nachbarn werden beim Aufnehmen zusammengefasst |
| UI-Instanzen | Als UI-Replaybefehl gespeichert |
| Modeltransform | Interne Transformoperationen werden gespeichert; Aufnahme startet bei Identität |
| Projection/View | Als Replayzustand gespeichert |
| Shader/Uniforms | Als Replayzustand gespeichert |
| Clips/Layer | Verschachtelung wird gespeichert und beim Abschluss validiert |
| Color/Stroke/Picking | Bei Geometrieerzeugung eingebacken beziehungsweise zugeordnet |
| `GLIcon` | Icon bleibt high-level; passende adaptive Variante wird beim Replay gewählt |
| Screen-Space-Stroke | View-abhängiger Compiler mit Transform-/View-Cache |
| MouseTarget | Schwache Referenz, verhindert Garbage Collection nicht |

## Nicht recordbar

- `beginRenderTarget`, `endRenderTarget`, `withRenderTarget`
- `drawRenderTarget`
- `draw(GLRecording)` während einer aktiven Aufnahme

RenderTargets sind dynamische, frameabhängige Ressourcen. Sie werden im
Immediate-Renderpfad aktualisiert und dargestellt.

## Typische Entscheidungen

| Situation | Vorgehen |
|---|---|
| Form ändert sich selten | Recording neu bauen, wenn dirty |
| Nur Position/Rotation/Skalierung ändert sich | Replaytransform/Componenttransform |
| Nur Farbe oder Opacity ändert sich | `GLRenderParameters` |
| Einzelne Bereiche ändern sich unabhängig | Parameterregionen oder getrennte Components |
| Shaderuniform ändert sich pro Frame | Immediate rendern oder passenden Parameteransatz entwerfen; Uniformwechsel bleibt Flushgrenze |
| Inhalt ist ein aktueller FBO | Nicht aufnehmen; `drawRenderTarget` immediate |

## Referenzen und Lebensdauer

Ein Recording besitzt Fonts, Texturen, Bilder oder Shader nicht. Diese müssen
mindestens so lange gültig bleiben wie das Replay. `GLMouseTarget` ist eine
Ausnahme: Es wird per `WeakReference` gehalten. Ist das Ziel sonst nicht mehr
erreichbar, liefert die Referenz `null` und blockiert den Garbage Collector
nicht.

## Diagnose

- `getCommandCount()` zählt koaleszierte Replaybefehle, nicht ursprüngliche
  Contextaufrufe und nicht zwingend Drawcalls.
- `isEmpty()` erkennt leere Aufnahmen.
- Unbalancierte Layer, Clips oder Parameterregionen werden bei `endRecording`
  abgewiesen.
- Screen-Space-Strokes dürfen bei Viewänderung etwas CPU-Zeit zur
  Neukompilierung benötigen; danach greift wieder der Cache.
