# Layouts und Größen

[UI-Übersicht](../ui-framework.md) · [Komponenten](components.md) ·
[Controls](controls.md)

## Inhalt

- [Grundmodell](#grundmodell)
- [Größenmodell](#größenmodell)
- [Freies Layout](#freies-layout)
- [`GLGridLayout`](#glgridlayout)
- [`GLConstraints`](#glconstraints)
- [Eigenes Layout](#eigenes-layout)

## Grundmodell

Ein `GLLayout` trennt Messen und Platzieren:

```java
public interface GLLayout {
    GLSize measure(GLParent parent);
    void layout(GLParent parent, float width, float height);
}
```

Alle Maße sind logische Pixel. `GLSize`, `GLInsets` und `GLConstraints` sind
unveränderlich. Layoutänderungen werden über `invalidateGLLayout()` markiert;
vor dem nächsten Rendering ruft der Parent das Layout bei Bedarf auf.

## Größenmodell

Jeder `GLContainer` kann Mindest-, bevorzugte und maximale Größe liefern oder
setzen. Eine explizit gesetzte bevorzugte Größe überschreibt die intrinsische
Größe des Controls, bis `clearPreferredGLSize()` auf die intrinsische Messung
zurückschaltet.

`GLSize.ZERO`, `GLSize.INFINITE` und `clamp(...)` vereinfachen eigene Layouts.
`GLInsets.ZERO`, `GLInsets.all(v)` und `GLInsets.symmetric(h,v)` beschreiben
Padding, Margins und visuellen Überstand.

`packGL()` wendet die gemessene bevorzugte Größe an. Bei `GLFrame` wird danach
auch das native Fenster gepackt.

## Freies Layout

`GLLayouts.FREE` ist die Vorgabe. Es bewahrt die expliziten Bounds aller Kinder:

```java
parent.setGLLayout(GLLayouts.FREE);
child.setBounds(40, 30, 180, 22);
parent.addGL(child);
```

Beim Messen bildet es die Vereinigung der sichtbaren, layoutverwalteten Kinder
zuzüglich Parent-Padding. Ein Kind mit `GLConstraints.overlay()` behält seine
Bounds und wird aus dieser Messung ausgenommen. Das eignet sich für Popups,
Overlays oder frei positionierte Node-Flächen.

## `GLGridLayout`

`GLGridLayout` ist ein leichter GridBag-artiger Layoutmanager. Ohne explizite
Zelle fließen Kinder zeilenweise durch die konfigurierte Spaltenzahl:

```java
GLContainer form = new GLContainer() {
    @Override protected void record(GLContext2D g) { }
};
form.setGLLayout(new GLGridLayout(2).withGap(8, 6));
form.setGLPadding(new GLInsets(12, 14, 12, 14));

form.addGL(new GLLabel("Name"), GLConstraints.auto().alignStart());
form.addGL(nameEditor, GLConstraints.auto()
        .weight(1, 0)
        .fillHorizontal());

form.addGL(new GLLabel("Optionen"), GLConstraints.at(0, 1)
        .span(2, 1)
        .fillHorizontal()
        .margin(new GLInsets(6, 0, 0, 0)));
```

`new GLGridLayout()` verwendet eine automatische Spalte.
`new GLGridLayout(columns)` legt die Zahl automatisch befüllter Spalten fest.
`withGap(horizontal, vertical)` liefert eine neue Layoutinstanz.

Explizite Zellen und Spans dürfen sich nicht überlappen. Ein solcher Konflikt
ist ein Programmierfehler und wird nicht durch unvorhersehbares Stapeln gelöst.

## `GLConstraints`

| Methode | Bedeutung |
|---|---|
| `auto()` | Nächste freie Zelle in der Flussreihenfolge. |
| `at(x,y)` | Explizite nullbasierte Spalte und Zeile. |
| `overlay()` | Bounds erhalten, nicht vom Layout messen oder platzieren lassen. |
| `span(columns,rows)` | Belegt mehrere Zellen. Werte sind Zellanzahlen. |
| `weight(wx,wy)` | Anteil am zusätzlich verfügbaren Platz; dimensionslos. |
| `fill(NONE/HORIZONTAL/VERTICAL/BOTH)` | Streckt das Kind innerhalb seiner Zelle. |
| `align(START/CENTER/END, ...)` | Positioniert ein nicht gefülltes Kind. |
| `margin(...)` | Außenabstand des Kindes in logischen Pixeln. |

`fillHorizontal()`, `fillVertical()`, `fillBoth()`, `alignStart()` und
`alignCenter()` sind Komfortmethoden. Gewichte verteilen nur zusätzlichen
Platz; die Basisgröße stammt weiterhin aus den bevorzugten Größen der Kinder.

## Eigenes Layout

Ein eigenes Layout sollte ausschließlich sichtbare und layoutverwaltete Kinder
berücksichtigen, Padding einrechnen und Größen an Minimum/Maximum begrenzen.
Die Layoutreihenfolge kommt aus `getGLChildren()`, nicht aus der veränderlichen
Paint-Z-Order.

```java
public final class VerticalLayout implements GLLayout {
    private final float gap;

    public VerticalLayout(float gap) {
        this.gap = gap;
    }

    @Override
    public GLSize measure(GLParent parent) {
        float width = 0, height = 0;
        int count = 0;
        for (GLContainer child : parent.getGLChildren()) {
            if (!child.isVisible()
                    || !parent.getGLConstraints(child).isLayoutManaged())
                continue;
            GLSize size = child.getPreferredGLSize();
            width = Math.max(width, size.width());
            height += size.height();
            count++;
        }
        GLInsets p = parent.getGLPadding();
        return new GLSize(width + p.horizontal(),
                height + p.vertical() + Math.max(0, count - 1) * gap);
    }

    @Override
    public void layout(GLParent parent, float width, float height) {
        GLInsets p = parent.getGLPadding();
        float y = p.top();
        for (GLContainer child : parent.getGLChildren()) {
            if (!child.isVisible()
                    || !parent.getGLConstraints(child).isLayoutManaged())
                continue;
            float h = child.getPreferredGLSize().height();
            child.setBounds(p.left(), y, width - p.horizontal(), h);
            y += h + gap;
        }
    }
}
```
