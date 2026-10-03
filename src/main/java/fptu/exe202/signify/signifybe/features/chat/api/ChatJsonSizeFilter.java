package fptu.exe202.signify.signifybe.features.chat.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

@Component
public class ChatJsonSizeFilter extends OncePerRequestFilter {
    private static final int MAX_JSON_BYTES = 16 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contentType = request.getContentType();
        return !path.startsWith("/api/conversations")
                || !("POST".equals(request.getMethod()) || "PUT".equals(request.getMethod()))
                || contentType == null || !contentType.startsWith(MediaType.APPLICATION_JSON_VALUE);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] body = request.getInputStream().readNBytes(MAX_JSON_BYTES + 1);
        if (body.length > MAX_JSON_BYTES) {
            response.sendError(413, "Chat request exceeds the maximum size");
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public int getContentLength() { return body.length; }
            @Override public long getContentLengthLong() { return body.length; }
            @Override public BufferedReader getReader() throws IOException {
                return new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncoding() == null
                        ? java.nio.charset.StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(getCharacterEncoding())));
            }
            @Override public ServletInputStream getInputStream() {
                ByteArrayInputStream source = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return source.read(); }
                    @Override public boolean isFinished() { return source.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException("Asynchronous chat JSON is unsupported");
                    }
                };
            }
        }, response);
    }
}
