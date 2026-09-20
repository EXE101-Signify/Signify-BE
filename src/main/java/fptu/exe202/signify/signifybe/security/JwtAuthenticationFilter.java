package fptu.exe202.signify.signifybe.security;

import fptu.exe202.signify.signifybe.auth.application.JwtService;
import fptu.exe202.signify.signifybe.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.auth.domain.exception.AuthException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Registered only inside SecurityFilterChain, never as a second servlet filter. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final ApiSecurityErrorHandler errors;

    public JwtAuthenticationFilter(JwtService jwtService, ApiSecurityErrorHandler errors) {
        this.jwtService = jwtService;
        this.errors = errors;
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
        } catch (AuthException ex) {
            SecurityContextHolder.clearContext();
            errors.commence(request, response, new BadCredentialsException("Invalid bearer token"));
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
