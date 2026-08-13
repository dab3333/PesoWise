package ph.pesowise.gateway.web;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Replaces WebFlux's default error body — {@code {timestamp, path, status, error, requestId}},
 * always a bare 500 unless the failing route itself set a status — with the one error shape
 * every other service already returns: {@code {timestamp, status, message, fieldErrors}}.
 *
 * <p>The gateway is the one place a downstream service being unreachable surfaces as a genuine
 * connectivity failure rather than an application exception, and Compose can produce that
 * failure several different ways depending on exactly how the container went away: DNS
 * deregisters a stopped container's hostname outright ({@link UnknownHostException}); a
 * container whose IP is still cached but no longer routable raises {@link
 * java.net.NoRouteToHostException}; one that exists but isn't accepting connections yet raises
 * {@link java.net.ConnectException} — both are {@link SocketException} subclasses, which is why
 * that's the check below rather than one of its subtypes; a slow/hanging one raises a client-side
 * {@link TimeoutException}. All of these get **503**, not 500: the gateway itself is fine, the
 * service it was proxying to is what's down, and the frontend's {@code fallbackMessage} already
 * has a dedicated, more specific message for exactly that status.
 */
@Component
public class GatewayErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Throwable error = getError(request);
        HttpStatus status = isDownstreamUnreachable(error) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.INTERNAL_SERVER_ERROR;

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("timestamp", Instant.now().toString());
        attributes.put("status", status.value());
        attributes.put("message", status == HttpStatus.SERVICE_UNAVAILABLE
                ? "This part of the app is temporarily unavailable. Please try again shortly."
                : "Something went wrong.");
        attributes.put("fieldErrors", Map.of());
        return attributes;
    }

    /** Walks the cause chain — Reactor/Netty wrap the real failure several layers deep. */
    private boolean isDownstreamUnreachable(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnknownHostException
                    || cause instanceof SocketException
                    || cause instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
