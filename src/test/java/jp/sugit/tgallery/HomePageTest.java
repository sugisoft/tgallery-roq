package jp.sugit.tgallery;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

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
                .body(containsString("/picture_100.jpg"))
                .body(containsString("/picture_200.jpg"))
                .body(containsString("夜景 &quot;quote&quot; &lt;b&gt;"))
                .body(containsString("Powered by Google Analytics and Quarkus Roq"))
                .body(not(containsString("Vercel")))
                .body(not(containsString("/vercel.svg")))
                .body(containsString("rel=\"icon\""))
                .body(containsString("id=G-TEST123456"))
                .body(not(containsString("G-G-TEST123456")))
                .body(not(containsString("access_token")));
    }
}
