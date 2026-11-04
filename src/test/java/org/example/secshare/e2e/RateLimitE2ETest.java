package org.example.secshare.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the per-IP rate limiter trips on the credential endpoints. Runs in its own Spring
 * context (limiter enabled, tiny capacity) so it does not interfere with — and is not masked
 * by — the rest of the suite, which runs with the limiter disabled (see {@link AbstractE2ETest}).
 *
 * <p>Reuses the shared singleton PostgreSQL container started by {@code AbstractE2ETest} rather
 * than booting a second database.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitE2ETest {

    private static final int CAPACITY = 3;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Touch the base class so its static initializer starts the shared container.
        registry.add("spring.datasource.url", AbstractE2ETest.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", AbstractE2ETest.POSTGRES::getUsername);
        registry.add("spring.datasource.password", AbstractE2ETest.POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("app.storage.base-path", AbstractE2ETest.STORAGE_DIR::toString);
        registry.add("app.cleanup.enabled", () -> "false");
        registry.add("app.mail.enabled", () -> "false");

        registry.add("app.ratelimit.enabled", () -> "true");
        registry.add("app.ratelimit.auth.capacity", () -> String.valueOf(CAPACITY));
        registry.add("app.ratelimit.auth.window-seconds", () -> "60");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    void login_endpoint_returns_429_once_the_bucket_is_exhausted() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Credentials don't matter: the limiter runs before authentication, so even failed
        // logins consume tokens. That is exactly the brute-force surface we are protecting.
        HttpEntity<String> attempt = new HttpEntity<>(
                "{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}", headers);
        String loginUrl = "http://localhost:" + port + "/api/auth/login";

        int allowed = 0;
        ResponseEntity<String> limited = null;
        for (int i = 0; i < CAPACITY + 5; i++) {
            ResponseEntity<String> res = rest.postForEntity(loginUrl, attempt, String.class);
            if (res.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                limited = res;
                break;
            }
            allowed++;
        }

        // The first CAPACITY requests pass the limiter; a later one is throttled.
        assertThat(allowed).isGreaterThanOrEqualTo(1).isLessThanOrEqualTo(CAPACITY);
        assertThat(limited).isNotNull();
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(limited.getBody()).contains("rate_limited");
    }
}
