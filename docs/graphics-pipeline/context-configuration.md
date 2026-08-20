# Context konfigurieren

[Zur API-Landkarte](../graphics-pipeline.md) · [Weiter: Zeichenbefehle](drawing.md)

`GLContext2D` wird zu Beginn jedes Frames auf definierte Defaults gesetzt. Ein
`GLContainer` sichert beim Zeichnen zusätzlich Transform, Farbe, Stroke und
Pickingzustand und stellt sie danach wieder her. Innerhalb eines eigenen
Renderhooks sollten temporäre Änderungen trotzdem möglichst lokal und bewusst
bleiben.

## Inhalt

- [Einheiten](#einheiten)
- [Zeichenstil](#zeichenstil)
- [Koordinatensysteme](#koordinatensysteme)
- [Frameausgaben und Clipping](#frameausgaben-und-clipping)
- [Shaderzustände](#shaderzustände)
- [Getter und Kopien](#getter-und-kopien)

## Einheiten

| Wertart | Einheit |
|---|---|
| Lokale X/Y-Koordinaten, Breiten und Höhen | Aktuelle Benutzerkoordinaten; mit `TOP_LEFT_PIXELS` logische Pixel |
| Screen-Werte | Logische Pixel, unabhängig von der physischen HiDPI-Auflösung |
| Winkel | Radiant; im Top-Left-System wirkt ein positiver Winkel optisch im Uhrzeigersinn |
| Transform-Skalierung, Miter-Limit, Alpha und Schwellwerte | Dimensionslos |
| Farben | `java.awt.Color`; Integer-Kanäle `0..255` |

„Logischer Pixel“ meint die Swing-/Benutzergröße. Der Renderer rechnet diese
bei Windows-Skalierung mit dem Device-Scale in physische Framebufferpixel um.

## Zeichenstil

### Farbe

```java
g.setColor(new Color(70, 140, 230));
g.fillRect(20, 20, 120, 50);  // verwendet Contextfarbe
Color current = g.getColor();
```

Die Contextfarbe gilt für Overloads ohne eigenes `Color`-Argument. Ein
explizites `Color` gilt nur für den jeweiligen Befehl. Die Alpha-Komponente ist
wie bei `java.awt.Color` ein Kanalwert von `0..255` und wird intern auf `0..1`
normalisiert.

### Stroke

`GLStroke` ist unveränderlich. Der Context bietet sowohl `setStroke` als auch
Komfortsetter, die intern eine veränderte Kopie erzeugen.

```java
GLStroke stroke = new GLStroke(3)
        .withCap(GLStroke.Cap.ROUND)
        .withJoin(GLStroke.Join.ROUND)
        .withMiterLimit(8)
        .withOffset(0)
        .withCurveTolerance(GLStroke.CURVE_TOLERANCE_FINE)
        .withWidthSpace(GLStroke.WidthSpace.SCREEN)
        .withDash(new float[] { 10, 6, 2, 6 }, 3);
g.setStroke(stroke);
```

| Eigenschaft | Einheit | Wirkung |
|---|---|---|
| Width | `WORLD`: lokale Benutzerkoordinaten; `SCREEN`: logische Pixel | Gesamtdicke, muss positiv sein |
| Cap | – | `BUTT` endet am Punkt, `SQUARE` verlängert um eine halbe Strichbreite, `ROUND` halbkreisförmig |
| Join | – | `MITER` spitz, `BEVEL` abgeschnitten, `ROUND` gerundet |
| Miter limit | Dimensionsloses Verhältnis, mindestens `1` | Maximal zulässige Miter-Länge relativ zum jeweiligen äußeren Stroke-Radius; darüber wird sicher abgeschnitten |
| Offset | Derselbe Raum wie Width | Verschiebt die Stroke-Mitte entlang der Pfadnormalen; `0` ist mittig |
| Curve tolerance | Logische Screenpixel | Maximale sichtbare Abweichung runder Stroke-Geometrie; kleiner erzeugt mehr Segmente |
| Width space | – | `WORLD` zoomt mit, `SCREEN` bleibt kosmetisch konstant |
| Dash array | Derselbe Raum wie Width | Abwechselnd sichtbare und leere positive Längen |
| Dash phase | Derselbe Raum wie Width | Bereits verbrauchte Musterlänge am Pfadanfang |

Offset, Dash-Muster und Dash-Phase folgen also gemeinsam dem `WidthSpace`: Bei
`WORLD` skalieren sie mit Model/View, bei `SCREEN` bleiben sie in logischen
Bildschirmpixeln konstant. Das Miter-Limit ist dagegen immer nur ein Verhältnis
und hat keine Längeneinheit.

Bei einem ungeraden Dash-Array wird das Muster wiederholt wie bei
`BasicStroke`. `getStrokeDash()` liefert eine defensive Kopie oder `null`.
`setStrokeDashPhase` ist nur bei aktivem Muster erlaubt.

Die immutable `GLStroke`-API spiegelt alle Werte mit `getWidth`, `getCap`,
`getJoin`, `getMiterLimit`, `getOffset`, `getCurveTolerance`, `getWidthSpace`,
`getDashArray`, `getDashPhase` und `isDashed`. Fluent Kopien entstehen über
`withWidth`, `withCap`, `withJoin`, `withMiterLimit`, `withOffset`,
`withCurveTolerance`, `withWidthSpace`, `withDash`, `withDashPhase` und
`withoutDash`. `DEFAULT` ist ein solider World-Space-Stroke mit Breite `1`.

Strokezustand wird nicht als eigener Replay-Befehl gespeichert: Er wird beim
Recording in die erzeugte Geometrie gebacken. Screen-Space-Geometrie speichert
zusätzlich einen Compiler und wird beim Replay gecacht neu berechnet, sobald
Transform oder View sich ändert.

### Kurventoleranzen

`setCurveTolerance(float)` steuert gefüllte runde Formen, Beziers und Paths.
`setStrokeCurveTolerance(float)` gehört dagegen zum Stroke. Beide Werte sind
maximale Abweichungen in logischen Screenpixeln: kleiner bedeutet glatter und
mehr Geometrie. Voreinstellungen des Strokes:

- `CURVE_TOLERANCE_COARSE = 0.4f` logische Pixel
- `CURVE_TOLERANCE_NORMAL = 0.2f` logische Pixel
- `CURVE_TOLERANCE_FINE = 0.1f` logische Pixel

## Koordinatensysteme

### Model-Transform

```java
GLTransform saved = g.getTransform(); // unabhängige Kopie
try {
    g.translate(200, 120);
    g.rotate((float) Math.toRadians(15));
    g.scale(1.5f, 0.8f);
    g.fillRect(-50, -25, 100, 50, Color.ORANGE);
} finally {
    g.setTransform(saved);
}
```

`translate` und Ankerkoordinaten verwenden lokale Benutzerkoordinaten,
`rotate` Radiant und `scale` dimensionslose Faktoren. Positive Winkel wirken
im üblichen Top-Left-Koordinatensystem optisch im Uhrzeigersinn.
`rotate(radians, anchorX, anchorY)` rotiert um einen lokalen Anker.
`resetTransform()` setzt Identität.

Model-Transforms werden auf Geometrie angewandt und müssen einen kompatiblen
Batch nicht beenden. Während `beginRecording()` beginnt der Modelraum bewusst
bei Identität; der vorherige Transform wird nach Ende/Abbruch restauriert.

### GLTransform-Referenz

`GLTransform` ist eine mutable affine 2D-Matrix. Konstruktoren: Identität,
Kopiekonstruktor und sechs affine Werte. `m00`, `m10`, `m01` und `m11` sind
dimensionslose lineare Faktoren; `m02` und `m12` sind Translationen in der
jeweils transformierten Koordinateneinheit. Die fluent mutierenden Methoden
geben dieselbe Instanz zurück.

| Methoden | Zweck |
|---|---|
| `setToIdentity` | Identität setzen |
| `setTransform(GLTransform)` / `(m00,m10,m01,m11,m02,m12)` | Matrix ersetzen |
| `translate`, `rotate`, `scale` | Operation anhängen |
| `concatenate` | Weitere affine Matrix anhängen |
| `transform(float[],offset,count)` | X/Y-Punktpaare in-place transformieren; `offset` und `count` sind Arrayindex beziehungsweise Punktanzahl |
| `transformVertices(float[],stride)` | Vertexarray mit X/Y am Stride-Anfang transformieren; `stride` ist die Float-Anzahl pro Vertex |
| `transformX`, `transformY` | Einzelne Koordinate aus X/Y berechnen |
| `isIdentity`, `isTranslation` | Spezialform testen |
| `getScaleX`, `getScaleY`, `getShearX`, `getShearY` | Linearen Anteil lesen |
| `getTranslateX`, `getTranslateY` | Translation lesen |
| `copy` | Unabhängige Kopie |
| `determinant` | Dimensionslose Determinante |
| `getMaximumScale` | Größten wirksamen dimensionslosen Skalierungsfaktor abschätzen |
| `createInverse` | Inverse Matrix; Fehler bei nicht invertierbarer Matrix |
| `toMatrix4` | Spaltenmajor-`float[16]` für OpenGL |

`equals`, `hashCode` und `toString` vergleichen beziehungsweise beschreiben die
sechs affinen Werte.

### Projection und View

`setProjection` ändert die World-to-Clip-Matrix und flusht, weil diese Matrix
eine Drawcall-Uniform ist.

| Projection | Koordinatenraum |
|---|---|
| `Projection2D.TOP_LEFT_PIXELS` | `(0,0)` links oben, logische Pixel |
| `Projection2D.CENTERED_PIXELS` | Ursprung in Fenstermitte, logische Pixel |
| `Projection2D.CENTERED_NORMALIZED` | Y `-1..1`, X mit Seitenverhältnis; dimensionslos |
| `Projection2D.CLIP_SPACE` | X/Y `-1..1`, dimensionslos, Seitenverhältnis nicht korrigiert |
| `Projection2D.fixed(transform)` | Unveränderliche eigene Matrix; Einheit durch diese Matrix definiert |

```java
g.setProjection(Projection2D.CENTERED_PIXELS);
```

`GLView2D` kombiniert die größenabhängige Berechnung in
`createViewProjection(width,height)`; `width` und `height` sind logische
Viewportpixel. `GLCamera2D` ist die fertige bewegliche View:

```java
GLCamera2D camera = new GLCamera2D(Projection2D.CENTERED_PIXELS);
camera.setPosition(120, 40);                       // Projektionskoordinaten
camera.setZoom(1.75f);                             // Faktor
camera.setRotation((float) Math.toRadians(12));   // Radiant
g.setView(camera);
```

Weitere Kameramethoden: `move`, `rotate`, `zoomBy`, `setProjection`,
`getProjection`, `getX`, `getY`, `getRotation`, `getZoom` sowie
`createViewTransform` und `createViewProjection`.
Projection/View-Wechsel sind recordbar und werden beim Replay erneut gesetzt.

`Projection2D` stellt zusätzlich die statischen Matrixfunktionen
`topLeftPixels(width,height)`, `centeredPixels`, `centeredNormalized` und
`clipSpace` bereit. `create(width,height)` ist die funktionale Kernmethode;
Breite und Höhe sind logische Viewportpixel. `createViewProjection` delegiert
darauf. Eigene Projektionen können als Lambda implementiert werden.

### Screen-Bounds und Damage-Test

```java
if (g.intersectsRedrawBounds(x, y, width, height)) {
    drawExpensiveThing(g);
}
float[] screen = g.getScreenBounds(x, y, width, height);
```

Die Eingabebounds verwenden lokale Benutzerkoordinaten. Beide Methoden
berücksichtigen Model und View; `getScreenBounds` liefert
`{x, y, width, height}` als achsenparallele Bounds in logischen Screenpixeln.
`GLContainer` nutzt diese Mechanik bereits für Culling; eigene Tests lohnen
sich vor allem innerhalb großer dynamischer Renderhooks.

## Frameausgaben und Clipping

### Clear

```java
g.clear(new Color(18, 23, 34));
```

`clear()` benutzt `DEFAULT_CLEAR_COLOR`. `clear(Color)` flusht offene Geometrie
und löscht nur die derzeit aktivierten Renderausgaben. In einem Recording wird
der Clear als Replay-Befehl gespeichert; das ist möglich, aber für normale
Komponenten selten sinnvoll.

### Farb- und Pickingausgabe

```java
g.setOutputMask(true, false);      // nur Farbe
g.setColorRenderingEnabled(false);
g.setPickingRenderingEnabled(true); // nur Picking
g.setOutputMask(true, true);       // Frame-Normalzustand
```

Beide Outputs sind am Frameanfang aktiv. Eine Änderung flusht und ist
recordbar. Bei temporären Änderungen muss der Aufrufer den gewünschten Zustand
wiederherstellen.

Die Maske bestimmt, **in welche vorhandenen Frameausgaben** nachfolgende
Befehle schreiben. Sie wählt kein anderes Renderziel: Dafür dienen
`beginRenderTarget` und `endRenderTarget`. `setOutputMask(false,true)` bedeutet
also beispielsweise „nur in den Picking-FBO schreiben“, nicht „einen FBO als
Target setzen“.

`setTexturePickAlphaThreshold` erwartet einen dimensionslosen Wert von `0..1`
und betrifft nur texturgebundene Geometrie: Verglichen wird Texturalpha mal
Tintalpha. Filled, Text und UI-Analytik sind davon nicht betroffen.

### Clip

```java
g.beginClip(20, 20, 300, 160);
try {
    drawScrollableContent(g);
} finally {
    g.endClip();
}
```

Die vier Argumente sind lokale Benutzerkoordinaten. Clips sind verschachtelbar
und recordbar. Verwendet wird die transformierte, achsenparallele
Screen-Boundingbox; es ist ein günstiger Scissor-Clip, kein runder
Geometrieclip. `getClipDepth()` liefert eine dimensionslose Ebenenanzahl und
hilft beim Diagnostizieren unbalancierter Paare. Für runde oder vollkommen
freie Vektorkanten siehe `roundedClip` und `pathClip` im
[Layerkapitel](layers-effects.md).

### Damage

Die Damage-Region selbst wird vom Host in logischen Screenpixeln geführt.
Komponenten können ihre äußeren Effektpixel über `visualOverflow` einbeziehen;
die Contextabfrage dazu ist `intersectsRedrawBounds`, wie oben beschrieben.

## Shaderzustände

Filled-, Texture-, Text- und UI-Shader sind getrennt, damit kein unverständliches
Attributlayout-Mismatch entsteht. Shader- und Uniformwechsel sind recordbar,
validieren ihre Zielpipeline und flushen deren offenen Batch. Der UI-Shader ist
ein bewusster vollständiger Ersatz für alle UI-Modi. Die vollständigen
Verträge und Einheiten der verwalteten Uniforms stehen im
[Shaderkapitel](shaders.md).

## Getter und Kopien

- `getTransform` und `getProjection` liefern unabhängige Kopien.
- `getStroke` darf direkt behalten werden, da `GLStroke` unveränderlich ist.
- `getStrokeDash` liefert eine defensive Kopie.
- Shader-Getter liefern die aktuell gesetzte Programminstanz oder `null` für
  den jeweiligen Defaultshader.
- Shadergetter liefern die aktuell gesetzte Instanz oder `null` für Default.
- Depth-Getter liefern dimensionslose Anzahlen der aktuell offenen
  Clip-/Layer-/RenderTarget-Ebenen.
