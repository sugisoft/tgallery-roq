package jp.sugit.tgallery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;

/**
 * Serves picture bytes held by {@link Pictures}. When this route has no bytes
 * for the id, the request continues to the static handler.
 */
@ApplicationScoped
public class PictureMemoryRoute {

    @Inject
    Pictures pictures;

    void register(@Observes Router router) {
        // FILE_EXTENSION is ".webp". The backslash keeps the dot from matching any character.
        router.getWithRegex("/picture_([0-9]+)\\" + SquareWebp.FILE_EXTENSION).order(0).handler(ctx -> {
            String id = ctx.pathParam("param0");
            byte[] image = pictures.image(id);
            if (image == null) {
                ctx.next();
                return;
            }
            ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", SquareWebp.CONTENT_TYPE)
                    .end(Buffer.buffer(image));
        });
    }
}
