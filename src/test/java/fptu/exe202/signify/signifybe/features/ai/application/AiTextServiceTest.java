package fptu.exe202.signify.signifybe.features.ai.application;

import fptu.exe202.signify.signifybe.features.ai.api.dto.AiPredictionResponse;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallEvent;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import fptu.exe202.signify.signifybe.features.call.infrastructure.persistence.JpaVideoCallRepository;
import fptu.exe202.signify.signifybe.features.chat.application.PrivateChatAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiTextServiceTest {
    private static final CurrentUser SIGNER = new CurrentUser(20, 2, Role.USER);
    private static final CurrentUser VIEWER = new CurrentUser(10, 1, Role.USER);
    private static final CurrentUser STRANGER = new CurrentUser(30, 3, Role.USER);
    private static final CurrentUser OTHER_SIGNER = new CurrentUser(40, 4, Role.USER);

    private JpaVideoCallRepository repository;
    private SimpMessagingTemplate messaging;
    private AiTextService text;
    private VideoCall call;

    @BeforeEach
    void setUp() {
        repository = mock(JpaVideoCallRepository.class);
        messaging = mock(SimpMessagingTemplate.class);
        var calls = new VideoCallService(repository, mock(PrivateChatAccessService.class),
                Clock.systemUTC(), mock(ApplicationEventPublisher.class));
        text = new AiTextService(calls, messaging, Clock.fixed(Instant.ofEpochMilli(1234), ZoneOffset.UTC));
        call = new VideoCall(7, VIEWER.userId(), SIGNER.userId(), 100);
        call.accept(200);
        when(repository.findById(100L)).thenReturn(Optional.of(call));
    }

    @Test
    void acceptedLettersBuildSequenceAndPublishAuthoritativeText() {
        assertEquals("L", text.appendAccepted(100, SIGNER, "L").text());
        text.appendAccepted(100, SIGNER, "E");
        AiTextUpdateEvent update = text.appendAccepted(100, SIGNER, "T");
        assertEquals("LET", update.text());
        assertEquals(AiTextUpdateEvent.TYPE, update.type());
        assertEquals(100, update.callId());
        assertEquals(7, update.conversationId());
        assertEquals(1234, update.timestamp());
        assertNotNull(update.eventId());
        verify(messaging).convertAndSendToUser("10", "/queue/calls/100", update);
        verify(messaging).convertAndSendToUser("20", "/queue/calls/100", update);
    }

    @Test
    void heldGestureAppendsOnceButReleaseAllowsDoubleLetter() {
        var tracker = new StableLetterTracker();
        assertNull(raw(tracker, "L"));
        assertNull(raw(tracker, "L"));
        text.appendAccepted(100, SIGNER, raw(tracker, "L").letter());
        for (int index = 0; index < 10; index++) assertNull(raw(tracker, "L"));
        tracker.noHand(100);
        tracker.noHand(100);
        assertNull(raw(tracker, "L"));
        assertNull(raw(tracker, "L"));
        assertEquals("LL", text.appendAccepted(100, SIGNER, raw(tracker, "L").letter()).text());
    }

    @Test
    void spaceIsSingleAndDoesNotStartEmptyText() {
        assertEquals("", text.space(100, SIGNER).text());
        append(100, SIGNER, "HELLO");
        assertEquals("HELLO ", text.space(100, SIGNER).text());
        assertEquals("HELLO ", text.space(100, SIGNER).text());
    }

    @Test
    void deleteRemovesOneCharacterOrDoesNothingWhenEmpty() {
        assertEquals("", text.delete(100, SIGNER).text());
        append(100, SIGNER, "HELLO");
        assertEquals("HELL", text.delete(100, SIGNER).text());
        text.appendAccepted(100, SIGNER, "O");
        assertEquals("HELLO ", text.space(100, SIGNER).text());
        assertEquals("HELLO", text.delete(100, SIGNER).text());
    }

    @Test
    void clearEmptiesTextAndIsSafeWhenAlreadyEmpty() {
        assertEquals("", text.clear(100, SIGNER).text());
        append(100, SIGNER, "HELLO");
        text.space(100, SIGNER);
        append(100, SIGNER, "WORLD");
        assertEquals("", text.clear(100, SIGNER).text());
        assertEquals("", text.clear(100, SIGNER).text());
    }

    @Test
    void callsHaveSeparateBuffers() {
        var other = new VideoCall(8, 35, OTHER_SIGNER.userId(), 100);
        other.accept(200);
        when(repository.findById(200L)).thenReturn(Optional.of(other));
        append(100, SIGNER, "HELLO");
        append(200, OTHER_SIGNER, "WORLD");
        assertEquals("HELLO ", text.space(100, SIGNER).text());
        assertEquals("WORL", text.delete(200, OTHER_SIGNER).text());
    }

    @Test
    void viewerAndNonparticipantCannotMutateAnyText() {
        assertThrows(VideoCallException.class, () -> text.space(100, VIEWER));
        assertThrows(VideoCallException.class, () -> text.delete(100, VIEWER));
        assertThrows(VideoCallException.class, () -> text.clear(100, VIEWER));
        assertThrows(VideoCallException.class, () -> text.appendAccepted(100, VIEWER, "A"));
        assertThrows(VideoCallException.class, () -> text.space(100, STRANGER));
        verifyNoInteractions(messaging);
    }

    @Test
    void nonActiveStatusesRejectEveryCommand() {
        for (VideoCallStatus status : new VideoCallStatus[]{VideoCallStatus.CALLING,
                VideoCallStatus.COMPLETED, VideoCallStatus.REJECTED,
                VideoCallStatus.MISSED, VideoCallStatus.BUSY}) {
            var inactive = new VideoCall(7, VIEWER.userId(), SIGNER.userId(), 100);
            switch (status) {
                case COMPLETED -> { inactive.accept(200); inactive.complete(300); }
                case REJECTED -> inactive.reject();
                case MISSED -> inactive.miss();
                case BUSY -> inactive.busy();
                default -> { }
            }
            when(repository.findById(99L)).thenReturn(Optional.of(inactive));
            assertThrows(VideoCallException.class, () -> text.space(99, SIGNER));
            assertThrows(VideoCallException.class, () -> text.delete(99, SIGNER));
            assertThrows(VideoCallException.class, () -> text.clear(99, SIGNER));
            assertThrows(VideoCallException.class, () -> text.appendAccepted(99, SIGNER, "A"));
        }
    }

    @Test
    void terminalEventRemovesBuffer() {
        append(100, SIGNER, "A");
        text.onCallStatus(new VideoCallEvent("event", VideoCallEvent.CALL_STATUS_CHANGED,
                100, 7, 10, 20, VideoCallStatus.COMPLETED, 300));
        // A fresh accepted call at the same test key must start empty.
        assertEquals("", text.delete(100, SIGNER).text());
    }

    private AiPredictionResponse raw(StableLetterTracker tracker, String letter) {
        return tracker.observe(100, new AiPredictionResponse(letter, 0.9, 1L));
    }

    private void append(long callId, CurrentUser signer, String letters) {
        for (char letter : letters.toCharArray()) text.appendAccepted(callId, signer, String.valueOf(letter));
    }
}
