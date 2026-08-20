# RenderTargets und GPU-Feedback

[Zur API-Landkarte](../graphics-pipeline.md) · [Layer und Effekte](layers-effects.md) · [Weiter: Ressourcen](resources.md)

Ein `GLRenderTarget` ist ein persistenter, lazy erzeugter RGBA8-Framebuffer mit
eigener Größe. Anders als ein Layer bleibt sein Inhalt über Frames erhalten und
wird von der Anwendung ausdrücklich aktualisiert.

## Layer oder RenderTarget?

| Layer | RenderTarget |
|---|---|
| Temporär, nur bis `endLayer` | Persistent über Frames |
| Für Gruppenopacity und Effekte | Für Offscreen-Canvas, Caches und Simulationen |
| Bounds aus aktueller Szene | Feste Pixelauflösung |
| Darf Teil eines Recordings sein | Updates und Darstellung nicht recordbar |
| Automatisch vom Renderer gepoolt | Anwendung besitzt das Java-Objekt |

## Erzeugen und Optionen

```java
GLRenderTarget target = new GLRenderTarget(512, 512);
GLRenderTarget pixels = new GLRenderTarget(
        256, 256, GLRenderTargetOptions.pixelArt());
```

`GLRenderTargetOptions` kombiniert:

- Filter `LINEAR` oder `NEAREST`,
- Wrap `CLAMP` oder `REPEAT`.

`DEFAULT` ist linear/clamp. `pixelArt()` ist nearest/clamp,
`repeatingPixelArt()` nearest/repeat. `withFilter` und `withWrap` erzeugen
veränderte immutable Optionen.

Die GPU-Ressource entsteht erst beim ersten Einsatz. Ein Target bindet sich
dabei an genau einen Renderer und kann nicht zwischen unabhängigen Fenstern
geteilt werden.

## Zeichnen in ein Target

```java
g.withRenderTarget(target, () -> {
    g.clear(Color.TRANSPARENT);
    g.fillCircle(256, 256, 80, Color.WHITE);
});
```

Die manuelle Form ist:

```java
g.beginRenderTarget(target);
try {
    drawTargetContent(g);
} finally {
    g.endRenderTarget();
}
```

Innerhalb gelten:

- logische Größe gleich Targetbreite/-höhe,
- Top-Left-Pixelkoordinaten,
- Projection passend zum Target,
- Picking deaktiviert,
- eigener Viewport und FBO.

`endRenderTarget` restauriert Output, Viewport, Transform, Projection, Shader,
Uniforms, Color, Stroke, Picking und weitere Contextzustände. Targets dürfen
verschachtelt werden; gleichzeitiges Lesen und Beschreiben desselben Targets
wird abgewiesen.

## Target darstellen

```java
g.drawRenderTarget(target, 20, 20); // Originalgröße
g.drawRenderTarget(target, 20, 20, 400, 400);
g.drawRenderTarget(target, 20, 20, 400, 400,
        new Color(100, 220, 170));
```

Weitere Overloads nehmen Pick-ID oder MouseTarget. Die FBO-Y-Ausrichtung wird
intern korrigiert. `drawRenderTarget` verwendet den Texture-Batch und dessen
aktuellen Shader.

Wichtig: `drawRenderTarget` und sämtliche Target-Updates sind nicht recordbar,
weil der gezeigte Inhalt dynamisch ist.

## Inhalt erhalten oder löschen

Ohne `clear` bleibt der vorherige Inhalt erhalten. Damit eignet sich ein Target
für schrittweises Malen:

```java
g.withRenderTarget(target, () ->
        g.fillCircle(mouseX, mouseY, 4, Color.WHITE));
```

`clear` löscht das aktuell gebundene Target, nicht automatisch den Bildschirm.

## Ping-Pong mit `GLFeedbackBuffer`

OpenGL darf nicht sicher dieselbe Textur gleichzeitig samplen und als
Farbausgabe beschreiben. `GLFeedbackBuffer` besitzt daher `current()` und
`next()`:

```java
GLFeedbackBuffer feedback = new GLFeedbackBuffer(
        512, 512, GLRenderTargetOptions.repeatingPixelArt());

feedback.update(g, () -> {
    g.setTextureShader(simulationShader);
    g.setTextureUniform("uTexelSize", 1f / 512, 1f / 512);
    g.drawRenderTarget(feedback.current(), 0, 0, 512, 512);
});

feedback.drawCurrent(g, 20, 20, 700, 700);
```

`update` schreibt in `next`, während `current` lesbar bleibt. Nur nach einem
erfolgreichen Callback werden beide getauscht. Bei einer Exception endet der
Targetpass ohne Swap.

Manuelle API:

| Methode | Bedeutung |
|---|---|
| `beginUpdate(context)` | `next` als Ziel binden |
| `endUpdate(context)` | Beenden und tauschen |
| `cancelUpdate(context)` | Beenden, nicht tauschen |
| `update(context,Runnable)` | Exception-sichere Komplettform |
| `clear(context,color)` | Beide Targets löschen |
| `drawCurrent(context,x,y,w,h)` | Aktuellen Zustand darstellen |
| `current`, `next` | Targets zugänglich machen |
| `getWidth`, `getHeight` | Auflösung |
| `close` | Beide Targets schließen |

Begin und End müssen denselben Context verwenden. Während eines Updates darf
der Feedbackbuffer weder erneut aktualisiert, geleert noch geschlossen werden.

## Shader für Simulationen

Ein Simulationsshader ist ein gewöhnlicher Texture-Shader. Der Vertexshader
muss das Texturelayout erfüllen; der Fragmentshader liest Nachbartexel und gibt
den nächsten Zustand aus. Es erfolgt kein GPU-zu-CPU-Readback. Die
`GameOfLifeDemo` zeigt eine vollständige Implementierung.

Für Kantenbedingungen ist `REPEAT` praktisch; feste Ränder verwenden `CLAMP`.
Zellraster sollten `NEAREST` wählen, kontinuierliche Felder oft `LINEAR`.

## Lebensdauer

```java
target.close();
feedback.close();
```

`close` markiert Targets als geschlossen. Die GL-Objekte gibt der Eigentümer-
Renderer sicher frei. Geschlossene Targets dürfen nicht wieder verwendet
werden; `isClosed()` fragt diesen Zustand ab. Ein Feedbackbuffer darf nicht mitten in einem Update geschlossen
werden.

## Performance

- Targetauflösung bestimmt die Fragmentarbeit, unabhängig von der späteren
  Bildschirmgröße.
- `NEAREST` ist nicht automatisch deutlich schneller; die Zahl bearbeiteter
  Pixel und Shaderkosten dominieren.
- Vermeide unnötiges Full-Target-Clear und unnötige Updates.
- Ping-Pong hält die Pipeline auf der GPU; ein synchroner Readback würde sie
  typischerweise blockieren.
- Jeder Wechsel in/aus einem Target flusht offene Batches.
