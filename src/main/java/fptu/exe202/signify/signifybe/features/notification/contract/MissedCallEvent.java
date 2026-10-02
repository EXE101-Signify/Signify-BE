package fptu.exe202.signify.signifybe.features.notification.contract;

public record MissedCallEvent(long callId, long receiverId, long callerId, String callerDisplayName) { }
