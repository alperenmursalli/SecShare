package org.example.secshare.e2e;

import org.example.secshare.user.User;
import org.example.secshare.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the broken-access-control fix: audience access is keyed on the JWT's
 * email claim, so an account that never proved it owns that address must not receive files shared
 * to it. Reproduces the original attack (register an unclaimed recipient address, read the file),
 * asserts it is now blocked, and confirms that verifying the email restores the intended access.
 */
class EmailVerificationE2ETest extends AbstractE2ETest {

    @Autowired
    private UserRepository userRepository;

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

        ResponseEntity<Map> resp = rest.postForEntity(
                url("/api/files/upload"), new HttpEntity<>(body, headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        return (String) resp.getBody().get("id");
    }

    /** Reads the pending verification token Hibernate persisted for an address (mail is off in tests). */
    private String verificationTokenFor(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("no user for " + email));
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getVerificationToken()).isNotBlank();
        return user.getVerificationToken();
    }

    @Test
    void unverified_audience_recipient_is_denied_until_email_is_verified() {
        // Owner uploads a file and shares it with a recipient address that has no account yet.
        String ownerToken = registerAndLogin(uniqueEmail(), "supersecret1");
        byte[] content = "confidential audience payload".getBytes(StandardCharsets.UTF_8);
        String fileId = uploadFile(ownerToken, "audience.txt", content);

        String recipientEmail = uniqueEmail();
        ResponseEntity<String> share = rest.postForEntity(
                url("/api/files/" + fileId + "/shares"),
                jsonEntity("{\"type\":\"AUDIENCE\",\"recipientEmails\":[\"" + recipientEmail + "\"]}", ownerToken),
                String.class);
        assertThat(share.getStatusCode()).isEqualTo(HttpStatus.OK);

        // The attack: claim the recipient address by registering it, then try to read the file.
        String recipientToken = registerAndLogin(recipientEmail, "supersecret1");

        // shared-with-me hides the file from the unverified account...
        ResponseEntity<List> beforeList = rest.exchange(
                url("/api/files/shared-with-me"), HttpMethod.GET,
                new HttpEntity<>(bearer(recipientToken)), List.class);
        assertThat(beforeList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(beforeList.getBody()).isEmpty();

        // ...and the direct download is forbidden.
        ResponseEntity<String> beforeDownload = rest.exchange(
                url("/api/files/" + fileId), HttpMethod.GET,
                new HttpEntity<>(bearer(recipientToken)), String.class);
        assertThat(beforeDownload.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Prove ownership of the address via the verification link.
        ResponseEntity<String> verify = rest.getForEntity(
                url("/api/auth/verify?token=" + verificationTokenFor(recipientEmail)), String.class);
        assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Now the intended access works: the file is listed...
        ResponseEntity<List> afterList = rest.exchange(
                url("/api/files/shared-with-me"), HttpMethod.GET,
                new HttpEntity<>(bearer(recipientToken)), List.class);
        assertThat(afterList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(afterList.getBody()).isNotNull();
        assertThat(afterList.getBody())
                .anySatisfy(entry -> assertThat(((Map<?, ?>) entry).get("fileId")).isEqualTo(fileId));

        // ...and the bytes download.
        ResponseEntity<byte[]> afterDownload = rest.exchange(
                url("/api/files/" + fileId), HttpMethod.GET,
                new HttpEntity<>(bearer(recipientToken)), byte[].class);
        assertThat(afterDownload.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(afterDownload.getBody()).isEqualTo(content);
    }

    @Test
    void verify_rejects_an_unknown_token() {
        ResponseEntity<String> verify = rest.getForEntity(
                url("/api/auth/verify?token=not-a-real-token"), String.class);
        assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void owner_can_always_download_their_own_file_without_verifying() {
        // Registration leaves the account unverified, but ownership never requires verification.
        String email = uniqueEmail();
        String ownerToken = registerAndLogin(email, "supersecret1");
        byte[] content = "my own upload".getBytes(StandardCharsets.UTF_8);
        String fileId = uploadFile(ownerToken, "mine.txt", content);
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().isEmailVerified()).isFalse();

        ResponseEntity<byte[]> download = rest.exchange(
                url("/api/files/" + fileId), HttpMethod.GET,
                new HttpEntity<>(bearer(ownerToken)), byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getBody()).isEqualTo(content);
    }
}
