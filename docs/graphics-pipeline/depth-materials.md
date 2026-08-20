# Depth und 2.5D-Materialien

[Zur API-Landkarte](../graphics-pipeline.md) · [Zeichenbefehle](drawing.md) · [Shader](shaders.md) · [RenderTargets](render-targets.md)

Diese Pipeline ergänzt die normale 2D-API um Tiefentest, pixelgenaue
Sprite-Tiefe und einfache Normalmap-Beleuchtung. Sie ist bewusst keine
vollständige Game-Engine: Szenengraph, Physik, Animation und Assetverwaltung
bleiben Sache der Anwendung.

## Inhalt

- [Depth-Konvention und Contextzustand](#depth-konvention-und-contextzustand)
- [Sprites und Materialien](#sprites-und-materialien)
- [Alpha-Modi](#alpha-modi)
- [Depth- und Normalmaps](#depth--und-normalmaps)
- [Licht](#licht)
- [Batching, Picking und Recording](#batching-picking-und-recording)
- [RenderTargets und Layer](#rendertargets-und-layer)
- [Eigene Materialshader](#eigene-materialshader)
- [Performance messen](#performance-messen)

## Depth-Konvention und Contextzustand

GLSwing verwendet öffentlich die intuitive Konvention:

- `0` liegt hinten,
- `1` liegt vorne,
- der Standardvergleich für 2.5D ist `GREATER`,
- `clearDepth()` setzt den Puffer auf `0`.

```java
g.clearDepth();
g.setDepthState(GLDepthState.READ_WRITE);
g.setDepth(0.25f);
g.fillRect(20, 20, 160, 80, Color.DARK_GRAY);
g.setDepthState(GLDepthState.DISABLED);
```

`setDepth(float)` setzt für normale Filled-, Texture-, Text- und UI-Geometrie
eine feste dimensionslose Tiefe. Eine Änderung flusht den offenen Batch.
`GLDepthState` steuert Test, Schreibmaske und `GLDepthFunction`; die fertigen
Konstanten sind:

| Zustand | Test | Schreiben | Typischer Einsatz |
|---|---:|---:|---|
| `DISABLED` | nein | nein | Normale GUI/Painter-Reihenfolge |
| `READ_WRITE` | ja | ja | Undurchsichtige oder ausgeschnittene Geometrie |
| `READ_ONLY` | ja | nein | Transparente, sortierte Geometrie |

`clear(Color)` löscht weiterhin nur Farbe und Picking. Depth ist absichtlich
separat, damit partielle oder persistente Tiefeninhalte möglich bleiben.

## Sprites und Materialien

`GLMaterial2D` beschreibt GPU-Zustand und Maps. `GLSprite` ist die komfortable
Assethülle mit natürlicher Größe:

```java
GLSprite rock = GLSprite.builder(colorImage)
        .depthMap(depthImage)
        .normalMap(normalImage)
        .alphaMode(GLAlphaMode.CUTOUT)
        .alphaCutoff(0.08f)
        .depthRange(-0.03f, 0.09f)
        .lit(true)
        .build();

g.drawSprite(rock, x, y);                         // natürliche Größe
g.drawSprite(rock, x, y, width, height);          // skaliert
g.drawSprite(rock, x, y, width, height,
        Color.WHITE, 0.62f);                      // explizite Basis-Tiefe
```

`drawMaterial(...)` akzeptiert direkt ein `GLMaterial2D`. Position und Größe
sind lokale Benutzerkoordinaten; Basis-Tiefe und Mapwerte sind dimensionslos.
Fehlt beim Draw die explizite Tiefe, wird `getDepth()` verwendet.

Materialien sind unveränderlich. Wiederverwendung derselben Instanz macht
Zustandsvergleich und Batching vorhersehbar.

## Alpha-Modi

| Modus | Verhalten | Standard-Depthstate |
|---|---|---|
| `OPAQUE` | Kein Alpha-Discard; schnellster Weg | `READ_WRITE` |
| `CUTOUT` | Fragmente unter `alphaCutoff` werden verworfen; Alpha-to-Coverage bei MSAA | `READ_WRITE` |
| `BLEND` | Gewöhnliches Alpha-Blending | `READ_ONLY` |

Eine Textur mit transparenten Löchern muss `CUTOUT` oder `BLEND` verwenden.
`OPAQUE` darf auch unsichtbare Texel in Depth schreiben. Für korrektes
halbtransparentes Compositing bleiben Sprites trotz Depth-Test in der Regel
von hinten nach vorne sortiert; ein einzelner Depthbuffer löst allgemeine
Transparenzsortierung nicht.

`depthState(...)` im Builder darf den Standard bewusst überschreiben.

## Depth- und Normalmaps

Die rote Komponente der Depthmap wird als relative Oberfläche `0..1`
interpretiert:

```text
fragmentDepth = baseDepth + mix(backOffset, frontOffset, depthMap.r)
```

Das Ergebnis wird auf `0..1` begrenzt. `depthRange(back,front)` verwendet
dimensionslose Depth-Einheiten; `back` muss kleiner oder gleich `front` sein.
Ohne Depthmap liegt die Oberfläche bei `0.5` innerhalb dieses Bereichs.

Normalmaps speichern Tangent-Space-Normalen in RGB: `(0.5,0.5,1)` zeigt
senkrecht zum Sprite zum Betrachter. Der Renderer leitet aus dem transformierten
Quad zwei Tangenten ab, sodass Rotation und nicht gespiegelte Skalierung mit
der Beleuchtung zusammenpassen.

Color-, Depth- und Normalmap werden unabhängig über `GLImage` aufgelöst. Sie
dürfen auf unterschiedlichen Atlasseiten und an unterschiedlichen UV-Rechtecken
liegen; jeder Instanzdatensatz führt dafür drei UV-Rechtecke. Die Anwendung
verwaltet daher keine Atlaspositionen.

## Licht

Die erste Lichtstufe ist ein kleiner Forward-Pass aus Ambientlicht und optional
einem gerichteten Licht:

```java
GLLighting2D light = new GLLighting2D(
        new Color(210, 225, 255), 0.25f,
        new GLLighting2D.DirectionalLight(
                -0.4f, -0.5f, 0.75f,
                Color.WHITE, 1.1f));
g.setLighting(light);
```

Richtung ist ein normalisierter Vektor: X nach rechts, Y nach unten, Z zum
Betrachter. Farben sind `java.awt.Color`, Intensitäten dimensionslose,
nichtnegative Faktoren. Nur Materialien mit Normalmap und `lit(true)` werden
beleuchtet. `GLLighting2D.DEFAULT` und `withoutDirectionalLight()` sind
Komfortwerte.

## Batching, Picking und Recording

Der spezialisierte Sprite-Batch zeichnet instanziert und bindet bis zu fünf
Material-Textursets. Jedes Set besteht aus Color-, Depth- und Normaltextur;
damit bleiben maximal 15 Texture-Units belegt und die garantierte
GL3-Untergrenze von 16 wird eingehalten.

Pro Sprite wird nur ein Instanzdatensatz übertragen: vier transformierte
Eckpunkte, je ein UV-Rechteck für Color/Depth/Normal, Tint, Depthbereich,
Tangentenbasis und die drei Integerwerte für Picking, Texturslot und Flags. Der
Vertexshader erzeugt daraus über `gl_VertexID` die sechs Eckaufrufe des Quads.
Bis zu 65.536 kompatible Sprites passen in einen Drawcall. Die CPU-Buffer
beginnen kleiner und wachsen erst bei Bedarf, sodass normale GUI-Szenen den
Maximalspeicher nicht vorab belegen.
Ein Flush entsteht bei:

- mehr als fünf gleichzeitig benötigten Textursets,
- Batchkapazität,
- Wechsel von Alpha-Modus, Depthstate, Materialshader oder dessen Uniforms,
- Projection-/Output-/Lichtwechsel oder explizitem `flush()`.

Picking verwendet einen eigenen Integer-FBO mit eigenem Depth-Attachment und
denselben Color-Alpha-, Cutout- und Depthmap-Regeln. Der sichtbare Gewinner und
das `GLMouseTarget` stimmen deshalb pixelgenau überein.

`drawSprite` und `drawMaterial` sind recordbar. Maps werden erst beim Replay
aufgelöst, sodass Recordings keine renderergebundenen Texture-IDs speichern.
MouseTargets bleiben wie bei anderen Recordings schwach referenziert. Ein
Recording speist den dynamischen Instanzbatch erneut.

Für unveränderte große Mengen dient getrennt davon `GLSpriteBuffer`:

```java
g.beginSpriteBuffer();
for (Tile tile : staticTiles)
    g.drawSprite(tile.sprite(), tile.x(), tile.y(), 32, 32,
            Color.WHITE, tile.depth());
GLSpriteBuffer chunk = g.endSpriteBuffer();

g.setTransform(cameraTransform);
g.draw(chunk);              // Instanzdaten bleiben auf der GPU
```

Der erste Draw lädt rendererlokale `GL_STATIC_DRAW`-Puffer; weitere Frames
ändern nur Drawzustand und Modellmatrix. Materialwechsel werden intern in
geordnete Abschnitte zerlegt. Picking bleibt möglich, aktualisiert wegen der
frame-lokalen Registry aber weiterhin den Integer-ID-Strom. Details und
Lebensdauer stehen im [Recording-Kapitel](recording.md#gpu-residente-sprite-puffer).

## RenderTargets und Layer

Depth in einem persistenten Target ist opt-in:

```java
GLRenderTarget target = new GLRenderTarget(512, 512,
        GLRenderTargetOptions.DEFAULT
                .withDepth(true)
                .withPicking(true));

g.withRenderTarget(target, () -> {
    g.clear(Color.TRANSPARENT);
    g.clearDepth();
    g.drawSprite(rock, 80, 80, 220, 220, Color.WHITE, 0.7f);
});
```

`withDepth(true)` erzeugt einen Depthbuffer für Farbe und – falls aktiviert –
einen unabhängigen Depthbuffer für Picking. Ohne die Option weist ein
depth-testendes Material im Target den Draw mit einer verständlichen Exception
ab. Hauptfenster, persistente Scene und temporäre Layer besitzen Depth bereits.

Layerinhalt darf Sprites mit Depth enthalten. Beim abschließenden
Layer-Compositing ist Depth kurz deaktiviert, weil der fertige Layer als eine
2D-Gruppe in den Parent gemischt wird. Effekte verändern derzeit nur Color und
Alpha, nicht die innere Depthoberfläche.

## Eigene Materialshader

Ein Material kann einen eigenen Sprite-Fragmentshader und typgeprüfte Uniforms
tragen. Der eingebaute Vertexvertrag entsteht bequem mit:

```java
ShaderProgram program = ShaderProgram.fromSpriteFragmentSource(gl,
        fragmentSource);

GLSprite custom = GLSprite.builder(colorImage)
        .depthMap(depthImage)
        .normalMap(normalImage)
        .shader(program)
        .uniform("uRoughness", 0.65f)
        .build();
```

Der vollständige Vertrag steht im [Shaderkapitel](shaders.md). Wie bei allen
eigenen Shadern besitzt das Material das Programm nicht; die Anwendung gibt es
mit aktuellem GL-Kontext frei, nachdem kein Material/Recording es mehr nutzt.

## Performance messen

`SpritePipelineBenchmarkDemo` zeichnet dieselbe Sprite-Menge kontinuierlich
und schaltet zur Laufzeit zwischen folgenden Dimensionen um:

- 100, 1.000, 10.000 oder 50.000 Sprites,
- nur Farbe, Farbe plus Depth oder Farbe plus Depth, Normalmap und Licht,
- `OPAQUE`, `CUTOUT` und `BLEND`,
- statische und pro Frame bewegte Positionen,
- Farbausgabe allein oder zusätzliche Picking-Ausgabe.
- dynamische Einreichung oder GPU-residenter Sprite-Puffer.

Der Fenstertitel trennt FPS und End-to-End-Zeit von CPU-Szenenzeit,
`drawSprite`-Einreichungszeit, GPU-Szenenzeit, Present-Zeit und Drawcalls. Die
UI-Controls gehören bei dieser Demo bewusst zum vollständigen Frame.

`SpritePipelineBenchmark` verwendet dieselbe Workload in einem
1280-mal-720-Offscreen-Framebuffer mit 4x MSAA. Er schließt Swing,
Fenstertausch und Desktop-Compositor aus und gibt p50/p95 für API-Aufbau,
CPU-Submission und `GL_TIME_ELAPSED` auf der GPU aus. Das ist der passende
Test für Rendereränderungen; die interaktive Demo beantwortet dagegen, wie
sich die vollständige Anwendung verhält.

`ResidentSpriteBenchmark` ist der direkte A/B-Test für 10.000 und 50.000
dynamische beziehungsweise residente Instanzen, jeweils mit CPU-API- und
`GL_TIME_ELAPSED`-Messung sowie einem separaten Picking-Vergleich.

Die visuelle Referenz ist `DepthSpriteDemo`; der zugehörige
`DepthSpriteGpuSelfTest` prüft Cutout, Occlusion, Lighting, Recording,
RenderTargets, residente Wiederverwendung/Transformation und depth-korrektes
Picking.
