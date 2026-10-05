package fptu.exe202.signify.signifybe.features.security;

import fptu.exe202.signify.signifybe.features.auth.application.JwtService;
import fptu.exe202.signify.signifybe.features.auth.application.AccountAccessService;
import fptu.exe202.signify.signifybe.features.auth.application.SessionAccessService;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    JwtService jwtService;

    ApiSecurityErrorHandler errors;

    AccountAccessService accountAccess;

    SessionAccessService sessionAccess;

    public JwtAuthenticationFilter(JwtService jwtService, ApiSecurityErrorHandler errors,
                                   AccountAccessService accountAccess, SessionAccessService sessionAccess) {
        this.jwtService = jwtService;
        this.errors = errors;
        this.accountAccess = accountAccess;
        this.sessionAccess = sessionAccess;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            chain.doFilter(request, response);
            return;
        }
        CurrentUser user;
        try {
            user = jwtService.validateAccessToken(authorization.substring(7));
            sessionAccess.requireActiveSession(user.sessionId(), user.userId());
            user = accountAccess.requireActiveUser(user.userId(), user.sessionId());
        } catch (AuthException ex) {
            SecurityContextHolder.clearContext();
            chain.doFilter(request, response);
            return;
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null,
                List.of(new SimpleGrantedAuthority(user.role().authority())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
