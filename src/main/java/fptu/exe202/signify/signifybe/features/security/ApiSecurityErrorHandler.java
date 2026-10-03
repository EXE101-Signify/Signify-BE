package fptu.exe202.signify.signifybe.features.security;

import fptu.exe202.signify.apiresponse.exception.ForbiddenException;
import fptu.exe202.signify.apiresponse.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    static String AUTHENTICATION_REQUIRED =
            "Authentication required or invalid bearer token";

    HandlerExceptionResolver exceptionResolver;

    public ApiSecurityErrorHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        resolve(request, response, new UnauthorizedException(AUTHENTICATION_REQUIRED));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        resolve(request, response, new ForbiddenException("Forbidden"));
    }

    private void resolve(HttpServletRequest request, HttpServletResponse response, RuntimeException exception) {
        response.setHeader("Cache-Control", "no-store");
        if (exceptionResolver.resolveException(request, response, null, exception) == null) {
            throw exception;
        }
    }
}
