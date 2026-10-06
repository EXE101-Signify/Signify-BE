package fptu.exe202.signify.signifybe.features.call.application;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import fptu.exe202.signify.signifybe.features.call.infrastructure.persistence.JpaVideoCallRepository;
import fptu.exe202.signify.signifybe.features.chat.application.PrivateChatAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

class VideoCallServiceTest {
    private static final long NOW = 1_720_000_000_000L;
    private static final CurrentUser CALLER = new CurrentUser(10, 1, Role.USER);
    private static final CurrentUser RECEIVER = new CurrentUser(20, 2, Role.USER);
    private static final CurrentUser STRANGER = new CurrentUser(30, 3, Role.USER);

    private JpaVideoCallRepository calls;
    private PrivateChatAccessService access;
    private ApplicationEventPublisher events;
    private VideoCallService service;
    private VideoCall call;

    @BeforeEach
    void setUp() {
        calls = mock(JpaVideoCallRepository.class);
        access = mock(PrivateChatAccessService.class);
        events = mock(ApplicationEventPublisher.class);
        service = new VideoCallService(calls, access,
                Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC), events);
        call = new VideoCall(7, CALLER.userId(), RECEIVER.userId(), NOW - 100);
        ReflectionTestUtils.setField(call, "id", 42L);
        when(calls.findLockedById(42L)).thenReturn(Optional.of(call));
    }

    @Test
    void createsCallingUsingAuthenticatedCallerAndConversationPeer() {
        when(access.unblockedPeer(7, CALLER.userId())).thenReturn(RECEIVER.userId());
        when(calls.save(any(VideoCall.class))).thenAnswer(invocation -> {
            VideoCall saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });
        VideoCall created = service.create(7, CALLER);
        assertEquals(VideoCallStatus.CALLING, created.getStatus());
        assertEquals(CALLER.userId(), created.getCallerId());
        assertEquals(RECEIVER.userId(), created.getReceiverId());
        assertEquals(7, created.getConversationId());
        assertEquals(NOW, created.getCreatedAt());
        verify(access).unblockedPeer(7, CALLER.userId());
        VideoCallEvent event = publishedEvent();
        assertEquals(VideoCallEvent.INCOMING_CALL, event.type());
        assertEquals(42, event.callId());
        assertEquals(7, event.conversationId());
        assertEquals(CALLER.userId(), event.callerId());
        assertEquals(RECEIVER.userId(), event.receiverId());
        assertEquals(VideoCallStatus.CALLING, event.status());
        assertEquals(NOW, event.timestamp());
        assertNotNull(event.eventId());
    }

    @Test
    void receiverAcceptsAndStartedAtIsSet() {
        VideoCall result = service.accept(42, RECEIVER);
        assertEquals(VideoCallStatus.ACCEPTED, result.getStatus());
        assertEquals(NOW, result.getStartedAt());
        assertStatusEvent(VideoCallStatus.ACCEPTED);
    }

    @Test
    void receiverRejectsCalling() {
        assertEquals(VideoCallStatus.REJECTED, service.reject(42, RECEIVER).getStatus());
        assertStatusEvent(VideoCallStatus.REJECTED);
    }

    @Test
    void callerEndsAcceptedAndEndedAtIsSet() {
        call.accept(NOW - 50);
        VideoCall result = service.complete(42, CALLER);
        assertEquals(VideoCallStatus.COMPLETED, result.getStatus());
        assertEquals(NOW, result.getEndedAt());
        assertStatusEvent(VideoCallStatus.COMPLETED);
    }

    @Test
    void callerMarksUnansweredCallMissed() {
        assertEquals(VideoCallStatus.MISSED, service.miss(42, CALLER).getStatus());
    }

    @Test
    void receiverMayMarkCallingAsBusy() {
        assertEquals(VideoCallStatus.BUSY, service.busy(42, RECEIVER).getStatus());
    }

    @Test
    void completedCannotBecomeAccepted() {
        call.accept(NOW - 50);
        call.complete(NOW - 10);
        assertThrows(VideoCallException.class, () -> service.accept(42, RECEIVER));
    }

    @Test
    void rejectedCannotBecomeAccepted() {
        call.reject();
        assertThrows(VideoCallException.class, () -> service.accept(42, RECEIVER));
    }

    @Test
    void missedCannotBecomeAccepted() {
        call.miss();
        assertThrows(VideoCallException.class, () -> service.accept(42, RECEIVER));
    }

    @Test
    void nonParticipantCannotAcceptRejectOrEnd() {
        assertThrows(VideoCallException.class, () -> service.accept(42, STRANGER));
        assertThrows(VideoCallException.class, () -> service.reject(42, STRANGER));
        assertThrows(VideoCallException.class, () -> service.complete(42, STRANGER));
        assertEquals(VideoCallStatus.CALLING, call.getStatus());
    }

    @Test
    void unauthenticatedLifecycleOperationIsRejected() {
        assertThrows(AuthException.class, () -> service.create(7, null));
        assertThrows(AuthException.class, () -> service.accept(42, null));
        assertThrows(AuthException.class, () -> service.reject(42, null));
        assertThrows(AuthException.class, () -> service.complete(42, null));
    }

    @Test
    void nonexistentCallIsRejected() {
        assertThrows(VideoCallException.class, () -> service.accept(999, RECEIVER));
    }

    @Test
    void changingCallIdDoesNotGrantAccessToAnotherCall() {
        VideoCall other = new VideoCall(8, STRANGER.userId(), 40, NOW);
        when(calls.findLockedById(43L)).thenReturn(Optional.of(other));
        assertThrows(VideoCallException.class, () -> service.accept(43, RECEIVER));
        assertEquals(VideoCallStatus.CALLING, other.getStatus());
    }

    @Test
    void callerCannotAcceptOrRejectOwnCall() {
        assertThrows(VideoCallException.class, () -> service.accept(42, CALLER));
        assertThrows(VideoCallException.class, () -> service.reject(42, CALLER));
    }

    private VideoCallEvent publishedEvent() {
        var captor = org.mockito.ArgumentCaptor.forClass(VideoCallEvent.class);
        verify(events, times(1)).publishEvent(captor.capture());
        return captor.getValue();
    }

    private void assertStatusEvent(VideoCallStatus status) {
        VideoCallEvent event = publishedEvent();
        assertEquals(VideoCallEvent.CALL_STATUS_CHANGED, event.type());
        assertEquals(7, event.conversationId());
        assertEquals(CALLER.userId(), event.callerId());
        assertEquals(RECEIVER.userId(), event.receiverId());
        assertEquals(status, event.status());
        assertEquals(NOW, event.timestamp());
        assertNotNull(event.eventId());
    }

    @Test
    void duplicateAcceptAndRejectAfterAcceptPublishNoFurtherEvents() {
        service.accept(42, RECEIVER);
        assertThrows(VideoCallException.class, () -> service.accept(42, RECEIVER));
        assertThrows(VideoCallException.class, () -> service.reject(42, RECEIVER));
        verify(events, times(1)).publishEvent(isA(VideoCallEvent.class));
    }

    @Test
    void endingCallingCallPublishesNothing() {
        assertThrows(VideoCallException.class, () -> service.complete(42, CALLER));
        verifyNoInteractions(events);
    }

    @Test
    void nonparticipantPublishesNothing() {
        assertThrows(VideoCallException.class, () -> service.accept(42, STRANGER));
        assertThrows(VideoCallException.class, () -> service.reject(42, STRANGER));
        assertThrows(VideoCallException.class, () -> service.complete(42, STRANGER));
        verifyNoInteractions(events);
    }
}
