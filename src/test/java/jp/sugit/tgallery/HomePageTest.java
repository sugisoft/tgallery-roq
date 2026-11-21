package jp.sugit.tgallery;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.testing.RoqAndRoll;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@RoqAndRoll
class HomePageTest {

    @Test
    void homePageShowsProfilePicturesAndAnalytics() {
        given()
                .when().get("/")
                .then().statusCode(200)
                .body(containsString("T-Gallery"))
                .body(containsString("Takeshi Sugita"))
                .body(containsString("関西を中心に夜景、花風景、ハイキング"))
                .body(containsString("Camera: SONY α7IV"))
                .body(containsString("Lens: SIGMA 85/50/35/20-60/16-28mm"))
                .body(containsString("https://yamap.com/users/1758316"))
                .body(containsString("https://www.instagram.com/p/AAAA/"))
                .body(containsString("https://www.instagram.com/p/BBBB/"))
                .body(containsString("/pictures/picture_100.webp"))
                .body(containsString("/pictures/picture_200.webp"))
                .body(containsString("夜景 &quot;quote&quot; &lt;b&gt;"))
                .body(containsString("Powered by Google Analytics and Quarkus Roq"))
                .body(not(containsString("Vercel")))
                .body(not(containsString("/vercel.svg")))
                .body(containsString("rel=\"icon\""))
                .body(containsString("id=G-TEST123456"))
                .body(not(containsString("G-G-TEST123456")))
                .body(not(containsString("access_token")));
    }

    @Test
    void webpEncoderLoadsInTheApplication() throws Exception {
        BufferedImage image = new BufferedImage(30, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.GREEN);
        graphics.fillRect(0, 0, 30, 10);
        graphics.dispose();
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", jpeg);

        byte[] webp = SquareWebp.toSquare(jpeg.toByteArray());

        assertEquals("RIFF", new String(webp, 0, 4, StandardCharsets.ISO_8859_1));
        assertEquals("WEBP", new String(webp, 8, 4, StandardCharsets.ISO_8859_1));
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(webp));
        assertNotNull(decoded);
        assertEquals(SquareWebp.SIZE, decoded.getWidth());
        assertEquals(SquareWebp.SIZE, decoded.getHeight());
    }
}
