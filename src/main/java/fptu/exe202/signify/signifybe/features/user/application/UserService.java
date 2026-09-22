package fptu.exe202.signify.signifybe.features.user.application;

import fptu.exe202.signify.apiresponse.exception.ConflictException;
import fptu.exe202.signify.signifybe.features.auth.application.AuthResult;
import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.auth.domain.SessionMetadata;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import fptu.exe202.signify.signifybe.features.user.domain.UserProfile;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.AccountRepository;
import fptu.exe202.signify.signifybe.features.auth.application.TokenService;
import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.exception.UserException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import fptu.exe202.signify.signifybe.features.storage.application.StorageService;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {

    AccountRepository accountRepository;

    UserRepository userRepository;

    PasswordEncoder passwordEncoder;

    TokenService tokenService;

    Clock clock;

    StorageService storageService;

    @Transactional
    public AuthResult register(String username, String password, String email, String firstName,
                               String lastName, MultipartFile avatar, SessionMetadata metadata) {
        //done
        validatePassword(password);
        if (accountRepository.usernameExists(username)) {
            throw new ConflictException("Username already exist.");
        }
        if (userRepository.isEmailExist(email)) {
            throw new ConflictException("Email already exist");
        }
        User newUser = new User(email, firstName, lastName, clock.millis());
        if (avatar != null) {
            newUser.setAvatar(storageService.uploadImage(avatar).url());
        }
        User user = userRepository.addUser(newUser);
        Account account = accountRepository.insertAccount(new Account(user.getId(), username, passwordEncoder.encode(password), clock.millis()));
        return new AuthResult(UserProfile.of(user, account), tokenService.issueTokens(account, metadata));
    }

    @Transactional(readOnly = true)
    public UserProfile me(long userId) {
        Account account = accountRepository.findAccountByUserId(userId).orElseThrow(AuthException::invalidAccessToken);
        User user = userRepository.findUserById(userId).orElseThrow(AuthException::accountDisabled);
        if (!account.isActive() || user.isDeleted()) throw AuthException.accountDisabled();
        return UserProfile.of(user, account);
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new ConflictException("Password must not be empty.");
        }

        if (password.length() < 8) {
            throw new ConflictException("Password must be at least 8 characters long.");
        }

        if (!password.matches(".*[a-z].*")) {
            throw new ConflictException("Password must contain at least one lowercase letter.");
        }

        if (!password.matches(".*[A-Z].*")) {
            throw new ConflictException("Password must contain at least one uppercase letter.");
        }

        if (!password.matches(".*\\d.*")) {
            throw new ConflictException("Password must contain at least one number.");
        }

        if (!password.matches(".*[^A-Za-z\\d].*")) {
            throw new ConflictException("Password must contain at least one special character.");
        }
    }
}
