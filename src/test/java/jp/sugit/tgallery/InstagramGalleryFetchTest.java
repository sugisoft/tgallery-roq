package jp.sugit.tgallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.core.http.HttpClient;
import io.vertx.mutiny.core.http.HttpServer;
import io.vertx.mutiny.core.http.HttpServerRequest;

class InstagramGalleryFetchTest {

    private Vertx vertx;
    private HttpServer server;
    private HttpClient client;
    private int port;
    private byte[] png;
    private int mediaStatus = 200;
    private int imageStatus = 200;
    private boolean mediaWithoutData;
    private String accept;
    private String userAgent;
    private String query;

    @BeforeEach
    void start() throws IOException {
        png = png();
        vertx = Vertx.vertx();
        server = vertx.createHttpServer().requestHandler(this::handle).listenAndAwait(0, "127.0.0.1");
        port = server.actualPort();
        client = vertx.createHttpClient(new HttpClientOptions().setConnectTimeout(20_000));
    }

    @AfterEach
    void stop() {
        if (client != null) {
            client.closeAndAwait();
        }
        if (server != null) {
            server.closeAndAwait();
        }
        if (vertx != null) {
            vertx.closeAndAwait();
        }
    }

    @Test
    void downloadsTheImageBehindARedirect() throws Exception {
        InstagramGallery.Fetched fetched = gallery().fetchIntoMemory("test-token");

        assertEquals(1, fetched.pictures().size());
        InstagramGallery.Picture picture = fetched.pictures().get(0);
        assertEquals("1000", picture.id());
        assertEquals("hello", picture.caption());
        assertEquals("https://www.instagram.com/p/abc/", picture.permalink());
        assertEquals(SquareWebp.fileName("1000"), picture.mediaUrl());
        byte[] webp = fetched.images().get("1000");
        assertTrue(webp.length > 12);
        assertEquals("RIFF", new String(webp, 0, 4, StandardCharsets.US_ASCII));
        assertEquals("WEBP", new String(webp, 8, 4, StandardCharsets.US_ASCII));
        assertEquals("application/json", accept);
        assertEquals("tgallery-roq", userAgent);
        assertTrue(query.contains("fields=id%2Ccaption%2Cmedia_url%2Cpermalink"));
        assertTrue(query.contains("access_token=test-token"));
    }

    @Test
    void mediaStatusIsReported() {
        mediaStatus = 503;

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> gallery().fetchIntoMemory("test-token"));

        assertEquals("Instagram media request failed: HTTP 503", error.getMessage());
    }

    @Test
    void missingDataArrayUsesTheStatus() {
        mediaWithoutData = true;

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> gallery().fetchIntoMemory("test-token"));

        assertEquals("Instagram media request failed: HTTP 200", error.getMessage());
    }

    @Test
    void imageStatusIsReported() {
        imageStatus = 404;

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> gallery().fetchIntoMemory("test-token"));

        assertEquals("Image download failed for id 1000: HTTP 404", error.getMessage());
    }

    private InstagramGallery gallery() {
        return new InstagramGallery(client, "http://127.0.0.1:" + port + "/me/media");
    }

    private void handle(HttpServerRequest request) {
        if ("/me/media".equals(request.path())) {
            accept = request.getHeader("Accept");
            query = request.query();
            if (mediaStatus != 200) {
                request.response().setStatusCode(mediaStatus).endAndForget("nope");
                return;
            }
            String body = mediaWithoutData
                    ? "{\"error\":{}}"
                    : "{\"data\":[{\"id\":\"1000\",\"caption\":\"hello\",\"permalink\":\"https://www.instagram.com/p/abc/\",\"media_url\":\"http://127.0.0.1:"
                            + port + "/cdn/1000\"}]}";
            request.response().putHeader("Content-Type", "application/json").endAndForget(body);
            return;
        }
        if ("/cdn/1000".equals(request.path())) {
            request.response()
                    .setStatusCode(302)
                    .putHeader("Location", "http://127.0.0.1:" + port + "/files/1000.png")
                    .endAndForget();
            return;
        }
        if ("/files/1000.png".equals(request.path())) {
            userAgent = request.getHeader("User-Agent");
            if (imageStatus != 200) {
                request.response().setStatusCode(imageStatus).endAndForget("missing");
                return;
            }
            request.response().endAndForget(Buffer.buffer(png));
            return;
        }
        request.response().setStatusCode(404).endAndForget();
    }

    private static byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("PNG writer is missing");
        }
        return out.toByteArray();
    }
}
