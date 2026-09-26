package fptu.exe202.signify.signifybe.features.email.application;

import fptu.exe202.signify.apiresponse.exception.BadRequestException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * OTP generation and verification service.
 * <p>
 * Primary store is Redis (key = {@code OTP:<email>}, TTL = 5 minutes).
 * Falls back to an in-memory {@link ConcurrentHashMap} when Redis is unavailable
 * so the flow still works during local development without Redis.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class OtpService {

    private static final long OTP_TTL_MINUTES = 5;
    private static final long OTP_TTL_MILLIS = TimeUnit.MINUTES.toMillis(OTP_TTL_MINUTES);
    private static final String OTP_KEY_PREFIX = "OTP:";

    RedisTemplate<String, Object> redisTemplate;
    EmailService emailService;

    /** In-memory fallback when Redis is down. */
    ConcurrentMap<String, LocalOtp> localOtpStore = new ConcurrentHashMap<>();

    /**
     * Generates a 6-digit OTP, stores it, and sends it to the given email.
     */
    public void generateAndSendOtp(String email) {
        String otp = generateOtp();
        String key = OTP_KEY_PREFIX + email;

        storeOtp(key, otp);
        emailService.sendOtpEmail(email, otp);
        log.info("OTP generated and queued for {}", email);
    }

    /**
     * Generates OTP for password reset flow (uses separate email template).
     */
    public void generateAndSendPasswordResetOtp(String email) {
        String otp = generateOtp();
        String key = OTP_KEY_PREFIX + email;

        storeOtp(key, otp);
        emailService.sendPasswordResetEmail(email, otp);
        log.info("Password-reset OTP generated and queued for {}", email);
    }

    /**
     * Verifies the OTP for the given email. Deletes on success.
     *
     * @throws BadRequestException if OTP is invalid or expired
     */
    public void verifyOtp(String email, String otp) {
        String key = OTP_KEY_PREFIX + email;

        // Try Redis first
        try {
            Object stored = redisTemplate.opsForValue().get(key);
            if (stored != null && stored.toString().equals(otp)) {
                redisTemplate.delete(key);
                localOtpStore.remove(key);
                log.info("OTP verified (Redis) for {}", email);
                return;
            }
        } catch (RuntimeException ex) {
            log.warn("Redis unavailable for OTP verify, falling back to local store", ex);
        }

        // Fallback to local store
        LocalOtp fallback = localOtpStore.get(key);
        if (fallback != null && fallback.isValid() && fallback.otp().equals(otp)) {
            localOtpStore.remove(key);
            log.info("OTP verified (local fallback) for {}", email);
            return;
        }

        throw new BadRequestException("OTP is invalid or expired");
    }

    /**
     * Resends OTP by generating a new one.
     */
    public void resendOtp(String email) {
        generateAndSendOtp(email);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private String generateOtp() {
        return String.valueOf(new Random().nextInt(899_999) + 100_000);
    }

    private void storeOtp(String key, String otp) {
        try {
            redisTemplate.opsForValue().set(key, otp, OTP_TTL_MINUTES, TimeUnit.MINUTES);
            localOtpStore.remove(key);
        } catch (RuntimeException ex) {
            log.warn("Redis unavailable, storing OTP locally for key {}", key, ex);
            localOtpStore.put(key, new LocalOtp(otp, Instant.now().toEpochMilli() + OTP_TTL_MILLIS));
        }
    }

    /** In-memory OTP record with expiry tracking. */
    private record LocalOtp(String otp, long expiresAtMillis) {
        boolean isValid() {
            return Instant.now().toEpochMilli() <= expiresAtMillis;
        }
    }
}
