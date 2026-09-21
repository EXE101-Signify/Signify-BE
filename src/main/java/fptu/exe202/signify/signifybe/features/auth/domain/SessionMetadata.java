package fptu.exe202.signify.signifybe.features.auth.domain;

/** Untrusted display metadata, never used as proof of identity or authorization. */
public record SessionMetadata(String deviceName, String ipAddress, String userAgent) {
    public SessionMetadata {
        deviceName = bounded(deviceName, 255);
        ipAddress = bounded(ipAddress, 100);
        userAgent = bounded(userAgent, 1024);
    }

    private static String bounded(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String clean = value.replaceAll("[\\p{Cntrl}]", "").strip();
        return clean.substring(0, Math.min(clean.length(), max));
    }
}
