package org.example.secshare.e2e;

import org.example.secshare.file.FileShare;
import org.example.secshare.file.FileShareRepository;
import org.example.secshare.file.ShareType;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the expiry-bypass fix: the authenticated download path used to gate on
 * {@code revoked == false} alone, so a signed-in grant recipient could keep downloading a share
 * that had already expired (the anonymous link path correctly returned 410). Both paths must now
 * agree via {@link FileShare#isActive()}.
 */
class SharedAccessExpiryE2ETest extends AbstractE2ETest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileShareRepository fileShareRepository;

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
        return (String) resp.getBody().get("id");
    }

    /** Registers, verifies (reading the pending token straight from the DB), and returns a token. */
    private String registerVerifiedAndLogin(String email, String password) {
        String token = registerAndLogin(email, password);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        rest.getForEntity(url("/api/auth/verify?token=" + user.getVerificationToken()), String.class);
        return token;
    }

    @Test
    void expired_audience_grant_is_refused_on_the_authenticated_path() {
        String ownerToken = registerAndLogin(uniqueEmail(), "supersecret1");
        byte[] content = "time-boxed payload".getBytes(StandardCharsets.UTF_8);
        String fileId = uploadFile(ownerToken, "expiring.txt", content);

        String recipientEmail = uniqueEmail();
        String recipientToken = registerVerifiedAndLogin(recipientEmail, "supersecret1");

        rest.postForEntity(
                url("/api/files/" + fileId + "/shares"),
                jsonEntity("{\"type\":\"AUDIENCE\",\"recipientEmails\":[\"" + recipientEmail + "\"]}", ownerToken),
                String.class);

        // While the share is live, the verified recipient downloads it.
        ResponseEntity<byte[]> live = rest.exchange(
                url("/api/files/" + fileId), HttpMethod.GET,
                new HttpEntity<>(bearer(recipientToken)), byte[].class);
        assertThat(live.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(live.getBody()).isEqualTo(content);

        // Age the share past its expiry (no time-travel over HTTP, so edit it directly).
        List<FileShare> shares = fileShareRepository
                .findByFile_IdAndTypeAndRevokedFalse(UUID.fromString(fileId), ShareType.AUDIENCE);
        assertThat(shares).hasSize(1);
        FileShare share = shares.get(0);
        share.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        fileShareRepository.save(share);

        // Now the authenticated download must be refused, matching the public link path.
        ResponseEntity<String> afterExpiry = rest.exchange(
                url("/api/files/" + fileId), HttpMethod.GET,
                new HttpEntity<>(bearer(recipientToken)), String.class);
        assertThat(afterExpiry.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // ...and the expired share drops out of shared-with-me too.
        ResponseEntity<List> list = rest.exchange(
                url("/api/files/shared-with-me"), HttpMethod.GET,
                new HttpEntity<>(bearer(recipientToken)), List.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).isEmpty();
    }
}
