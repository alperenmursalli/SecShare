package org.example.secshare.e2e;

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

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of the share lifecycle a real user drives: authenticate, upload a file,
 * mint a public share link, then fetch it anonymously through the public download page. Also
 * exercises the security-defining behaviours: password gating and burn-after-reading.
 */
class FileShareE2ETest extends AbstractE2ETest {

    /** Uploads {@code content} as {@code filename} and returns the stored file id. */
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

    /** Creates a share of the given JSON body and returns the token from the {@code /s/<token>} url. */
    private String createLinkShare(String token, String fileId, String requestBody) {
        ResponseEntity<Map> resp = rest.postForEntity(
                url("/api/files/" + fileId + "/shares"),
                jsonEntity(requestBody, token), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        String shareUrl = (String) resp.getBody().get("url");
        assertThat(shareUrl).startsWith("/s/");
        return shareUrl.substring("/s/".length());
    }

    @Test
    void upload_share_and_public_download_roundtrip() {
        String token = registerAndLogin(uniqueEmail(), "supersecret1");
        byte[] content = "hello secshare".getBytes(StandardCharsets.UTF_8);
        String fileId = uploadFile(token, "greeting.txt", content);

        String shareToken = createLinkShare(token, fileId, "{\"type\":\"LINK\"}");

        // Public metadata is reachable without any auth.
        ResponseEntity<Map> meta = rest.getForEntity(
                url("/api/public/shares/" + shareToken), Map.class);
        assertThat(meta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meta.getBody()).isNotNull();
        assertThat(meta.getBody().get("fileName")).isEqualTo("greeting.txt");
        assertThat(meta.getBody().get("needsPassword")).isEqualTo(false);
        assertThat(meta.getBody().get("available")).isEqualTo(true);

        // Anonymous download returns the exact bytes.
        ResponseEntity<byte[]> download = rest.exchange(
                url("/api/public/shares/" + shareToken + "/download"),
                HttpMethod.POST, new HttpEntity<>(null, jsonHeaders()), byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getBody()).isEqualTo(content);
    }

    @Test
    void password_protected_link_requires_correct_password() {
        String token = registerAndLogin(uniqueEmail(), "supersecret1");
        String fileId = uploadFile(token, "secret.txt", "top secret".getBytes(StandardCharsets.UTF_8));

        String shareToken = createLinkShare(token, fileId,
                "{\"type\":\"LINK\",\"password\":\"open-sesame\"}");

        // Metadata advertises the password requirement without leaking the file.
        ResponseEntity<Map> meta = rest.getForEntity(
                url("/api/public/shares/" + shareToken), Map.class);
        assertThat(meta.getBody().get("needsPassword")).isEqualTo(true);

        // Wrong / missing password is refused.
        ResponseEntity<String> wrong = rest.exchange(
                url("/api/public/shares/" + shareToken + "/download"),
                HttpMethod.POST, jsonEntity("{\"password\":\"nope\"}"), String.class);
        assertThat(wrong.getStatusCode().is2xxSuccessful()).isFalse();

        // Correct password unlocks the download.
        ResponseEntity<byte[]> ok = rest.exchange(
                url("/api/public/shares/" + shareToken + "/download"),
                HttpMethod.POST, jsonEntity("{\"password\":\"open-sesame\"}"), byte[].class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(ok.getBody(), StandardCharsets.UTF_8)).isEqualTo("top secret");
    }

    @Test
    void burn_after_reading_link_self_destructs_after_first_download() {
        String token = registerAndLogin(uniqueEmail(), "supersecret1");
        String fileId = uploadFile(token, "once.txt", "read me once".getBytes(StandardCharsets.UTF_8));

        // maxDownloads=1 makes the link burn after the first fetch.
        String shareToken = createLinkShare(token, fileId,
                "{\"type\":\"LINK\",\"maxDownloads\":1}");

        ResponseEntity<byte[]> first = rest.exchange(
                url("/api/public/shares/" + shareToken + "/download"),
                HttpMethod.POST, new HttpEntity<>(null, jsonHeaders()), byte[].class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second attempt against the exhausted link must fail.
        ResponseEntity<String> second = rest.exchange(
                url("/api/public/shares/" + shareToken + "/download"),
                HttpMethod.POST, new HttpEntity<>(null, jsonHeaders()), String.class);
        assertThat(second.getStatusCode().is2xxSuccessful()).isFalse();
    }

    @Test
    void unknown_token_is_not_found() {
        ResponseEntity<String> meta = rest.getForEntity(
                url("/api/public/shares/does-not-exist"), String.class);
        assertThat(meta.getStatusCode().is2xxSuccessful()).isFalse();
    }

    @Test
    void listing_files_requires_authentication() {
        ResponseEntity<String> resp = rest.getForEntity(url("/api/files"), String.class);
        assertThat(resp.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
