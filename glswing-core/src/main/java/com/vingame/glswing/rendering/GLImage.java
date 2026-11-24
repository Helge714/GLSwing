package com.vingame.glswing.rendering;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/** Renderer-independent image whose GPU storage is chosen lazily by GLSwing. */
public final class GLImage {

	private BufferedImage pixels;
	private final GLImageOptions options;
	private long version = 1;

	private GLImage(BufferedImage pixels, GLImageOptions options) {
		this.pixels = copy(requireImage(pixels));
		this.options = options == null ? GLImageOptions.DEFAULT : options;
	}

	public static GLImage of(BufferedImage image) {
		return of(image, GLImageOptions.DEFAULT);
	}

	public static GLImage of(BufferedImage image, GLImageOptions options) {
		return new GLImage(image, options);
	}

	public static GLImage load(Path path) throws IOException {
		return load(path, GLImageOptions.DEFAULT);
	}

	public static GLImage load(Path path, GLImageOptions options) throws IOException {
		if (path == null)
			throw new IllegalArgumentException("Image path must not be null");
		try (InputStream input = Files.newInputStream(path)) {
			return read(input, options, path.toString());
		}
	}

	public static GLImage loadResource(Class<?> anchor, String name)
			throws IOException {
		return loadResource(anchor, name, GLImageOptions.DEFAULT);
	}

	public static GLImage loadResource(Class<?> anchor, String name,
			GLImageOptions options) throws IOException {
		if (anchor == null || name == null || name.isBlank())
			throw new IllegalArgumentException("Resource anchor and name are required");
		try (InputStream input = anchor.getResourceAsStream(name)) {
			if (input == null)
				throw new IOException("Image resource not found: " + name);
			return read(input, options, name);
		}
	}

	public synchronized int getWidth() {
		return pixels.getWidth();
	}

	public synchronized int getHeight() {
		return pixels.getHeight();
	}

	public GLImageOptions getOptions() {
		return options;
	}

	/** Replaces pixels of a dynamic image; upload occurs on the next draw. */
	public synchronized void update(BufferedImage image) {
		if (options.usage() != GLImageOptions.Usage.DYNAMIC)
			throw new IllegalStateException("Only dynamic GLImages can be updated");
		BufferedImage replacement = requireImage(image);
		if (replacement.getWidth() != pixels.getWidth()
				|| replacement.getHeight() != pixels.getHeight())
			throw new IllegalArgumentException(
					"A dynamic image update must preserve its dimensions");
		pixels = copy(replacement);
		version++;
	}

	synchronized BufferedImage pixels() {
		return pixels;
	}

	synchronized long version() {
		return version;
	}

	private static GLImage read(InputStream input, GLImageOptions options,
			String description) throws IOException {
		BufferedImage image = ImageIO.read(input);
		if (image == null)
			throw new IOException("Unsupported or invalid image: " + description);
		return of(image, options);
	}

	private static BufferedImage requireImage(BufferedImage image) {
		if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0)
			throw new IllegalArgumentException("Image must have a positive size");
		return image;
	}

	private static BufferedImage copy(BufferedImage source) {
		BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(),
				BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = result.createGraphics();
		try {
			graphics.drawImage(source, 0, 0, null);
		} finally {
			graphics.dispose();
		}
		return result;
	}
}
