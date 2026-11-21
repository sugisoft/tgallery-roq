package jp.sugit.tgallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class SquareWebpTest {

    @Test
    void landscapeKeepsTheLargestCenteredSquare() {
        BufferedImage source = new BufferedImage(900, 300, BufferedImage.TYPE_INT_RGB);
        fill(source, 0, 0, 300, 300, 0xFF0000);
        fill(source, 300, 0, 100, 300, 0x00FF00);
        fill(source, 400, 0, 100, 300, 0xFFFFFF);
        fill(source, 500, 0, 100, 300, 0xFFFF00);
        fill(source, 600, 0, 300, 300, 0x0000FF);

        BufferedImage square = SquareWebp.coverSquare(source, 300);

        assertEquals(300, square.getWidth());
        assertEquals(300, square.getHeight());
        assertRgb(square, 10, 150, 0x00FF00);
        assertRgb(square, 150, 150, 0xFFFFFF);
        assertRgb(square, 290, 150, 0xFFFF00);
        assertNoColor(square, 0xFF0000);
        assertNoColor(square, 0x0000FF);
    }

    @Test
    void portraitKeepsFullWidthAndCropsTopAndBottom() {
        BufferedImage source = new BufferedImage(300, 900, BufferedImage.TYPE_INT_RGB);
        fill(source, 0, 0, 300, 300, 0xFF0000);
        fill(source, 0, 300, 300, 300, 0x00FF00);
        fill(source, 0, 300, 1, 300, 0xFF00FF);
        fill(source, 299, 300, 1, 300, 0x00FFFF);
        fill(source, 0, 600, 300, 300, 0x0000FF);

        BufferedImage square = SquareWebp.coverSquare(source, 300);

        assertEquals(300, square.getWidth());
        assertEquals(300, square.getHeight());
        assertRgb(square, 0, 150, 0xFF00FF);
        assertRgb(square, 299, 150, 0x00FFFF);
        assertRgb(square, 150, 0, 0x00FF00);
        assertRgb(square, 150, 299, 0x00FF00);
        assertNoColor(square, 0xFF0000);
        assertNoColor(square, 0x0000FF);
    }

    @Test
    void oddWidthCentersByDroppingOnePixelFromEachSide() {
        BufferedImage source = new BufferedImage(7, 3, BufferedImage.TYPE_INT_RGB);
        fill(source, 0, 0, 2, 3, 0xFF0000);
        fill(source, 2, 0, 3, 3, 0x00FF00);
        fill(source, 5, 0, 2, 3, 0x0000FF);

        BufferedImage square = SquareWebp.coverSquare(source, 3);

        assertEquals(3, square.getWidth());
        assertNoColor(square, 0xFF0000);
        assertNoColor(square, 0x0000FF);
        assertRgb(square, 0, 1, 0x00FF00);
        assertRgb(square, 2, 1, 0x00FF00);
    }

    @Test
    void circleStaysRoundAfterCenterCrop() {
        BufferedImage source = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 200, 400);
        graphics.setColor(Color.BLACK);
        graphics.fillRect(200, 0, 400, 400);
        graphics.dispose();
        int radius = 100;
        for (int y = 100; y <= 300; y++) {
            for (int x = 300; x <= 500; x++) {
                int dx = x - 400;
                int dy = y - 200;
                if (dx * dx + dy * dy <= radius * radius) {
                    source.setRGB(x, y, 0xFFFFFF);
                }
            }
        }

        BufferedImage square = SquareWebp.coverSquare(source, 400);

        assertRgb(square, 200, 200, 0xFFFFFF);
        assertRgb(square, 200, 110, 0xFFFFFF);
        assertRgb(square, 290, 200, 0xFFFFFF);
        assertRgb(square, 200, 40, 0x000000);
        assertRgb(square, 40, 200, 0x000000);
        assertNoColor(square, 0xFF0000);
        int[] box = whiteBounds(square);
        assertEquals(box[2] - box[0], box[3] - box[1], "circle was stretched");
    }

    @Test
    void squareSourceKeepsItsCorners() {
        BufferedImage source = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
        fill(source, 0, 0, 200, 200, 0xFF0000);
        fill(source, 200, 0, 200, 200, 0x00FF00);
        fill(source, 0, 200, 200, 200, 0x0000FF);
        fill(source, 200, 200, 200, 200, 0xFFFF00);

        BufferedImage square = SquareWebp.coverSquare(source, SquareWebp.SIZE);

        assertEquals(SquareWebp.SIZE, square.getWidth());
        assertEquals(SquareWebp.SIZE, square.getHeight());
        assertRgb(square, 20, 20, 0xFF0000);
        assertRgb(square, 460, 20, 0x00FF00);
        assertRgb(square, 20, 460, 0x0000FF);
        assertRgb(square, 460, 460, 0xFFFF00);
    }

    @Test
    void encodesJpegAsSmallerSquareWebp() throws Exception {
        BufferedImage source = new BufferedImage(1800, 600, BufferedImage.TYPE_INT_RGB);
        fill(source, 0, 0, 600, 600, 0xFF0000);
        fill(source, 600, 0, 200, 600, 0x00FF00);
        fill(source, 800, 0, 200, 600, 0xFFFFFF);
        fill(source, 1000, 0, 200, 600, 0xFFFF00);
        fill(source, 1200, 0, 600, 600, 0x0000FF);
        byte[] jpeg = jpeg(source);

        byte[] webp = SquareWebp.toSquare(jpeg);

        assertTrue(webp.length < jpeg.length);
        assertEquals("RIFF", new String(webp, 0, 4, StandardCharsets.ISO_8859_1));
        assertEquals("WEBP", new String(webp, 8, 4, StandardCharsets.ISO_8859_1));
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(webp));
        assertNotNull(decoded);
        assertEquals(SquareWebp.SIZE, decoded.getWidth());
        assertEquals(SquareWebp.SIZE, decoded.getHeight());
        assertNear(decoded, 40, 240, 0x00FF00);
        assertNear(decoded, 240, 240, 0xFFFFFF);
        assertNear(decoded, 440, 240, 0xFFFF00);
    }

    @Test
    void rejectsBytesThatAreNotAnImage() {
        assertThrows(IOException.class, () -> SquareWebp.toSquare(new byte[] {1, 2, 3, 4}));
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "jpg", out));
        return out.toByteArray();
    }

    private static void fill(BufferedImage image, int x, int y, int width, int height, int rgb) {
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(rgb));
        graphics.fillRect(x, y, width, height);
        graphics.dispose();
    }

    private static void assertRgb(BufferedImage image, int x, int y, int expected) {
        assertEquals(expected, image.getRGB(x, y) & 0xFFFFFF, "pixel " + x + "," + y);
    }

    private static void assertNear(BufferedImage image, int x, int y, int expected) {
        int actual = image.getRGB(x, y);
        int distance = Math.abs(((actual >> 16) & 0xFF) - ((expected >> 16) & 0xFF))
                + Math.abs(((actual >> 8) & 0xFF) - ((expected >> 8) & 0xFF))
                + Math.abs((actual & 0xFF) - (expected & 0xFF));
        assertTrue(distance < 30, "pixel " + x + "," + y + " was " + Integer.toHexString(actual));
    }

    private static void assertNoColor(BufferedImage image, int rgb) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) == rgb) {
                    throw new AssertionError("unexpected color at " + x + "," + y);
                }
            }
        }
    }

    private static int[] whiteBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = 0;
        int maxY = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) == 0xFFFFFF) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return new int[] {minX, minY, maxX, maxY};
    }
}
