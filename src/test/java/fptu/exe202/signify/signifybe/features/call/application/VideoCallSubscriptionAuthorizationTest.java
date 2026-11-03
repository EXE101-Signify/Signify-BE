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
}
