package fptu.exe202.signify.signifybe.features.email.application;

import fptu.exe202.signify.apiresponse.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OtpServiceTest {
    @Test
    void registrationCheckKeepsOtpForRegistration() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("OTP:alice@example.com")).thenReturn("123456");
        OtpService service = new OtpService(redis, mock(EmailService.class));

        service.checkOtp("alice@example.com", "123456");
        verify(redis, never()).delete(anyString());

        service.verifyOtp("alice@example.com", "123456");
        verify(redis).delete("OTP:alice@example.com");
    }

    @Test
    void incorrectOtpIsRejectedWithoutDeletion() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("OTP:alice@example.com")).thenReturn("123456");
        OtpService service = new OtpService(redis, mock(EmailService.class));

        assertThrows(BadRequestException.class,
                () -> service.verifyOtp("alice@example.com", "000000"));
        verify(redis, never()).delete(anyString());
    }
}
