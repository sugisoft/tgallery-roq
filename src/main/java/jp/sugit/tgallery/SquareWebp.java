package jp.sugit.tgallery;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.spi.IIORegistry;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import com.luciad.imageio.webp.CompressionType;
import com.luciad.imageio.webp.WebPImageReaderSpi;
import com.luciad.imageio.webp.WebPImageWriterSpi;
import com.luciad.imageio.webp.WebPWriteParam;

/**
 * Center-crops a picture to the largest square that fits, then encodes a
 * {@link #SIZE} pixel WebP. Scaling keeps the aspect ratio, so the picture is not stretched.
 */
public final class SquareWebp {

    public static final int SIZE = 480;
    public static final String FILE_EXTENSION = ".webp";
    public static final String CONTENT_TYPE = "image/webp";

    /** Lossy WebP quality, 0 to 1. Eighty keeps photo detail at a thumbnail size. */
    private static final float QUALITY = 0.90f;

    static {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        registry.registerServiceProvider(new WebPImageWriterSpi());
        registry.registerServiceProvider(new WebPImageReaderSpi());
    }

    private SquareWebp() {
    }

    public static String fileName(String id) {
        return "picture_" + id + FILE_EXTENSION;
    }

    public static byte[] toSquare(byte[] source) throws IOException {
        BufferedImage image;
        try (ByteArrayInputStream in = new ByteArrayInputStream(source)) {
            image = ImageIO.read(in);
        }
        if (image == null) {
            throw new IOException("Image format is not supported");
        }
        if (image.getWidth() < 1 || image.getHeight() < 1) {
            throw new IOException("Image has no pixels");
        }
        return writeWebp(coverSquare(image, SIZE));
    }

    /**
     * Largest centered square, then scaled to {@code size}. Equal source and destination
     * rectangles copy pixels without filtering.
     */
    static BufferedImage coverSquare(BufferedImage image, int size) {
        int width = image.getWidth();
        int height = image.getHeight();
        int side = Math.min(width, height);
        int x = (width - side) / 2;
        int y = (height - side) / 2;
        int type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage cropped = new BufferedImage(side, side, type);
        Graphics2D crop = cropped.createGraphics();
        try {
            crop.drawImage(image, 0, 0, side, side, x, y, x + side, y + side, null);
        } finally {
            crop.dispose();
        }
        return scale(cropped, size);
    }

    private static BufferedImage scale(BufferedImage image, int size) {
        BufferedImage current = image;
        // Halve until one step remains. One huge bicubic jump softens a photo thumbnail.
        while (current.getWidth() / 2 >= size) {
            current = resample(current, current.getWidth() / 2);
        }
        if (current.getWidth() != size) {
            current = resample(current, size);
        }
        return current;
    }

    private static BufferedImage resample(BufferedImage image, int size) {
        int type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage scaled = new BufferedImage(size, size, type);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private static byte[] writeWebp(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType(CONTENT_TYPE);
        if (!writers.hasNext()) {
            throw new IOException("WebP writer is not available");
        }
        ImageWriter writer = writers.next();
        WebPWriteParam param = (WebPWriteParam) writer.getDefaultWriteParam();
        param.setCompressionType(CompressionType.Lossy);
        param.setCompressionQuality(QUALITY);
        param.setUseSharpYUV(true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
