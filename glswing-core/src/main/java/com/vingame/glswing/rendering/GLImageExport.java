package com.vingame.glswing.rendering;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Objects;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Image encoding and replace-on-success file output, independent of UI and applications. */
public final class GLImageExport {
    private GLImageExport() { }
    public static final class EncoderUnavailableException extends IOException {
        public EncoderUnavailableException(String format) { super("No image encoder for " + format); }
    }
    public static void write(BufferedImage image, String format, Path destination, float quality) throws IOException {
        Path target = destination.toAbsolutePath();
        Path temp = Files.createTempFile(target.getParent(), ".glswing-image-", ".tmp");
        try {
            try (var stream = Files.newOutputStream(temp)) { encode(image, format, quality, stream); }
            try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unavailable) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    /** Does not close the caller's output stream. JPEG alpha is composited over white. */
    public static void encode(BufferedImage image, String format, float quality, OutputStream output) throws IOException {
        Objects.requireNonNull(image); Objects.requireNonNull(format); Objects.requireNonNull(output);
        boolean jpeg = format.equalsIgnoreCase("jpg") || format.equalsIgnoreCase("jpeg");
        if (jpeg && (!Float.isFinite(quality) || quality < .01f || quality > 1))
            throw new IllegalArgumentException("JPEG quality must be between 0.01 and 1");
        var writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) throw new EncoderUnavailableException(format);
        var writer = writers.next();
        try (var stream = new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(stream);
            var options = writer.getDefaultWriteParam();
            if (jpeg) {
                options.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                options.setCompressionQuality(quality);
                if (image.getColorModel().hasAlpha()) {
                    var opaque = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                    var graphics = opaque.createGraphics();
                    try { graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0,0,image.getWidth(),image.getHeight()); graphics.drawImage(image,0,0,null); }
                    finally { graphics.dispose(); }
                    image = opaque;
                }
            }
            writer.write(null, new IIOImage(image, null, null), options);
            stream.flush();
        } finally { writer.dispose(); }
    }
}
