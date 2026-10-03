package fptu.exe202.signify.signifybe.features.chat.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ChatJsonSizeFilterTest {
    private final ChatJsonSizeFilter filter = new ChatJsonSizeFilter();

    @Test void oversizedChatJsonIsRejectedBeforeController() throws Exception {
        var request = request(new byte[16 * 1024 + 1]);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(413, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test void normalChatJsonReachesControllerIntact() throws Exception {
        byte[] body = "{\"content\":\"hello\",\"messageType\":\"TEXT\"}"
                .getBytes(StandardCharsets.UTF_8);
        var request = request(body);
        var chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertArrayEquals(body, chain.getRequest().getInputStream().readAllBytes());
    }

    private MockHttpServletRequest request(byte[] body) {
        var request = new MockHttpServletRequest("POST", "/api/conversations/7/messages");
        request.setContentType("application/json");
        request.setContent(body);
        return request;
    }
}
