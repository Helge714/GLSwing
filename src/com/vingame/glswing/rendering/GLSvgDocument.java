package com.vingame.glswing.rendering;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Immutable vector document loaded from an SVG resource or stream.
 * <p>
 * The initial importer supports paths, nested groups, view boxes, affine SVG
 * transforms, fill rules, fill opacity, linear gradients in user space and
 * path-based clipping. Unsupported visible SVG elements fail explicitly instead
 * of silently producing an incomplete image.
 */
public final class GLSvgDocument {

	private static final Pattern NUMBER = Pattern.compile(
			"[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?");
	private static final Pattern TRANSFORM = Pattern.compile(
			"([A-Za-z]+)\\s*\\(([^)]*)\\)");
	private static final Pattern URL = Pattern.compile(
			"url\\(\\s*#([^\\s)]+)\\s*\\)");

	private final float viewX;
	private final float viewY;
	private final float viewWidth;
	private final float viewHeight;
	private final List<SvgShape> shapes;

	private GLSvgDocument(float viewX, float viewY, float viewWidth,
			float viewHeight, List<SvgShape> shapes) {
		this.viewX = viewX;
		this.viewY = viewY;
		this.viewWidth = viewWidth;
		this.viewHeight = viewHeight;
		this.shapes = List.copyOf(shapes);
	}

	/** Loads an SVG from an absolute or class-loader-relative resource path. */
	public static GLSvgDocument loadResource(String resourcePath) {
		if (resourcePath == null || resourcePath.isBlank())
			throw new IllegalArgumentException("SVG resource path must not be blank");
		String normalized = resourcePath.startsWith("/")
				? resourcePath.substring(1) : resourcePath;
		// Class-relative lookup is module-aware and can access this library's own
		// non-public resource packages. The class-loader fallback also supports
		// traditional non-modular applications.
		InputStream found = GLSvgDocument.class.getResourceAsStream("/" + normalized);
		if (found == null)
			found = GLSvgDocument.class.getClassLoader().getResourceAsStream(normalized);
		if (found == null)
			throw new IllegalArgumentException("SVG resource was not found: " + resourcePath);
		try (InputStream stream = found) {
			return parse(stream);
		} catch (IOException exception) {
			throw new IllegalArgumentException(
					"Could not close SVG resource: " + resourcePath, exception);
		}
	}

	/** Loads an SVG file from the filesystem. */
	public static GLSvgDocument load(Path path) {
		if (path == null)
			throw new IllegalArgumentException("SVG file path must not be null");
		try (InputStream stream = Files.newInputStream(path)) {
			return parse(stream);
		} catch (IOException exception) {
			throw new IllegalArgumentException("Could not read SVG file: " + path, exception);
		}
	}

	/** Parses an SVG document without taking ownership of the supplied stream. */
	public static GLSvgDocument parse(InputStream stream) {
		if (stream == null)
			throw new IllegalArgumentException("SVG input stream must not be null");
		try {
			DocumentBuilderFactory factory = secureDocumentBuilderFactory();
			Document document = factory.newDocumentBuilder().parse(stream);
			return new Importer(document).importDocument();
		} catch (ParserConfigurationException | SAXException | IOException exception) {
			throw new IllegalArgumentException("Could not parse SVG document", exception);
		}
	}

	public float getViewBoxX() { return viewX; }
	public float getViewBoxY() { return viewY; }
	public float getViewBoxWidth() { return viewWidth; }
	public float getViewBoxHeight() { return viewHeight; }
	public int getShapeCount() { return shapes.size(); }

	/** Rasterizes all supported SVG paints with aspect-preserving transparent padding. */
	public BufferedImage rasterize(int width, int height) {
		if (width <= 0 || height <= 0)
			throw new IllegalArgumentException("SVG raster size must be positive");
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		float scale = Math.min(width / viewWidth, height / viewHeight);
		float offsetX = (width - viewWidth * scale) * 0.5f - viewX * scale;
		float offsetY = (height - viewHeight * scale) * 0.5f - viewY * scale;
		Shape[] outlines = new Shape[shapes.size()];
		for (int index = 0; index < shapes.size(); index++)
			outlines[index] = shapes.get(index).path().toAwtPath();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				float red = 0;
				float green = 0;
				float blue = 0;
				float alpha = 0;
				for (int sampleY = 0; sampleY < 2; sampleY++) {
					for (int sampleX = 0; sampleX < 2; sampleX++) {
						float documentX = (x + (sampleX + 0.5f) * 0.5f - offsetX) / scale;
						float documentY = (y + (sampleY + 0.5f) * 0.5f - offsetY) / scale;
						float sampleRed = 0;
						float sampleGreen = 0;
						float sampleBlue = 0;
						float sampleAlpha = 0;
						for (int index = 0; index < shapes.size(); index++) {
							if (!outlines[index].contains(documentX, documentY))
								continue;
							Color color = shapes.get(index).paint().colorAt(documentX, documentY);
							float sourceAlpha = color.getAlpha() / 255f;
							float resultAlpha = sourceAlpha + sampleAlpha * (1 - sourceAlpha);
							if (resultAlpha > 0) {
								sampleRed = (color.getRed() / 255f * sourceAlpha
										+ sampleRed * sampleAlpha * (1 - sourceAlpha)) / resultAlpha;
								sampleGreen = (color.getGreen() / 255f * sourceAlpha
										+ sampleGreen * sampleAlpha * (1 - sourceAlpha)) / resultAlpha;
								sampleBlue = (color.getBlue() / 255f * sourceAlpha
										+ sampleBlue * sampleAlpha * (1 - sourceAlpha)) / resultAlpha;
							}
							sampleAlpha = resultAlpha;
						}
						red += sampleRed * sampleAlpha;
						green += sampleGreen * sampleAlpha;
						blue += sampleBlue * sampleAlpha;
						alpha += sampleAlpha;
					}
				}
				alpha *= 0.25f;
				if (alpha > 0) {
					int a = Math.round(alpha * 255);
					int r = Math.round(Math.min(1, red * 0.25f / alpha) * 255);
					int g = Math.round(Math.min(1, green * 0.25f / alpha) * 255);
					int b = Math.round(Math.min(1, blue * 0.25f / alpha) * 255);
					image.setRGB(x, y, a << 24 | r << 16 | g << 8 | b);
				}
			}
		}
		return image;
	}

	/** True when all visible samples share one RGB color and may be tinted as MSDF. */
	public boolean isMonochrome() {
		BufferedImage sample = rasterize(32, 32);
		int reference = -1;
		for (int y = 0; y < sample.getHeight(); y++) {
			for (int x = 0; x < sample.getWidth(); x++) {
				int argb = sample.getRGB(x, y);
				if ((argb >>> 24) < 32)
					continue;
				int rgb = argb & 0xffffff;
				if (reference < 0)
					reference = rgb;
				else if (rgb != reference)
					return false;
			}
		}
		return reference >= 0;
	}

	Area monochromeOutline(int resolution, float margin) {
		Area outline = new Area();
		for (SvgShape shape : shapes)
			outline.add(new Area(shape.path().toAwtPath()));
		float availableWidth = resolution - margin * 2;
		float availableHeight = resolution - margin * 2;
		float scale = Math.min(availableWidth / viewWidth, availableHeight / viewHeight);
		float offsetX = (resolution - viewWidth * scale) * 0.5f - viewX * scale;
		float offsetY = (resolution - viewHeight * scale) * 0.5f - viewY * scale;
		outline.transform(new AffineTransform(scale, 0, 0, scale, offsetX, offsetY));
		return outline;
	}

	/** Draws the document centered and aspect-preserving inside the target box. */
	public void draw(GLContext2D context, float x, float y, float width, float height) {
		if (context == null)
			throw new IllegalArgumentException("SVG context must not be null");
		requireFinitePositive(width, "SVG target width");
		requireFinitePositive(height, "SVG target height");
		if (!Float.isFinite(x) || !Float.isFinite(y))
			throw new IllegalArgumentException("SVG target position must be finite");
		float scale = Math.min(width / viewWidth, height / viewHeight);
		float renderedWidth = viewWidth * scale;
		float renderedHeight = viewHeight * scale;
		float offsetX = x + (width - renderedWidth) * 0.5f - viewX * scale;
		float offsetY = y + (height - renderedHeight) * 0.5f - viewY * scale;
		GLTransform original = context.getTransform();
		try {
			context.setTransform(original.copy().translate(offsetX, offsetY).scale(scale));
			for (SvgShape shape : shapes)
				context.fillPath(shape.path(), shape.paint());
		} finally {
			context.setTransform(original);
		}
	}

	private static DocumentBuilderFactory secureDocumentBuilderFactory()
			throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		return factory;
	}

	private static final class Importer {
		private final Document document;
		private final Map<String, Element> clips = new HashMap<>();
		private final Map<String, LinearGradientDefinition> gradients = new HashMap<>();
		private final List<SvgShape> shapes = new ArrayList<>();

		Importer(Document document) {
			this.document = document;
		}

		GLSvgDocument importDocument() {
			Element root = document.getDocumentElement();
			if (root == null || !"svg".equals(localName(root)))
				throw new IllegalArgumentException("Document root must be an <svg> element");
			float[] viewBox = parseViewBox(root);
			collectDefinitions(root);
			compileChildren(root, Style.initial(), new GLTransform(), List.of());
			return new GLSvgDocument(viewBox[0], viewBox[1], viewBox[2], viewBox[3], shapes);
		}

		private void collectDefinitions(Element element) {
			String name = localName(element);
			String id = attribute(element, "id");
			if (!id.isEmpty()) {
				if ("clipPath".equals(name))
					clips.put(id, element);
				else if ("linearGradient".equals(name))
					gradients.put(id, parseGradient(element));
			}
			forEachChildElement(element, this::collectDefinitions);
		}

		private void compileChildren(Element parent, Style inherited,
				GLTransform parentTransform, List<ClipUse> inheritedClips) {
			forEachChildElement(parent, child -> compileElement(
					child, inherited, parentTransform, inheritedClips));
		}

		private void compileElement(Element element, Style inherited,
				GLTransform parentTransform, List<ClipUse> inheritedClips) {
			String name = localName(element);
			if ("defs".equals(name) || "clipPath".equals(name)
					|| "linearGradient".equals(name) || "stop".equals(name))
				return;
			GLTransform transform = parentTransform.copy()
					.concatenate(parseTransform(attribute(element, "transform")));
			Style style = inherited.derive(element);
			List<ClipUse> clipUses = inheritedClips;
			String clipReference = referenceId(attribute(element, "clip-path"));
			if (clipReference != null) {
				clipUses = new ArrayList<>(inheritedClips);
				clipUses.add(new ClipUse(clipReference, transform.copy()));
				clipUses = List.copyOf(clipUses);
			}

			switch (name) {
			case "g", "svg" -> compileChildren(element, style, transform, clipUses);
			case "path" -> compilePath(element, style, transform, clipUses);
			case "title", "desc", "metadata" -> { }
			default -> throw new IllegalArgumentException(
					"Unsupported visible SVG element <" + name + ">");
			}
		}

		private void compilePath(Element element, Style style,
				GLTransform transform, List<ClipUse> clipUses) {
			String data = attribute(element, "d");
			if (data.isBlank())
				return;
			GLPath2D path = GLSvgPathParser.parse(data, style.fillRule())
					.transformed(transform);
			if (!clipUses.isEmpty()) {
				Area area = new Area(path.toAwtPath());
				for (ClipUse clipUse : clipUses)
					area.intersect(resolveClip(clipUse));
				if (area.isEmpty())
					return;
				path = GLPath2D.fromAwtPath(area.getPathIterator(null));
			}
			GLPaint2D paint = resolvePaint(style, transform);
			if (paint != null)
				shapes.add(new SvgShape(path, paint));
		}

		private Area resolveClip(ClipUse use) {
			Element definition = clips.get(use.id());
			if (definition == null)
				throw new IllegalArgumentException("Unknown SVG clip path #" + use.id());
			String units = attribute(definition, "clipPathUnits");
			if (!units.isEmpty() && !"userSpaceOnUse".equals(units))
				throw new IllegalArgumentException(
						"Only userSpaceOnUse clip paths are currently supported");
			GLTransform transform = use.transform().copy()
					.concatenate(parseTransform(attribute(definition, "transform")));
			Area result = new Area();
			appendClipChildren(definition, transform, result,
					GLPath2D.FillRule.NON_ZERO);
			return result;
		}

		private void appendClipChildren(Element parent, GLTransform parentTransform,
				Area target, GLPath2D.FillRule inheritedRule) {
			forEachChildElement(parent, child -> {
				GLTransform transform = parentTransform.copy()
						.concatenate(parseTransform(attribute(child, "transform")));
				GLPath2D.FillRule rule = parseFillRule(firstNonEmpty(
						attribute(child, "clip-rule"), attribute(child, "fill-rule")),
						inheritedRule);
				switch (localName(child)) {
				case "g" -> appendClipChildren(child, transform, target, rule);
				case "path" -> {
					GLPath2D path = GLSvgPathParser.parse(attribute(child, "d"), rule)
							.transformed(transform);
					target.add(new Area(path.toAwtPath()));
				}
				default -> throw new IllegalArgumentException(
						"Unsupported element in SVG clip path: <" + localName(child) + ">");
				}
			});
		}

		private GLPaint2D resolvePaint(Style style, GLTransform elementTransform) {
			if ("none".equalsIgnoreCase(style.fill()))
				return null;
			float opacity = clamp01(style.opacity() * style.fillOpacity());
			String gradientId = referenceId(style.fill());
			if (gradientId != null) {
				LinearGradientDefinition gradient = gradients.get(gradientId);
				if (gradient == null)
					throw new IllegalArgumentException("Unknown SVG gradient #" + gradientId);
				return gradient.createPaint(elementTransform, opacity);
			}
			Color color = parseColor(style.fill(), opacity);
			return GLPaint2D.solid(color);
		}

		private LinearGradientDefinition parseGradient(Element element) {
			String units = attribute(element, "gradientUnits");
			if (!units.isEmpty() && !"userSpaceOnUse".equals(units))
				throw new IllegalArgumentException(
						"Only userSpaceOnUse linear gradients are currently supported");
			float x1 = parseLength(attributeOr(element, "x1", "0"));
			float y1 = parseLength(attributeOr(element, "y1", "0"));
			float x2 = parseLength(attributeOr(element, "x2", "1"));
			float y2 = parseLength(attributeOr(element, "y2", "0"));
			GLTransform transform = parseTransform(attribute(element, "gradientTransform"));
			List<GradientStop> stops = new ArrayList<>();
			forEachChildElement(element, child -> {
				if (!"stop".equals(localName(child)))
					return;
				float offset = parseRatio(attributeOr(child, "offset", "0"));
				float opacity = parseRatio(attributeOr(child, "stop-opacity", "1"));
				Color color = parseColor(attributeOr(child, "stop-color", "black"), opacity);
				stops.add(new GradientStop(clamp01(offset), color));
			});
			if (stops.isEmpty())
				throw new IllegalArgumentException("SVG linear gradient has no stops");
			stops.sort(Comparator.comparingDouble(GradientStop::offset));
			return new LinearGradientDefinition(x1, y1, x2, y2,
					transform, List.copyOf(stops));
		}
	}

	private record SvgShape(GLPath2D path, GLPaint2D paint) { }
	private record ClipUse(String id, GLTransform transform) { }
	private record GradientStop(float offset, Color color) { }

	private record LinearGradientDefinition(float x1, float y1, float x2, float y2,
			GLTransform transform, List<GradientStop> stops) {

		GLPaint2D createPaint(GLTransform elementTransform, float opacity) {
			GLTransform gradientToDocument = elementTransform.copy().concatenate(transform);
			GLTransform documentToGradient = gradientToDocument.createInverse();
			float directionX = x2 - x1;
			float directionY = y2 - y1;
			float lengthSquared = directionX * directionX + directionY * directionY;
			if (!(lengthSquared > 0)) {
				Color color = withOpacity(stops.get(stops.size() - 1).color(), opacity);
				return GLPaint2D.solid(color);
			}
			return (documentX, documentY) -> {
				float gradientX = documentToGradient.transformX(documentX, documentY);
				float gradientY = documentToGradient.transformY(documentX, documentY);
				float position = ((gradientX - x1) * directionX
						+ (gradientY - y1) * directionY) / lengthSquared;
				return sampleStops(stops, clamp01(position), opacity);
			};
		}
	}

	private record Style(String fill, float fillOpacity, float opacity,
			GLPath2D.FillRule fillRule) {

		static Style initial() {
			return new Style("black", 1, 1, GLPath2D.FillRule.NON_ZERO);
		}

		Style derive(Element element) {
			String nextFill = attribute(element, "fill");
			float nextFillOpacity = fillOpacity;
			String fillOpacityText = attribute(element, "fill-opacity");
			if (!fillOpacityText.isEmpty())
				nextFillOpacity = parseRatio(fillOpacityText);
			float nextOpacity = opacity;
			String opacityText = attribute(element, "opacity");
			if (!opacityText.isEmpty())
				nextOpacity *= parseRatio(opacityText);
			GLPath2D.FillRule nextRule = parseFillRule(
					attribute(element, "fill-rule"), fillRule);
			return new Style(nextFill.isEmpty() ? fill : nextFill,
					nextFillOpacity, nextOpacity, nextRule);
		}
	}

	private static Color sampleStops(List<GradientStop> stops,
			float position, float opacity) {
		GradientStop first = stops.get(0);
		if (position <= first.offset())
			return withOpacity(first.color(), opacity);
		for (int index = 1; index < stops.size(); index++) {
			GradientStop second = stops.get(index);
			if (position > second.offset())
				continue;
			GradientStop previous = stops.get(index - 1);
			float distance = second.offset() - previous.offset();
			float amount = distance <= 0 ? 1
					: (position - previous.offset()) / distance;
			return interpolate(previous.color(), second.color(), amount, opacity);
		}
		return withOpacity(stops.get(stops.size() - 1).color(), opacity);
	}

	private static Color interpolate(Color first, Color second,
			float amount, float opacity) {
		float inverse = 1 - amount;
		float red = (first.getRed() * inverse + second.getRed() * amount) / 255f;
		float green = (first.getGreen() * inverse + second.getGreen() * amount) / 255f;
		float blue = (first.getBlue() * inverse + second.getBlue() * amount) / 255f;
		float alpha = (first.getAlpha() * inverse + second.getAlpha() * amount)
				/ 255f * opacity;
		return new Color(clamp01(red), clamp01(green), clamp01(blue), clamp01(alpha));
	}

	private static Color withOpacity(Color color, float opacity) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(),
				Math.round(color.getAlpha() * clamp01(opacity)));
	}

	private static Color parseColor(String text, float opacity) {
		String color = text.trim().toLowerCase(Locale.ROOT);
		if (color.startsWith("rgb(")) {
			String[] components = color.substring(4, color.length() - 1).split("\\s*,\\s*");
			if (components.length != 3)
				throw new IllegalArgumentException("Invalid SVG rgb() color: " + text);
			return new Color(parseColorComponent(components[0]),
					parseColorComponent(components[1]), parseColorComponent(components[2]),
					clamp01(opacity));
		}
		if (color.startsWith("#")) {
			String hex = color.substring(1);
			if (hex.length() == 3)
				hex = "" + hex.charAt(0) + hex.charAt(0)
						+ hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
			if (hex.length() != 6)
				throw new IllegalArgumentException("Unsupported SVG hex color: " + text);
			int value = Integer.parseInt(hex, 16);
			return new Color((value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff,
					Math.round(255 * clamp01(opacity)));
		}
		Color named = switch (color) {
		case "black" -> Color.BLACK;
		case "white" -> Color.WHITE;
		case "red" -> Color.RED;
		case "green" -> Color.GREEN;
		case "blue" -> Color.BLUE;
		case "gray", "grey" -> Color.GRAY;
		case "transparent" -> new Color(0, 0, 0, 0);
		default -> throw new IllegalArgumentException("Unsupported SVG color: " + text);
		};
		return withOpacity(named, opacity);
	}

	private static float parseColorComponent(String text) {
		String value = text.trim();
		return value.endsWith("%")
				? clamp01(Float.parseFloat(value.substring(0, value.length() - 1)) / 100f)
				: clamp01(Float.parseFloat(value) / 255f);
	}

	private static float[] parseViewBox(Element root) {
		String text = attribute(root, "viewBox");
		if (!text.isBlank()) {
			float[] values = parseNumbers(text);
			if (values.length != 4)
				throw new IllegalArgumentException("SVG viewBox requires four numbers");
			requireFinitePositive(values[2], "SVG viewBox width");
			requireFinitePositive(values[3], "SVG viewBox height");
			return values;
		}
		float width = parseLength(attribute(root, "width"));
		float height = parseLength(attribute(root, "height"));
		requireFinitePositive(width, "SVG width");
		requireFinitePositive(height, "SVG height");
		return new float[] { 0, 0, width, height };
	}

	private static GLTransform parseTransform(String text) {
		GLTransform result = new GLTransform();
		if (text == null || text.isBlank())
			return result;
		Matcher matcher = TRANSFORM.matcher(text);
		int end = 0;
		while (matcher.find()) {
			if (!text.substring(end, matcher.start()).matches("[\\s,]*"))
				throw new IllegalArgumentException("Malformed SVG transform: " + text);
			float[] values = parseNumbers(matcher.group(2));
			GLTransform operation = switch (matcher.group(1)) {
			case "matrix" -> requireTransformCount(text, values, 6,
					new GLTransform(values[0], values[1], values[2], values[3],
							values[4], values[5]));
			case "translate" -> {
				if (values.length < 1 || values.length > 2)
					throw new IllegalArgumentException("translate() needs one or two values");
				yield new GLTransform().translate(values[0],
						values.length == 2 ? values[1] : 0);
			}
			case "scale" -> {
				if (values.length < 1 || values.length > 2)
					throw new IllegalArgumentException("scale() needs one or two values");
				yield new GLTransform().scale(values[0],
						values.length == 2 ? values[1] : values[0]);
			}
			case "rotate" -> {
				if (values.length != 1 && values.length != 3)
					throw new IllegalArgumentException("rotate() needs one or three values");
				float radians = (float) Math.toRadians(values[0]);
				yield values.length == 1 ? new GLTransform().rotate(radians)
						: new GLTransform().rotate(radians, values[1], values[2]);
			}
			case "skewX" -> requireTransformCount(text, values, 1,
					new GLTransform(1, 0,
							(float) Math.tan(Math.toRadians(values[0])), 1, 0, 0));
			case "skewY" -> requireTransformCount(text, values, 1,
					new GLTransform(1, (float) Math.tan(Math.toRadians(values[0])),
							0, 1, 0, 0));
			default -> throw new IllegalArgumentException(
					"Unsupported SVG transform: " + matcher.group(1));
			};
			result.concatenate(operation);
			end = matcher.end();
		}
		if (end == 0 || !text.substring(end).matches("[\\s,]*"))
			throw new IllegalArgumentException("Malformed SVG transform: " + text);
		return result;
	}

	private static GLTransform requireTransformCount(String source,
			float[] values, int count, GLTransform transform) {
		if (values.length != count)
			throw new IllegalArgumentException("Wrong value count in SVG transform: " + source);
		return transform;
	}

	private static float[] parseNumbers(String text) {
		List<Float> values = new ArrayList<>();
		Matcher matcher = NUMBER.matcher(text);
		int end = 0;
		while (matcher.find()) {
			if (!text.substring(end, matcher.start()).matches("[\\s,]*"))
				throw new IllegalArgumentException("Malformed SVG number list: " + text);
			float value = Float.parseFloat(matcher.group());
			if (!Float.isFinite(value))
				throw new IllegalArgumentException("SVG number is not finite: " + text);
			values.add(value);
			end = matcher.end();
		}
		if (!text.substring(end).matches("[\\s,]*"))
			throw new IllegalArgumentException("Malformed SVG number list: " + text);
		float[] result = new float[values.size()];
		for (int index = 0; index < result.length; index++)
			result[index] = values.get(index);
		return result;
	}

	private static float parseLength(String text) {
		String value = text.trim();
		if (value.endsWith("px"))
			value = value.substring(0, value.length() - 2).trim();
		if (value.endsWith("%"))
			throw new IllegalArgumentException(
					"Percentage SVG lengths are not supported here: " + text);
		float result = Float.parseFloat(value);
		if (!Float.isFinite(result))
			throw new IllegalArgumentException("SVG length must be finite: " + text);
		return result;
	}

	private static float parseRatio(String text) {
		String value = text.trim();
		float result = value.endsWith("%")
				? Float.parseFloat(value.substring(0, value.length() - 1)) / 100f
				: Float.parseFloat(value);
		if (!Float.isFinite(result))
			throw new IllegalArgumentException("SVG ratio must be finite: " + text);
		return clamp01(result);
	}

	private static GLPath2D.FillRule parseFillRule(String value,
			GLPath2D.FillRule fallback) {
		if (value == null || value.isBlank())
			return fallback;
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
		case "evenodd" -> GLPath2D.FillRule.EVEN_ODD;
		case "nonzero" -> GLPath2D.FillRule.NON_ZERO;
		default -> throw new IllegalArgumentException("Unknown SVG fill rule: " + value);
		};
	}

	private static String attribute(Element element, String name) {
		String direct = element.getAttribute(name);
		if (!direct.isEmpty())
			return direct.trim();
		String style = element.getAttribute("style");
		for (String declaration : style.split(";")) {
			int separator = declaration.indexOf(':');
			if (separator > 0 && name.equals(declaration.substring(0, separator).trim()))
				return declaration.substring(separator + 1).trim();
		}
		return "";
	}

	private static String attributeOr(Element element, String name, String fallback) {
		String value = attribute(element, name);
		return value.isEmpty() ? fallback : value;
	}

	private static String referenceId(String value) {
		if (value == null || value.isBlank())
			return null;
		Matcher matcher = URL.matcher(value.trim());
		return matcher.matches() ? matcher.group(1) : null;
	}

	private static String firstNonEmpty(String first, String second) {
		return first == null || first.isBlank() ? second : first;
	}

	private static String localName(Element element) {
		String local = element.getLocalName();
		return local == null ? element.getTagName() : local;
	}

	private static void forEachChildElement(Element parent,
			java.util.function.Consumer<Element> consumer) {
		NodeList children = parent.getChildNodes();
		for (int index = 0; index < children.getLength(); index++) {
			Node child = children.item(index);
			if (child instanceof Element element)
				consumer.accept(element);
		}
	}

	private static float clamp01(float value) {
		return Math.max(0, Math.min(1, value));
	}

	private static void requireFinitePositive(float value, String description) {
		if (!Float.isFinite(value) || value <= 0)
			throw new IllegalArgumentException(description + " must be finite and positive");
	}
}
