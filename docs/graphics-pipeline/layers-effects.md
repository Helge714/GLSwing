# Layer, Compositing und Effekte

[Zur API-Landkarte](../graphics-pipeline.md) · [Shader](shaders.md) · [Weiter: RenderTargets](render-targets.md)

Ein Layer sammelt Farbe in einer temporären Offscreentextur. `endLayer` setzt
das fertige Ergebnis einmal in den Parent ein. Picking bleibt unabhängig und
wird während des Inhalts direkt in den Picking-FBO geschrieben.

## Inhalt

- [Wann ein Layer nötig ist](#wann-ein-layer-nötig-ist)
- [Begin/End und Einheiten](#beginend)
- [Composite und Blendmodi](#glcomposite)
- [Effektreihenfolge und Kosten](#effektreihenfolge-und-kostenmodell)
- [Alle eingebauten Effekte](#alle-eingebauten-effekte)
- [Effektketten und HiDPI](#effektketten)
- [Picking](#picking)

## Wann ein Layer nötig ist

- gemeinsame Opacity ohne dunklere Überlappungen,
- Blendmodus auf eine Gruppe,
- Blur, Schatten, Outline, Farbkorrektur oder Formclip,
- verschachtelte Postprocessing-Schritte.

Für einen einfachen Schatten eines runden UI-Rechtecks ist
`drawUIRoundRectShadow` günstiger.

## Begin/End

```java
g.beginLayer(30, 30, 300, 180);
try {
    g.fillRect(30, 30, 220, 90, Color.WHITE);
    g.drawLine(40, 60, 290, 150, Color.WHITE);
} finally {
    g.endLayer(0.45f);
}
```

| Begin | Verwendung |
|---|---|
| `beginLayer()` | Automatische Bounds aus allen tatsächlich gezeichneten Farbvertices |
| `beginLayer(x,y,w,h)` | Explizite lokale Bounds und früher Scissor-Clip |
| `beginLayer(x,y,w,h,screenInset)` | Bounds an allen Seiten um logische Screenpixel nach innen setzen |

X/Y/Breite/Höhe verwenden aktuelle lokale Benutzerkoordinaten. `screenInset`
ist dagegen ausdrücklich eine Dicke in logischen Screenpixeln und bleibt daher
unter Model-/Kamerazoom konstant. `endLayer(opacity)` erwartet einen
dimensionslosen Wert von `0..1`.

Der Screen-Inset wird beim Recording-Replay neu aufgelöst und bleibt unter
World-Zoom stabil. Effekte dürfen über die Bounds hinaus wachsen; ihr Padding
wird automatisch zur Verarbeitungsregion addiert.

Layer sind verschachtelbar und recordbar. Begin/End müssen auf derselben Ebene
balanciert sein; `getLayerDepth()` unterstützt die Diagnose.

Die parameterlose Variante belegt nicht pro Aufruf eine neue Textur in
Objektgröße. Der Renderer nutzt wiederverwendete Fullsize-FBOs, sammelt beim
Zeichnen konservative Screenbounds und beschränkt Resolve, Effekte und
Composite am Ende auf diese Region. Ein leerer Layer erzeugt keinen
Composite-Drawcall. Bei Verschachtelung meldet ein fertiger innerer Layer seine
inklusive Effektpadding vergrößerte Region automatisch an den Parent.

Explizite Bounds bleiben sinnvoll, wenn der Inhalt schon bekannt ist: Sie
clippen bereits während des Zeichnens in den Layer und schützen bewusst vor
außerhalb liegenden Zeichenbefehlen. Automatische Bounds beschreiben dagegen
alles, was tatsächlich in Farbe gezeichnet wurde. `clear(...)` innerhalb eines
automatischen Layers betrifft absichtlich den kompletten aktuellen Clip.

## `GLComposite`

```java
GLComposite composite = GLComposite.NORMAL
        .withOpacity(0.9f)
        .withBlendMode(GLBlendMode.MULTIPLY)
        .withEffect(GLLayerEffects.dropShadow(
                8, 10, 14, new Color(0, 0, 0, 130)))
        .withEffect(GLLayerEffects.outline(
                2, new Color(120, 200, 255)));
g.endLayer(composite);
```

`GLComposite` ist unveränderlich. Konstruktoren/Fabriken: leer, nur Opacity,
nur Blendmodus, beides, `opacity(...)`, `blend(...)`. Konstanten: `DEFAULT`,
`NORMAL`, `ADD`, `MULTIPLY`, `SCREEN`, `SUBTRACT`, `ERASE`.
`withEffect` hängt an die Reihenfolge an, `withoutEffects` entfernt alle.
`getOpacity`, `getBlendMode` und `getEffects` lesen den Zustand;
`getEffectPadding` summiert das deklarierte Padding der Kette.
Opacity ist dimensionslos (`0..1`); Effektpadding ist eine Länge in logischen
Screenpixeln.

## Blendmodi

| Modus | Ergebnis |
|---|---|
| `NORMAL` | Premultiplied Source-over |
| `ADD` | Quellfarbe zur Destination addieren |
| `MULTIPLY` | Quelle und Destination multiplizieren, verdunkelt meist |
| `SCREEN` | Inverse Multiplikation, hellt meist auf |
| `SUBTRACT` | Quellfarbe von Destination abziehen |
| `ERASE` | Destinationfarbe und -alpha mit Layeralpha entfernen |

Diese destinationabhängigen Operationen werden direkt von der festen
OpenGL-Blendstufe unterstützt und benötigen kein Ping-Pong-Backdrop-Sampling.

## Effektreihenfolge und Kostenmodell

Effekte laufen exakt in `withEffect`-Reihenfolge. Das Ergebnis eines Effekts
ist die Eingabe des nächsten. Der letzte Pass schreibt direkt mit Composite-
Opacity und Blendmodus in den Parent.

- Blur, DropShadow und InnerShadow benötigen je zwei Drawcalls: horizontaler
  Blur und vertikaler/zusammensetzender Pass.
- Outline, InnerOutline, Color, RoundedClip und Custom benötigen je einen
  Drawcall.
- PathClip benötigt einen Masken- und einen Composite-Drawcall.
- Ein Layer ohne Effekte benötigt einen Composite-Drawcall.
- Die eigentliche Layergeometrie besitzt zusätzlich ihre normalen Drawcalls.

Kleine Bounds sind daher oft wichtiger als eine kleine Objektzahl.

## Alle eingebauten Effekte

### Blur

```java
GLLayerEffects.blur(8f)
// identisch zu new GLBlurEffect(8f)
```

Separabler Gaussian Blur. `radius >= 0`, gemessen in logischen Swingpixeln.
Padding: `radius`. Zwei Passes. Der gesamte Layer einschließlich Alpha wird
weichgezeichnet.

API: Konstruktor `GLBlurEffect(radius)`, `getRadius`, `getPadding`.

### Drop Shadow

```java
GLLayerEffects.dropShadow(6, 9, 12, new Color(0, 0, 0, 130));
GLLayerEffects.outerGlow(12, new Color(80, 160, 255, 150));
```

Erzeugt aus der verschobenen Layeralpha einen weichen Schatten und erhält den
Quellinhalt. Offset und Radius sind logische Screenpixel; Offset muss endlich,
Radius nichtnegativ sein. `outerGlow` ist ein
DropShadow mit Offset `(0,0)`. Padding:
`radius + max(abs(offsetX), abs(offsetY))`. Zwei Passes.

API: Konstruktor sowie `getOffsetX`, `getOffsetY`, `getRadius`, `getColor`,
`getPadding`.

### Outer Outline

```java
GLLayerEffects.outline(3, Color.WHITE)
```

Färbt transparente Pixel, wenn in der angegebenen Nachbarschaft Layeralpha
existiert. Die Outline liegt außerhalb der Form. Breite in logischen
Screenpixeln, `>= 0`; Padding gleich Breite, ein Pass.

API: Konstruktor `GLOutlineEffect(width,color)`, `getWidth`, `getColor`,
`getPadding`.

### Inner Outline

```java
GLLayerEffects.innerOutline(3, new Color(20, 30, 45))
```

Färbt den Rand vollständig innerhalb der vorhandenen Layeralpha. Breite in
logischen Screenpixeln, `>= 0`; Padding `0`, ein Pass. Die Außenkontur wächst
nicht.

API: Konstruktor `GLInnerOutlineEffect(width,color)`, `getWidth`, `getColor`,
`getPadding`.

### Inner Shadow

```java
GLLayerEffects.innerShadow(3, 5, 8, new Color(0, 0, 0, 120))
```

Weicher verschobener Schatten, auf die Innenseite der Alphaform geclippt.
Offset und Radius sind logische Screenpixel. Offset endlich, Radius `>= 0`,
Padding `0`, zwei Passes.

API: Konstruktor sowie `getOffsetX`, `getOffsetY`, `getRadius`, `getColor`,
`getPadding`.

### Kombinierte Farbkorrektur

```java
GLColorEffect color = GLLayerEffects.colorAdjust()
        .withBrightness(1.1f)
        .withContrast(1.15f)
        .withSaturation(0.7f)
        .withGamma(1.05f)
        .withInvert(0.1f)
        .withTint(new Color(90, 150, 255), 0.2f);
```

Alle Operationen laufen gemeinsam in einem Texture-Sample/einem Pass:

| Wert | Neutral | Bereich |
|---|---:|---|
| Brightness | 1 | Dimensionsloser Faktor, endlich, `>= 0` |
| Contrast | 1 | Dimensionsloser Faktor, endlich, `>= 0` |
| Saturation | 1 | Dimensionsloser Faktor, endlich, `>= 0`; 0 ist Graustufe |
| Gamma | 1 | Dimensionsloser Exponent, endlich, `> 0` |
| Invert | 0 | Dimensionsloser Anteil `0..1` |
| Tint strength | 0 | Dimensionsloser Anteil `0..1` |

Padding `0`. Komfortfabriken: `grayscale(strength)` setzt Sättigung auf
`1-strength`; `invert(strength)` setzt die Invertstärke.

Zu jedem `with...` existiert ein entsprechender Getter: `getBrightness`,
`getContrast`, `getSaturation`, `getGamma`, `getInvert`, `getTint` und
`getTintStrength`; `getPadding` liefert 0.

### Rounded Clip

```java
GLLayerEffects.roundedClip(new GLCornerRadii(14, 14, 4, 4))
```

Clippt antialiasend auf ein gerundetes Rechteck an den ursprünglichen
Layerbounds. Jeder Eckenradius ist in logischen Screenpixeln konfigurierbar.
Padding `0`, ein Pass. Anders als `beginClip` bleibt die tatsächliche runde
Form erhalten.

Der Record-Accessor heißt `radii()`, dazu kommt `getPadding()`.

### Freier Path Clip

```java
GLShape2D clipShape = GLShape2D.roundedRectangle(
        30, 30, 240, 120, new GLCornerRadii(24, 8, 24, 8));

g.beginLayer();
g.fillRect(10, 10, 300, 160, artworkColor);
g.endLayer(GLComposite.NORMAL.withEffect(
        GLLayerEffects.pathClip(clipShape)));
```

`pathClip(GLShape2D)` clippt den Layer gegen jede gefüllte Vektorform:
Rechteck, RoundRect, Ellipse, Kreis, Polygon, eigener `GLPath2D` oder eine
eigene `GLShape2D`-Implementierung. Der Path verwendet den lokalen
Koordinatenraum und Transformationszustand, der bei `beginLayer()` aktiv war.
Er wird mit der aktuellen `curveTolerance` tesselliert und in eine
MSAA-aufgelöste Maske gezeichnet; dadurch bleiben auch Kurven und schräge
Kanten antialiasend. Padding `0`, zwei Drawcalls.

Fabriken stehen sowohl auf `GLShape2D` als auch auf `GLPath2D` bereit:
`rectangle`, `roundedRectangle`, `ellipse`, `circle` und `polygon`. Jede
`GLShape2D` besitzt `toPath()` und liefert damit eine unabhängig veränderbare
Pfaddarstellung. `GLPath2D` implementiert dieses Interface selbst und gibt bei
`toPath()` ebenfalls eine Kopie zurück.

API: `new GLPathClipEffect(shape)`, `getPath`, `getPadding` sowie
`GLLayerEffects.pathClip(shape)`.

### Custom Shader

```java
GLShaderEffect custom = GLLayerEffects.custom(program)
        .withUniform("uAmount", 0.5f)
        .withUniform("uDirection", 1f, 0f)
        .withPadding(6);
```

Ein Pass, sofern der eigene Shader nicht selbst durch mehrere Effects
verkettet wird. Uniformtypen: Float, Vec2/3/4, Int und Mat4. Padding ist
standardmäßig `0`, wird in logischen Screenpixeln gemessen und muss für
außerhalb entstehende Pixel ausdrücklich gesetzt werden. Shadervertrag und
verwaltete Uniforms stehen im [Shaderkapitel](shaders.md).

API: Konstruktor oder `GLShaderEffect.of(shader)`, `getShader`, alle
`withUniform`-Overloads, `withUniformMatrix4`, `withPadding`, `getPadding`.

Die Fabrik gibt bewusst den konkreten `GLShaderEffect` und nicht nur das
gemeinsame Interface `GLLayerEffect` zurück. Dadurch bleiben die fluent
Methoden `withUniform(...)` und `withPadding(...)` ohne Cast erreichbar.
`GLShaderEffect` implementiert dennoch `GLLayerEffect`; nach der Konfiguration
kann der Wert deshalb problemlos als allgemeiner Layereffekt gespeichert oder
an `withEffect(...)` übergeben werden.

## Effektketten

Schatten vor Blur ist nicht dasselbe wie Blur vor Schatten:

```java
GLComposite softShadowed = GLComposite.NORMAL
        .withEffect(GLLayerEffects.dropShadow(5, 7, 10, shadowColor))
        .withEffect(GLLayerEffects.blur(1.5f));
```

Jedes `withEffect` ist ein eigener logischer Schritt und sein Padding wird zur
bisherigen Kette addiert. Farbkorrekturen sollten zusammen in einem
`GLColorEffect` statt als mehrere äquivalente Custompasses formuliert werden.

## HiDPI und logische Pixel

Radius, Breite, Offset und Padding der eingebauten Effekte sind logische Pixel.
Der Renderer wandelt sie mit dem aktuellen DeviceScale in physische FBO-Pixel
um. Deshalb bleiben Blur, Schatten und Outline unter Windows-Skalierung glatt
und visuell gleich groß.

## Picking

Layer-Effekte verändern den Picking-FBO nicht. Ein Schatten oder Glow wird
nicht automatisch pickbar; nur die während `beginLayer` ausgeführten
Zeichenbefehle schreiben ihre IDs/Ziele. Das ist normalerweise das gewünschte
Verhalten.
