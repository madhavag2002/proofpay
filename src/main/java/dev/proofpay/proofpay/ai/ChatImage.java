package dev.proofpay.proofpay.ai;

import java.util.Base64;
import java.util.Set;

/** One chat screenshot. Sent to the AI, never stored. */
public record ChatImage(String mediaType, byte[] bytes) {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of("image/png", "image/jpeg", "image/webp");

    public ChatImage {
        if (mediaType == null || !ALLOWED.contains(mediaType)) {
            throw new IllegalArgumentException("Screenshots must be PNG, JPG or WEBP");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("A screenshot is empty");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Each screenshot must be under 5 MB");
        }
    }

    /** Guesses the type from a file name, for uploads and the spike. */
    public static String mediaTypeFor(String fileName) {
        String f = fileName == null ? "" : fileName.toLowerCase();
        if (f.endsWith(".png")) return "image/png";
        if (f.endsWith(".jpg") || f.endsWith(".jpeg")) return "image/jpeg";
        if (f.endsWith(".webp")) return "image/webp";
        return "unknown";
    }

    String dataUrl() {
        return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
