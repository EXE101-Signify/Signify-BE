package fptu.exe202.signify.signifybe.features.call.application;

import tools.jackson.databind.JsonNode;

/** Transient, call-scoped signaling metadata; media flows directly between browsers. */
public record WebRtcSignalEvent(String eventId, String type, long callId, long senderId,
                                long receiverId, JsonNode payload, long timestamp) { }
