package fptu.exe202.signify.signifybe.features.chat.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Client-provided senderId is ignored; the authenticated STOMP principal is authoritative. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TypingRequest(String type) { }
