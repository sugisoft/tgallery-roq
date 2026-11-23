package jp.sugit.tgallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.VertxOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.core.http.HttpClient;
import io.vertx.mutiny.core.http.HttpServer;
import io.vertx.mutiny.core.http.HttpServerRequest;
import io.vertx.mutiny.core.http.HttpServerResponse;

class InstagramGalleryFetchTest {

    private Vertx vertx;
    private HttpServer server;
    private HttpClient client;
    private int port;
    private byte[] png;
    private int mediaStatus = 200;
    private int imageStatus = 200;
    private int imageCount = 1;
    private boolean holdUntilAllArrive;
    private final List<HttpServerResponse> held = new ArrayList<>();
    private boolean mediaWithoutData;
    private String accept;
    private String userAgent;
    private String query;

    @BeforeEach
    void start() throws IOException {
        png = png();
        vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(1));
        server = vertx.createHttpServer().requestHandler(this::handle).listenAndAwait(0, "127.0.0.1");
        port = server.actualPort();
        client = InstagramGallery.createClient(vertx);
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

    @Test
    void downloadsEveryImageBeforeTheFirstResponseReturns() {
        imageCount = 8;
        holdUntilAllArrive = true;

        InstagramGallery.Fetched fetched = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> gallery().fetchIntoMemory("test-token"));

        assertEquals(imageCount, fetched.pictures().size());
        assertEquals(imageCount, fetched.images().size());
        for (InstagramGallery.Picture picture : fetched.pictures()) {
            byte[] webp = fetched.images().get(picture.id());
            assertTrue(webp.length > 12);
            assertEquals("RIFF", new String(webp, 0, 4, StandardCharsets.US_ASCII));
        }
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
            String body = mediaWithoutData ? "{\"error\":{}}" : mediaJson();
            request.response().putHeader("Content-Type", "application/json").endAndForget(body);
            return;
        }
        if (request.path().startsWith("/cdn/")) {
            String id = request.path().substring("/cdn/".length());
            request.response()
                    .setStatusCode(302)
                    .putHeader("Location", "http://127.0.0.1:" + port + "/files/" + id + ".png")
                    .endAndForget();
            return;
        }
        if (request.path().startsWith("/files/") && request.path().endsWith(".png")) {
            userAgent = request.getHeader("User-Agent");
            if (imageStatus != 200) {
                request.response().setStatusCode(imageStatus).endAndForget("missing");
                return;
            }
            if (holdUntilAllArrive) {
                hold(request.response());
                return;
            }
            request.response().endAndForget(Buffer.buffer(png));
            return;
        }
        request.response().setStatusCode(404).endAndForget();
    }

    private String mediaJson() {
        if (imageCount == 1) {
            return "{\"data\":[{\"id\":\"1000\",\"caption\":\"hello\",\"permalink\":\"https://www.instagram.com/p/abc/\",\"media_url\":\"http://127.0.0.1:"
                    + port + "/cdn/1000\"}]}";
        }
        StringBuilder json = new StringBuilder("{\"data\":[");
        for (int i = 0; i < imageCount; i++) {
            if (i > 0) {
                json.append(',');
            }
            String id = Integer.toString(1000 + i);
            json.append("{\"id\":\"").append(id)
                    .append("\",\"caption\":\"cap\",\"permalink\":\"https://www.instagram.com/p/")
                    .append(id)
                    .append("/\",\"media_url\":\"http://127.0.0.1:")
                    .append(port)
                    .append("/cdn/")
                    .append(id)
                    .append("\"}");
        }
        return json.append("]}").toString();
    }

    /**
     * Holds image responses until every file request has arrived.
     * A client that downloads one picture at a time never reaches that count, and each
     * request fails after three seconds.
     */
    private void hold(HttpServerResponse response) {
        List<HttpServerResponse> ready;
        synchronized (held) {
            held.add(response);
            ready = held.size() == imageCount ? List.copyOf(held) : List.of();
        }
        if (!ready.isEmpty()) {
            for (HttpServerResponse pending : ready) {
                pending.endAndForget(Buffer.buffer(png));
            }
            return;
        }
        vertx.setTimer(3_000, id -> {
            if (!response.ended()) {
                response.setStatusCode(503).endAndForget("not concurrent");
            }
        });
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
