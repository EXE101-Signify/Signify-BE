package fptu.exe202.signify.signifybe.features.email.api;

import fptu.exe202.signify.apiresponse.exception.ConflictException;
import fptu.exe202.signify.apiresponse.exception.ResourceNotFoundException;
import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.common.UserValidation;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.AccountRepository;
import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.email.api.dto.OtpVerifyRequest;
import fptu.exe202.signify.signifybe.features.email.api.dto.PasswordResetRequest;
import fptu.exe202.signify.signifybe.features.email.api.dto.SendOtpRequest;
import fptu.exe202.signify.signifybe.features.email.application.OtpService;
import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EmailController {

    OtpService otpService;
    UserRepository userRepository;
    AccountRepository accountRepository;
    PasswordEncoder passwordEncoder;
    Clock clock;

    // ── Registration OTP ────────────────────────────────────────────────────────

    /**
     * Sends an OTP to the given email for registration verification.
     * Fails if the email is already registered.
     */
    @PostMapping("/otp/send")
    public ApiResponse<Void> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        if (userRepository.isEmailExist(request.email())) {
            throw new ConflictException("This email is already registered");
        }
        otpService.generateAndSendOtp(request.email());
        return ApiResponse.success("OTP has been sent to " + request.email(), null);
    }

    /**
     * Verifies the OTP for registration flow.
     */
    @PostMapping("/otp/verify")
    public ApiResponse<Void> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        otpService.verifyOtp(request.email(), request.otp());
        return ApiResponse.success("Email verified successfully", null);
    }

    /**
     * Resends an OTP to the given email.
     */
    @PostMapping("/otp/resend")
    public ApiResponse<Void> resendOtp(@Valid @RequestBody SendOtpRequest request) {
        otpService.resendOtp(request.email());
        return ApiResponse.success("OTP resent to " + request.email(), null);
    }

    // ── Forgot Password ─────────────────────────────────────────────────────────

    /**
     * Sends a password-reset OTP. Requires the email to belong to an existing account.
     */
    @PostMapping("/forgot-password/send")
    public ApiResponse<Void> sendPasswordResetOtp(@Valid @RequestBody SendOtpRequest request) {
        userRepository.findUserByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));
        otpService.generateAndSendPasswordResetOtp(request.email());
        return ApiResponse.success("Password reset OTP sent to " + request.email(), null);
    }

    /**
     * Verifies the OTP and resets the user's password.
     */
    @PostMapping("/forgot-password/verify")
    @Transactional
    public ApiResponse<Void> verifyAndResetPassword(@Valid @RequestBody PasswordResetRequest request) {
        // 1. Verify OTP (throws BadRequestException if invalid)
        otpService.verifyOtp(request.email(), request.otp());

        // 2. Validate new password
        UserValidation.validatePassword(request.newPassword());

        // 3. Find user and account
        User user = userRepository.findUserByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));
        Account account = accountRepository.lockAccount(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email"));

        // 4. Update password
        account.updatePassword(passwordEncoder.encode(request.newPassword()), clock.millis());

        return ApiResponse.success("Password reset successfully", null);
    }
}
