# Eigene Shader und Uniforms

[Zur API-Landkarte](../graphics-pipeline.md) · [Recording](recording.md) · [Weiter: Layer und Effekte](layers-effects.md)

GLSwing trennt Filled-, Texture-, Text- und Layer-Shader, weil ihre
Vertexlayouts und verwalteten Uniforms verschieden sind. Der Renderer validiert
den Vertrag beim ersten Einsatz und meldet fehlende Attribute, falsche
Locations oder inaktive Pflichtuniforms als Exception.

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
`drawRenderTarget`, nicht auf `drawUIImage` oder `drawIcon`.

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

## Contextuniforms

Jede Pipeline bietet:

```java
g.setFilledUniform("uValue", 1.0f);
g.setFilledUniform("uOffset", 2.0f, 3.0f);
g.setFilledUniform("uColor", 1.0f, 0.5f, 0.2f);
g.setFilledUniform("uRect", 1f, 2f, 3f, 4f);
g.setFilledUniform("uMode", 2);
g.setFilledUniformMatrix4("uExtraMatrix", matrix16);
```

Ersetze `Filled` durch `Texture` oder `Text` für die anderen Batches. Uniforms
werden typgeprüft: `float`, `vec2`, `vec3`, `vec4`, `int`/`bool` und `mat4`.
Name muss aktiv sein und zum Typ passen.

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
| `uTexture` | Eingabetextur, Texture Unit 0 |
| `uOpacity` | Composite-Opacity im letzten Pass, sonst 1 |
| `uResolution` | Physische FBO-Pixelgröße |
| `uTexelSize` | Kehrwert der physischen Größe |
| `uLogicalResolution` | Logische Zielgröße |
| `uDeviceScale` | Physische/logische Skalierung je Achse |

Diese Namen dürfen nicht als Useruniform gesetzt werden. `withUniform` bietet
dieselben Typen wie der Context. `withPadding` erklärt, wie weit der Shader
außerhalb der ursprünglichen Layerbounds sichtbare Pixel erzeugen kann; ein zu
kleiner Wert schneidet den Effekt ab.

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
