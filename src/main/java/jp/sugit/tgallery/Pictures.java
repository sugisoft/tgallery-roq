package jp.sugit.tgallery;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.roq.generator.runtime.RoqSelection;
import io.quarkiverse.roq.generator.runtime.SelectedPath;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.Startup;

import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Picture list held in memory for {@code cdi:pictures}. Quarkus loads it at startup,
 * which is also when {@code quarkus:run} generates the static site.
 */
@Startup
@Named("pictures")
@ApplicationScoped
public class Pictures {

    private static final String FIXTURE = "fixtures/pictures.json";
    private static final Logger LOG = Logger.getLogger(Pictures.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Object lock = new Object();
    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private boolean loaded;

    public record Picture(String id, String caption, String permalink, String media_url) {
    }

    private final static String IG_TOKEN = ConfigProvider.getConfig().getValue("ig.token", String.class);

    @PostConstruct
    void loadOnStartup() {
        ensureLoaded();
    }

    public List<Picture> getItems() {
        ensureLoaded();
        return snapshot.items();
    }

    public byte[] image(String id) {
        ensureLoaded();
        return snapshot.images().get(id);
    }

    public RoqSelection selection() {
        ensureLoaded();
        if (snapshot.images().isEmpty()) {
            return new RoqSelection(List.of());
        }
        List<SelectedPath> paths = new ArrayList<>();
        for (Picture picture : snapshot.items()) {
            if (!snapshot.images().containsKey(picture.id())) {
                continue;
            }
            paths.add(SelectedPath.builder()
                    .path(picture.media_url())
                    .outputPath(picture.media_url())
                    .build());
        }
        return new RoqSelection(paths);
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (lock) {
            if (loaded) {
                return;
            }
            snapshot = load();
            loaded = true;
        }
    }

    private static Snapshot load() {
        if (LaunchMode.current() == LaunchMode.TEST) {
            Snapshot fixture = readResource(FIXTURE);
            LOG.infof("Loaded %d pictures into memory from the test fixture", fixture.items().size());
            return fixture;
        }
        if (Pictures.IG_TOKEN == null || Pictures.IG_TOKEN.isBlank()) {
            throw new IllegalStateException("IG_TOKEN is required");
        }
        return fetchInstagram(Pictures.IG_TOKEN);
    }

    private static Snapshot fetchInstagram(String token) {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        try (client) {
            InstagramGallery.Fetched fetched = new InstagramGallery(client).fetchIntoMemory(token);
            List<Picture> items = fetched.pictures().stream()
                    .map(picture -> new Picture(picture.id(), picture.caption(), picture.permalink(), picture.mediaUrl()))
                    .toList();
            LOG.infof("Loaded %d Instagram pictures into memory", items.size());
            return new Snapshot(items, fetched.images());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Instagram fetch was interrupted", e);
        }
    }

    private static Snapshot readResource(String name) {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
        if (in == null) {
            throw new IllegalStateException("Missing test fixture " + name);
        }
        try (in) {
            return read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Snapshot read(InputStream in) throws IOException {
        JsonNode items = MAPPER.readTree(in).path("items");
        if (!items.isArray()) {
            throw new IllegalStateException("Pictures file has no items array");
        }
        List<Picture> pictures = new ArrayList<>();
        for (JsonNode item : items) {
            pictures.add(new Picture(
                    item.path("id").asText(""),
                    item.path("caption").asText(""),
                    item.path("permalink").asText(""),
                    item.path("media_url").asText("")));
        }
        return new Snapshot(List.copyOf(pictures), Map.of());
    }

    private record Snapshot(List<Picture> items, Map<String, byte[]> images) {
        private static final Snapshot EMPTY = new Snapshot(List.of(), Map.of());
    }
}
