package fptu.exe202.signify.signifybe.features.user.application;

import fptu.exe202.signify.apiresponse.exception.BadRequestException;
import fptu.exe202.signify.apiresponse.exception.ConflictException;
import fptu.exe202.signify.signifybe.features.auth.application.TokenService;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.AccountRepository;
import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.auth.domain.SessionMetadata;
import fptu.exe202.signify.signifybe.features.email.application.OtpService;
import fptu.exe202.signify.signifybe.features.storage.application.StorageService;
import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserRegistrationOtpTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final TokenService tokens = mock(TokenService.class);
    private final OtpService otp = mock(OtpService.class);
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(accounts, users, passwords, tokens, Clock.systemUTC(),
                mock(StorageService.class), otp);
        when(accounts.listPassword()).thenReturn(List.of());
        when(accounts.insertAccount(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.addUser(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });
    }

    @Test
    void matchingOtpCreatesVerifiedUser() {
        var result = register();

        verify(otp).verifyOtp("alice@example.com", "123456");
        assertTrue(result.user().emailVerified());
        verify(users).addUser(argThat(User::isEmailVerified));
    }

    @Test
    void invalidOtpDoesNotCreateUser() {
        doThrow(new BadRequestException("OTP is invalid or expired"))
                .when(otp).verifyOtp("alice@example.com", "000000");

        assertThrows(BadRequestException.class, () -> service.register("alice", "StrongPass1!",
                "alice@example.com", "000000", "Alice", null, null, new SessionMetadata(null, null, null)));
        verify(users, never()).addUser(any());
        verify(accounts, never()).insertAccount(any());
    }

    @Test
    void duplicateEmailDoesNotConsumeOtp() {
        when(users.isEmailExist("alice@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, this::register);
        verifyNoInteractions(otp);
    }

    private fptu.exe202.signify.signifybe.features.auth.application.AuthResult register() {
        return service.register("alice", "StrongPass1!", "alice@example.com", "123456",
                "Alice", null, null, new SessionMetadata(null, null, null));
    }
}
