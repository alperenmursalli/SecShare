package org.example.secshare.security;

import org.example.secshare.e2e.AbstractE2ETest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HW1 — Injection (SQL injection).
 *
 * <p>Exploit coverage for the "search my files" feature ({@code GET /api/files?q=}). The search
 * term reaches a native SQL query. At tag {@code hw1-vulnerable} the term is string-concatenated
 * into the SQL, so a UNION-based payload exfiltrates every user's e-mail and BCrypt password hash
 * from the {@code users} table. At tag {@code hw1-fixed} the search runs through a parameterised
 * JPA query scoped to the caller, so the identical payload is treated as a harmless literal.
 *
 * <p>Both tests assert the <em>safe</em> behaviour (A6 recommended pattern): they FAIL at
 * {@code hw1-vulnerable} (the attack leaks data) and PASS at {@code hw1-fixed} (the attack is
 * neutralised and the real feature still works).
 *
 * <p>Run: {@code ./mvnw test -Dtest=Hw1SqlInjectionE2ETest}
 */
class Hw1SqlInjectionE2ETest extends AbstractE2ETest {

    /**
     * Breaks out of the {@code ILIKE '%<q>%'} string literal and UNION-selects the credentials
     * table. Column list matches the search projection
     * (id, original_filename, size_bytes, content_type, created_at, scan_status).
     */
    private static final String UNION_PAYLOAD =
            "' UNION SELECT id, email || ':' || password_hash, 0, 'x'::varchar, created_at, 'CLEAN' "
                    + "FROM users -- ";

    private String uploadFile(String token, String filename, byte[] content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ByteArrayResource filePart = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);
        ResponseEntity<java.util.Map> resp = rest.postForEntity(
                url("/api/files/upload"), new HttpEntity<>(body, headers), java.util.Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("id");
    }

    private ResponseEntity<String> search(String token, String q) {
        URI uri = UriComponentsBuilder.fromUriString(url("/api/files"))
                .queryParam("q", q)
                .build()
                .encode()
                .toUri();
        return rest.exchange(uri, HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    }

    @Test
    void benign_search_returns_only_my_own_matching_files() {
        String token = registerAndLogin(uniqueEmail(), "supersecret1");
        uploadFile(token, "quarterly-report.txt", "r".getBytes(StandardCharsets.UTF_8));
        uploadFile(token, "holiday-notes.txt", "p".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<String> resp = search(token, "report");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("quarterly-report.txt");
        assertThat(resp.getBody()).doesNotContain("holiday-notes.txt");
    }

    @Test
    void sql_injection_cannot_exfiltrate_other_users_credentials() {
        // A victim with credentials sitting in the users table.
        String victimEmail = uniqueEmail();
        registerAndLogin(victimEmail, "victim-password-123");

        // The attacker is an ordinary authenticated user searching their own (empty) file list.
        String attackerToken = registerAndLogin(uniqueEmail(), "attacker-password-123");

        ResponseEntity<String> resp = search(attackerToken, UNION_PAYLOAD);
        String body = resp.getBody() == null ? "" : resp.getBody();

        // SAFE behaviour: the search must never surface another account's e-mail or a BCrypt hash.
        // At hw1-vulnerable the UNION leaks both and these assertions FAIL.
        assertThat(body)
                .as("attacker's search response must not leak the victim's e-mail")
                .doesNotContain(victimEmail);
        assertThat(body)
                .as("attacker's search response must not leak any BCrypt password hash")
                .doesNotContain("$2a$");
    }
}
