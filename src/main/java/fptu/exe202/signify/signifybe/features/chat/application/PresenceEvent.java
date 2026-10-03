package fptu.exe202.signify.signifybe.features.chat.application;

public record PresenceEvent(String eventId, String type, long userId, long timestamp) {
    public static PresenceEvent of(long userId, boolean online, long timestamp) {
        String type = online ? "PRESENCE_ONLINE" : "PRESENCE_OFFLINE";
        return new PresenceEvent(type + ":" + userId + ":" + timestamp, type, userId, timestamp);
    }
}
