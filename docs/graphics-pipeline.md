# Grafikpipeline und `GLContext2D`

Dieses Handbuch erklärt die öffentliche 2D-Grafikpipeline von GLSwingNext. Es
ist Bedienungsanleitung und Schnellreferenz: Erst kommt das mentale Modell,
danach folgen alle Zustands- und Zeichenbefehle des `GLContext2D` sowie größere
Beispiele für Shader, Atlanten, Recording, Picking, Layer und RenderTargets.

## 1. Das mentale Modell

```text
GLFrame / GLSwingCanvas / GLSwingPanel
        -> GLContext2D
        -> normalisierte Geometrie und Zustandswechsel
        -> Filled-, Texture-, Text- oder Universal-UI-Batch
        -> Farb-FBO und optional Picking-FBO
        -> Bildschirm
```

`GLContext2D` verhält sich ähnlich wie `Graphics2D`: Farbe, Transform, Stroke,
Shader und Picking-Ziel sind Zustände. Ein Zeichenbefehl verwendet den aktiven
Zustand, sofern sein Overload keinen expliziten Wert erhält.

- Koordinaten sind `float` und standardmäßig logische Pixel mit `(0, 0)` links
  oben.
- Java-Zeichenaufrufe werden gebatcht; ein Aufruf ist kein GPU-Drawcall.
- Inkompatible Zustandswechsel flushen automatisch.
- `GLContainer` cached stabile eigene Geometrie als `GLRecording`; Kinder
  besitzen jeweils ihr eigenes Recording.
- GPU-Ressourcen gehören dem Renderer/GL-Kontext, in dem sie erzeugt oder zuerst
  verwendet werden.

## 2. Ein minimales Programm

```java
import java.awt.Color;
import javax.swing.SwingUtilities;
import com.vingame.glswing.component.*;
import com.vingame.glswing.rendering.*;

public final class FirstScene {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GLFrame.RenderingSettings settings =
                    GLFrame.RenderingSettings.DEFAULT
                            .withAntialiasingSamples(4)
                            .withVSync(true)
                            .withTargetFps(144);

            GLFrame frame = new GLFrame("Erste Szene", settings);
            frame.setSize(900, 600);
            frame.setGLClearColor(new Color(18, 23, 34));

            GLContainer scene = new GLContainer() {
                @Override
                protected void record(GLContext2D g) {
                    g.fillRoundRect(40, 40, 260, 130, 18,
                            new Color(55, 125, 220));
                    g.setStroke(new GLStroke(3)
                            .withJoin(GLStroke.Join.ROUND));
                    g.drawRoundRect(40, 40, 260, 130, 18,
                            new Color(170, 210, 255));
                }
            };
            scene.setBounds(0, 0, 900, 600);
            frame.addGL(scene);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
```

`GLFrame` verwaltet Canvas, Animator und Shutdown. Wer eine eigene
Swing-Hierarchie braucht, kann `GLSwingContainerCanvas` in einen `JFrame`
setzen. `GLSwingPanel` ist für echte Swing-Komposition gedacht, besitzt wegen
der zusätzlichen Swing-Ausgabe aber einen anderen Performancepfad. Unter
Windows benötigt der direkte Canvas die in den Launch-Konfigurationen gesetzten
`--add-opens`-Argumente.

## 3. `record` oder `render`?

`GLContainer.record(GLContext2D)` beschreibt stabilen Inhalt in lokalen
Koordinaten. GLSwing nimmt ihn lazy auf. Nach einer geometrischen oder visuellen
Änderung ruft man `invalidateRecording()` auf.

```java
final class Card extends GLContainer {
    private Color color = new Color(48, 89, 150);

    void setColor(Color color) {
        this.color = color;
        invalidateRecording();
    }

    @Override
    protected void record(GLContext2D g) {
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, color);
    }
}
```

Die Parent-Transform wirkt erst beim Abspielen. `render(GLContext2D)` ist für
dynamische Präsentation; seine Standardimplementierung zeichnet das Recording.
Bei einer Überschreibung zeichnet man es üblicherweise selbst:

```java
@Override
protected void render(GLContext2D g) {
    GLRenderParameters p = new GLRenderParameters()
            .setOpacity(enabled ? 1f : 0.45f);
    g.draw(getRecording(), p);
}
```

Ein `GLContext2D` gilt nur für den aktuellen Renderaufruf und darf nicht für
spätere Frames gespeichert werden.

## 4. Overload-Regeln

- Ohne `Color` wird `getColor()` verwendet; ein explizites `Color` verändert
  den Context nicht.
- `int pickId` und `GLMouseTarget` machen genau diesen Befehl pickbar.
- `setPickId` beziehungsweise `setMouseTarget` gelten für folgende Befehle ohne
  explizites Picking-Argument. Beide schließen sich gegenseitig aus.
- `float[] points` ist `x0, y0, x1, y1, ...`; bei `Color[]` gehört eine Farbe zu
  jedem Punkt.

```java
g.setColor(Color.ORANGE);
g.fillRect(10, 10, 80, 30);                 // Contextfarbe
g.fillRect(100, 10, 80, 30, Color.CYAN);    // lokale Farbe
g.fillRect(190, 10, 80, 30, 42);            // Pick-ID 42
g.fillRect(280, 10, 80, 30, mouseTarget);    // direktes Mausziel
```

## 5. Alle Context-Zustände und Konfigurationen

### Allgemein, Clear und Damage

| Befehle | Bedeutung |
|---|---|
| `setColor`, `getColor` | Standardfarbe |
| `clear()`, `clear(Color)` | Aktive Outputs löschen; vorheriger Batch wird geflusht |
| `getScreenBounds(x,y,w,h)` | Transformierte, achsenparallele Screen-Bounds |
| `intersectsRedrawBounds(x,y,w,h)` | Test gegen aktuelle Damage-Region |
| `flush()` | Offene Batches ausdrücklich absenden |

`clear()` verwendet `DEFAULT_CLEAR_COLOR`. Ein manuelles `flush()` ist fast nie
nötig, da der Context Zustandsgrenzen selbst kennt.

### Model, Projection und Kamera

| Befehle | Bedeutung |
|---|---|
| `getTransform`, `setTransform`, `resetTransform` | Model-Transform; Getter liefert Kopie |
| `translate`, `rotate`, `scale` | Transform fortschreiben; Winkel in Radiant |
| `rotate(radians,anchorX,anchorY)` | Rotation um lokalen Anker |
| `getProjection`, `setProjection(GLTransform)` | Projektionsmatrix direkt |
| `setProjection(Projection2D)` | Projection anhand der Zielgröße |
| `setView(GLView2D)` | View/Kamera samt Projection anwenden |

Eingebaute Projektionen:

- `TOP_LEFT_PIXELS`: `(0,0)` links oben, Pixelmaß.
- `CENTERED_PIXELS`: Ursprung mittig, Pixelmaß.
- `CENTERED_NORMALIZED`: Y `-1..1`, X mit Seitenverhältnis.
- `CLIP_SPACE`: X/Y `-1..1`, eventuell verzerrt.
- `Projection2D.fixed(transform)`: feste Matrix.

```java
GLCamera2D camera = new GLCamera2D(Projection2D.CENTERED_PIXELS)
        .setPosition(120, 40)
        .setZoom(1.75f)
        .setRotation((float) Math.toRadians(12));
g.setView(camera);
```

Projection definiert den Raum, Kamera den sichtbaren Ausschnitt und der
Model-Transform einzelne Objekte. Projection/View sind Uniformzustände.

### Stroke und Kurvenqualität

| Befehle | Bedeutung |
|---|---|
| `setStroke`, `getStroke` | Ganzen unveränderlichen `GLStroke` setzen/lesen |
| `set/getStrokeWidth` | Breite |
| `set/getStrokeCap` | `BUTT`, `SQUARE`, `ROUND` |
| `set/getStrokeJoin` | `MITER`, `BEVEL`, `ROUND` |
| `set/getStrokeOffset` | Versatz der Stroke-Mitte |
| `set/getStrokeMiterLimit` | Grenze spitzer Miter-Ecken |
| `set/getStrokeCurveTolerance` | Rundungsabweichung in Screenpixeln |
| `set/getStrokeWidthSpace` | `WORLD` oder `SCREEN` |
| `setStrokeDash`, `getStrokeDash`, `clearStrokeDash` | Dash-Muster |
| `set/getStrokeDashPhase` | Start im Muster |
| `set/getCurveTolerance` | Path-/Bezier-/Formtessellierung |

```java
g.setStroke(new GLStroke(3)
        .withCap(GLStroke.Cap.ROUND)
        .withJoin(GLStroke.Join.ROUND)
        .withWidthSpace(GLStroke.WidthSpace.SCREEN)
        .withCurveTolerance(GLStroke.CURVE_TOLERANCE_FINE)
        .withDash(new float[] { 10, 6, 2, 6 }, 3));
g.drawPolyline(new float[] { 30, 90, 150, 30, 280, 110 });
```

In `WORLD` zoomen Breite, Offset und Dash-Längen mit. In `SCREEN` bleiben sie
logische Bildschirmpixel. Die Rundungstoleranz bleibt screenbezogen. Ein
Screen-Space-Stroke in einem Recording wird nur bei geändertem Transform/View
neu kompiliert.

### Clip und Layer

| Befehle | Bedeutung |
|---|---|
| `beginClip`, `endClip`, `getClipDepth` | Verschachtelbarer rechteckiger Scissor-Clip |
| `beginLayer()` | Transparenter Offscreen-Layer |
| `beginLayer(x,y,w,h)` | Layer auf echte Bounds begrenzen |
| `beginLayer(x,y,w,h,screenInset)` | Zusätzlich stabiler Screenpixel-Inset |
| `endLayer()` / `(opacity)` / `(blendMode)` | Zusammensetzen |
| `endLayer(GLComposite)` | Opacity, Blendmodus und Effektkette |
| `getLayerDepth()` | Offene Layer |

Der Clip verwendet die transformierte achsenparallele Screenbox. Für runde,
antialiaste Grenzen dient `GLLayerEffects.roundedClip(...)`.

### Outputs und Picking

| Befehle | Bedeutung |
|---|---|
| `setRenderOutputs(color,picking)` | Farb- und Picking-FBO unabhängig aktivieren |
| `set/isColorRenderingEnabled` | Farbausgabe |
| `set/isPickingRenderingEnabled` | Pickingausgabe |
| `set/getTexturePickAlphaThreshold` | Alpha-Grenze für Texturen inklusive Tint |
| `set/getPickId`, `clearPickId` | Numerische ID |
| `set/getMouseTarget`, `clearMouseTarget` | Direktes Mausziel |
| `setMouseTarget(target,originX,originY)` | Expliziter lokaler Ursprung |

Die Alpha-Grenze gilt nicht für Filled oder Text. `0` pickt jeden Texel, `1`
nur vollständig deckende.

### Shader und Uniforms

| Batch | Shader | Default | Uniforms |
|---|---|---|---|
| Filled | `set/getFilledShader` | `useDefaultFilledShader` | `setFilledUniform...` |
| Texture | `set/getTextureShader` | `useDefaultTextureShader` | `setTextureUniform...` |
| Text | `set/getTextShader` | `useDefaultTextShader` | `setTextUniform...` |

Jede Uniformfamilie unterstützt `float`, `vec2`, `vec3`, `vec4`, `int` und
`set...UniformMatrix4(name,float[16])`. Änderung flushes den betroffenen Batch.
Dieselbe Shaderinstanz erneut zu setzen ist günstig; `null` wählt den Default.

### Recording und RenderTargets

| Befehle | Bedeutung |
|---|---|
| `beginRecording`, `endRecording`, `cancelRecording`, `isRecording` | Aufnahme |
| `draw(recording[,parameters])` | Wiedergabe |
| `beginParameters`, `endParameters`, `withParameters` | Parametrisierbare Aufnahmeregion |
| `beginRenderTarget`, `endRenderTarget`, `withRenderTarget` | Persistenter FBO-Pass |
| `getRenderTargetDepth` | Offene Targets |
| `drawRenderTarget` | Targetinhalt darstellen |

RenderTarget-Updates sind nicht recordbar. Im Target gelten Top-Left-Pixel und
Picking ist aus; `endRenderTarget` restauriert den kompletten Zustand.

## 6. Alle Zeichenfamilien

### Linien und Umrisse

| Familie | Varianten |
|---|---|
| `drawLine` | eine Farbe oder Farbe pro Endpunkt |
| `drawPolyline` | offen; eine Farbe oder `Color[]` |
| `drawPolygon` | geschlossen; eine Farbe oder `Color[]` |
| `drawCubicBezier` | eine Farbe oder Verlauf, optional `GLColorInterpolator` |
| `drawPath` | Subpaths, Linien und Kurven |
| `drawRect` | einfarbig oder vier Eckfarben |
| `drawRoundRect` | Radius oder `GLCornerRadii` |
| `drawEllipse`, `drawCircle` | gekrümmte Grundformen |

Bezier-Verläufe verwenden standardmäßig sRGB. Verfügbar sind außerdem
`LINEAR_RGB`, `HSV_SHORTEST`, `OKLAB`, `OKLCH_SHORTEST` oder ein eigener
`GLColorInterpolator`.

### Gefüllte Formen

| Familie | Varianten |
|---|---|
| `fillTriangle` | eine Farbe oder Farbe pro Vertex |
| `fillRect` | Rechteck |
| `fillPolygon` | auch konkav; eine Farbe oder `Color[]` |
| `fillRoundRect` | Radius oder `GLCornerRadii` |
| `fillEllipse`, `fillCircle` | gekrümmte Formen |
| `fillPath` | `Color` oder ortsabhängiges `GLPaint2D` |

```java
GLPaint2D stripes = (x, y) -> ((int) (x / 16) & 1) == 0
        ? new Color(80, 180, 255)
        : new Color(35, 90, 180);
g.fillPath(path, stripes);
```

### Text, Bilder und Texturen

| Familie | Bedeutung |
|---|---|
| `drawText(font,text,x,baselineY,...)` | MSDF-Text; Y ist Baseline |
| `drawImage(image,x,y[,w,h,tint])` | High-Level-Bild, automatische Speicherung |
| `drawUIImage(image,x,y,w,h[,tint])` | Bild im Universal-UI-Batch |
| `drawIcon(icon,x,y,w,h[,color])` | RGBA-/MSDF-Icon im UI-Batch |
| `drawTexture(texture,x,y,...)` | Manuelle Low-Level-Textur |
| `drawTexture(texture,source...,destination...,tint)` | Pixel-Teilbereich |
| `drawRenderTarget(target,x,y[,w,h,tint/picking])` | FBO-Inhalt |

### Universal-UI-Batch

| Befehl | Bedeutung |
|---|---|
| `fillUIRoundRect` | Analytisch antialiaste Fläche |
| `drawUIRoundRect` | Analytischer innerer Border; World/Screen |
| `fillAndDrawUIRoundRect` | Fill + Border ohne AA-Bleeding |
| `drawUIRoundRectShadow` | Nicht pickbarer Schatten ohne Layer |
| `drawUIText` | MSDF-Text |
| `drawUICheckMark`, `drawUIChevron` | Symbole |
| `fillUIProgressRoundRect` | Geclippter Fortschrittsverlauf |
| `fillUIVerticalGradientRoundRect` | Voller Verlauf |
| `addUIHitArea` | Unsichtbare Pickingfläche |
| `drawUIImage`, `drawIcon` | Atlasinhalt |

Dieser spezialisierte Pfad ignoriert eigene Filled-Shader. Für frei shaderbare
Geometrie dienen die normalen Shape-, Texture- und Textbefehle.

## 7. Pfade und SVG

```java
GLPath2D heart = new GLPath2D(GLPath2D.FillRule.NON_ZERO)
        .moveTo(50, 88)
        .cubicTo(5, 55, 18, 12, 50, 35)
        .cubicTo(82, 12, 95, 55, 50, 88)
        .closePath();
g.fillPath(heart, new Color(225, 65, 95));
g.drawPath(heart, new Color(110, 25, 45));
```

`EVEN_ODD` schaltet bei jeder Kontur innen/außen; `NON_ZERO` verwendet die
Richtung. Außerdem: `copy`, `reset`, `transformed`, `getBounds`,
`getSegmentCount`, `getCommandCount`.

```java
GLPath2D onePath = GLPath2D.fromSvgPath("M10 10 H90 V90 H10 Z");
GLSvgDocument logo = GLSvgDocument.loadResource("com/example/logo.svg");
logo.draw(g, 20, 20, 220, 120);
```

Der Pfadparser unterstützt `M/L/H/V/C/S/Q/T/A/Z`, absolut und relativ; Arcs
werden Beziers. Für häufige Icons ist `GLIcon` günstiger als das wiederholte
Zeichnen eines kompletten Dokuments.

## 8. Text, Bilder und Atlanten

### Text

```java
GLFont font = new GLFont(GLFont.SANS_SERIF, GLFont.PLAIN, 18);
g.drawText(font, "Baseline bei y = 80", 30, 80, Color.WHITE);
```

MSDF-Text bleibt bei Skalierung/Rotation scharf. Eigene Fonts werden im
GL-Disposehook mit `font.dispose(gl)` freigegeben. Die GUI-Schicht bietet über
`GLParent.getGLFont(GLFontSpec)` einen verwalteten Fontcache. `drawText` nutzt
den Textbatch; `drawUIText` den Universal-UI-Batch.

### `GLImage` und automatische Atlanten

```java
GLImage photo = GLImage.loadResource(MyApp.class, "/images/photo.png");
GLImage pixels = GLImage.load(path, GLImageOptions.pixelArt());
g.drawImage(photo, 20, 20, 320, 180);
g.drawUIImage(pixels, 360, 20, 48, 48);
```

Beim ersten Zeichnen entscheidet der Renderer:

- kleine statische Clamp-Bilder landen normalerweise in RGBA-Atlaspages,
- große, wiederholte oder dynamische Bilder werden Standalone-Texturen,
- `Storage.ATLAS`/`STANDALONE` erzwingt die Entscheidung,
- ein UI-Batch bindet bis zu 16 Texturen; danach folgt ein neuer Drawcall.

```java
GLImageOptions.atlas();
GLImageOptions.standalone();
GLImageOptions.pixelArt();
GLImageOptions.dynamic();
GLImageOptions.repeating();

GLImageOptions custom = GLImageOptions.DEFAULT
        .withFilter(GLImageOptions.Filter.LINEAR)
        .withMipmapping(GLImageOptions.Mipmapping.ENABLED)
        .withStorage(GLImageOptions.Storage.STANDALONE);
```

Statische linear gefilterte Bilder verwenden in `AUTO` Mipmaps. Atlasränder
werden extrudiert. `GLImage.update` ist nur bei `Usage.DYNAMIC` erlaubt.

### `GLIcon`: MSDF oder adaptive RGBA

```java
GLIcon link = GLIcon.fromSvgResource("com/example/link.svg");
GLIcon logo = GLIcon.fromSvgResource(
        "com/example/logo.svg", GLIcon.Format.RGBA);
g.drawIcon(link, 20, 240, 24, 24, accent);
g.drawIcon(logo, 60, 210, 96, 96);
```

`AUTO` wählt für monochrome SVGs tintbares MSDF, für mehrfarbige SVGs adaptive
RGBA-Stufen. Größere Stufen entstehen verzögert im Hintergrund; bis dahin bleibt
die beste vorhandene sichtbar. Transform, Projection und HiDPI gehen in die
Auswahl ein, auch beim Recording-Replay.

```java
GLIconOptions options = GLIconOptions.DEFAULT
        .withResolutionRange(32, 1024)
        .withResolutionStep(2)
        .withOversampling(1.25f)
        .withRasterizationDelayMillis(150);
GLIcon adaptive = GLIcon.fromSvgResource(
        "com/example/logo.svg", GLIcon.Format.RGBA, options);
adaptive.prepareAsync(512).thenRun(frame::repaintGL);
```

`GLIconOptions.fixed(resolution)` erzwingt eine Stufe. `prepareAsync` wärmt die
CPU-Rasterisierung; der Upload bleibt im Rendererthread.

### `GLTexture` als Low-Level-Weg

`GLTexture.create(gl,image)` benötigt einen aktuellen Kontext, wird mit
`drawTexture` gezeichnet und vom Besitzer mit `dispose(gl)` freigegeben. Für
normale Bilder sollte `GLImage` die Atlasentscheidung übernehmen.

## 9. Eigene Shader und Uniforms

GLSwing validiert beim ersten Einsatz:

| Batch | Attribute | Pflichtuniforms |
|---|---|---|
| Filled | `aPosition` 0, `aColor` 1 | `uProjection` |
| Texture | `aPosition` 0, `aTexCoord` 1, `aColor` 2 | `uProjection`, `uTexture` |
| Text | wie Texture | zusätzlich `uMsdfPixelRange` |
| Layer | eingebauter Vertexshader | `uTexture`, `uOpacity` |

```java
private static final String VERTEX = """
        #version 330 core
        layout(location = 0) in vec2 aPosition;
        layout(location = 1) in vec4 aColor;
        uniform mat4 uProjection;
        out vec4 vColor;
        void main() {
            vColor = aColor;
            gl_Position = uProjection * vec4(aPosition, 0.0, 1.0);
        }
        """;

private static final String FRAGMENT = """
        #version 330 core
        in vec4 vColor;
        uniform float uGamma;
        out vec4 fragColor;
        void main() {
            fragColor = vec4(pow(vColor.rgb, vec3(1.0 / uGamma)), vColor.a);
        }
        """;

// initialize(GL3 gl):
shader = ShaderProgram.fromSource(gl, VERTEX, FRAGMENT);

// render:
g.setFilledShader(shader);
g.setFilledUniform("uGamma", 2.2f);
g.fillRect(20, 20, 180, 100, new Color(70, 150, 235));
g.useDefaultFilledShader();

// disposeResources(GL3 gl):
shader.dispose(gl);
```

Inaktive oder falsch typisierte Uniforms erzeugen eine klare Exception. Für
Layer steht `ShaderProgram.fromLayerFragmentSource(gl, fragmentSource)` bereit;
der Fragmentshader deklariert `vTexCoord`, `uTexture` und `uOpacity` und wird
über `GLLayerEffects.custom(shader)` eingebunden.

## 10. Recording und Parameter

Recording spart Tessellierung, Stroke-Aufbau, Textvertices und Java-Aufrufe.
Beim Replay fließen seine Befehle wieder in die offenen Framebatches, sodass
benachbarte Recordings weiterhin gemeinsam gebatcht werden.

```java
g.beginRecording();
try {
    g.fillRoundRect(0, 0, 240, 110, 14, new Color(45, 80, 135));
    g.drawLine(20, 76, 220, 76, new Color(100, 220, 255));
    card = g.endRecording();
} catch (RuntimeException error) {
    g.cancelRecording();
    throw error;
}

GLRenderParameters state = new GLRenderParameters()
        .setTranslation(300, 120)
        .setTint(new Color(210, 235, 255))
        .setOpacity(0.8f);
g.draw(card, state);
```

`setTint` multipliziert aufgezeichnete Farben; `setColor` ersetzt sie und
`clearColor` restauriert sie. Parameter-Opacity ist Vertex-Alpha, keine
Gruppen-Opacity.

Parametrisierbare, verschachtelbare Regionen:

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

Ein Recording gehört seinem Erzeuger-Renderer und besitzt referenzierte
Texturen, Fonts oder Shader nicht. `GLMouseTarget` wird schwach referenziert.
RenderTarget-Inhalte und `drawRenderTarget` sind dynamisch und nicht recordbar;
auch Playback innerhalb einer aktiven Aufnahme ist derzeit nicht erlaubt.

## 11. Picking

```java
g.setPickId(17);
g.fillCircle(80, 80, 30, Color.ORANGE);
g.clearPickId();

g.setMouseTarget(target, 40, 25);
g.fillRect(40, 25, 180, 60, Color.BLUE);
g.clearMouseTarget();
```

Bei IDs verwaltet die Anwendung die Zuordnung. Ein direktes MouseTarget kann
GLSwing aufrufen. Der Ursprung bestimmt lokale Mauskoordinaten. Ohne expliziten
Ursprung verwendet der erste Zeichenbefehl seine linke obere Bounds-Ecke oder
seinen ersten Punkt. `GLContainer` setzt sich automatisch mit `(0,0)` als Ziel,
sofern `isMouseTargetEnabled()` nicht deaktiviert ist.

```java
g.setRenderOutputs(false, true);  // nur Pickingmaske
g.addUIHitArea(20, 20, 260, 50);
g.setRenderOutputs(true, true);   // immer restaurieren
```

## 12. Layer und Effekte

Layer wenden Alpha einmal auf die fertige Gruppe an und vermeiden dadurch
dunkle Knotenpunkte überlappender transparenter Formen.

```java
GLComposite composite = GLComposite.NORMAL
        .withOpacity(0.9f)
        .withBlendMode(GLBlendMode.MULTIPLY)
        .withEffect(GLLayerEffects.dropShadow(
                8, 10, 14, new Color(0, 0, 0, 130)))
        .withEffect(GLLayerEffects.outline(
                2, new Color(120, 200, 255)))
        .withEffect(GLLayerEffects.blur(1.5f));

g.beginLayer(30, 30, 300, 180);
g.fillRoundRect(30, 30, 300, 180, 18, Color.WHITE);
g.endLayer(composite);
```

Blendmodi: `NORMAL`, `ADD`, `MULTIPLY`, `SCREEN`, `SUBTRACT`, `ERASE`.
Effekte: `blur`, `dropShadow`, `outerGlow`, `outline`, `innerOutline`,
`innerShadow`, `colorAdjust`, `grayscale`, `invert`, `roundedClip`, `custom`.
Sie laufen in `withEffect`-Reihenfolge. Layer sind verschachtelbar; Picking
geht direkt in den unabhängigen Picking-FBO. Begrenzte Layer sind deutlich
günstiger als Fullscreen-Layer, äußeres Effektpadding wird berücksichtigt.

## 13. RenderTargets und Feedback

```java
GLRenderTarget target = new GLRenderTarget(
        512, 512, GLRenderTargetOptions.pixelArt());
g.withRenderTarget(target, () -> {
    g.clear(Color.TRANSPARENT);
    g.fillCircle(256, 256, 80, Color.WHITE);
});
g.drawRenderTarget(target, 20, 20, 400, 400,
        new Color(100, 220, 170));
target.close(); // wenn endgültig nicht mehr benötigt
```

Optionen sind `LINEAR`/`NEAREST` und `CLAMP`/`REPEAT`; Kurzformen sind
`pixelArt()` und `repeatingPixelArt()`. Ein Target bindet sich bei der ersten
Verwendung an einen Renderer.

Für Simulationen kapselt `GLFeedbackBuffer` zwei Targets:

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

Während `update` bleibt `current()` lesbar und `next()` ist Ziel. Nur nach
Erfolg wird getauscht. `clear`, `beginUpdate`, `endUpdate`, `cancelUpdate` und
`close` erlauben manuelle Steuerung.

## 14. Batching und Performance

Typische Flush-Grenzen:

- Wechsel zwischen Filled, Texture, Text und Universal-UI,
- Shader- oder Uniformwechsel,
- Projection/View-Wechsel,
- Clip, Layer oder RenderTarget,
- Wechsel der Color-/Picking-Outputs,
- `clear`, `flush` oder eine Kapazitäts-/Texturslotgrenze.

Model-Transforms müssen nicht automatisch flushen. Praktisch:

1. Nach Typ/Shader zusammenhängend zeichnen.
2. Stabile komplexe Geometrie recorden.
3. Für Controls die `...UI...`-Methoden verwenden.
4. Kleine statische Bilder als `GLImage` der Atlasautomatik überlassen.
5. Layer begrenzen und GPU-Readbacks vermeiden.

Am Host helfen `getLastDrawCallCount`, `getFramesPerSecond`,
`getAverageFrameTimeMillis` und `getFrameProfile`. FPS allein trennt VSync,
Swing-Präsentation, CPU-Aufbau und GPU-Zeit nicht.

## 15. Ressourcen und Lebensdauer

| Ressource | Erzeugung | Freigabe/Eigentum |
|---|---|---|
| `GLImage` | ohne GL-Kontext | Renderer verwaltet Atlas-/Texturekopie |
| `GLIcon` | ohne GL-Kontext | Renderer verwaltet Varianten |
| `GLFont` | Atlas lazy | eigener Font: `dispose(gl)`; GUI-Cache verwaltet |
| `GLTexture` | aktueller GL-Kontext | Besitzer: `dispose(gl)` |
| `ShaderProgram` | aktueller GL-Kontext | Besitzer: `dispose(gl)` |
| `GLRecording` | Renderaufruf | renderergebunden, besitzt Ressourcen nicht |
| `GLRenderTarget` | lazy | `close()`, renderergebunden |
| `GLFeedbackBuffer` | lazy | `close()` schließt beide Targets |

Low-Level-GL-Ressourcen gehören in Initialisierungs- und Dispose-Hooks. Sie
dürfen nicht auf einem beliebigen Swing-Thread ohne aktuellen GL-Kontext erzeugt
oder gelöscht werden.

## 16. Fehlersuche

- Schwarzes Bild: richtiger Renderhook, Color-Output aktiv, passende Projection?
- Eigener Shader unsichtbar: Attribute, Locations und Pflichtuniforms prüfen.
- Falsches Picking: Pickzustand und Outputs restauriert? Alpha-Schwelle passend?
- Langsamer Layer: echte Bounds statt Fullscreen verwenden.
- Recording unverändert: `invalidateRecording` für Geometrie; Parameter für
  Farbe/Transform.
- Text vertikal falsch: `drawText` erwartet die Baseline.
- Bild nicht im UI-Batch: `drawImage` ist Texturebatch, `drawUIImage` UI-Batch.
- Pixelgrafik weich: jeweilige `pixelArt()`-Option wählen.
- Bildupdate abgewiesen: `GLImageOptions.dynamic()` verwenden.

Die vollständigen ausführbaren Fassungen der komplexeren Beispiele liegen in
den Demo-Klassen aus dem Dokumentationsindex.
