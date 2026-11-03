package fptu.exe202.signify.signifybe.features.call.application;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import fptu.exe202.signify.signifybe.features.call.infrastructure.persistence.JpaVideoCallRepository;
import fptu.exe202.signify.signifybe.features.chat.application.PrivateChatAccessService;
import fptu.exe202.signify.signifybe.features.payment.application.SubscriptionService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class VideoCallService {
    private final JpaVideoCallRepository calls;
    private final PrivateChatAccessService access;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final SubscriptionService subscriptions;
    private final CallExpiryService expiry;

    public VideoCallService(JpaVideoCallRepository calls, PrivateChatAccessService access, Clock clock,
                            ApplicationEventPublisher events, SubscriptionService subscriptions,
                            CallExpiryService expiry) {
        this.calls = calls;
        this.access = access;
        this.clock = clock;
        this.events = events;
        this.subscriptions = subscriptions;
        this.expiry = expiry;
    }

    @Transactional
    public VideoCall create(long conversationId, CurrentUser actor) {
        long callerId = userId(actor);
        long receiverId = access.unblockedPeer(conversationId, callerId);
        calls.lockParticipant(Math.min(callerId, receiverId));
        calls.lockParticipant(Math.max(callerId, receiverId));
        requirePremium(callerId, receiverId);
        List<VideoCallStatus> ongoing = List.of(VideoCallStatus.CALLING, VideoCallStatus.ACCEPTED);
        if (calls.hasOngoingCall(callerId, ongoing) || calls.hasOngoingCall(receiverId, ongoing))
            throw VideoCallException.alreadyInCall();
        VideoCall call = calls.save(new VideoCall(conversationId, callerId, receiverId, clock.millis()));
        events.publishEvent(VideoCallEvent.incoming(call, call.getCreatedAt()));
        return call;
    }

    @Transactional
    public VideoCall accept(long callId, CurrentUser actor) {
        VideoCall call = lockedParticipantCall(callId, actor);
        requireReceiver(call, actor.userId());
        requirePremium(call.getCallerId(), call.getReceiverId());
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
        if (expiry.closeIfExpired(callId)) throw VideoCallException.premiumRequired();
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

    private void requirePremium(long callerId, long receiverId) {
        if (!subscriptions.hasActiveSubscription(callerId)
                || !subscriptions.hasActiveSubscription(receiverId)) throw VideoCallException.premiumRequired();
    }
}
