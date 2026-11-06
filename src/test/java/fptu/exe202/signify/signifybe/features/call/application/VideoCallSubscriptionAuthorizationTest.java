package fptu.exe202.signify.signifybe.features.call.application;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import fptu.exe202.signify.signifybe.features.call.infrastructure.persistence.JpaVideoCallRepository;
import fptu.exe202.signify.signifybe.features.chat.application.PrivateChatAccessService;
import fptu.exe202.signify.signifybe.features.payment.application.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoCallSubscriptionAuthorizationTest {
    @Mock JpaVideoCallRepository repository;
    @Mock PrivateChatAccessService access;
    @Mock ApplicationEventPublisher events;
    @Mock SubscriptionService subscriptions;
    @Mock CallExpiryService expiry;
    VideoCallService service;
    CurrentUser caller = new CurrentUser(10, 1, Role.USER);
    CurrentUser receiver = new CurrentUser(20, 2, Role.USER);

    @BeforeEach void setUp() {
        service = new VideoCallService(repository, access,
                Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC), events, subscriptions, expiry);
    }

    @Test void bothPremiumParticipantsMayCreateCall() {
        when(access.unblockedPeer(5, 10)).thenReturn(20L);
        when(subscriptions.hasActiveSubscription(10)).thenReturn(true);
        when(subscriptions.hasActiveSubscription(20)).thenReturn(true);
        when(repository.save(any(VideoCall.class))).thenAnswer(inv -> {
            VideoCall call = inv.getArgument(0);
            ReflectionTestUtils.setField(call, "id", 99L);
            return call;
        });

        assertEquals(99L, service.create(5, caller).getId());
        verify(repository).lockParticipant(10);
        verify(repository).lockParticipant(20);
    }

    @Test void callerWithoutPremiumCannotCreate() {
        when(access.unblockedPeer(5, 10)).thenReturn(20L);
        assertThrows(VideoCallException.class, () -> service.create(5, caller));
        verify(repository, never()).save(any());
    }

    @Test void receiverWithoutPremiumCannotCreateOrAccept() {
        when(access.unblockedPeer(5, 10)).thenReturn(20L);
        when(subscriptions.hasActiveSubscription(10)).thenReturn(true);
        assertThrows(VideoCallException.class, () -> service.create(5, caller));
        VideoCall call = new VideoCall(5, 10, 20, 100);
        when(repository.findLockedById(99)).thenReturn(Optional.of(call));
        assertThrows(VideoCallException.class, () -> service.accept(99, receiver));
        assertEquals(VideoCallStatus.CALLING, call.getStatus());
    }

    @Test void receiverMayAcceptWhenBothParticipantsHavePremium() {
        VideoCall call = new VideoCall(5, 10, 20, 100);
        ReflectionTestUtils.setField(call, "id", 99L);
        when(repository.findLockedById(99L)).thenReturn(Optional.of(call));
        when(subscriptions.hasActiveSubscription(10L)).thenReturn(true);
        when(subscriptions.hasActiveSubscription(20L)).thenReturn(true);

        assertEquals(VideoCallStatus.ACCEPTED, service.accept(99L, receiver).getStatus());
        verify(events).publishEvent(any(VideoCallEvent.class));
        verify(subscriptions).hasActiveSubscription(10L);
        verify(subscriptions).hasActiveSubscription(20L);
    }

    @Test void receiverCannotAcceptAfterCallerPremiumExpires() {
        VideoCall call = new VideoCall(5, 10, 20, 100);
        when(repository.findLockedById(99L)).thenReturn(Optional.of(call));

        assertThrows(VideoCallException.class, () -> service.accept(99L, receiver));
        assertEquals(VideoCallStatus.CALLING, call.getStatus());
        verify(repository, never()).save(any());
    }

    @Test void simultaneousCallIsRejectedAfterParticipantLocks() {
        when(access.unblockedPeer(5, 10)).thenReturn(20L);
        when(subscriptions.hasActiveSubscription(10)).thenReturn(true);
        when(subscriptions.hasActiveSubscription(20)).thenReturn(true);
        when(repository.hasOngoingCall(eq(10L), anyList())).thenReturn(true);
        assertThrows(VideoCallException.class, () -> service.create(5, caller));
        verify(repository, never()).save(any());
    }

    @Test void expiredPremiumClosesActiveCallAndDeniesReconnect() {
        VideoCall call = new VideoCall(5, 10, 20, 100);
        call.accept(200);
        when(repository.findById(99L)).thenReturn(Optional.of(call));
        when(expiry.closeIfExpired(99)).thenReturn(true);
        assertThrows(VideoCallException.class, () -> service.validateActiveCall(99L, caller));
    }

    @Test void eitherParticipantCanEndRingingCallAndRetrySafely() {
        VideoCall call = new VideoCall(5, 10, 20, 100);
        ReflectionTestUtils.setField(call, "id", 99L);
        when(repository.findLockedById(99L)).thenReturn(Optional.of(call));

        assertEquals(VideoCallStatus.COMPLETED, service.complete(99L, caller).getStatus());
        assertEquals(1_000L, call.getEndedAt());
        assertNull(call.getStartedAt());
        assertSame(call, service.complete(99L, receiver));
        verify(events, times(1)).publishEvent(any(VideoCallEvent.class));
    }

    @Test void endCurrentClosesOnlyActorsOngoingCalls() {
        VideoCall ringing = new VideoCall(5, 10, 20, 100);
        VideoCall connected = new VideoCall(6, 30, 10, 100);
        connected.accept(200);
        VideoCall unrelated = new VideoCall(7, 30, 40, 100);
        ReflectionTestUtils.setField(ringing, "id", 1L);
        ReflectionTestUtils.setField(connected, "id", 2L);
        when(repository.findOngoingIdsForUser(eq(10L), anyList())).thenReturn(java.util.List.of(1L, 2L, 3L));
        when(repository.findLockedById(1L)).thenReturn(Optional.of(ringing));
        when(repository.findLockedById(2L)).thenReturn(Optional.of(connected));
        when(repository.findLockedById(3L)).thenReturn(Optional.of(unrelated));

        assertEquals(2, service.endCurrent(caller).size());
        assertEquals(VideoCallStatus.COMPLETED, ringing.getStatus());
        assertEquals(VideoCallStatus.COMPLETED, connected.getStatus());
        assertEquals(VideoCallStatus.CALLING, unrelated.getStatus());
        verify(events, times(2)).publishEvent(any(VideoCallEvent.class));
    }
}
