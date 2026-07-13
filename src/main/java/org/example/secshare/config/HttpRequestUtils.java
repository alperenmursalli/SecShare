package org.example.secshare.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Reads client details from the current request thread via {@link RequestContextHolder}, so
 * services can capture them without threading {@code HttpServletRequest} through their signatures.
 * Returns null when there is no bound request (e.g. a scheduled job).
 *
 * <p>The app runs behind an external reverse proxy, so the real client IP comes from
 * {@code X-Forwarded-For} / {@code X-Real-IP}; {@code getRemoteAddr()} is the last resort.</p>
 */
public final class HttpRequestUtils {

    private HttpRequestUtils() {
    }

    public static String clientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return trim(forwarded.split(",")[0], 64); // first hop is the original client
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return trim(realIp, 64);
        }
        return trim(request.getRemoteAddr(), 64);
    }

    public static String userAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : trim(request.getHeader("User-Agent"), 300);
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        s = s.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
