package fptu.exe202.signify.signifybe.features.ai.infrastructure;

import fptu.exe202.signify.signifybe.features.ai.application.AiService;
import fptu.exe202.signify.signifybe.features.ai.domain.exception.AiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.POST;

class AiClientTest {
    private MockRestServiceServer server;
    private AiService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-test.local");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new AiService(new AiClient(builder.build()));
    }

    @Test
    void availableServiceReceivesMultipartImageAndMapsA() {
        server.expect(once(), requestTo("http://ai-test.local/api/v1/predict"))
                .andExpect(method(POST))
                .andExpect(header("Content-Type", containsString("multipart/form-data")))
                .andExpect(content().string(containsString("name=\"image\"")))
                .andRespond(withSuccess("{\"letter\":\"A\",\"confidence\":0.96,\"timestamp\":1720000000000}", MediaType.APPLICATION_JSON));

        var result = service.predict("image bytes".getBytes(StandardCharsets.UTF_8), MediaType.IMAGE_JPEG);
        assertEquals("A", result.letter());
        assertEquals(0.96, result.confidence());
        assertEquals(1720000000000L, result.timestamp());
        server.verify();
    }

    @Test
    void mapsB() {
        server.expect(requestTo("http://ai-test.local/api/v1/predict"))
                .andRespond(withSuccess("{\"letter\":\"B\",\"confidence\":0.8,\"timestamp\":1720000000001}", MediaType.APPLICATION_JSON));
        assertEquals("B", service.predict(new byte[]{1}, MediaType.IMAGE_PNG).letter());
        server.verify();
    }

    @Test
    void connectionRefusedBecomesSafeApplicationException() {
        server.expect(requestTo("http://ai-test.local/api/v1/predict"))
                .andRespond(withException(new ConnectException("refused")));
        AiException error = assertThrows(AiException.class,
                () -> service.predict(new byte[]{1}, MediaType.IMAGE_JPEG));
        assertEquals("AI service unavailable", error.getMessage());
        server.verify();
    }

    @Test
    void timeoutBecomesSafeApplicationException() {
        server.expect(requestTo("http://ai-test.local/api/v1/predict"))
                .andRespond(withException(new SocketTimeoutException("timed out")));
        AiException error = assertThrows(AiException.class,
                () -> service.predict(new byte[]{1}, MediaType.IMAGE_JPEG));
        assertEquals("AI service timed out", error.getMessage());
        server.verify();
    }

    @Test
    void malformedAndUnexpectedResponsesAreRejected() {
        server.expect(requestTo("http://ai-test.local/api/v1/predict"))
                .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
        assertEquals("Invalid AI service response", assertThrows(AiException.class,
                () -> service.predict(new byte[]{1}, MediaType.IMAGE_JPEG)).getMessage());
        server.verify();
    }

    @Test
    void missingPredictionFieldsAreRejected() {
        server.expect(requestTo("http://ai-test.local/api/v1/predict"))
                .andRespond(withSuccess("{\"letter\":\"A\"}", MediaType.APPLICATION_JSON));
        assertEquals("Invalid AI service response", assertThrows(AiException.class,
                () -> service.predict(new byte[]{1}, MediaType.IMAGE_JPEG)).getMessage());
        server.verify();
    }

    @Test
    void http500DoesNotExposePythonDetails() {
        server.expect(requestTo("http://ai-test.local/api/v1/predict"))
                .andRespond(withServerError().body("Python internal stack trace"));
        AiException error = assertThrows(AiException.class,
                () -> service.predict(new byte[]{1}, MediaType.IMAGE_JPEG));
        assertEquals("AI service unavailable", error.getMessage());
        server.verify();
    }
}
