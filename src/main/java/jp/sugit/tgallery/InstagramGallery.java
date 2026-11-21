package jp.sugit.tgallery;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class InstagramGallery {

    static final int LIMIT = 20;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient client;

    public InstagramGallery(HttpClient client) {
        this.client = client;
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
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://graph.instagram.com/me/media?" + query))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Instagram media request failed: HTTP " + response.statusCode());
        }
        JsonNode root = MAPPER.readTree(response.body());
        if (!root.path("data").isArray()) {
            throw new IllegalStateException("Instagram media request failed: HTTP " + response.statusCode());
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
        HttpRequest request = HttpRequest.newBuilder(URI.create(source))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "tgallery-roq")
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Image download failed for id " + picture.id() + ": HTTP " + response.statusCode());
        }
        try {
            return SquareWebp.toSquare(response.body());
        } catch (IOException e) {
            throw new IOException("Could not convert image " + picture.id() + " to WebP", e);
        }
    }
}
