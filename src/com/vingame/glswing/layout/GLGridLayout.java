package com.vingame.glswing.layout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.layout.GLConstraints.Alignment;
import com.vingame.glswing.layout.GLConstraints.Fill;

/**
 * Lightweight GridBag-style layout with inferred tracks, spans, weights, fill,
 * alignment and margins. Automatic children flow row by row.
 */
public final class GLGridLayout implements GLLayout {

	private final int automaticColumns;
	private final float horizontalGap;
	private final float verticalGap;

	/** Creates a one-column automatically flowing grid. */
	public GLGridLayout() {
		this(1, 0, 0);
	}

	/** Creates an automatically flowing grid with the requested column count. */
	public GLGridLayout(int automaticColumns) {
		this(automaticColumns, 0, 0);
	}

	private GLGridLayout(int automaticColumns, float horizontalGap, float verticalGap) {
		if (automaticColumns < 1)
			throw new IllegalArgumentException("Automatic grid column count must be positive");
		if (!Float.isFinite(horizontalGap) || !Float.isFinite(verticalGap)
				|| horizontalGap < 0 || verticalGap < 0)
			throw new IllegalArgumentException("Grid gaps must be finite and non-negative");
		this.automaticColumns = automaticColumns;
		this.horizontalGap = horizontalGap;
		this.verticalGap = verticalGap;
	}

	public GLGridLayout withGap(float gap) {
		return withGap(gap, gap);
	}

	public GLGridLayout withGap(float horizontal, float vertical) {
		return new GLGridLayout(automaticColumns, horizontal, vertical);
	}

	public int getAutomaticColumns() {
		return automaticColumns;
	}

	public float getHorizontalGap() {
		return horizontalGap;
	}

	public float getVerticalGap() {
		return verticalGap;
	}

	@Override
	public GLSize measure(GLParent parent) {
		Model model = buildModel(parent);
		GLInsets padding = parent.getGLPadding();
		return new GLSize(
				padding.horizontal() + sum(model.preferredColumns)
						+ gapTotal(model.columnCount, horizontalGap),
				padding.vertical() + sum(model.preferredRows)
						+ gapTotal(model.rowCount, verticalGap));
	}

	@Override
	public void layout(GLParent parent, float width, float height) {
		Model model = buildModel(parent);
		if (model.items.isEmpty())
			return;
		GLInsets padding = parent.getGLPadding();
		float trackWidth = Math.max(0, width - padding.horizontal()
				- gapTotal(model.columnCount, horizontalGap));
		float trackHeight = Math.max(0, height - padding.vertical()
				- gapTotal(model.rowCount, verticalGap));
		float[] columns = resolveTracks(model.preferredColumns,
				model.minimumColumns, model.columnWeights, trackWidth);
		float[] rows = resolveTracks(model.preferredRows,
				model.minimumRows, model.rowWeights, trackHeight);
		float[] columnPositions = positions(columns, padding.left(), horizontalGap);
		float[] rowPositions = positions(rows, padding.top(), verticalGap);

		for (Item item : model.items) {
			GLConstraints constraints = item.constraints;
			GLInsets margin = constraints.margin();
			float cellX = columnPositions[item.column] + margin.left();
			float cellY = rowPositions[item.row] + margin.top();
			float cellWidth = Math.max(0,
					spanSize(columns, item.column, constraints.columnSpan(), horizontalGap)
							- margin.horizontal());
			float cellHeight = Math.max(0,
					spanSize(rows, item.row, constraints.rowSpan(), verticalGap)
							- margin.vertical());
			GLSize preferred = item.preferred;
			GLSize minimum = item.minimum;
			GLSize maximum = item.maximum;
			boolean fillX = constraints.fill() == Fill.HORIZONTAL
					|| constraints.fill() == Fill.BOTH;
			boolean fillY = constraints.fill() == Fill.VERTICAL
					|| constraints.fill() == Fill.BOTH;
			float childWidth = clamp(fillX ? cellWidth : preferred.width(),
					minimum.width(), maximum.width());
			float childHeight = clamp(fillY ? cellHeight : preferred.height(),
					minimum.height(), maximum.height());
			float childX = align(cellX, cellWidth, childWidth, constraints.alignX());
			float childY = align(cellY, cellHeight, childHeight, constraints.alignY());
			item.child.setBounds(childX, childY, childWidth, childHeight);
		}
	}

	private Model buildModel(GLParent parent) {
		List<Item> items = resolveItems(parent);
		int columnCount = 0;
		int rowCount = 0;
		for (Item item : items) {
			columnCount = Math.max(columnCount,
					item.column + item.constraints.columnSpan());
			rowCount = Math.max(rowCount,
					item.row + item.constraints.rowSpan());
		}
		float[] minimumColumns = new float[columnCount];
		float[] preferredColumns = new float[columnCount];
		float[] columnWeights = new float[columnCount];
		float[] minimumRows = new float[rowCount];
		float[] preferredRows = new float[rowCount];
		float[] rowWeights = new float[rowCount];

		for (Item item : items) {
			GLConstraints constraints = item.constraints;
			float weightPerColumn = constraints.weightX() / constraints.columnSpan();
			float weightPerRow = constraints.weightY() / constraints.rowSpan();
			for (int column = item.column;
					column < item.column + constraints.columnSpan(); column++)
				columnWeights[column] = Math.max(columnWeights[column], weightPerColumn);
			for (int row = item.row;
					row < item.row + constraints.rowSpan(); row++)
				rowWeights[row] = Math.max(rowWeights[row], weightPerRow);
		}

		// Single-track items establish the normal track sizes first.
		for (Item item : items)
			if (item.constraints.columnSpan() == 1)
				applyHorizontalRequirement(item, minimumColumns, preferredColumns);
		for (Item item : items)
			if (item.constraints.rowSpan() == 1)
				applyVerticalRequirement(item, minimumRows, preferredRows);
		// Spanning items only grow the tracks when their combined space is insufficient.
		for (Item item : items)
			if (item.constraints.columnSpan() > 1)
				applyHorizontalRequirement(item, minimumColumns, preferredColumns);
		for (Item item : items)
			if (item.constraints.rowSpan() > 1)
				applyVerticalRequirement(item, minimumRows, preferredRows);

		return new Model(items, columnCount, rowCount,
				minimumColumns, preferredColumns, columnWeights,
				minimumRows, preferredRows, rowWeights);
	}

	private List<Item> resolveItems(GLParent parent) {
		List<Item> explicit = new ArrayList<>();
		List<PendingItem> automatic = new ArrayList<>();
		Set<Long> occupied = new HashSet<>();
		for (GLContainer child : parent.getGLChildren()) {
			if (!child.isVisible())
				continue;
			GLConstraints constraints = parent.getGLConstraints(child);
			if (constraints.gridX() == GLConstraints.AUTO
					|| constraints.gridY() == GLConstraints.AUTO) {
				automatic.add(new PendingItem(child, constraints));
				continue;
			}
			occupy(occupied, constraints.gridX(), constraints.gridY(),
					constraints.columnSpan(), constraints.rowSpan());
			explicit.add(item(child, constraints,
					constraints.gridX(), constraints.gridY()));
		}

		int cursor = 0;
		for (PendingItem pending : automatic) {
			GLConstraints constraints = pending.constraints;
			int flowColumns = Math.max(automaticColumns, constraints.columnSpan());
			while (true) {
				int column = cursor % flowColumns;
				int row = cursor / flowColumns;
				cursor++;
				if (column + constraints.columnSpan() > flowColumns
						|| intersects(occupied, column, row,
								constraints.columnSpan(), constraints.rowSpan()))
					continue;
				occupy(occupied, column, row,
						constraints.columnSpan(), constraints.rowSpan());
				explicit.add(item(pending.child, constraints, column, row));
				break;
			}
		}
		return explicit;
	}

	private void applyHorizontalRequirement(Item item,
			float[] minimum, float[] preferred) {
		GLInsets margin = item.constraints.margin();
		GLSize minimumSize = item.minimum;
		GLSize preferredSize = item.preferred;
		ensureSpan(minimum, item.column, item.constraints.columnSpan(),
				minimumSize.width() + margin.horizontal(), horizontalGap);
		ensureSpan(preferred, item.column, item.constraints.columnSpan(),
				Math.max(minimumSize.width(), preferredSize.width())
						+ margin.horizontal(), horizontalGap);
	}

	private void applyVerticalRequirement(Item item,
			float[] minimum, float[] preferred) {
		GLInsets margin = item.constraints.margin();
		GLSize minimumSize = item.minimum;
		GLSize preferredSize = item.preferred;
		ensureSpan(minimum, item.row, item.constraints.rowSpan(),
				minimumSize.height() + margin.vertical(), verticalGap);
		ensureSpan(preferred, item.row, item.constraints.rowSpan(),
				Math.max(minimumSize.height(), preferredSize.height())
						+ margin.vertical(), verticalGap);
	}

	private static void ensureSpan(float[] tracks, int start, int span,
			float required, float gap) {
		float current = spanSize(tracks, start, span, gap);
		float deficit = required - current;
		if (deficit <= 0)
			return;
		float addition = deficit / span;
		for (int index = start; index < start + span; index++)
			tracks[index] += addition;
	}

	private static float[] resolveTracks(float[] preferred, float[] minimum,
			float[] weights, float available) {
		float[] result = preferred.clone();
		float preferredTotal = sum(result);
		if (available >= preferredTotal) {
			float totalWeight = sum(weights);
			if (totalWeight > 0) {
				float extra = available - preferredTotal;
				for (int index = 0; index < result.length; index++)
					result[index] += extra * weights[index] / totalWeight;
			}
			return result;
		}

		float deficit = preferredTotal - available;
		float flexibility = 0;
		for (int index = 0; index < result.length; index++)
			flexibility += Math.max(0, result[index] - minimum[index]);
		if (flexibility > 0) {
			float used = Math.min(deficit, flexibility);
			for (int index = 0; index < result.length; index++) {
				float trackFlexibility = Math.max(0, result[index] - minimum[index]);
				result[index] -= used * trackFlexibility / flexibility;
			}
			deficit -= used;
		}
		if (deficit > 0) {
			float total = sum(result);
			float scale = total == 0 ? 0 : Math.max(0, available) / total;
			for (int index = 0; index < result.length; index++)
				result[index] *= scale;
		}
		return result;
	}

	private static float[] positions(float[] tracks, float start, float gap) {
		float[] result = new float[tracks.length];
		float position = start;
		for (int index = 0; index < tracks.length; index++) {
			result[index] = position;
			position += tracks[index] + gap;
		}
		return result;
	}

	private static float align(float start, float available,
			float size, Alignment alignment) {
		return switch (alignment) {
		case START -> start;
		case CENTER -> start + (available - size) / 2;
		case END -> start + available - size;
		};
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(value, maximum));
	}

	private static float spanSize(float[] tracks, int start, int span, float gap) {
		float size = gapTotal(span, gap);
		for (int index = start; index < start + span; index++)
			size += tracks[index];
		return size;
	}

	private static float gapTotal(int trackCount, float gap) {
		return Math.max(0, trackCount - 1) * gap;
	}

	private static float sum(float[] values) {
		float result = 0;
		for (float value : values)
			result += value;
		return result;
	}

	private static boolean intersects(Set<Long> occupied,
			int column, int row, int columnSpan, int rowSpan) {
		for (int y = row; y < row + rowSpan; y++)
			for (int x = column; x < column + columnSpan; x++)
				if (occupied.contains(cell(x, y)))
					return true;
		return false;
	}

	private static void occupy(Set<Long> occupied,
			int column, int row, int columnSpan, int rowSpan) {
		if (intersects(occupied, column, row, columnSpan, rowSpan))
			throw new IllegalArgumentException("GL grid constraints overlap another child");
		for (int y = row; y < row + rowSpan; y++)
			for (int x = column; x < column + columnSpan; x++)
				occupied.add(cell(x, y));
	}

	private static long cell(int column, int row) {
		return (long) row << 32 | column & 0xffffffffL;
	}

	private static Item item(GLContainer child, GLConstraints constraints,
			int column, int row) {
		return new Item(child, constraints, column, row,
				child.getMinimumGLSize(), child.getPreferredGLSize(),
				child.getMaximumGLSize());
	}

	private record PendingItem(GLContainer child, GLConstraints constraints) {
	}

	private record Item(GLContainer child, GLConstraints constraints,
			int column, int row, GLSize minimum,
			GLSize preferred, GLSize maximum) {
	}

	private record Model(List<Item> items, int columnCount, int rowCount,
			float[] minimumColumns, float[] preferredColumns, float[] columnWeights,
			float[] minimumRows, float[] preferredRows, float[] rowWeights) {
	}
}
