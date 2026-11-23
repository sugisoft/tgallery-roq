package jp.sugit.tgallery;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.core.http.HttpClient;
import io.vertx.mutiny.core.http.HttpClientRequest;

public final class InstagramGallery {

    static final int LIMIT = 20;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration MEDIA_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration IMAGE_TIMEOUT = Duration.ofSeconds(60);
    private static final String MEDIA_ENDPOINT = "https://graph.instagram.com/me/media";

    private final HttpClient client;
    private final String mediaEndpoint;

    public InstagramGallery(HttpClient client) {
        this(client, MEDIA_ENDPOINT);
    }

    InstagramGallery(HttpClient client, String mediaEndpoint) {
        this.client = client;
        this.mediaEndpoint = mediaEndpoint;
    }

    public record Picture(String id, String caption, String permalink, String mediaUrl) {
    }

    public static List<Picture> select(JsonNode data, int limit, Random random) {
        if (data == null || !data.isArray()) {
            throw new IllegalArgumentException("Instagram response has no data array");
        }
        List<Picture> posts = new ArrayList<>();
        for (JsonNode item : data) {
            String permalink = item.path("permalink").asText("");
            if (!permalink.contains("/p/")) {
                continue;
            }
            String id = item.path("id").asText("");
            if (!id.matches("[0-9]+")) {
                continue;
            }
            JsonNode captionNode = item.get("caption");
            String caption = captionNode == null || captionNode.isNull() ? "" : captionNode.asText("");
            posts.add(new Picture(id, caption, permalink, SquareWebp.fileName(id)));
        }
        // Java の sort は乱数 Comparator を受け付けないので、意図どおりの無作為順は shuffle で作る。
        Collections.shuffle(posts, random);
        if (posts.size() > limit) {
            return List.copyOf(posts.subList(0, limit));
        }
        return List.copyOf(posts);
    }

    public record Fetched(List<Picture> pictures, Map<String, byte[]> images) {
    }

    /**
     * Downloads the selected images into memory as 480px square WebP.
     * Nothing is written under {@code public/}, so Quarkus dev mode does not treat the download as a source change.
     */
    public Fetched fetchIntoMemory(String token) throws IOException, InterruptedException {
        JsonNode data = media(token);
        List<Picture> pictures = select(data, LIMIT, new Random());
        Map<String, byte[]> images = new LinkedHashMap<>();
        for (Picture picture : pictures) {
            images.put(picture.id(), downloadBytes(picture, data));
        }
        return new Fetched(List.copyOf(pictures), Map.copyOf(images));
    }

    private JsonNode media(String token) throws IOException, InterruptedException {
        String query = "fields=" + URLEncoder.encode("id,caption,media_url,permalink", StandardCharsets.UTF_8)
                + "&access_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        Reply reply = get(mediaEndpoint + "?" + query, MEDIA_TIMEOUT,
                request -> request.putHeader("Accept", "application/json"));
        if (reply.statusCode() != 200) {
            throw new IllegalStateException("Instagram media request failed: HTTP " + reply.statusCode());
        }
        JsonNode root = MAPPER.readTree(reply.body().toString(StandardCharsets.UTF_8));
        if (!root.path("data").isArray()) {
            throw new IllegalStateException("Instagram media request failed: HTTP " + reply.statusCode());
        }
        return root.get("data");
    }

    private byte[] downloadBytes(Picture picture, JsonNode data) throws IOException, InterruptedException {
        String source = null;
        for (JsonNode item : data) {
            if (picture.id().equals(item.path("id").asText())) {
                source = item.path("media_url").asText(null);
                break;
            }
        }
        if (source == null || source.isBlank()) {
            throw new IllegalStateException("Instagram image is missing for id " + picture.id());
        }
        Reply reply = get(source, IMAGE_TIMEOUT, request -> request.putHeader("User-Agent", "tgallery-roq"));
        if (reply.statusCode() != 200) {
            throw new IllegalStateException("Image download failed for id " + picture.id() + ": HTTP " + reply.statusCode());
        }
        try {
            return SquareWebp.toSquare(reply.body().getBytes());
        } catch (IOException e) {
            throw new IOException("Could not convert image " + picture.id() + " to WebP", e);
        }
    }

    /** GET that follows redirects. {@code timeout} bounds the whole call, including the body. */
    private Reply get(String uri, Duration timeout, Consumer<HttpClientRequest> configure) throws IOException, InterruptedException {
        // request(method, string) sets the path only and leaves the host at localhost:80.
        Uni<Reply> call = client.request(new RequestOptions()
                        .setMethod(HttpMethod.GET)
                        .setAbsoluteURI(uri)
                        .setFollowRedirects(true)
                        .setTimeout(timeout.toMillis()))
                .flatMap(request -> {
                    configure.accept(request);
                    return request.send();
                })
                .flatMap(response -> response.body()
                        .map(body -> new Reply(response.statusCode(), body == null ? Buffer.buffer() : body)));
        return await(call, timeout);
    }

    private static <T> T await(Uni<T> uni, Duration timeout) throws IOException, InterruptedException {
        try {
            return uni.await().atMost(timeout);
        } catch (CompletionException e) {
            throw failure(e.getCause() == null ? e : e.getCause());
        } catch (RuntimeException e) {
            throw failure(e);
        }
    }

    private static RuntimeException failure(Throwable cause) throws IOException, InterruptedException {
        Throwable current = cause;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof InterruptedException interrupted) {
            throw interrupted;
        }
        if (current instanceof IOException io) {
            throw io;
        }
        if (current instanceof IllegalStateException illegal) {
            throw illegal;
        }
        throw new IOException(current.getMessage(), current);
    }

    private record Reply(int statusCode, Buffer body) {
    }
}
