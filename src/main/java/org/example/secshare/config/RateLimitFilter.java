package org.example.secshare.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Throttles the unauthenticated, abuse-prone endpoints before the request reaches auth or
 * business logic: credential endpoints (login/register/verify) and the public share-link
 * endpoints (a password brute-force / token-enumeration surface). Buckets are keyed per
 * client IP so one noisy host cannot deny service to everyone. Returns {@code 429} with a
 * {@code Retry-After} header when a bucket is exhausted.
 *
 * <p>Not a Spring {@code @Component} by design: it is wired into the security chain in
 * {@link org.example.secshare.auth.SecurityConfig}, which avoids Boot also registering it as
 * a plain servlet filter (double execution).</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiter rateLimiter;
    private final boolean enabled;

    public RateLimitFilter(RateLimiter rateLimiter, boolean enabled) {
        this.rateLimiter = rateLimiter;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String spec = specFor(request);
        if (spec == null) {
            chain.doFilter(request, response);
            return;
        }

        String ip = HttpRequestUtils.clientIp(request);
        String key = ip == null ? "unknown" : ip;
        RateLimiter.Decision decision = rateLimiter.tryAcquire(spec, key);

        if (!decision.allowed()) {
            log.warn("Rate limit hit: spec={} ip={} path={}", spec, key, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\":\"rate_limited\",\"message\":\"Too many requests. Please try again later.\","
                            + "\"retryAfterSeconds\":" + decision.retryAfterSeconds() + "}");
            return;
        }

        chain.doFilter(request, response);
    }

    /** Maps a request to a limiter spec, or {@code null} when the path is not throttled. */
    private String specFor(HttpServletRequest request) {
        if (!enabled) {
            return null;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return null;
        }
        if (path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/register")
                || path.startsWith("/api/auth/verify")
                || path.startsWith("/api/auth/resend-verification")) {
            return "auth";
        }
        if (path.startsWith("/api/public/shares/")) {
            return "download";
        }
        return null;
    }
}
