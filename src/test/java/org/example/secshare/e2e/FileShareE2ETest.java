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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage of the share lifecycle a real user drives: authenticate, upload a file,
 * mint a public share link, then fetch it anonymously through the public download page. Also
 * exercises the security-defining behaviours: password gating and burn-after-reading.
 */
class FileShareE2ETest extends AbstractE2ETest {

    @Autowired
    private UserRepository userRepository;

    /** Verifies an account's email via its pending token (mail is off in tests). */
    private void verifyEmail(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("no user for " + email));
        ResponseEntity<String> verify = rest.getForEntity(
                url("/api/auth/verify?token=" + user.getVerificationToken()), String.class);
        assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

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

    /** Creates an AUDIENCE share with the given burn mode and returns a member's public token. */
    @SuppressWarnings("unchecked")
    private String createAudienceShareAndMemberToken(String ownerToken, String fileId,
                                                     String memberEmail, String burnMode) {
        String body = "{\"type\":\"AUDIENCE\",\"recipientEmails\":[\"" + memberEmail
                + "\"],\"burnMode\":\"" + burnMode + "\"}";
        ResponseEntity<Map> created = rest.postForEntity(
                url("/api/files/" + fileId + "/shares"), jsonEntity(body, ownerToken), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody()).isNotNull();
        String shareId = (String) created.getBody().get("id");

        ResponseEntity<List> members = rest.exchange(
                url("/api/files/shares/" + shareId + "/members"),
                HttpMethod.GET, new HttpEntity<>(bearer(ownerToken)), List.class);
        assertThat(members.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(members.getBody()).isNotEmpty();
        Map<String, Object> member = (Map<String, Object>) members.getBody().get(0);
        String memberUrl = (String) member.get("url");
        assertThat(memberUrl).startsWith("/s/");
        return memberUrl.substring("/s/".length());
    }

    /** Fires {@code n} downloads of {@code token} simultaneously and returns how many got 2xx. */
    private int concurrentDownloadSuccesses(String token, int n) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await(); // all threads block here, then fire together
                ResponseEntity<byte[]> resp = rest.exchange(
                        url("/api/public/shares/" + token + "/download"),
                        HttpMethod.POST, new HttpEntity<>(null, jsonHeaders()), byte[].class);
                return resp.getStatusCode().is2xxSuccessful() ? 1 : 0;
            }));
        }
        ready.await();
        go.countDown();
        int successes = 0;
        for (Future<Integer> f : futures) {
            successes += f.get();
        }
        pool.shutdown();
        return successes;
    }

    /** Fires {@code n} authenticated downloads of {@code fileId} simultaneously; returns 2xx count. */
    private int concurrentAuthDownloadSuccesses(String fileId, String bearerToken, int n) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                ResponseEntity<byte[]> resp = rest.exchange(
                        url("/api/files/" + fileId), HttpMethod.GET,
                        new HttpEntity<>(bearer(bearerToken)), byte[].class);
                return resp.getStatusCode().is2xxSuccessful() ? 1 : 0;
            }));
        }
        ready.await();
        go.countDown();
        int successes = 0;
        for (Future<Integer> f : futures) {
            successes += f.get();
        }
        pool.shutdown();
        return successes;
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
    void burn_link_served_at_most_once_under_concurrent_downloads() throws Exception {
        // Regression for the burn-after-reading race: a one-download link must be served exactly
        // once even when many requests hit it simultaneously (atomic claimDownload).
        String token = registerAndLogin(uniqueEmail(), "supersecret1");
        String fileId = uploadFile(token, "race.txt", "only once".getBytes(StandardCharsets.UTF_8));
        String shareToken = createLinkShare(token, fileId, "{\"type\":\"LINK\",\"maxDownloads\":1}");

        int successes = concurrentDownloadSuccesses(shareToken, 8);
        assertThat(successes).isEqualTo(1);
    }

    @Test
    void first_mode_audience_share_served_at_most_once_under_concurrent_opens() throws Exception {
        // Regression for MISS-01: the same race on the audience path. A FIRST-mode group share must
        // be served to exactly one of many simultaneous opens; the losers get 410, not the file.
        String owner = registerAndLogin(uniqueEmail(), "supersecret1");
        String fileId = uploadFile(owner, "group-once.txt",
                "burn on first open".getBytes(StandardCharsets.UTF_8));
        String memberToken = createAudienceShareAndMemberToken(owner, fileId, uniqueEmail(), "FIRST");

        int successes = concurrentDownloadSuccesses(memberToken, 8);
        assertThat(successes).isEqualTo(1);
    }

    @Test
    void first_mode_user_grant_served_at_most_once_under_concurrent_authenticated_downloads() throws Exception {
        // Regression for MISS-02: the burn race also existed on the authenticated USER-grant path
        // (recordAuthenticatedAccessAndMaybeBurn). A FIRST-mode grant must be served to exactly one
        // of many simultaneous authenticated downloads; the losers get 410, not the file.
        String recipientEmail = uniqueEmail();
        String recipientToken = registerAndLogin(recipientEmail, "supersecret1");
        verifyEmail(recipientEmail); // non-owner access requires a verified email

        String ownerToken = registerAndLogin(uniqueEmail(), "supersecret1");
        String fileId = uploadFile(ownerToken, "user-once.txt",
                "burn on first recipient open".getBytes(StandardCharsets.UTF_8));
        ResponseEntity<Map> share = rest.postForEntity(
                url("/api/files/" + fileId + "/shares"),
                jsonEntity("{\"type\":\"USER\",\"recipientEmail\":\"" + recipientEmail
                        + "\",\"burnMode\":\"FIRST\"}", ownerToken), Map.class);
        assertThat(share.getStatusCode()).isEqualTo(HttpStatus.OK);

        int successes = concurrentAuthDownloadSuccesses(fileId, recipientToken, 8);
        assertThat(successes).isEqualTo(1);
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
