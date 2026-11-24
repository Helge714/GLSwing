# Layout and Sizing

[UI guide](../ui-framework.md) · [Components](components.md) · [Next: Controls](controls.md)

Layouts operate in parent-local logical pixels. A `GLContainer` delegates child
placement and preferred-size calculation to its `GLLayout`.

## Contents

- [Sizing model](#sizing-model)
- [Free layout](#free-layout)
- [`GLGridLayout`](#glgridlayout)
- [`GLConstraints`](#glconstraints)
- [Custom layouts](#custom-layouts)

## Sizing model

`GLSize` represents width and height. `GLSize.ZERO` means no requested space;
`GLSize.INFINITE` is an unbounded limit. Components may set or clear an explicit
preferred GL size. `packGL()` sizes a host or container from its preferred
content where supported.

Layout invalidation propagates upward. Call `invalidateGLLayout()` after a
custom property changes preferred size or child placement. Ordinary setters and
container mutations already invalidate as required.

`GLInsets` stores top, left, bottom, and right logical pixels. Constructors and
helpers include `ZERO`, `all(v)`, and `symmetric(horizontal,vertical)`.

## Free layout

`GLLayouts.FREE` preserves each child's explicit bounds. It is appropriate for
canvas-like editors, overlays, and application-controlled positioning.

```java
container.setGLLayout(GLLayouts.FREE);
child.setBounds(20, 30, 180, 40);
container.addGL(child);
```

## `GLGridLayout`

`GLGridLayout` arranges children in rows and columns using constraints.

```java
GLGridLayout grid = new GLGridLayout(2).withGap(8, 6);
container.setGLLayout(grid);
container.addGL(label, GLConstraints.at(0, 0).alignEnd());
container.addGL(editor, GLConstraints.at(1, 0).fillHorizontal().weight(1, 0));
```

The no-argument constructor derives columns from constraints; the integer
constructor sets a column count. Horizontal and vertical gaps are logical
pixels. Available space is distributed according to weights after fixed and
preferred sizes are satisfied.

## `GLConstraints`

Constraints specify grid position, span, alignment, fill, weight, margins, and
size behavior.

| Builder | Purpose |
|---|---|
| `at(column,row)` | Grid origin |
| `span(columns,rows)` | Number of occupied cells |
| `align(START/CENTER/END, ...)` | Placement within available cell space |
| `fill(NONE/HORIZONTAL/VERTICAL/BOTH)` | Expansion axes |
| `weight(wx,wy)` | Share of remaining space |
| `margin(...)` | Outer insets |
| `auto()` | Automatic placement |
| `overlay()` / `GLConstraints.overlay()` | Share the same layout region |
| `clamp(...)` | Minimum/maximum sizing limits |

Convenience methods include `alignStart`, `alignCenter`, `fillHorizontal`,
`fillVertical`, and `fillBoth`.

## Custom layouts

Implement `GLLayout` when grid or free placement is insufficient. Calculate
preferred size from visible children, margins, and gaps; then assign child
bounds during layout. Iterate `getGLChildren()` and avoid mutating the child
list while laying it out. Respect minimum/maximum input sizes and return finite,
non-negative results.
