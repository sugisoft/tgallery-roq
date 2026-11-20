package jp.sugit.tgallery;

public final class GaId {

    private GaId() {
    }

    /**
     * Roq の ga4 タグは測定 ID の G- より後ろだけを受け取る。
     */
    public static String ga4Tag(String measurementId) {
        if (measurementId == null || measurementId.isBlank()) {
            return null;
        }
        if (measurementId.startsWith("G-")) {
            return measurementId.substring(2);
        }
        return measurementId;
    }
}
