package com.vingame.glswing.layout;

/** Immutable placement and sizing rules for one child in a GL layout. */
public final class GLConstraints {

	public enum Fill {
		NONE,
		HORIZONTAL,
		VERTICAL,
		BOTH
	}

	public enum Alignment {
		START,
		CENTER,
		END
	}

	public static final int AUTO = -1;
	public static final GLConstraints DEFAULT = auto();

	private final int gridX;
	private final int gridY;
	private final int columnSpan;
	private final int rowSpan;
	private final float weightX;
	private final float weightY;
	private final Fill fill;
	private final Alignment alignX;
	private final Alignment alignY;
	private final GLInsets margin;
	private final boolean layoutManaged;

	private GLConstraints(int gridX, int gridY, int columnSpan, int rowSpan,
			float weightX, float weightY, Fill fill,
			Alignment alignX, Alignment alignY, GLInsets margin,
			boolean layoutManaged) {
		if (gridX < AUTO || gridY < AUTO)
			throw new IllegalArgumentException("Grid coordinates must be automatic or non-negative");
		if (columnSpan < 1 || rowSpan < 1)
			throw new IllegalArgumentException("Grid spans must be positive");
		if (!Float.isFinite(weightX) || !Float.isFinite(weightY)
				|| weightX < 0 || weightY < 0)
			throw new IllegalArgumentException("Grid weights must be finite and non-negative");
		if (fill == null || alignX == null || alignY == null || margin == null)
			throw new IllegalArgumentException("Constraint values must not be null");
		this.gridX = gridX;
		this.gridY = gridY;
		this.columnSpan = columnSpan;
		this.rowSpan = rowSpan;
		this.weightX = weightX;
		this.weightY = weightY;
		this.fill = fill;
		this.alignX = alignX;
		this.alignY = alignY;
		this.margin = margin;
		this.layoutManaged = layoutManaged;
	}

	public static GLConstraints auto() {
		return new GLConstraints(AUTO, AUTO, 1, 1, 0, 0,
				Fill.NONE, Alignment.CENTER, Alignment.CENTER, GLInsets.ZERO, true);
	}

	/** Preserves explicit bounds and excludes an overlay from layout measurement. */
	public static GLConstraints overlay() {
		return new GLConstraints(AUTO, AUTO, 1, 1, 0, 0,
				Fill.NONE, Alignment.CENTER, Alignment.CENTER, GLInsets.ZERO, false);
	}

	public static GLConstraints at(int gridX, int gridY) {
		return new GLConstraints(gridX, gridY, 1, 1, 0, 0,
				Fill.NONE, Alignment.CENTER, Alignment.CENTER, GLInsets.ZERO, true);
	}

	public GLConstraints span(int columns, int rows) {
		return copy(gridX, gridY, columns, rows, weightX, weightY,
				fill, alignX, alignY, margin);
	}

	public GLConstraints weight(float horizontal, float vertical) {
		return copy(gridX, gridY, columnSpan, rowSpan, horizontal, vertical,
				fill, alignX, alignY, margin);
	}

	public GLConstraints fill(Fill fill) {
		return copy(gridX, gridY, columnSpan, rowSpan, weightX, weightY,
				fill, alignX, alignY, margin);
	}

	public GLConstraints fillHorizontal() {
		return fill(Fill.HORIZONTAL);
	}

	public GLConstraints fillVertical() {
		return fill(Fill.VERTICAL);
	}

	public GLConstraints fillBoth() {
		return fill(Fill.BOTH);
	}

	public GLConstraints align(Alignment horizontal, Alignment vertical) {
		return copy(gridX, gridY, columnSpan, rowSpan, weightX, weightY,
				fill, horizontal, vertical, margin);
	}

	public GLConstraints alignStart() {
		return align(Alignment.START, Alignment.START);
	}

	public GLConstraints alignCenter() {
		return align(Alignment.CENTER, Alignment.CENTER);
	}

	public GLConstraints margin(float margin) {
		return margin(GLInsets.all(margin));
	}

	public GLConstraints margin(GLInsets margin) {
		return copy(gridX, gridY, columnSpan, rowSpan, weightX, weightY,
				fill, alignX, alignY, margin);
	}

	public int gridX() {
		return gridX;
	}

	public int gridY() {
		return gridY;
	}

	public int columnSpan() {
		return columnSpan;
	}

	public int rowSpan() {
		return rowSpan;
	}

	public float weightX() {
		return weightX;
	}

	public float weightY() {
		return weightY;
	}

	public Fill fill() {
		return fill;
	}

	public Alignment alignX() {
		return alignX;
	}

	public Alignment alignY() {
		return alignY;
	}

	public GLInsets margin() {
		return margin;
	}

	public boolean isLayoutManaged() {
		return layoutManaged;
	}

	private GLConstraints copy(int gridX, int gridY,
			int columnSpan, int rowSpan, float weightX, float weightY,
			Fill fill, Alignment alignX, Alignment alignY, GLInsets margin) {
		return new GLConstraints(gridX, gridY, columnSpan, rowSpan,
				weightX, weightY, fill, alignX, alignY, margin, layoutManaged);
	}
}
