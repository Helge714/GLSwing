# Handbuch der Grafikpipeline

Dieses Handbuch dokumentiert die aktuelle öffentliche Rendering-API von
GLSwingNext. Es ist in kurze, abgegrenzte Kapitel aufgeteilt: zuerst die
vollständige API-Landkarte, dann Konfiguration, Zeichnen und anschließend die
fortgeschrittenen Pipelinestufen.

## Inhalt

- [Empfohlene Reihenfolge](#empfohlene-reihenfolge)
- [Grundbegriffe und Einheiten](#grundbegriffe-und-einheiten)
- [Vollständige GLContext2D-Übersicht](#vollständige-glcontext2d-übersicht)
- [Konstanten](#konstanten)
- [Welches Kapitel brauche ich?](#welches-kapitel-brauche-ich)

## Empfohlene Reihenfolge

1. [Context konfigurieren](graphics-pipeline/context-configuration.md)
2. [Zeichenbefehle und Renderpfade](graphics-pipeline/drawing.md)
3. [Recording und Laufzeitparameter](graphics-pipeline/recording.md)
4. [Eigene Shader und Uniforms](graphics-pipeline/shaders.md)
5. [Layer, Compositing und alle Effekte](graphics-pipeline/layers-effects.md)
6. [RenderTargets und Feedback](graphics-pipeline/render-targets.md)
7. [Bilder, Atlanten, Icons, Text und SVG](graphics-pipeline/resources.md)
8. [Picking und Mausziele](graphics-pipeline/picking.md)
9. [Batching, Performance und Lebensdauer](graphics-pipeline/performance.md)

## Grundbegriffe und Einheiten

| Begriff | Bedeutung |
|---|---|
| Immediate | Ein Befehl wird im aktuellen Frame direkt an den Renderer übergeben. |
| Recording | Derselbe Contextbefehl wird als wiederverwendbare Anweisung gespeichert. |
| Replay | `draw(GLRecording)` speist gespeicherte Anweisungen wieder in die aktuellen Batches ein. |
| Standardpipeline | Frei shaderbare Filled-, Texture- oder Text-Batches. |
| UI-Pipeline | Spezialisierter instanzierter Batch für Standardcontrols, Text und Icons. |
| Layer | Temporärer Offscreen-Inhalt, der als Gruppe zusammengesetzt und gefiltert wird. |
| RenderTarget | Persistenter, anwendungsverwalteter RGBA-FBO. |

Fast alle Zeichenbefehle funktionieren sowohl immediate als auch in einem
Recording. Ausnahmen sind RenderTarget-Updates und `drawRenderTarget`, weil ihr
Inhalt dynamisch ist. Details stehen in den Tabellen der jeweiligen Kapitel.

Wenn eine Methode nichts Abweichendes sagt, gelten folgende Einheiten:

| Wertart | Einheit |
|---|---|
| Geometrie, Position und Größe | Aktuelle lokale Benutzerkoordinaten; bei `TOP_LEFT_PIXELS` entspricht eine Einheit einem logischen Pixel |
| Explizite Screen-Werte | Logische Pixel; HiDPI-Skalierung in physische Framebufferpixel übernimmt der Renderer |
| Winkel | Radiant |
| Opacity, Alpha und normalisierte Anteile | Dimensionslos, üblicherweise `0..1` |
| Farben | `java.awt.Color`; Kanalwerte der Integer-Konstruktoren `0..255` |
| Zeitwerte mit `Millis` im Namen | Millisekunden |

Stroke-Werte hängen teilweise von `GLStroke.WidthSpace` ab; die genaue Tabelle
steht im [Konfigurationskapitel](graphics-pipeline/context-configuration.md#stroke).

## Vollständige `GLContext2D`-Übersicht

Die Übersicht listet jeden öffentlichen Methodennamen. Overloads derselben
Operation sind zusammengefasst; das Zeichenkapitel enthält deren vollständige
Varianten und Argumentregeln.

### Aufnahme und Replay

| Methoden | Zweck |
|---|---|
| `beginRecording`, `endRecording`, `cancelRecording`, `isRecording` | Recording-Lebenszyklus |
| `beginParameters`, `endParameters`, `withParameters` | Verschachtelbare Parameterregionen |
| `draw(GLRecording)` | Recording abspielen |
| `draw(GLRecording, GLRenderParameters)` | Recording parametrisiert abspielen |

### Allgemeiner Zustand, Clear und Bounds

| Methoden | Zweck |
|---|---|
| `setColor`, `getColor` | Aktuelle Standardfarbe |
| `clear()`, `clear(Color)` | Aktive Ausgaben löschen |
| `getScreenBounds` | Lokale Bounds in Screen-Bounds umrechnen |
| `intersectsRedrawBounds` | Gegen aktuelle Damage-Region testen |
| `flush` | Offene Batches ausdrücklich absenden |

### Transform, Projection und View

| Methoden | Zweck |
|---|---|
| `getTransform`, `setTransform`, `resetTransform` | Model-Transform |
| `translate` | Verschieben |
| `rotate(radians)`, `rotate(radians,x,y)` | Rotieren, optional um Anker |
| `scale(s)`, `scale(x,y)` | Gleichmäßig oder getrennt skalieren |
| `getProjection`, `setProjection(GLTransform)` | Projektionsmatrix direkt |
| `setProjection(Projection2D)` | Projektionsprovider anwenden |
| `setView(GLView2D)` | Komplette View-Projection anwenden |

### Stroke und Tessellierung

| Methoden | Zweck |
|---|---|
| `setStroke`, `getStroke` | Vollständiger `GLStroke` |
| `setStrokeWidth`, `getStrokeWidth` | Breite |
| `setStrokeCap`, `getStrokeCap` | Endkappen |
| `setStrokeJoin`, `getStrokeJoin` | Ecken |
| `setStrokeOffset`, `getStrokeOffset` | Mittiger/seitlicher Versatz |
| `setStrokeMiterLimit`, `getStrokeMiterLimit` | Miter-Grenze |
| `setStrokeCurveTolerance`, `getStrokeCurveTolerance` | Rundungsqualität des Strokes |
| `setStrokeWidthSpace`, `getStrokeWidthSpace` | `WORLD` oder `SCREEN` |
| `setStrokeDash`, `getStrokeDash`, `clearStrokeDash` | Dash-Muster |
| `setStrokeDashPhase`, `getStrokeDashPhase` | Dash-Startversatz |
| `setCurveTolerance`, `getCurveTolerance` | Qualität gefüllter Kurven und Pfade |

### Picking und Ausgaben

| Methoden | Zweck |
|---|---|
| `setOutputMask` | Farb- und Pickingausgabe gemeinsam ein-/ausschalten |
| `setColorRenderingEnabled`, `isColorRenderingEnabled` | Farbausgabe |
| `setPickingRenderingEnabled`, `isPickingRenderingEnabled` | Pickingausgabe |
| `setTexturePickAlphaThreshold`, `getTexturePickAlphaThreshold` | Alpha-Grenze für Textur-Picking |
| `setPickId`, `getPickId`, `clearPickId` | Numerisches Picking |
| `setMouseTarget`, `getMouseTarget`, `clearMouseTarget` | Direktes `GLMouseTarget` |

### Shader und Uniforms

| Methoden | Zweck |
|---|---|
| `setFilledShader`, `getFilledShader`, `useDefaultFilledShader` | Shader für Shapes und Strokes |
| `setTextureShader`, `getTextureShader`, `useDefaultTextureShader` | Shader für Texturen/Bilder/Targets |
| `setTextShader`, `getTextShader`, `useDefaultTextShader` | Shader für Standard-MSDF-Text |
| `setUIShader`, `getUIShader`, `useDefaultUIShader` | Vollständiger Shaderersatz für den Universal-UI-Batch |
| `setFilledUniform`, `setFilledUniformMatrix4` | Filled-Uniforms |
| `setTextureUniform`, `setTextureUniformMatrix4` | Texture-Uniforms |
| `setTextUniform`, `setTextUniformMatrix4` | Text-Uniforms |
| `setUIUniform`, `setUIUniformMatrix4` | Uniforms des eigenen UI-Shaders |

Jede `set...Uniform`-Familie besitzt Overloads für `float`, `vec2`, `vec3`,
`vec4` und `int`; die Matrixmethode erwartet 16 `float`-Werte.

### Clip und Layer

| Methoden | Zweck |
|---|---|
| `beginClip`, `endClip`, `getClipDepth` | Verschachtelbarer Scissor-Clip |
| `beginLayer()` | Layer mit automatisch ermittelten Inhaltsbounds beginnen |
| `beginLayer(x,y,w,h)` | Begrenzten Layer beginnen |
| `beginLayer(x,y,w,h,screenInset)` | Begrenzten Layer mit Screen-Inset beginnen |
| `endLayer()` | Normal zusammensetzen |
| `endLayer(opacity)` | Mit Gruppen-Opacity zusammensetzen |
| `endLayer(GLBlendMode)` | Mit Blendmodus zusammensetzen |
| `endLayer(GLComposite)` | Mit Opacity, Blendmodus und Effekten zusammensetzen |
| `getLayerDepth` | Offene Layer zählen |

### Persistente RenderTargets

| Methoden | Zweck |
|---|---|
| `beginRenderTarget`, `endRenderTarget`, `withRenderTarget` | In persistenten FBO rendern |
| `getRenderTargetDepth` | Offene Target-Pässe zählen |
| `drawRenderTarget` | Targetinhalt darstellen |

### Standard-Zeichenpipeline

| Methodenfamilie | Zielbatch |
|---|---|
| `drawLine`, `drawPolyline`, `drawPolygon`, `drawCubicBezier` | Filled |
| `drawPath`, `drawRect`, `drawRoundRect`, `drawEllipse`, `drawCircle` | Filled |
| `fillPath`, `fillTriangle`, `fillRect`, `fillPolygon` | Filled |
| `fillRoundRect`, `fillEllipse`, `fillCircle` | Filled |
| `drawTexture`, `drawImage`, `drawIcon`, `drawRenderTarget` | Texture |
| `drawText` | Text |

Strokes landen als tessellierte Dreiecke ebenfalls im Filled-Batch.

### Universal-UI-Pipeline

| Methodenfamilie | Inhalt |
|---|---|
| `fillUIRoundRect` | Analytische Fläche |
| `drawUIRoundRect` | Analytischer innerer Border |
| `fillAndDrawUIRoundRect` | Fläche und Border ohne AA-Bleeding |
| `drawUIRoundRectShadow` | Analytischer, nicht pickbarer Schatten |
| `drawUIText` | MSDF-Text |
| `drawUICheckMark`, `drawUIChevron` | Standardsymbole |
| `fillUIProgressRoundRect` | Fortschrittsverlauf |
| `fillUIVerticalGradientRoundRect` | Vollständiger vertikaler Verlauf |
| `addUIHitArea` | Nur Picking, keine Farbe |
| `drawUIImage`, `drawUIIcon` | RGBA-/MSDF-Atlasinhalt |

## Konstanten

| Konstante | Wert/Bedeutung |
|---|---|
| `NO_PICK_ID` | `0`, kein numerisches Ziel |
| `DEFAULT_CLEAR_COLOR` | Dunkler Standardhintergrund `(15,20,31)` |
| `DEFAULT_TEXTURE_PICK_ALPHA_THRESHOLD` | `0.1f` |

## Welches Kapitel brauche ich?

- Eine Form zeichnen: [Zeichenbefehle](graphics-pipeline/drawing.md)
- Kamera, Stroke oder Projection setzen: [Konfiguration](graphics-pipeline/context-configuration.md)
- Unveränderte Geometrie beschleunigen: [Recording](graphics-pipeline/recording.md)
- Eigenes GLSL: [Shader](graphics-pipeline/shaders.md)
- Blur, Schatten, Outline oder Gruppenalpha: [Layer und Effekte](graphics-pipeline/layers-effects.md)
- Game of Life oder Offscreen-Canvas: [RenderTargets](graphics-pipeline/render-targets.md)
- Bilder oder SVG-Icons: [Ressourcen](graphics-pipeline/resources.md)
- Mausinteraktion: [Picking](graphics-pipeline/picking.md)
