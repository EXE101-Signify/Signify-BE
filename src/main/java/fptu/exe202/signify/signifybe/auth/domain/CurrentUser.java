package fptu.exe202.signify.signifybe.auth.domain;

import java.security.Principal;

public record CurrentUser(long userId, Role role) implements Principal {
    @Override public String getName() { return Long.toString(userId); }
}
