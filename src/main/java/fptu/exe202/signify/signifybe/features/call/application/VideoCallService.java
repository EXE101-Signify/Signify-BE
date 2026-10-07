package fptu.exe202.signify.signifybe.features.call.application;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import fptu.exe202.signify.signifybe.features.call.infrastructure.persistence.JpaVideoCallRepository;
import fptu.exe202.signify.signifybe.features.chat.application.PrivateChatAccessService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class VideoCallService {
    private final JpaVideoCallRepository calls;
    private final PrivateChatAccessService access;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public VideoCallService(JpaVideoCallRepository calls, PrivateChatAccessService access, Clock clock,
                            ApplicationEventPublisher events) {
        this.calls = calls;
        this.access = access;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public VideoCall create(long conversationId, CurrentUser actor) {
        long callerId = userId(actor);
        long receiverId = access.unblockedPeer(conversationId, callerId);
        VideoCall call = calls.save(new VideoCall(conversationId, callerId, receiverId, clock.millis()));
        events.publishEvent(VideoCallEvent.incoming(call, call.getCreatedAt()));
        return call;
    }

    @Transactional
    public VideoCall accept(long callId, CurrentUser actor) {
        VideoCall call = lockedParticipantCall(callId, actor);
        requireReceiver(call, actor.userId());
        long now = clock.millis();
        call.accept(now);
        events.publishEvent(VideoCallEvent.statusChanged(call, now));
        return call;
    }

    @Transactional
    public VideoCall reject(long callId, CurrentUser actor) {
        VideoCall call = lockedParticipantCall(callId, actor);
        requireReceiver(call, actor.userId());
        call.reject();
        events.publishEvent(VideoCallEvent.statusChanged(call, clock.millis()));
        return call;
    }

    @Transactional
    public VideoCall complete(long callId, CurrentUser actor) {
        VideoCall call = lockedParticipantCall(callId, actor);
        long now = clock.millis();
        call.complete(now);
        events.publishEvent(VideoCallEvent.statusChanged(call, now));
        return call;
    }

    @Transactional
    public VideoCall miss(long callId, CurrentUser actor) {
        VideoCall call = lockedParticipantCall(callId, actor);
        if (call.getCallerId() != actor.userId()) throw VideoCallException.forbidden();
        call.miss();
        return call;
    }

    @Transactional
    public VideoCall busy(long callId, CurrentUser actor) {
        VideoCall call = lockedParticipantCall(callId, actor);
        requireReceiver(call, actor.userId());
        call.busy();
        return call;
    }

    /** Returns a call only when the authenticated user belongs to it and it is connected. */
    @Transactional(readOnly = true)
    public VideoCall validateActiveCall(Long callId, CurrentUser actor) {
        long authenticatedUserId = userId(actor);
        if (callId == null || callId <= 0) throw VideoCallException.invalidId();
        VideoCall call = calls.findById(callId).orElseThrow(VideoCallException::notFound);
        requireParticipant(call, authenticatedUserId);
        if (call.getStatus() != VideoCallStatus.ACCEPTED) throw VideoCallException.notActive();
        return call;
    }

    private VideoCall lockedParticipantCall(long callId, CurrentUser actor) {
        long userId = userId(actor);
        VideoCall call = calls.findLockedById(callId).orElseThrow(VideoCallException::notFound);
        requireParticipant(call, userId);
        return call;
    }

    private static void requireParticipant(VideoCall call, long userId) {
        if (call.getCallerId() != userId && call.getReceiverId() != userId) {
            throw VideoCallException.forbidden();
        }
    }

    private static long userId(CurrentUser actor) {
        if (actor == null) throw AuthException.invalidAccessToken();
        return actor.userId();
    }

    private static void requireReceiver(VideoCall call, long userId) {
        if (call.getReceiverId() != userId) throw VideoCallException.forbidden();
    }
}
