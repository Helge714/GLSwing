# Eigene Shader und Uniforms

[Zur API-Landkarte](../graphics-pipeline.md) · [Recording](recording.md) · [Weiter: Layer und Effekte](layers-effects.md)

GLSwing trennt Filled-, Texture-, Text-, UI- und Layer-Shader, weil ihre
Vertexlayouts und verwalteten Uniforms verschieden sind. Der Renderer validiert
den Vertrag beim ersten Einsatz und meldet fehlende Attribute, falsche
Locations oder inaktive Pflichtuniforms als Exception.

## Inhalt

- [Erzeugen und freigeben](#erzeugen-und-freigeben)
- [Shaderverträge](#shaderverträge)
- [Standardpipeline-Beispiele](#filled-beispiel)
- [Eigener UI-Shader](#eigener-ui-shader)
- [Context-Uniforms](#context-uniforms)
- [Shader in Recordings](#shader-in-recordings)
- [Eigene Layer-Shader](#eigener-layer-shader)
- [Alpha und Low-Level-Zugriff](#premultiplied-alpha)

## Erzeugen und freigeben

```java
ShaderProgram shader = ShaderProgram.fromSource(gl, vertexSource, fragmentSource);
ShaderProgram resourceShader = ShaderProgram.fromResources(
        gl, "/com/example/my.vert", "/com/example/my.frag");
```

Beide Aufrufe sowie `dispose(gl)` benötigen einen aktuellen GL-Kontext. Sie
gehören in `initialize(GL3)`/`disposeResources(GL3)` des verwendeten Hosts.
`isDisposed()` zeigt die Lebensdauer. `fromBuiltInResources` lädt gezielt aus
dem internen GLSwing-Shaderordner und ist hauptsächlich für Bibliothekscode.

## Shaderverträge

| Pipeline | Attribute | Verwaltete Pflichtuniforms |
|---|---|---|
| Filled | `aPosition` Location 0, `aColor` Location 1 | `uProjection` |
| Texture | `aPosition` 0, `aTexCoord` 1, `aColor` 2 | `uProjection`, `uTexture` |
| Text | wie Texture | `uProjection`, `uTexture`, `uMsdfPixelRange` |
| UI | eingebauter instanzierter Vertexshader; Locations `0..7`, `9`, `10` wie unten | `uProjection`, `uDeviceScale`, alle `uTextures[0..15]` |
| Layer | interner Fullscreen-Vertexshader | `uTexture`, `uOpacity` |

Attribute und Pflichtuniforms müssen aktiv sein. Ein GLSL-Compiler darf
unbenutzte Werte wegoptimieren; bloßes Deklarieren ohne Benutzung reicht daher
nicht immer.

## Filled-Beispiel

```java
private static final String FILLED_VERTEX = """
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

private static final String FILLED_FRAGMENT = """
        #version 330 core
        in vec4 vColor;
        uniform float uGamma;
        out vec4 fragColor;
        void main() {
            fragColor = vec4(pow(vColor.rgb, vec3(1.0 / uGamma)), vColor.a);
        }
        """;

// initialize(GL3 gl)
filledShader = ShaderProgram.fromSource(gl, FILLED_VERTEX, FILLED_FRAGMENT);

// render(GLContext2D g)
g.setFilledShader(filledShader);
g.setFilledUniform("uGamma", 2.2f);
g.fillRect(20, 20, 180, 100, new Color(70, 150, 235));
g.useDefaultFilledShader();

// disposeResources(GL3 gl)
filledShader.dispose(gl);
```

Filled-Shader wirken auf normale Fills, Strokes und Paths, nicht auf die
analytischen `...UI...`-Methoden.

## Texture-Shader

Ein Texture-Shader erhält Texturefarbe multipliziert mit Vertex-Tint. Ein
minimaler Vertrag:

```glsl
#version 330 core
in vec2 vTexCoord;
in vec4 vColor;
uniform sampler2D uTexture;
uniform float uGamma;
out vec4 fragColor;

void main() {
    vec4 sampled = texture(uTexture, vTexCoord);
    fragColor = vec4(pow(sampled.rgb, vec3(1.0 / uGamma)), sampled.a) * vColor;
}
```

Der passende Vertexshader verwendet die Texture-Attributtabelle oben.
`setTextureShader` wirkt auf `drawTexture`, `drawImage` und
`drawIcon` sowie `drawRenderTarget`, nicht auf `drawUIImage` oder
`drawUIIcon`.

## Text-Shader

Text liest ein Multi-Channel Signed Distance Field. Ein eigener Shader muss
`uMsdfPixelRange` benutzen und Coverage aus dem Median der drei Kanäle bilden.
Die vollständige Referenzimplementierung steht in `CustomShaderDemo`.

```glsl
vec3 msdf = texture(uTexture, vTexCoord).rgb;
float distance = max(min(msdf.r, msdf.g), min(max(msdf.r, msdf.g), msdf.b)) - 0.5;
vec2 unitRange = vec2(uMsdfPixelRange) / vec2(textureSize(uTexture, 0));
vec2 screenSize = vec2(1.0) / max(fwidth(vTexCoord), vec2(0.000001));
float pixelRange = max(0.5 * dot(unitRange, screenSize), 1.0);
float coverage = clamp(distance * pixelRange + 0.5, 0.0, 1.0);
fragColor = vec4(vColor.rgb, vColor.a * coverage);
```

`setTextShader` betrifft `drawText`, nicht `drawUIText`.

## Eigener UI-Shader

```java
// Vollständige eigene Variante des mitgelieferten ui_quad.frag:
uiShader = ShaderProgram.fromUIFragmentSource(gl, myUIFragmentSource);

g.setUIShader(uiShader);
g.setUIUniform("uAccentStrength", 0.35f);
drawMyControls(g);
g.useDefaultUIShader();
```

`fromUIFragmentSource` kombiniert den eigenen Fragmentshader mit dem
eingebauten instanzierten UI-Vertexshader. Der Fragmentshader erhält:

| Eingang | GLSL-Typ | Inhalt |
|---|---|---|
| `vLocal` | `vec2` | Lokale Position innerhalb des Instanzquads |
| `vTexCoord` | `vec2` | Atlas-/Textur-UV |
| `vColor` | `vec4` | Primärfarbe |
| `vParameters` | `flat vec4` | Modus und modusspezifische Werte |
| `vSize` | `flat vec2` | Lokale Instanzgröße |
| `vSecondaryColor` | `flat vec4` | Zweitfarbe, Eckradien oder Schattenbounds je Modus |
| `vTextureSlot` | `flat int` | Index in `uTextures` |

Der vollständige Vertexvertrag verwendet `aCorner`, `aP00`, `aP10`, `aP01`,
`aUvRect`, `aColor`, `aParameters`, `aSize`, `aSecondaryColor` und
`aTextureSlot` an den Locations `0,1,2,3,4,5,6,7,9,10`. Verwaltet werden die
Projektionsmatrix `uProjection`, der logische-zu-physische Faktor
`uDeviceScale` sowie alle 16 Sampler `uTextures[0]` bis `uTextures[15]`.
Sämtliche Werte müssen im gelinkten Programm **aktiv** bleiben; andernfalls
weist `setUIShader` den Shader mit einer verständlichen Vertragsverletzung
zurück.

Ein eigener UI-Shader ist ein kompletter Ersatz. Er muss alle Modi des
mitgelieferten `ui_quad.frag` behandeln: runde und asymmetrische Flächen und
Border, Fill+Border, MSDF-Text, RGBA-Texturen, Checkmark, Chevron,
Fortschrittsverläufe, Hit-Area und analytischen Schatten. Der praktikable
Startpunkt ist daher eine Kopie von
`resources/com/vingame/glswing/shaders/ui_quad.frag`, in die nur der gewünschte
Effekt eingebaut wird. Das interne Picking bleibt unabhängig davon erhalten.

Der Shaderwechsel selbst erzeugt nur dann einen Flush, wenn gerade UI-Instanzen
offen sind und sich das Programm wirklich ändert. Wiederholtes Setzen derselben
Instanz und desselben Uniformwerts ist günstig. Unterschiedliche Uniformwerte
pro Control bilden hingegen jeweils eine Batchgrenze.

### Was mit einem Materialsystem gemeint ist

Ein Material würde Shader und dessen Parameter als wiederverwendbaren,
möglichst unveränderlichen Stilwert bündeln, etwa
`GLUIMaterial(shader, uniforms)`. Viele Controls könnten dasselbe Material
referenzieren; der Renderer sortiert nicht um, könnte aber benachbarte gleiche
Materialien ohne Wechsel gemeinsam zeichnen. Eine fortgeschrittene Variante
würde kleine Materialparameter als Instanzdaten oder über einen GPU-Puffer
liefern, sodass sogar verschiedene Looks mit einem gemeinsamen Shader im
selben Drawcall bleiben.

Eine solche Abstraktion existiert derzeit bewusst noch nicht. Die jetzige
`setUIShader`-/`setUIUniform`-API ist der direkte, flexible Grundbaustein. Für
einzelne Control- oder Gruppeneffekte ohne Nachbau des kompletten UI-Shaders
bleibt ein Layer mit `GLShaderEffect` die einfachere Wahl.

## Context-Uniforms

Jede Pipeline bietet:

```java
g.setFilledUniform("uValue", 1.0f);
g.setFilledUniform("uOffset", 2.0f, 3.0f);
g.setFilledUniform("uColor", 1.0f, 0.5f, 0.2f);
g.setFilledUniform("uRect", 1f, 2f, 3f, 4f);
g.setFilledUniform("uMode", 2);
g.setFilledUniformMatrix4("uExtraMatrix", matrix16);
```

Ersetze `Filled` durch `Texture`, `Text` oder `UI` für die anderen Batches. Uniforms
werden typgeprüft: `float`, `vec2`, `vec3`, `vec4`, `int`/`bool` und `mat4`.
Name muss aktiv sein und zum Typ passen.

Eigene Uniforms besitzen keine von GLSwing vorgegebene Einheit; ihr Shader
definiert die Semantik. Im Beispiel ist `uGamma` dimensionslos. Soll eine
Uniform auf Fenster- oder Layergrößen reagieren, sollten die unten
aufgeführten verwalteten Auflösungsuniforms verwendet werden, damit physische
und logische Pixel nicht unbemerkt vermischt werden.

Shader- und Uniformänderungen flushen den betroffenen Batch, damit bereits
gesammelte Vertices ihren alten Wert behalten. Dieselbe Shaderinstanz erneut zu
setzen verursacht keinen Wechsel. `set...Shader(null)` entspricht
`useDefault...Shader()`.

## Shader in Recordings

Shaderwechsel und Uniformwerte werden als Replaybefehle gespeichert. Das
Recording besitzt den Shader nicht; er darf vor dem letzten Replay nicht
disposed werden. Eine häufig pro Objekt wechselnde Uniform kann Batching trotz
Recording begrenzen.

## Eigener Layer-Shader

```java
ShaderProgram effectProgram = ShaderProgram.fromLayerFragmentSource(gl, """
        #version 330 core
        in vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform float uOpacity;
        uniform float uAmount;
        out vec4 fragColor;
        void main() {
            vec4 c = texture(uTexture, vTexCoord);
            c.rgb = mix(c.rgb, vec3(1.0) - c.rgb, uAmount);
            fragColor = c * uOpacity;
        }
        """);

GLShaderEffect effect = GLLayerEffects.custom(effectProgram)
        .withUniform("uAmount", 0.35f)
        .withPadding(0);
```

Der Renderer verwaltet bei vorhandener Deklaration zusätzlich:

| Uniform | Inhalt |
|---|---|
| `uTexture` | Eingabetextur, dimensionsloser Texture-Unit-Index `0` |
| `uOpacity` | Dimensionsloser Composite-Faktor `0..1` im letzten Pass, sonst `1` |
| `uResolution` | Physische FBO-Größe in Framebufferpixeln |
| `uTexelSize` | Normierte UV-Schrittweite pro physischem Pixel (`1 / uResolution`) |
| `uLogicalResolution` | Logische Zielgröße in Screenpixeln |
| `uDeviceScale` | Dimensionsloses Verhältnis physische/logische Pixel je Achse |

Diese Namen dürfen nicht als Useruniform gesetzt werden. `withUniform` bietet
dieselben Typen wie der Context. `withPadding` wird in logischen Screenpixeln
angegeben und erklärt, wie weit der Shader außerhalb der ursprünglichen
Layerbounds sichtbare Pixel erzeugen kann; ein zu kleiner Wert schneidet den
Effekt ab.

## Premultiplied Alpha

Layer-Shader arbeiten mit premultiplied Alpha. RGB enthält also bereits den
Alphafaktor. Eigene Effekte müssen dieses Modell erhalten, insbesondere beim
Erzeugen transparenter Pixel. Ein gewöhnlicher Texture-/Textshader sollte
hingegen die Semantik des jeweiligen Defaultshaders nachbilden.

## Direkte `ShaderProgram`-Methoden

`use`, `setFloat`, `setVector2`, `setVector3`, `setVector4`, `setInteger` und
`setMatrix4` sind
Low-Level-Operationen für interne oder bewusste GL-Integration. Im normalen
`GLContext2D`-Rendering sollen die Contextuniformmethoden verwendet werden,
damit Flush, Validierung und Recording korrekt bleiben. `hasUniform` prüft, ob
eine aktive Uniform existiert.
