# Bilder, Atlanten, Icons, Text, Pfade und SVG

[Zur API-Landkarte](../graphics-pipeline.md) · [RenderTargets](render-targets.md) · [Weiter: Picking](picking.md)

Dieses Kapitel erklärt alle Ressourcen, die von den Zeichenbefehlen konsumiert
werden. Die wichtigste Trennung ist High-Level (`GLImage`, `GLIcon`) gegen
manuelle GL-Ressourcen (`GLTexture`, eigener `GLFont`, `ShaderProgram`).

## Inhalt

- [Bilder und GLImageOptions](#glimage)
- [Icons und adaptive Auflösung](#glicon)
- [Texturslots und GLTexture](#texturslots)
- [Fonts](#glfont)
- [Pfade und SVG](#glpath2d)
- [Lebensdauer](#lebensdauertabelle)

Größen von CPU-Bildern, Texturen und Atlaspages sind ganzzahlige
Raster-/Texturpixel. Zielbounds beim Zeichnen verwenden lokale
Benutzerkoordinaten. Adaptive Iconauflösungen beziehen sich ausdrücklich auf
physische Framebufferpixel; Zeitparameter mit `Millis` im Namen verwenden
Millisekunden.

## `GLImage`

`GLImage` hält CPU-Pixel und Optionen und kann ohne aktuellen GL-Kontext geladen
werden:

```java
GLImage fromMemory = GLImage.of(bufferedImage);
GLImage fromFile = GLImage.load(path);
GLImage fromResource = GLImage.loadResource(
        MyApp.class, "/images/photo.png");
```

Alle Erzeugungswege besitzen eine Variante mit `GLImageOptions`. Getter:
`getWidth`, `getHeight`, `getOptions`.

### Speicherentscheidung

Beim ersten Zeichnen löst jeder Renderer das Bild lazy auf:

- `AUTO`: kleine statische Clamp-Bilder in Atlas, große als Standalone,
- `ATLAS`: Atlas erzwingen; Fehler, wenn es nicht passt,
- `STANDALONE`: eigene Textur erzwingen,
- `REPEAT` oder `DYNAMIC`: immer Standalone.

Die gewünschte Atlaspage ist `2048 × 2048` Texturpixel groß, höchstens jedoch
`GL_MAX_TEXTURE_SIZE`. In `AUTO` wird ein Bild nur atlasiert, wenn jede Dimension
höchstens die halbe Pagegröße besitzt und es mit Padding passt.

### `GLImageOptions`

| Achse | Werte | Bedeutung |
|---|---|---|
| Storage | `AUTO`, `ATLAS`, `STANDALONE` | Speicherort |
| Filter | `LINEAR`, `NEAREST` | Samplerfilter |
| Wrap | `CLAMP`, `REPEAT` | Verhalten außerhalb `0..1` |
| Usage | `STATIC`, `DYNAMIC` | Änderbarkeit |
| Mipmapping | `NONE`, `AUTO`, `ENABLED` | Mipkette |

Presets: `DEFAULT`, `atlas()`, `standalone()`, `pixelArt()`, `dynamic()`,
`repeating()`. `withStorage`, `withFilter`, `withWrap`, `withUsage` und
`withMipmapping` erzeugen immutable Kopien. Die Record-Accessors heißen
`storage`, `filter`, `wrap`, `usage` und `mipmapping`. Atlasbilder
müssen statisch und Clamp sein. Explizite Mipmaps erfordern statisch + linear.
Nearest schaltet Mipmapping aus; Dynamic schaltet Atlas und Mipmapping aus.

### Mipmaps und Atlasränder

Statische linear gefilterte Bilder erhalten bei `AUTO` Mipmaps. Atlasbilder
verwenden extrudierte Ränder und nur so viele Stufen, wie Bildgröße und Padding
sicher erlauben. Dirty Atlas-Mipketten werden erst unmittelbar vor dem nächsten
Sampling erzeugt.

### Dynamische Bilder

```java
GLImage dynamic = GLImage.of(initialPixels, GLImageOptions.dynamic());
dynamic.update(nextPixels);
```

`update` ist nur bei `Usage.DYNAMIC` erlaubt. Bleibt die Größe gleich, kann der
Renderer die Standalone-Textur aktualisieren. Andere Renderer besitzen jeweils
ihre eigene aufgelöste GPU-Kopie.

## `drawImage` oder `drawUIImage`?

- `drawImage`: normaler Texture-Batch, eigener TextureShader möglich.
- `drawUIImage`: Universal-UI-Batch zusammen mit Controls/Text/Icons, aber kein
  eigener TextureShader.

Beide Wege verwenden dieselbe automatische Speicherauflösung.

## `GLIcon`

```java
GLIcon imageIcon = GLIcon.fromImage(image);
GLIcon autoSvg = GLIcon.fromSvgResource("com/example/link.svg");
GLIcon rgbaSvg = GLIcon.fromSvgResource(
        "com/example/logo.svg", GLIcon.Format.RGBA);
```

Formate:

| Format | Verhalten |
|---|---|
| `AUTO` | Monochromes SVG → MSDF, mehrfarbiges SVG → RGBA |
| `MSDF` | Tintbares Signed-Distance-Field, eine feste Quellauflösung |
| `RGBA` | Originalfarben, optional adaptive Rasterstufen |

`fromSvg(document,format[,options])` arbeitet mit einem bereits geladenen
`GLSvgDocument`. Getter: `getFormat`, `getRasterResolution`, `getOptions`,
`getCachedVariantCount`.

### Adaptive RGBA-Auflösung

```java
GLIconOptions options = GLIconOptions.DEFAULT
        .withResolutionRange(32, 1024)
        .withResolutionStep(2)
        .withOversampling(1.25f)
        .withRasterizationDelayMillis(150);
GLIcon adaptive = GLIcon.fromSvgResource(
        "com/example/logo.svg", GLIcon.Format.RGBA, options);
```

Der Renderer bestimmt aus Bounds, Model, Projection und HiDPI die benötigten
physischen Pixel. Fehlende Stufen werden nach der konfigurierten Verzögerung in
einem Hintergrundworker rasterisiert. Bis dahin bleibt die beste vorhandene
Stufe sichtbar; der kurze Upload folgt später im GL-Thread. Beim
Recording-Replay wird die Stufe neu gewählt, ohne das Recording zu erneuern.

`prepareAsync(physicalPixels)` wärmt eine quadratische Stufe der angegebenen
physischen Framebufferpixelzahl vor und liefert deren
`CompletableFuture<GLImage>`. `GLIconOptions.fixed(resolution)` verwendet genau
eine Größe in physischen Pixeln. Validierung: Auflösungen mindestens 16
physische Pixel, Max >= Min, der `resolutionStep` ist ein dimensionsloser
Faktor >= 2, `oversampling` ein dimensionsloser Faktor `>0..4` und die
Verzögerung liegt bei `0..2000 ms`.

Die immutable Options-API bietet `withRasterPolicy`, `withResolutionRange`,
`withMinimumResolution`, `withMaximumResolution`, `withResolutionStep`,
`withOversampling` und `withRasterizationDelayMillis`. Record-Accessors:
`rasterPolicy`, `minimumResolution`, `maximumResolution`, `resolutionStep`,
`oversampling`, `rasterizationDelayMillis`.

## Texturslots

Der Universal-UI-Batch kann bis zu 16 unterschiedliche Texturen gleichzeitig
binden. Eine Atlaspage belegt einen Slot, nicht jedes darin liegende Bild.
Weitere Texturen starten automatisch den nächsten Drawcall. Picking verwendet
dieselben Slots.

## `GLTexture`

```java
GLTexture texture = GLTexture.create(gl, bufferedImage);
g.drawTexture(texture, 20, 20, 200, 120);
texture.dispose(gl);
```

`create`, `bind` und `dispose` benötigen einen aktuellen GL-Kontext. Getter:
`getWidth`, `getHeight`, `isDisposed`. `bind(gl,unit)` ist Low-Level-Zugriff.
Breite/Höhe sind Texturpixel; `unit` ist ein dimensionsloser ganzzahliger
Texture-Unit-Index.
`GLTexture` wird nicht automatisch atlasiert und ist für bewusste manuelle
Integration; normale Bilder sollten `GLImage` verwenden.

## `GLFont`

```java
GLFont sans = new GLFont(GLFont.SANS_SERIF, GLFont.PLAIN, 18);
GLFont custom = GLFont.fromFile(fontPath, GLFont.BOLD, 20);
```

Familienkonstanten: `DIALOG`, `DIALOG_INPUT`, `MONOSPACED`, `SANS_SERIF`,
`SERIF`; Styles: `PLAIN`, `BOLD`, `ITALIC`. Konstruktoren akzeptieren Familie,
Style, AWT-Schriftgröße und optional einen eingeschränkten Zeichensatz oder eine
AWT-Font. Die Größe entspricht der `java.awt.Font`-Point-Size und wird als
logische Benutzerraumgröße gerendert, nicht als physische HiDPI-Pixelzahl.

`initialize(gl)` ist optional; der erste Renderzugriff initialisiert lazy.
Metriken: `getAscent`, `getDescent`, `getLineHeight`, `measureText`; außerdem
`getAwtFont`, `getFamily`, `getStyle`, `getSize`, `isInitialized`,
`isDisposed`. Eigene Fonts mit `dispose(gl)` freigeben. Die GUI-Hierarchie
bietet über `getGLFont(GLFontSpec)` einen verwalteten Cache.

`drawText` verwendet den Standard-Textbatch; `drawUIText` den UI-Batch. Beide
erwarten eine Baseline in lokalen Benutzerkoordinaten. Ascent, Descent,
Zeilenhöhe und `measureText` liefern ebenfalls logische Benutzerraumlängen.

## `GLPath2D`

`GLPath2D` ist eine mutable geometrische Beschreibung:

```java
GLPath2D path = new GLPath2D(GLPath2D.FillRule.NON_ZERO)
        .moveTo(10, 10)
        .lineTo(100, 10)
        .quadTo(140, 50, 100, 90)
        .cubicTo(70, 120, 30, 120, 10, 90)
        .closePath();
```

Methoden: `moveTo`, `lineTo`, `quadTo`, `cubicTo`, `closePath`,
`set/getFillRule`, `isEmpty`, `getSegmentCount`, `getCommandCount`, `reset`,
`copy`, `transformed`, `getBounds`, `fromSvgPath`.

Alle Pathkoordinaten und Bounds sind einheitenneutral und werden beim Zeichnen
als aktuelle lokale Benutzerkoordinaten interpretiert.

`NON_ZERO` berücksichtigt gerichtete Konturen; `EVEN_ODD` toggelt bei jeder
Kreuzung. `setFillRule` und `getFillRule` ändern/lesen die Regel. Bounds
enthalten exakte Kurvenextrema. `GLPath2D.Bounds` bietet die Record-Accessors
`x`, `y`, `width`, `height` sowie die abgeleiteten Kanten `maximumX` und
`maximumY`, jeweils in lokalen Benutzerkoordinaten. Der SVG-Pfadparser unterstützt
`M/L/H/V/C/S/Q/T/A/Z` absolut und relativ; Arcs werden kubische Beziers.

## `GLSvgDocument`

```java
GLSvgDocument svg = GLSvgDocument.loadResource("com/example/logo.svg");
GLSvgDocument fileSvg = GLSvgDocument.load(path);
svg.draw(g, 20, 20, 220, 120);
```

Zusätzlich: `parse(InputStream)`, `getViewBoxX`, `getViewBoxY`,
`getViewBoxWidth`, `getViewBoxHeight`, `getShapeCount`,
`isMonochrome`, `rasterize(width,height)`. `draw` verwendet normale
Contextbefehle und ist dadurch recordbar, batchbar und transformierbar. Für
häufig gezeichnete Icons ist `GLIcon` meist effizienter.

ViewBox-Werte sind SVG-Dokumenteinheiten. Die vier `draw`-Bounds sind lokale
Benutzerkoordinaten; `rasterize(width,height)` erwartet ganzzahlige
Ausgabepixel.

Der derzeitige Importer unterstützt Pfade, verschachtelte Gruppen, ViewBox,
affine SVG-Transforms, Fill-Regeln, Fill-Opacity, lineare Gradients im
User-Space und pathbasierte Clips. Ein sichtbares, nicht unterstütztes
SVG-Element führt ausdrücklich zu einem Fehler, statt unbemerkt in einer
unvollständigen Grafik zu fehlen.

## Lebensdauertabelle

| Ressource | GL-Kontext bei Erzeugung? | Freigabe |
|---|---|---|
| `GLImage` | Nein | Renderer verwaltet GPU-Auflösung |
| `GLIcon` | Nein | Renderer verwaltet Varianten |
| `GLPath2D`, `GLSvgDocument` | Nein | normale Java-Objekte |
| `GLFont` | Atlas lazy | eigener Font: `dispose(gl)` |
| `GLTexture` | Ja | `dispose(gl)` |
| `ShaderProgram` | Ja | `dispose(gl)` |
| `GLRenderTarget` | Lazy | `close()` |
