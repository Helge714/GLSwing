# Zeichenbefehle und Renderpfade

[Zur API-Landkarte](../graphics-pipeline.md) · [Konfiguration](context-configuration.md) · [Weiter: Recording](recording.md)

Dieses Kapitel trennt vier Fragen ausdrücklich:

1. Wird immediate gerendert oder aufgenommen?
2. Läuft der Befehl über die frei shaderbare Standardpipeline oder den
   spezialisierten UI-Batch?
3. Welcher Batchtyp wird verwendet?
4. Welche Overloads und Einheiten gelten?

## Inhalt

- [Einheiten](#einheiten)
- [Immediate, Recording und Replay](#immediate-recording-und-replay)
- [Overload-Konvention](#overload-konvention)
- [Nicht-UI-Pipelines](#nicht-ui-filled-batch)
- [2.5D-Sprite-Pipeline](#25d-sprite-pipeline)
- [UI-Pipeline](#ui-pipeline)
- [Pfade](#pfade)
- [Zeichenreihenfolge und Entscheidungshilfe](#zeichenreihenfolge-und-überlappung)

## Einheiten

Sofern eine Signatur nichts anderes sagt, sind X/Y, Breite, Höhe, Radius,
Bezier-Kontrollpunkte und Pfadkoordinaten lokale Benutzerkoordinaten. Unter
`TOP_LEFT_PIXELS` entspricht eine Einheit einem logischen Pixel. Winkel werden
in Radiant angegeben, normalisierte Werte wie `progress` und
Interpolations-`amount` liegen in `0..1`.

Ausnahmen sind ausdrücklich benannt: Textur-Quellrechtecke verwenden
Texturpixel, Text-Y ist eine Baseline, Stroke- und UI-Borderbreiten folgen dem
jeweiligen `WidthSpace`. Farben sind `java.awt.Color`-Werte mit Integerkanälen
von `0..255`.

## Immediate, Recording und Replay

| Modus | Was ein Zeichenbefehl tut |
|---|---|
| Kein Recording aktiv | Geometrie wird direkt in den offenen Rendererbatch eingefügt. |
| Zwischen `beginRecording`/`endRecording` | Normalisierte Anweisung/Geometrie wird gespeichert, nicht sichtbar gezeichnet. |
| `draw(recording)` | Gespeicherte Anweisungen fließen in die aktuellen Batches ein. |
| Zwischen `beginSpriteBuffer`/`endSpriteBuffer` | Ausschließlich Sprite-/Materialinstanzen werden als statischer Puffer aufgenommen. |
| `draw(spriteBuffer)` | GPU-residente Instanzen werden relativ zum aktuellen Modeltransform gezeichnet. |

Alle unten genannten Zeichenbefehle sind recordbar, außer
`drawRenderTarget(...)`. `draw(GLRecording)` selbst ist während einer aktiven
Aufnahme nicht erlaubt.
Die engere Sprite-Buffer-Aufnahme ist im
[Recording-Kapitel](recording.md#gpu-residente-sprite-puffer) beschrieben.

## Overload-Konvention

Bei Standardformen wiederholen sich diese Varianten:

- ohne `Color`: aktuelle Contextfarbe,
- mit `Color`: Farbe nur für diesen Befehl,
- mit `int pickId`: numerisches Ziel nur für diesen Befehl,
- mit `GLMouseTarget`: direktes Ziel nur für diesen Befehl,
- mit `Color[]`: interpolierte Vertex-/Punktfarben.

Ein explizites Pickingargument ersetzt für den Befehl den Contextzustand.
Pick-ID und MouseTarget werden nie gleichzeitig übergeben. Arrays mit Punkten
sind immer `x0,y0,x1,y1,...` in lokalen Benutzerkoordinaten. `Color[]`-Arrays
enthalten genau eine Farbe pro zugehörigem Punkt/Vertex.

## Nicht-UI: Filled-Batch

Diese Methoden erzeugen normale Filled-Geometrie. Eigene Filled-Shader wirken;
Strokes werden auf der CPU in Dreiecke umgewandelt.

### Linien

| Methode | Parameter/Varianten | Verhalten |
|---|---|---|
| `drawLine(x1,y1,x2,y2,...)` | Contextfarbe, `Color`, Pick-ID oder MouseTarget | Ein Segment |
| `drawLine(x1,y1,color1,x2,y2,color2,...)` | optional Picking | Farbverlauf zwischen Endpunkten |
| `drawPolyline(points,...)` | `Color` oder `Color[]`, optional Picking | Offen; letzter Punkt wird nicht mit erstem verbunden |
| `drawPolygon(points,...)` | `Color` oder `Color[]`, optional Picking | Geschlossen |

Alle verwenden den aktuellen `GLStroke`, einschließlich Dash, Cap, Join,
Offset und WidthSpace.

### Kubische Beziers

```java
g.drawCubicBezier(
        x0, y0, control1X, control1Y,
        control2X, control2Y, x1, y1,
        startColor, endColor, GLColorInterpolation.OKLAB);
```

Overloads bieten Contextfarbe, eine Farbe, Start-/Endfarbe, optional
`GLColorInterpolator` und jeweils Pick-ID oder MouseTarget. Ohne explizite
Interpolation gilt `SRGB`. Eingebaut sind `SRGB`, `LINEAR_RGB`,
`HSV_SHORTEST`, `OKLAB`, `OKLCH_SHORTEST`.

Alle Modi implementieren `GLColorInterpolator.interpolate(start,end,amount)`.
`amount` ist ein dimensionsloser Anteil von `0` (Startfarbe) bis `1`
(Endfarbe).
`GLColorInterpolation.parse(String)` liest einen Modusnamen tolerant, während
`valueOf` die normale Enum-Semantik besitzt. Eine eigene Interpolation ist ein
Lambda `(start, end, amount) -> Color`.

### Umrisse

| Methode | Besonderheiten |
|---|---|
| `drawRect(x,y,w,h,...)` | Optional vier Farben: oben links, oben rechts, unten rechts, unten links |
| `drawRoundRect(x,y,w,h,radius,...)` | Einheitlicher Radius |
| `drawRoundRect(x,y,w,h,GLCornerRadii,...)` | Eigener Radius pro Ecke |
| `drawEllipse(x,y,w,h,...)` | Bounds beschreiben die Ellipse |
| `drawCircle(centerX,centerY,radius,...)` | Radius muss nichtnegativ sein |
| `drawPath(path,...)` | Alle Subpaths werden gestrokt |

Die `GLCornerRadii`-Reihenfolge ist oben links, oben rechts, unten rechts,
unten links. Die Record-Accessors heißen `topLeft`, `topRight`, `bottomRight`
und `bottomLeft`; `GLCornerRadii.all(radius)` erzeugt vier gleiche Werte. Alle
Radien verwenden lokale Benutzerkoordinaten.

### Fills

| Methode | Besonderheiten |
|---|---|
| `fillTriangle(...)` | Eine Farbe oder eine pro Vertex; optional Picking |
| `fillRect(x,y,w,h,...)` | Optimierter Quadpfad |
| `fillPolygon(points,...)` | Konkave Polygone; `Color` oder `Color[]` |
| `fillRoundRect(...)` | Radius oder `GLCornerRadii` |
| `fillEllipse(...)`, `fillCircle(...)` | Kurvenqualität über `curveTolerance` |
| `fillPath(path,Color)` | Pfad mit einer Farbe |
| `fillPath(path,GLPaint2D)` | Farbe wird an lokalen Tessellierungspunkten ausgewertet |

```java
GLPaint2D stripes = (x, y) -> ((int) (x / 16) & 1) == 0
        ? new Color(80, 180, 255)
        : new Color(35, 90, 180);
g.fillPath(path, stripes);
```

`GLPaint2D` ist CPU-seitig und erzeugt Vertexfarben; es ist kein frei pro Pixel
ausgewerteter GPU-Paint. Für pixelgenaue prozedurale Fills dient ein Shader.
Die funktionale Methode heißt `colorAt(x,y)`; `GLPaint2D.solid(color)` ist die
konstante Fabrik.

## Nicht-UI: Texture-Batch

| Methode | Varianten | Recordbar |
|---|---|---|
| `drawTexture(texture,x,y)` | Originalgröße; Tint/Pick-ID/MouseTarget möglich | Ja |
| `drawTexture(texture,x,y,w,h)` | Skalierte Zielbounds; Tint/Picking möglich | Ja |
| `drawTexture(texture,sourceX,sourceY,sourceW,sourceH,destX,destY,destW,destH,...)` | Teilbereich; Quellwerte in Texturpixeln | Ja |
| `drawImage(image,x,y)` | Originalgröße | Ja |
| `drawImage(image,x,y,w,h[,tint])` | High-Level-Bild | Ja |
| `drawIcon(icon,x,y,w,h[,color])` | Icon als adaptive RGBA-Textur; frei shaderbar | Ja |
| `drawRenderTarget(target,x,y)` | Targetgröße | Nein |
| `drawRenderTarget(target,x,y,w,h[,tint/picking])` | Dynamischer Targetinhalt | Nein |

Negative Zielbreite/-höhe spiegeln ein Bild; die Quellregion von
`drawTexture` muss positiv und vollständig innerhalb der Textur liegen.
Quell-X/Y/Breite/Höhe sind Texturpixel; Ziel-X/Y/Breite/Höhe sind lokale
Benutzerkoordinaten.
`drawImage` löst `GLImage` automatisch als Atlasregion oder Standalone-Textur
auf. `drawIcon` wählt auch bei einem vektorbasierten Icon eine passende
RGBA-Rasterstufe. Ein MSDF-Icon erzeugt für diesen Pfad bei Bedarf lazy eine
RGBA-Repräsentation, weil ein beliebiger Texture-Shader die MSDF-Distanz nicht
automatisch interpretieren kann. Der aktive TextureShader wirkt auf alle
Methoden dieser Tabelle.

## 2.5D-Sprite-Pipeline

| Methode | Varianten | Recordbar |
|---|---|---|
| `drawSprite(sprite,x,y)` | Natürliche Größe, aktuelle Context-Tiefe | Ja |
| `drawSprite(sprite,x,y,w,h[,tint[,depth]])` | Skaliert, optional Tint und Basis-Tiefe | Ja |
| `drawMaterial(material,x,y,w,h)` | Direktes Material, aktuelle Context-Tiefe | Ja |
| `drawMaterial(material,x,y,w,h,tint,depth)` | Vollständig explizit | Ja |

Bounds sind lokale Benutzerkoordinaten, Tint ist `java.awt.Color` und Depth
dimensionslos `0..1`. Die Pipeline verwendet eigene Color-/Depth-/Normal-UVs
und bleibt von Texture- und UI-Batch getrennt. Details zu Materialbuilder,
Alpha-Modi, Licht und Picking: [Depth und 2.5D-Materialien](depth-materials.md).

## Nicht-UI: Text-Batch

```java
g.drawText(font, "Baseline", 30, 80, Color.WHITE);
```

`drawText(GLFont,String,x,baselineY,...)` besitzt Varianten mit Contextfarbe,
`Color`, Pick-ID und MouseTarget. Y ist immer die Baseline wie bei
`Graphics2D.drawString`, nicht die obere Textkante. Text verwendet MSDF und den
separaten, frei austauschbaren Textshader.

## UI-Pipeline

Der Universal-UI-Batch ist nicht bloß eine Abkürzung für die Standardformen.
Er verwendet instanzierte analytische Quads und kann Standardflächen,
MSDF-Glyphen und bis zu 16 Texturen in einem Drawcall kombinieren. Eigene
Filled-/Texture-/Textshader wirken hier bewusst nicht. Mit `setUIShader` kann
der komplette UI-Shader ersetzt werden; dieser fortgeschrittene Weg muss alle
UI-Modi nachbilden und ist im [Shaderkapitel](shaders.md#eigener-ui-shader)
beschrieben.

Alle UI-Methoden sind recordbar. Picking wird über den aktuellen
`setPickId`-/`setMouseTarget`-Zustand gesteuert; sie besitzen keine zusätzlichen
Pickingoverloads.

Das ist bewusst zustandsbasiert: Ein Control setzt sein Ziel einmal und kann
danach Fläche, Border, Text, Icon und eine größere Hit-Area demselben Ziel
zuordnen. Pickingoverloads an jeder spezialisierten UI-Methode würden diese
Zustandsgruppe nur duplizieren und die API stark verbreitern. Für genau einen
Befehl kann der Zustand direkt davor gesetzt und danach mit `clearPickId` oder
`clearMouseTarget` beendet werden.

### Flächen und Border

| Methode | Verhalten |
|---|---|
| `fillUIRoundRect(...,radius,color)` | Analytisch antialiaste Fläche |
| `fillUIRoundRect(...,GLCornerRadii,color)` | Unterschiedliche Eckradien |
| `drawUIRoundRect(...,radius,strokeWidth,color)` | Innerer World-Space-Border |
| `drawUIRoundRect(...,radius,strokeWidth,color,widthSpace)` | World/Screen wählbar |
| `drawUIRoundRect(...,radii,strokeWidth,color,widthSpace)` | Eckradien + WidthSpace |
| `fillAndDrawUIRoundRect(...)` | Fill und innerer Border in einer Instanz, kein AA-Bleeding |

`fillAndDrawUIRoundRect` ist für Standardcontrols meist besser als getrenntes
Fill und Border. `SCREEN` hält die Borderdicke beim Zoom konstant.
Radien und World-Space-Borderbreiten sind lokale Benutzerkoordinaten;
Screen-Space-Borderbreiten sind logische Pixel.

### Verläufe und Symbole

| Methode | Verhalten |
|---|---|
| `fillUIProgressRoundRect(...,radius,progress,top,bottom)` | Verlauf bis `progress` geclippt |
| `fillUIProgressRoundRect(...,topRadius,bottomRadius,progress,...)` | Getrennte obere/untere Rundung |
| `fillUIVerticalGradientRoundRect(...)` | Fortschritt immer `1` |
| `drawUICheckMark(...)` | Analytischer Haken |
| `drawUIChevron(...,pointsRight,strokeWidth,color,widthSpace)` | Linker/rechter Chevron |

`progress` ist ein dimensionsloser Anteil in `0..1`. Chevron-Strichbreite und
sonstige UI-Borderbreiten folgen dem angegebenen `WidthSpace`.

### Schatten, Text und Bilder

| Methode | Verhalten |
|---|---|
| `drawUIRoundRectShadow(...)` | Analytischer weicher Schatten, absichtlich nicht pickbar |
| `drawUIText(font,text,x,baselineY,color)` | MSDF-Glyphen im UI-Batch |
| `drawUIImage(image,x,y,w,h[,tint])` | `GLImage`/Atlasregion im UI-Batch |
| `drawUIIcon(icon,x,y,w,h[,color])` | MSDF- oder RGBA-Icon, adaptive Wahl beim Replay |
| `addUIHitArea(x,y,w,h)` | Schreibt nur Picking, nicht Farbe |

Der analytische UI-Schatten ist deutlich günstiger als ein Layer-Blur, kann
aber nur das gerundete Rechteckmodell darstellen.

`addUIHitArea` ist keine globale Ausgabekonfiguration. Die Methode fügt eine
unsichtbare rechteckige UI-Instanz hinzu, die nur im Picking-FBO erscheint.
`setOutputMask(color,picking)` schaltet dagegen für **alle** nachfolgenden
Zeichenbefehle die beiden Ausgaben ein oder aus.

## Pfade

```java
GLPath2D path = new GLPath2D(GLPath2D.FillRule.NON_ZERO)
        .moveTo(10, 10)
        .lineTo(100, 10)
        .quadTo(140, 50, 100, 90)
        .cubicTo(70, 120, 30, 120, 10, 90)
        .closePath();

GLPath2D badge = GLPath2D.build(p -> {
    p.append(GLShape2D.roundedRectangle(0, 0, 80, 30, 6));
    p.add(GLShape2D.circle(68, 15, 5)); // Alias für append
});

GLPath2D combined = GLPath2D.combine(badge, GLShape2D.circle(90, 15, 8));
```

`GLPath2D` kann mehrere Subpaths enthalten. `EVEN_ODD` toggelt innen/außen bei
jeder Kontur; `NON_ZERO` berücksichtigt die Richtung. Weitere Operationen:
`setFillRule`, `getFillRule`, `copy`, `reset`, `transformed`, `getBounds`,
`getSegmentCount`, `getCommandCount`, `fromSvgPath`, `toPath`, `build`,
`append`/`add` und `combine`.

`build(callback)` stellt dem Callback bewusst nur einen `GLPath2D` bereit –
keinen rendernden `GLContext2D`. `append(shape)` fügt die Konturen eines
`GLShape2D` an; `combine(shapes...)` erzeugt dafür einen neuen Path. Das ist
eine geometrische Kombination anhand der Fill-Rule, keine boolesche Union oder
Subtraktion. Ein `g.toPath(() -> g.fill...())` gibt es absichtlich nicht:
Contextbefehle enthalten neben Geometrie auch Farbe, Picking, Shaderwechsel,
Texturen und andere Renderzustände, die ein Path nicht verlustfrei abbilden
kann.

Für einzelne Standardformen muss kein Pfad von Hand aufgebaut werden:

```java
GLPath2D ellipse = GLPath2D.ellipse(20, 30, 120, 60);
GLPath2D polygon = GLShape2D.polygon(new float[] {
        0, 30, 25, 0, 80, 15, 60, 55
}).toPath();
```

`GLShape2D` ist das leichte gemeinsame Forminterface mit `toPath()` und den
Fabriken `rectangle`, `roundedRectangle`, `ellipse`, `circle` und `polygon`.
Die Konvertierung liefert immer eine unabhängige mutable Kopie. Dieselben
Fabriken auf `GLPath2D` geben direkt einen Path zurück. Formen können deshalb
einheitlich gezeichnet, mit `GLIcon.fromPath(...)` in Icons umgewandelt oder
mit `GLLayerEffects.pathClip(...)` als freie Layerclips verwendet werden.

Paths laufen über den normalen Filled-Batch und sind daher nicht direkt
UI-batchfähig. Für ein wiederverwendetes einfarbiges UI-Symbol dient
`GLIcon.fromPath(path)`; anschließend zeichnet `drawUIIcon` das erzeugte MSDF
im Universal-UI-Batch. Mit `drawIcon` kann dasselbe Icon stattdessen als
frei shaderbare RGBA-Textur dargestellt werden.

## Zeichenreihenfolge und Überlappung

GLSwing bewahrt die sichtbare Reihenfolge. Wenn ein Wechsel zwischen Filled,
Text und Texture nötig ist, wird der vorherige Batch geflusht, bevor der nächste
Typ darüber zeichnet. Deshalb können viele Controls mit abwechselndem Fill und
Text mehr Flushes erzeugen; die UI-Pipeline existiert genau für diesen Fall.

## Entscheidungshilfe

| Ziel | Empfohlener Befehl |
|---|---|
| Frei shaderbare Form | Standard-`fill...`/`draw...` |
| Standardcontrol | `...UI...` |
| Normales Bild | `GLImage` + `drawImage` |
| Bild im Controlbatch | `drawUIImage` |
| Frei shaderbares Icon | `GLIcon` + `drawIcon` |
| Icon im Controlbatch | `GLIcon` + `drawUIIcon` |
| Einfarbiger Path als UI-Icon | `GLIcon.fromPath(path)` + `drawUIIcon` |
| Manuelle GL-Textur | `drawTexture` |
| Scharfer frei shaderbarer Text | `drawText` |
| Text zusammen mit Controls | `drawUIText` |
| Gruppeneffekt | Layer |
| Wiederverwendete komplexe Geometrie | Recording |
