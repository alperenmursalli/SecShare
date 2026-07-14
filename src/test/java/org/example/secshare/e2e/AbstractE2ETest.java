package org.example.secshare.e2e;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Base for full-stack end-to-end tests. Boots the real application on a random port against a
 * throwaway PostgreSQL container, so every test drives the same HTTP stack a browser would:
 * Spring Security + JWT filter, JPA/Hibernate schema, and on-disk file storage.
 *
 * <p>A single container is started once (singleton pattern) and shared across every E2E test
 * class for the life of the JVM; it is never stopped between classes, so Spring's cached context
 * always points at a live database. The schema is built once by {@code ddl-auto=update} and each
 * test uses freshly-registered accounts so runs stay independent. Uploaded files are written to a
 * per-run temp directory that is discarded when the JVM exits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractE2ETest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static final Path STORAGE_DIR;

    static {
        // Singleton container: start it here and let it live until the JVM exits (Ryuk / shutdown
        // reaps it). Starting once avoids the stopped-container-with-cached-context problem that
        // arises when @Testcontainers stops a shared static container between test classes.
        POSTGRES.start();
        try {
            STORAGE_DIR = Files.createTempDirectory("secshare-e2e-storage");
            STORAGE_DIR.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new IllegalStateException("could not create temp storage dir for E2E tests", e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Build the schema on the throwaway container; tests use unique accounts for isolation.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("app.storage.base-path", STORAGE_DIR::toString);
        // Keep the background reaper and outbox quiet during tests.
        registry.add("app.cleanup.enabled", () -> "false");
        registry.add("app.mail.enabled", () -> "false");
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate rest;

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected String url(String path) {
        return baseUrl() + path;
    }

    /** A unique email so repeated runs against a reused container never collide. */
    protected String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected HttpHeaders bearer(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    protected HttpEntity<String> jsonEntity(String body) {
        return new HttpEntity<>(body, jsonHeaders());
    }

    protected HttpEntity<String> jsonEntity(String body, String token) {
        return new HttpEntity<>(body, bearer(token));
    }

    /** Registers a fresh account and returns a valid bearer token for it. */
    protected String registerAndLogin(String email, String password) {
        String creds = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";

        var register = rest.postForEntity(url("/api/auth/register"), jsonEntity(creds), Void.class);
        if (!register.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("register failed: " + register.getStatusCode());
        }

        var login = rest.postForEntity(url("/api/auth/login"), jsonEntity(creds), java.util.Map.class);
        if (!login.getStatusCode().is2xxSuccessful() || login.getBody() == null) {
            throw new IllegalStateException("login failed: " + login.getStatusCode());
        }
        return (String) login.getBody().get("accessToken");
    }
}
