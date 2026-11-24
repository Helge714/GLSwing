package com.vingame.glswing.rendering;

/**
 * Parser for SVG path {@code d} attributes.
 * <p>
 * All SVG 1.1 path commands are supported in their absolute and relative
 * forms: {@code M/L/H/V/C/S/Q/T/A/Z}. Elliptical arcs are converted to one or
 * more cubic Bezier segments, so the resulting {@link GLPath2D} needs no
 * SVG-specific handling while it is rendered or recorded.
 */
public final class GLSvgPathParser {

	private GLSvgPathParser() {
	}

	/** Parses path data using {@link GLPath2D.FillRule#NON_ZERO}. */
	public static GLPath2D parse(String data) {
		return parse(data, GLPath2D.FillRule.NON_ZERO);
	}

	/** Parses path data using the supplied fill rule. */
	public static GLPath2D parse(String data, GLPath2D.FillRule fillRule) {
		if (data == null)
			throw new IllegalArgumentException("SVG path data must not be null");
		if (fillRule == null)
			throw new IllegalArgumentException("SVG path fill rule must not be null");
		return new Parser(data, fillRule).parse();
	}

	/** A syntax error carrying the exact character offset in the source data. */
	public static final class ParseException extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;
		private final int index;

		private ParseException(String message, int index) {
			super(message + " at index " + index);
			this.index = index;
		}

		public int getIndex() {
			return index;
		}
	}

	private static final class Parser {
		private final String data;
		private final GLPath2D path;
		private int index;
		private char activeCommand;
		private char previousSegment;
		private boolean hasCurrentPoint;
		private float currentX;
		private float currentY;
		private float subpathStartX;
		private float subpathStartY;
		private float cubicControlX;
		private float cubicControlY;
		private float quadraticControlX;
		private float quadraticControlY;

		Parser(String data, GLPath2D.FillRule fillRule) {
			this.data = data;
			path = new GLPath2D(fillRule);
		}

		GLPath2D parse() {
			while (true) {
				skipSeparators();
				if (atEnd())
					return path;
				char next = data.charAt(index);
				if (isCommand(next)) {
					activeCommand = next;
					index++;
				} else if (Character.isLetter(next)) {
					throw error("Unknown SVG path command '" + next + "'");
				} else if (activeCommand == 0) {
					throw error("Expected an SVG path command");
				} else if (!isNumberStart(next)) {
					throw error("Expected a number or SVG path command");
				}

				if (!hasCurrentPoint
						&& activeCommand != 'M' && activeCommand != 'm')
					throw error("An SVG path must begin with M or m");
				parseOneCommand();
			}
		}

		private void parseOneCommand() {
			boolean relative = Character.isLowerCase(activeCommand);
			switch (Character.toUpperCase(activeCommand)) {
			case 'M' -> {
				float x = coordinate(number(), currentX, relative);
				float y = coordinate(number(), currentY, relative);
				path.moveTo(x, y);
				currentX = subpathStartX = x;
				currentY = subpathStartY = y;
				hasCurrentPoint = true;
				previousSegment = 'M';
				activeCommand = relative ? 'l' : 'L';
			}
			case 'L' -> lineTo(
					coordinate(number(), currentX, relative),
					coordinate(number(), currentY, relative));
			case 'H' -> lineTo(coordinate(number(), currentX, relative), currentY);
			case 'V' -> lineTo(currentX, coordinate(number(), currentY, relative));
			case 'C' -> {
				float control1X = coordinate(number(), currentX, relative);
				float control1Y = coordinate(number(), currentY, relative);
				float control2X = coordinate(number(), currentX, relative);
				float control2Y = coordinate(number(), currentY, relative);
				float x = coordinate(number(), currentX, relative);
				float y = coordinate(number(), currentY, relative);
				path.cubicTo(control1X, control1Y, control2X, control2Y, x, y);
				cubicControlX = control2X;
				cubicControlY = control2Y;
				setCurrent(x, y, 'C');
			}
			case 'S' -> {
				float control1X = currentX;
				float control1Y = currentY;
				if (previousSegment == 'C' || previousSegment == 'S') {
					control1X = 2 * currentX - cubicControlX;
					control1Y = 2 * currentY - cubicControlY;
				}
				float control2X = coordinate(number(), currentX, relative);
				float control2Y = coordinate(number(), currentY, relative);
				float x = coordinate(number(), currentX, relative);
				float y = coordinate(number(), currentY, relative);
				path.cubicTo(control1X, control1Y, control2X, control2Y, x, y);
				cubicControlX = control2X;
				cubicControlY = control2Y;
				setCurrent(x, y, 'S');
			}
			case 'Q' -> {
				float controlX = coordinate(number(), currentX, relative);
				float controlY = coordinate(number(), currentY, relative);
				float x = coordinate(number(), currentX, relative);
				float y = coordinate(number(), currentY, relative);
				path.quadTo(controlX, controlY, x, y);
				quadraticControlX = controlX;
				quadraticControlY = controlY;
				setCurrent(x, y, 'Q');
			}
			case 'T' -> {
				float controlX = currentX;
				float controlY = currentY;
				if (previousSegment == 'Q' || previousSegment == 'T') {
					controlX = 2 * currentX - quadraticControlX;
					controlY = 2 * currentY - quadraticControlY;
				}
				float x = coordinate(number(), currentX, relative);
				float y = coordinate(number(), currentY, relative);
				path.quadTo(controlX, controlY, x, y);
				quadraticControlX = controlX;
				quadraticControlY = controlY;
				setCurrent(x, y, 'T');
			}
			case 'A' -> {
				float radiusX = number();
				float radiusY = number();
				float rotation = number();
				boolean largeArc = flag();
				boolean sweep = flag();
				float x = coordinate(number(), currentX, relative);
				float y = coordinate(number(), currentY, relative);
				appendArc(radiusX, radiusY, rotation, largeArc, sweep, x, y);
				setCurrent(x, y, 'A');
			}
			case 'Z' -> {
				path.closePath();
				currentX = subpathStartX;
				currentY = subpathStartY;
				previousSegment = 'Z';
				activeCommand = 0;
			}
			default -> throw error("Unknown SVG path command '" + activeCommand + "'");
			}
		}

		private void lineTo(float x, float y) {
			path.lineTo(x, y);
			setCurrent(x, y, 'L');
		}

		private void setCurrent(float x, float y, char segment) {
			currentX = x;
			currentY = y;
			previousSegment = segment;
		}

		private void appendArc(float rawRadiusX, float rawRadiusY, float rotation,
				boolean largeArc, boolean sweep, float endX, float endY) {
			double radiusX = Math.abs((double) rawRadiusX);
			double radiusY = Math.abs((double) rawRadiusY);
			if (currentX == endX && currentY == endY)
				return;
			if (radiusX == 0 || radiusY == 0) {
				path.lineTo(endX, endY);
				return;
			}

			double phi = Math.toRadians(rotation % 360d);
			double cosine = Math.cos(phi);
			double sine = Math.sin(phi);
			double deltaX = (currentX - endX) * 0.5;
			double deltaY = (currentY - endY) * 0.5;
			double transformedX = cosine * deltaX + sine * deltaY;
			double transformedY = -sine * deltaX + cosine * deltaY;

			double radiiScale = transformedX * transformedX / (radiusX * radiusX)
					+ transformedY * transformedY / (radiusY * radiusY);
			if (radiiScale > 1) {
				double scale = Math.sqrt(radiiScale);
				radiusX *= scale;
				radiusY *= scale;
			}

			double radiusX2 = radiusX * radiusX;
			double radiusY2 = radiusY * radiusY;
			double transformedX2 = transformedX * transformedX;
			double transformedY2 = transformedY * transformedY;
			double denominator = radiusX2 * transformedY2
					+ radiusY2 * transformedX2;
			double numerator = radiusX2 * radiusY2
					- radiusX2 * transformedY2 - radiusY2 * transformedX2;
			double centerScale = denominator == 0 ? 0
					: Math.sqrt(Math.max(0, numerator / denominator));
			if (largeArc == sweep)
				centerScale = -centerScale;
			double transformedCenterX = centerScale
					* radiusX * transformedY / radiusY;
			double transformedCenterY = centerScale
					* -radiusY * transformedX / radiusX;
			double centerX = cosine * transformedCenterX
					- sine * transformedCenterY + (currentX + endX) * 0.5;
			double centerY = sine * transformedCenterX
					+ cosine * transformedCenterY + (currentY + endY) * 0.5;

			double startVectorX = (transformedX - transformedCenterX) / radiusX;
			double startVectorY = (transformedY - transformedCenterY) / radiusY;
			double endVectorX = (-transformedX - transformedCenterX) / radiusX;
			double endVectorY = (-transformedY - transformedCenterY) / radiusY;
			double startAngle = signedAngle(1, 0, startVectorX, startVectorY);
			double sweepAngle = signedAngle(startVectorX, startVectorY,
					endVectorX, endVectorY);
			if (!sweep && sweepAngle > 0)
				sweepAngle -= Math.PI * 2;
			else if (sweep && sweepAngle < 0)
				sweepAngle += Math.PI * 2;

			int pieces = Math.max(1,
					(int) Math.ceil(Math.abs(sweepAngle) / (Math.PI * 0.5)));
			double pieceAngle = sweepAngle / pieces;
			for (int piece = 0; piece < pieces; piece++) {
				double angle0 = startAngle + piece * pieceAngle;
				double angle1 = angle0 + pieceAngle;
				double alpha = 4d / 3d * Math.tan(pieceAngle * 0.25);
				double cos0 = Math.cos(angle0);
				double sin0 = Math.sin(angle0);
				double cos1 = Math.cos(angle1);
				double sin1 = Math.sin(angle1);
				double control1X = cos0 - alpha * sin0;
				double control1Y = sin0 + alpha * cos0;
				double control2X = cos1 + alpha * sin1;
				double control2Y = sin1 - alpha * cos1;
				double[] mappedControl1 = mapEllipse(control1X, control1Y,
						centerX, centerY, radiusX, radiusY, cosine, sine);
				double[] mappedControl2 = mapEllipse(control2X, control2Y,
						centerX, centerY, radiusX, radiusY, cosine, sine);
				double[] mappedEnd = mapEllipse(cos1, sin1,
						centerX, centerY, radiusX, radiusY, cosine, sine);
				float pieceEndX = piece + 1 == pieces ? endX : finiteFloat(mappedEnd[0]);
				float pieceEndY = piece + 1 == pieces ? endY : finiteFloat(mappedEnd[1]);
				path.cubicTo(finiteFloat(mappedControl1[0]), finiteFloat(mappedControl1[1]),
						finiteFloat(mappedControl2[0]), finiteFloat(mappedControl2[1]),
						pieceEndX, pieceEndY);
			}
		}

		private float number() {
			skipSeparators();
			int start = index;
			if (!atEnd() && (data.charAt(index) == '+' || data.charAt(index) == '-'))
				index++;
			boolean digits = consumeDigits();
			if (!atEnd() && data.charAt(index) == '.') {
				index++;
				digits |= consumeDigits();
			}
			if (!digits)
				throw errorAt("Expected an SVG number", start);
			if (!atEnd() && (data.charAt(index) == 'e' || data.charAt(index) == 'E')) {
				int exponent = index++;
				if (!atEnd() && (data.charAt(index) == '+' || data.charAt(index) == '-'))
					index++;
				if (!consumeDigits())
					throw errorAt("Malformed exponent", exponent);
			}
			try {
				return finiteFloat(Double.parseDouble(data.substring(start, index)));
			} catch (NumberFormatException exception) {
				throw errorAt("Malformed SVG number", start);
			}
		}

		private boolean flag() {
			skipSeparators();
			if (atEnd() || (data.charAt(index) != '0' && data.charAt(index) != '1'))
				throw error("Arc flags must be 0 or 1");
			return data.charAt(index++) == '1';
		}

		private boolean consumeDigits() {
			int start = index;
			while (!atEnd() && Character.isDigit(data.charAt(index)))
				index++;
			return index != start;
		}

		private void skipSeparators() {
			while (!atEnd()) {
				char character = data.charAt(index);
				if (character == ',' || Character.isWhitespace(character))
					index++;
				else
					return;
			}
		}

		private boolean atEnd() {
			return index >= data.length();
		}

		private ParseException error(String message) {
			return errorAt(message, index);
		}

		private ParseException errorAt(String message, int errorIndex) {
			return new ParseException(message, errorIndex);
		}

		private static float coordinate(float value, float origin, boolean relative) {
			return finiteFloat(relative ? (double) origin + value : value);
		}
	}

	private static boolean isCommand(char character) {
		return switch (character) {
		case 'M', 'm', 'L', 'l', 'H', 'h', 'V', 'v', 'C', 'c', 'S', 's',
				'Q', 'q', 'T', 't', 'A', 'a', 'Z', 'z' -> true;
		default -> false;
		};
	}

	private static boolean isNumberStart(char character) {
		return character == '+' || character == '-' || character == '.'
				|| Character.isDigit(character);
	}

	private static float finiteFloat(double value) {
		float result = (float) value;
		if (!Double.isFinite(value) || !Float.isFinite(result))
			throw new IllegalArgumentException("SVG path coordinates must be finite floats");
		return result;
	}

	private static double signedAngle(double fromX, double fromY,
			double toX, double toY) {
		double cross = fromX * toY - fromY * toX;
		double dot = fromX * toX + fromY * toY;
		return Math.atan2(cross, dot);
	}

	private static double[] mapEllipse(double x, double y,
			double centerX, double centerY, double radiusX, double radiusY,
			double cosine, double sine) {
		return new double[] {
				centerX + cosine * radiusX * x - sine * radiusY * y,
				centerY + sine * radiusX * x + cosine * radiusY * y
		};
	}
}
