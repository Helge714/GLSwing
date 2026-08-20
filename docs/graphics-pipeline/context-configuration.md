# Context konfigurieren

[Zur API-Landkarte](../graphics-pipeline.md) · [Weiter: Zeichenbefehle](drawing.md)

`GLContext2D` wird zu Beginn jedes Frames auf definierte Defaults gesetzt. Ein
`GLContainer` sichert beim Zeichnen zusätzlich Transform, Farbe, Stroke und
Pickingzustand und stellt sie danach wieder her. Innerhalb eines eigenen
Renderhooks sollten temporäre Änderungen trotzdem möglichst lokal und bewusst
bleiben.

## Farbe und Clear

```java
g.setColor(new Color(70, 140, 230));
g.fillRect(20, 20, 120, 50);  // verwendet Contextfarbe
Color current = g.getColor();

g.clear(new Color(18, 23, 34));
```

`clear()` benutzt `DEFAULT_CLEAR_COLOR`. `clear(Color)` flusht offene Geometrie
und löscht nur die derzeit aktivierten Renderausgaben. In einem Recording wird
der Clear als Replay-Befehl gespeichert; das ist möglich, aber für normale
Komponenten selten sinnvoll.

## Model-Transform

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

Positive Winkel wirken im üblichen Top-Left-Koordinatensystem optisch im
Uhrzeigersinn. `rotate(radians, anchorX, anchorY)` rotiert um einen lokalen
Anker. `resetTransform()` setzt Identität.

Model-Transforms werden auf Geometrie angewandt und müssen einen kompatiblen
Batch nicht beenden. Während `beginRecording()` beginnt der Modelraum bewusst
bei Identität; der vorherige Transform wird nach Ende/Abbruch restauriert.

### `GLTransform`-Referenz

`GLTransform` ist eine mutable affine 2D-Matrix. Konstruktoren: Identität,
Kopiekonstruktor und sechs affine Werte. Die fluent mutierenden Methoden geben
dieselbe Instanz zurück.

| Methoden | Zweck |
|---|---|
| `setToIdentity` | Identität setzen |
| `setTransform(GLTransform)` / `(m00,m10,m01,m11,m02,m12)` | Matrix ersetzen |
| `translate`, `rotate`, `scale` | Operation anhängen |
| `concatenate` | Weitere affine Matrix anhängen |
| `transform(float[],offset,count)` | X/Y-Punktpaare in-place transformieren |
| `transformVertices(float[],stride)` | Vertexarray mit X/Y am Stride-Anfang transformieren |
| `transformX`, `transformY` | Einzelne Koordinate aus X/Y berechnen |
| `isIdentity`, `isTranslation` | Spezialform testen |
| `getScaleX`, `getScaleY`, `getShearX`, `getShearY` | Linearen Anteil lesen |
| `getTranslateX`, `getTranslateY` | Translation lesen |
| `copy` | Unabhängige Kopie |
| `determinant` | Determinante |
| `getMaximumScale` | Größten wirksamen Skalierungsfaktor abschätzen |
| `createInverse` | Inverse Matrix; Fehler bei nicht invertierbarer Matrix |
| `toMatrix4` | Spaltenmajor-`float[16]` für OpenGL |

`equals`, `hashCode` und `toString` vergleichen beziehungsweise beschreiben die
sechs affinen Werte.

## Projection und View

`setProjection` ändert die World-to-Clip-Matrix und flusht, weil diese Matrix
eine Drawcall-Uniform ist.

| Projection | Koordinatenraum |
|---|---|
| `Projection2D.TOP_LEFT_PIXELS` | `(0,0)` links oben, logische Pixel |
| `Projection2D.CENTERED_PIXELS` | Ursprung in Fenstermitte, Pixelmaß |
| `Projection2D.CENTERED_NORMALIZED` | Y `-1..1`, X mit Seitenverhältnis |
| `Projection2D.CLIP_SPACE` | X/Y `-1..1`, Seitenverhältnis nicht korrigiert |
| `Projection2D.fixed(transform)` | Unveränderliche eigene Matrix |

```java
g.setProjection(Projection2D.CENTERED_PIXELS);
```

`GLView2D` kombiniert die größenabhängige Berechnung in
`createViewProjection(width,height)`. `GLCamera2D` ist die fertige bewegliche
View:

```java
GLCamera2D camera = new GLCamera2D(Projection2D.CENTERED_PIXELS);
camera.setPosition(120, 40);
camera.setZoom(1.75f);
camera.setRotation((float) Math.toRadians(12));
g.setView(camera);
```

Weitere Kameramethoden: `move`, `rotate`, `zoomBy`, `setProjection`,
`getProjection`, `getX`, `getY`, `getRotation`, `getZoom` sowie
`createViewTransform` und `createViewProjection`.
Projection/View-Wechsel sind recordbar und werden beim Replay erneut gesetzt.

`Projection2D` stellt zusätzlich die statischen Matrixfunktionen
`topLeftPixels(width,height)`, `centeredPixels`, `centeredNormalized` und
`clipSpace` bereit. `create(width,height)` ist die funktionale Kernmethode;
`createViewProjection` delegiert darauf. Eigene Projektionen können als Lambda
implementiert werden.

## Stroke

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

| Eigenschaft | Wirkung |
|---|---|
| Width | Gesamtdicke, muss positiv sein |
| Cap | `BUTT` endet am Punkt, `SQUARE` verlängert gerade, `ROUND` halbkreisförmig |
| Join | `MITER` spitz, `BEVEL` abgeschnitten, `ROUND` gerundet |
| Miter limit | Ab wann ein zu langer Miter in eine sichere Ecke fällt |
| Offset | Verschiebt die Stroke-Mitte entlang der Pfadnormalen |
| Curve tolerance | Maximale sichtbare Abweichung runder Stroke-Geometrie in Screenpixeln |
| Width space | `WORLD` zoomt mit, `SCREEN` bleibt kosmetisch konstant |
| Dash array | Abwechselnd sichtbare/leere positive Längen |
| Dash phase | Bereits verbrauchte Länge am Pfadanfang |

Bei einem ungeraden Dash-Array wird das Muster wiederholt wie bei
`BasicStroke`. `getStrokeDash()` liefert eine defensive Kopie oder `null`.
`setStrokeDashPhase` ist nur bei aktivem Muster erlaubt.

Die immutable `GLStroke`-API spiegelt alle Werte mit `getWidth`, `getCap`,
`getJoin`, `getMiterLimit`, `getOffset`, `getCurveTolerance`, `getWidthSpace`,
`getDashArray`, `getDashPhase` und `isDashed`. Fluent Kopien entstehen über
`withWidth`, `withCap`, `withJoin`, `withMiterLimit`, `withOffset`,
`withCurveTolerance`, `withWidthSpace`, `withDash`, `withDashPhase` und
`withoutDash`. `DEFAULT` ist ein solider World-Space-Stroke mit Breite 1.

Strokezustand wird nicht als eigener Replay-Befehl gespeichert: Er wird beim
Recording in die erzeugte Geometrie gebacken. Screen-Space-Geometrie speichert
zusätzlich einen Compiler und wird beim Replay gecacht neu berechnet, sobald
Transform oder View sich ändert.

## Toleranzen

`setCurveTolerance(float)` steuert gefüllte runde Formen, Beziers und Paths.
`setStrokeCurveTolerance(float)` gehört dagegen zum Stroke. Beide Werte sind
maximale Abweichungen in logischen Screenpixeln: kleiner bedeutet glatter und
mehr Geometrie. Voreinstellungen des Strokes:

- `CURVE_TOLERANCE_COARSE = 0.4f`
- `CURVE_TOLERANCE_NORMAL = 0.2f`
- `CURVE_TOLERANCE_FINE = 0.1f`

## Farb- und Pickingausgabe

```java
g.setRenderOutputs(true, false);  // nur Farbe
g.setColorRenderingEnabled(false);
g.setPickingRenderingEnabled(true); // nur Picking
g.setRenderOutputs(true, true);   // Frame-Normalzustand
```

Beide Outputs sind am Frameanfang aktiv. Eine Änderung flusht und ist
recordbar. Bei temporären Änderungen muss der Aufrufer den gewünschten Zustand
wiederherstellen.

`setTexturePickAlphaThreshold(0..1)` betrifft nur Texturbatches: verglichen
wird Texturalpha mal Tintalpha. Filled, Text und UI-Analytik sind davon nicht
betroffen.

## Clip

```java
g.beginClip(20, 20, 300, 160);
try {
    drawScrollableContent(g);
} finally {
    g.endClip();
}
```

Clips sind verschachtelbar und recordbar. Verwendet wird die transformierte,
achsenparallele Screen-Boundingbox; es ist ein günstiger Scissor-Clip, kein
runder Geometrieclip. `getClipDepth()` hilft beim Diagnostizieren unbalancierter
Paare. Für runde Kanten siehe `roundedClip` im [Layerkapitel](layers-effects.md).

## Damage und Screen-Bounds

```java
if (g.intersectsRedrawBounds(x, y, width, height)) {
    drawExpensiveThing(g);
}
float[] screen = g.getScreenBounds(x, y, width, height);
```

Beide Methoden berücksichtigen Model und View. Die zurückgegebenen vier Werte
sind die achsenparallelen Screen-Bounds. `GLContainer` nutzt diese Mechanik
bereits für Culling; eigene Tests lohnen sich vor allem innerhalb großer
dynamischer Renderhooks.

## Shaderzustände

Filled-, Texture- und Textshader sind getrennt, damit kein unverständliches
Attributlayout-Mismatch entsteht. Shader- und Uniformwechsel sind recordbar,
validieren ihre Zielpipeline und flushen deren offenen Batch. Die vollständigen
Verträge stehen im [Shaderkapitel](shaders.md).

## Getter und Kopien

- `getTransform` und `getProjection` liefern unabhängige Kopien.
- `getStroke` darf direkt behalten werden, da `GLStroke` unveränderlich ist.
- `getStrokeDash` liefert eine defensive Kopie.
- Shadergetter liefern die aktuell gesetzte Instanz oder `null` für Default.
- Depth-Getter zählen die aktuell offenen Clip-/Layer-/RenderTarget-Ebenen.
