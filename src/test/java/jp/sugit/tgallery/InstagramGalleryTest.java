package jp.sugit.tgallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class InstagramGalleryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void keepsPostsShufflesAndLimitsToTwenty() {
        ArrayNode data = mapper.createArrayNode();
        data.add(item("1", "reel", "https://www.instagram.com/reel/ZZZZ/", "https://cdn.example/1.jpg"));
        for (int i = 0; i < 22; i++) {
            data.add(item(Integer.toString(1000 + i), "cap " + i, "https://www.instagram.com/p/P" + i + "/", "https://cdn.example/" + i + ".jpg"));
        }
        data.add(item("nope", null, "https://www.instagram.com/p/SKIP/", "https://cdn.example/skip.jpg"));
        ObjectNode nullCaption = item("42", "", "https://www.instagram.com/p/NULL/", "https://cdn.example/null.jpg");
        nullCaption.putNull("caption");
        data.add(nullCaption);

        List<InstagramGallery.Picture> first = InstagramGallery.select(data, InstagramGallery.LIMIT, new Random(1));
        List<InstagramGallery.Picture> second = InstagramGallery.select(data, InstagramGallery.LIMIT, new Random(1));

        assertEquals(20, first.size());
        assertEquals(first, second);
        assertTrue(first.stream().allMatch(picture -> picture.permalink().contains("/p/")));
        assertTrue(first.stream().noneMatch(picture -> picture.id().equals("1") || picture.id().equals("nope")));
        InstagramGallery.Picture nullCaptionPicture = InstagramGallery.select(mapper.createArrayNode().add(nullCaption), 20, new Random(0)).get(0);
        assertEquals("", nullCaptionPicture.caption());
        assertFalse(first.stream().map(InstagramGallery.Picture::id).toList()
                .equals(data.findValuesAsText("id").stream().filter(id -> id.matches("[0-9]+") && !id.equals("1")).limit(20).toList()));
    }

    @Test
    void gaTagDropsPrefix() {
        assertEquals("TEST123456", GaId.ga4Tag("G-TEST123456"));
        assertEquals("UA-1", GaId.ga4Tag("UA-1"));
        assertEquals(null, GaId.ga4Tag("  "));
    }

    private ObjectNode item(String id, String caption, String permalink, String mediaUrl) {
        ObjectNode item = mapper.createObjectNode();
        item.put("id", id);
        item.put("caption", caption);
        item.put("permalink", permalink);
        item.put("media_url", mediaUrl);
        return item;
    }
}
