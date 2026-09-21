package fptu.exe202.signify.signifybe.security;

import fptu.exe202.signify.apiresponse.exception.UnauthorizedException;
import fptu.exe202.signify.signifybe.features.security.ApiSecurityErrorHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiSecurityErrorHandlerTest {

    @Test
    void unauthenticatedRequestUsesApiResponseUnauthorizedException() throws Exception {
        HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
        when(resolver.resolveException(any(), any(), isNull(), any())).thenReturn(new ModelAndView());
        ApiSecurityErrorHandler handler = new ApiSecurityErrorHandler(resolver);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request, response,
                new InsufficientAuthenticationException("Full authentication is required"));

        ArgumentCaptor<Exception> exception = ArgumentCaptor.forClass(Exception.class);
        verify(resolver).resolveException(any(), any(), isNull(), exception.capture());
        assertThat(exception.getValue())
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Authentication required or invalid bearer token");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }
}
