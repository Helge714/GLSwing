# RenderTargets und GPU-Feedback

[Zur API-Landkarte](../graphics-pipeline.md) · [Layer und Effekte](layers-effects.md) · [Weiter: Ressourcen](resources.md)

Ein `GLRenderTarget` ist ein persistenter, lazy erzeugter RGBA8-Framebuffer mit
eigener Größe. Anders als ein Layer bleibt sein Inhalt über Frames erhalten und
wird von der Anwendung ausdrücklich aktualisiert.

## Inhalt

- [Layer oder RenderTarget?](#layer-oder-rendertarget)
- [Erzeugen, Optionen und Einheiten](#erzeugen-und-optionen)
- [In ein Target zeichnen](#zeichnen-in-ein-target)
- [Target darstellen](#target-darstellen)
- [Ping-Pong mit GLFeedbackBuffer](#ping-pong-mit-glfeedbackbuffer)
- [Shader, Lebensdauer und Performance](#shader-für-simulationen)

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
GLRenderTarget interactive = new GLRenderTarget(
        512, 512,
        GLRenderTargetOptions.DEFAULT.withPicking(true));
```

`GLRenderTargetOptions` kombiniert:

- Filter `LINEAR` oder `NEAREST`,
- Wrap `CLAMP` oder `REPEAT`,
- optionales persistentes Picking mit `withPicking(true)`.

`DEFAULT` ist linear/clamp. `pixelArt()` ist nearest/clamp,
`repeatingPixelArt()` nearest/repeat. `withFilter` und `withWrap` erzeugen
veränderte immutable Optionen. Picking ist standardmäßig aus, sodass ein
gewöhnliches Target weder die zusätzliche Integertextur noch deren FBO
allokiert. `withPicking(true)` ergänzt beides in Targetauflösung. Die
Pickingtextur verwendet immer `NEAREST`, unabhängig vom Farbfilter.

Konstruktorbreite und -höhe sind ganzzahlige Targetpixel und damit zugleich
die Texelauflösung. Innerhalb des Targets entspricht eine logische
Top-Left-Koordinate genau einem Targetpixel. Beim späteren Darstellen sind
X/Y/Breite/Höhe wieder lokale Benutzerkoordinaten der aktuellen Szene.

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
- Picking entsprechend der Targetoption,
- eigener Viewport und FBO.

In einem Target mit `withPicking(true)` funktionieren `setPickId`,
`setMouseTarget`, ihre Zeichenoverloads, `addUIHitArea` und
`setOutputMask(color,picking)` wie im Hauptbild. Ein Target ohne diese Option
weist das Aktivieren der Picking-Ausgabe innerhalb des Targetpasses ab.

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

Hat das Target Picking aktiviert, wird seine lokale ID-Textur mit genau
demselben Quad wie die Farbtextur dargestellt. Skalierung, Spiegelung,
Rotation, View und Projection wirken dadurch automatisch identisch auf Farbe
und Treffer. Auch verschachtelte Picking-Targets bleiben korrekt. Die beim
Zeichnen in das Target erfassten lokalen Koordinaten werden beim Treffer durch
alle Präsentationstransformationen zurückgerechnet.

Eine Pick-ID oder ein MouseTarget am `drawRenderTarget`-Aufruf ist in diesem
Fall ein **Fallback für sichtbare Pixel ohne interne ID**. Interne Treffer haben
Vorrang. Unsichtbare interne `addUIHitArea`-Flächen bleiben pickbar; der
Alpha-Schwellwert gilt nur für den sichtbaren Fallback. Ohne Pickingoption
verhält sich der Overload weiterhin wie ein einziges pickbares Textur-Quad.

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
Bei einem Picking-Target werden mit der standardmäßigen Output-Maske Farb- und
ID-Inhalt gemeinsam geleert und die lokalen Zuordnungen zurückgesetzt. Über
`setOutputMask` kann auch nur eine der beiden Flächen geleert werden. Ohne Clear
der Picking-Ausgabe bleiben deren Daten für partielle Updates erhalten.
Häufiges vollständiges Neuzeichnen ohne Picking-Clear würde dagegen unnötig
alte Zuordnungen ansammeln.

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

`uTexelSize` im Beispiel ist eine dimensionslose UV-Schrittweite pro Texel,
also `(1 / targetWidth, 1 / targetHeight)`.

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
- `withPicking(true)` benötigt zusätzlich ungefähr vier Bytes pro Targetpixel
  für `R32UI` und beim Darstellen einen Picking-Drawcall. Die Zuordnung zu
  `GLMouseTarget` verwendet schwache Referenzen und hält entfernte UI-Objekte
  daher nicht künstlich am Leben.
