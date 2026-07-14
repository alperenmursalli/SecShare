package org.example.secshare.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of the authentication journey: register, log in, and read the current
 * identity, plus the guardrails around bad credentials and unauthenticated access.
 */
class AuthE2ETest extends AbstractE2ETest {

    @Test
    void register_login_and_me_roundtrip() {
        String email = uniqueEmail();
        String password = "supersecret1";

        // Register -> 201 Created
        var register = rest.postForEntity(
                url("/api/auth/register"),
                jsonEntity("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"),
                Void.class);
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Login -> token
        ResponseEntity<Map> login = rest.postForEntity(
                url("/api/auth/login"),
                jsonEntity("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).isNotNull();
        String token = (String) login.getBody().get("accessToken");
        assertThat(token).isNotBlank();

        // /me with the token echoes the identity
        ResponseEntity<Map> me = rest.exchange(
                url("/api/auth/me"), HttpMethod.GET,
                new HttpEntity<>(bearer(token)), Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).isNotNull();
        assertThat(me.getBody().get("email")).isEqualTo(email);
    }

    @Test
    void login_with_wrong_password_is_rejected() {
        String email = uniqueEmail();
        registerAndLogin(email, "supersecret1");

        ResponseEntity<String> login = rest.postForEntity(
                url("/api/auth/login"),
                jsonEntity("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"),
                String.class);
        assertThat(login.getStatusCode().is2xxSuccessful()).isFalse();
    }

    @Test
    void register_rejects_short_password() {
        var register = rest.postForEntity(
                url("/api/auth/register"),
                jsonEntity("{\"email\":\"" + uniqueEmail() + "\",\"password\":\"short\"}"),
                String.class);
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void me_without_token_is_unauthorized() {
        ResponseEntity<String> me = rest.getForEntity(url("/api/auth/me"), String.class);
        assertThat(me.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
