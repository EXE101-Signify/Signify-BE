package fptu.exe202.signify.signifybe.features.call.application;

import tools.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebRtcSignalingServiceTest {
    private static final CurrentUser CALLER = new CurrentUser(10, 1, Role.USER);
    private static final CurrentUser RECEIVER = new CurrentUser(20, 2, Role.USER);
    private final ObjectMapper json = new ObjectMapper();
    private VideoCallService calls;
    private SimpMessagingTemplate messaging;
    private WebRtcSignalingService service;
    private VideoCall call;

    @BeforeEach
    void setUp() {
        calls = mock(VideoCallService.class);
        messaging = mock(SimpMessagingTemplate.class);
        service = new WebRtcSignalingService(calls, messaging,
                Clock.fixed(Instant.ofEpochMilli(1234), ZoneOffset.UTC));
        call = mock(VideoCall.class);
        when(call.getCallerId()).thenReturn(10L);
        when(call.getReceiverId()).thenReturn(20L);
        when(calls.validateActiveCall(eq(42L), any(CurrentUser.class))).thenReturn(call);
    }

    @Test
    void callerOfferAndIceReachOnlyReceiver() throws Exception {
        service.relay(42, CALLER, signal("WEBRTC_OFFER", "{\"type\":\"offer\",\"sdp\":\"v=0\"}"));
        service.relay(42, CALLER, signal("WEBRTC_ICE_CANDIDATE",
                "{\"candidate\":\"candidate:1\",\"sdpMid\":\"0\",\"sdpMLineIndex\":0}"));
        var events = org.mockito.ArgumentCaptor.forClass(WebRtcSignalEvent.class);
        verify(messaging, times(2)).convertAndSendToUser(eq("20"), eq("/queue/calls/42/webrtc"), events.capture());
        assertEquals("WEBRTC_OFFER", events.getAllValues().get(0).type());
        assertEquals("WEBRTC_ICE_CANDIDATE", events.getAllValues().get(1).type());
        for (WebRtcSignalEvent event : events.getAllValues()) {
            assertEquals(10, event.senderId());
            assertEquals(20, event.receiverId());
            assertEquals(42, event.callId());
            assertEquals(1234, event.timestamp());
            assertNotNull(event.eventId());
        }
    }

    @Test
    void receiverReadyAnswerAndIceReachOnlyCaller() throws Exception {
        service.relay(42, RECEIVER, signal("WEBRTC_READY", "{}"));
        service.relay(42, RECEIVER, signal("WEBRTC_ANSWER", "{\"type\":\"answer\",\"sdp\":\"v=0\"}"));
        service.relay(42, RECEIVER, signal("WEBRTC_ICE_CANDIDATE",
                "{\"candidate\":\"candidate:2\",\"sdpMid\":null,\"sdpMLineIndex\":0}"));
        verify(messaging, times(3)).convertAndSendToUser(eq("10"), eq("/queue/calls/42/webrtc"), any(WebRtcSignalEvent.class));
    }

    @Test
    void nonparticipantMissingOrInactiveCallCannotSignal() throws Exception {
        for (VideoCallException failure : new VideoCallException[]{VideoCallException.forbidden(),
                VideoCallException.notFound(), VideoCallException.notActive()}) {
            reset(calls);
            doThrow(failure).when(calls).validateActiveCall(42L, CALLER);
            assertThrows(VideoCallException.class,
                    () -> service.relay(42, CALLER, signal("WEBRTC_OFFER", "{\"type\":\"offer\",\"sdp\":\"v=0\"}")));
        }
        verifyNoInteractions(messaging);
    }

    @Test
    void endedWhileProcessingIsNotForwarded() throws Exception {
        when(calls.validateActiveCall(42L, CALLER)).thenReturn(call).thenThrow(VideoCallException.notActive());
        assertThrows(VideoCallException.class,
                () -> service.relay(42, CALLER, signal("WEBRTC_OFFER", "{\"type\":\"offer\",\"sdp\":\"v=0\"}")));
        verifyNoInteractions(messaging);
    }

    @Test
    void forgedIdentityMalformedPayloadAndWrongRoleAreRejected() throws Exception {
        for (String body : new String[]{
                "{\"type\":\"WEBRTC_OFFER\",\"payload\":{\"type\":\"offer\",\"sdp\":\"v=0\"},\"senderId\":20}",
                "{\"type\":\"WEBRTC_OFFER\",\"payload\":{\"type\":\"answer\",\"sdp\":\"v=0\"}}",
                "{\"type\":\"WEBRTC_ICE_CANDIDATE\",\"payload\":{\"candidate\":\"x\",\"sdpMid\":0,\"sdpMLineIndex\":0}}",
                "{\"type\":\"BOGUS\",\"payload\":{}}"
        }) {
            assertThrows(IllegalArgumentException.class, () -> service.relay(42, CALLER, json.readTree(body)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> service.relay(42, RECEIVER, signal("WEBRTC_OFFER", "{\"type\":\"offer\",\"sdp\":\"v=0\"}")));
        assertThrows(IllegalArgumentException.class,
                () -> service.relay(42, CALLER, signal("WEBRTC_ANSWER", "{\"type\":\"answer\",\"sdp\":\"v=0\"}")));
        verifyNoInteractions(messaging);
    }

    private JsonNode signal(String type, String payload) throws Exception {
        return json.readTree("{\"type\":\"" + type + "\",\"payload\":" + payload + "}");
    }
}
