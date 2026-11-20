package jp.sugit.tgallery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkiverse.roq.generator.runtime.RoqSelection;

/**
 * Roq Generator reads every {@link RoqSelection} when the application starts,
 * so the picture list is already in memory before pages are crawled.
 */
@ApplicationScoped
public class PictureSelection {

    @Inject
    Pictures pictures;

    @Produces
    @Singleton
    RoqSelection instagramPictures() {
        return pictures.selection();
    }
}
