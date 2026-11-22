package jp.sugit.tgallery;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Named("gallery")
public class GalleryConfig {

    @ConfigProperty(name = "gallery.ga.id", defaultValue = "")
    String gaId;

    public String ga4Tag() {
        return GaId.ga4Tag(gaId);
    }
}
