package fptu.exe202.signify.signifybe.features.auth.domain;

import java.security.Principal;

public record CurrentUser(long userId, long sessionId, Role role) implements Principal {
    @Override public String getName() { return Long.toString(userId); }
}
