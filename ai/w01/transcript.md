# AI Security Transcript — w01 (burn-after-reading atomicity)

> **VERBATIM AI OUTPUT — DO NOT EDIT.** This is the raw output of the five standard
> prompts run against tag `w01`. It is reproduced as-is, *including the places where the
> AI was wrong* (see §2 F-6 and §5 F-10, which triage later rejects). Human analysis of
> this transcript lives in `../../security/w01/{triage,misses,critique}.md`.
>
> Model: Claude (Opus). Target: `fix/burn-atomicity` @ build tagged `w01`.
> Scope: Spring Boot + PostgreSQL file-sharing service.

---

## Prompt 1 — Attack surface

> "Map the attack surface of this application: entry points, trust boundaries,
> authentication model, and the assets worth protecting. Think like an attacker."

**Entry points.**
- Unauthenticated: `POST /api/auth/register`, `POST /api/auth/login`,
  `GET /api/auth/verify`, `GET /api/info`, `GET /api/public/shares/{token}` (meta),
  `POST /api/public/shares/{token}/download`, static pages (`/`, `/index.html`,
  `/guide.html`, `/share.html`, `/s/**`), `GET /health`.
- Authenticated (JWT bearer): `POST /api/files/upload`, `GET /api/files`,
  `GET /api/files/{id}` (download), `DELETE /api/files/{id}`,
  `POST /api/files/{id}/shares`, `GET /api/files/{id}/shares`,
  `DELETE /api/files/shares/{shareId}`, `GET /api/files/{id}/downloads`,
  `GET /api/files/shares/{shareId}/members`, `GET /api/files/shared-with-me`,
  `POST /api/auth/resend-verification`, `GET /api/auth/me`.
- Admin only: `GET /api/files/all` (hasRole ADMIN, double-guarded by `@PreAuthorize`).

**Trust boundaries.**
- JWT: HS256, secret from `app.jwt.secret` (env), enforced ≥32 bytes decoded. Stateless;
  no server-side session, **no revocation list** — a token is valid until it expires.
- Public share tokens: the secret *is* the capability. `generateToken()` =
  `SecureRandom` URL-safe base64 (not guessable).
- Email-keyed authorization: audience membership / USER grants are matched on the JWT
  `email` claim, gated behind `emailVerified`.

**Assets.** Uploaded file bytes on disk (stored **plaintext**, no at-rest encryption),
share tokens, password hashes (bcrypt cost 12), user emails, download audit log.

**Attacker notes to pursue.** (a) No rate limiting is wired into this build — every
unauthenticated endpoint is unthrottled. (b) Files are stored in cleartext on disk.
(c) Upload content scanning only covers a few formats. (d) Burn-after-reading just got
reworked — worth hammering for concurrency bugs.

---

## Prompt 2 — Control smoke test

> "For each security control the system claims to have, verify it actually works.
> Where is it enforced, and can it be bypassed?"

- **AuthN (JWT):** `JwtAuthenticationFilter` parses + verifies signature; on any exception
  it clears the context and continues **anonymous** (fail-closed for protected routes —
  `anyRequest().authenticated()` then returns 401/403). OK.
  - **F-1 (Low):** No logout / token revocation. A leaked JWT is usable until expiry
    (`app.jwt.expiration-minutes`). No `jti` deny-list. Acceptable for the threat model
    only if expiry is short.
- **AuthZ (ownership/grant):** `getOwnedFileOrThrow` checks `owner.id == principal`;
  `getAccessibleFileOrThrow` allows owner OR active USER grant OR audience membership.
  Admin list double-gated. I could not find an IDOR in share creation — `createShare`
  goes through `getOwnedFileOrThrow` first. OK.
- **Email verification:** Enforced for all non-owner access in `getAccessibleFileOrThrow`
  (`user.isEmailVerified()` → 403). OK. Closes the "register an unclaimed address" path.
- **Share expiry / download limit:** `FileShare.isActive()` is checked on both the public
  and authenticated download paths. OK.
- **Burn-after-reading (link path):** `resolveLinkForDownload` now uses an atomic
  conditional `UPDATE` (`claimDownload`) with a row lock; concurrent downloads serialize
  and the loser gets 410. Verified by reading the SQL — this looks correct.
  - **F-2 (High):** **Rate limiting is absent in this build.** `login`, `register`,
    `verify`, and the public share endpoints are unthrottled → credential stuffing and
    token-guessing are unmetered. (Note: a `feature/rate-limiting` branch exists but is
    NOT merged into the tagged build, so for `w01` the control does not exist.)
- **Malware scanning:** `BuiltInFileScanner` blocks EICAR, PE/ELF/shebang, and
  extension/magic-byte mismatch for pdf/png/jpg. Runs before persistence. Partial — see F-7.

---

## Prompt 3 — Regression check

> "The repo has a prior security write-up (access control, stored XSS, share expiry).
> Confirm each previously-fixed issue is still fixed, and that the new change did not
> reopen anything."

- **Prior #1 — unverified-email access (IDOR):** still fixed. `getAccessibleFileOrThrow`
  requires `emailVerified` for non-owners. ✅
- **Prior #4 — expired/exhausted shares downloadable when authenticated:** still fixed.
  `isActive()` is enforced. ✅
- **Prior #2 — stored XSS via filename in admin list:** the legacy HTML pages were
  removed. The API now returns filenames as JSON only.
  - **F-3 (Medium, needs frontend check):** `originalFilename` is stored verbatim and
    echoed in `PublicShareMetaResponse` and `FileInfoResponse`. If any remaining static
    page injects it into the DOM with `innerHTML`, the stored XSS is back. I reviewed the
    server only; the frontend templating needs a human check.
  - **F-4 (Low):** `Content-Disposition` sets `filename="<originalFilename>"` with no
    sanitization (`FileController` / `PublicShareController`). A filename containing `"`
    or CR/LF can break/inject the header. Use RFC 5987 encoding.
- **New change (burn fix):** did not reopen any prior finding. The link-path race is
  closed. ✅

---

## Prompt 4 — Diff review

> "Review the diff that this tag introduces (the burn-after-reading fix) for new bugs,
> logic errors, or incomplete coverage."

Files changed in the diff: `FileShareRepository` (+`claimDownload`, +`claimRevoke`),
`AudienceMemberRepository` (+`claimFirstOpen`, +`incrementDownloadCount`),
`AudienceRepository` (+`incrementOpenedCount`, +`currentOpenedCount`),
`FileShareService.resolveLinkForDownload` + `recordAudienceOpenAndMaybeBurn`.

- The link path (`claimDownload`) and the audience path (`claimRevoke` + member/audience
  counters) are now atomic. FIRST-mode audience losers correctly throw 410. The logic
  reads correctly. ✅
- **F-5 (Low, ALL mode):** In `recordAudienceOpenAndMaybeBurn`, the ALL-mode terminal
  caller purges bytes after commit; a concurrent sub-threshold open can still call
  `loadResource` on a file that is about to be purged → possible `500`/`GONE` on a
  benign read. Narrow window; the counter logic itself is correct.
- `clearAutomatically` is set on `claimDownload` (re-read via `findById`) but deliberately
  not on `claimRevoke` (to keep LAZY associations attached for the caller) — this is
  correct and intentional.
- **No other defects found in the diff.** The fix is well-scoped and the regression tests
  assert "exactly one 2xx out of N concurrent downloads" on both the link and audience
  paths.

*(Scope note: this prompt reviewed only the lines in the diff. Code not touched by the
diff was out of scope for this pass.)*

---

## Prompt 5 — Abuse cases

> "Enumerate ways a malicious user could abuse the intended features."

- **F-6 (Medium) — email-claim spoofing:** Audience/USER authorization trusts the `email`
  claim in the JWT. An attacker could put an arbitrary email in the token to read files
  shared to that address. *(AI assessment: this is exploitable.)*
- **F-7 (Medium) — scanner evasion:** Only pdf/png/jpg get magic-byte checks, and
  executables/EICAR are caught. But `zip`, `doc`, `docx`, `xlsx`, `txt` are allowed and
  **not content-inspected** — a malicious macro doc or zipped payload passes. Also the
  scanner loads the entire file into memory (`file.getBytes()`), so many concurrent 50 MB
  uploads are a memory-pressure DoS.
- **F-8 (Low) — user enumeration:** `register` returns `409 "Email already exists"`,
  letting an attacker enumerate which emails have accounts. `login` is uniform (good).
- **F-9 (Low) — pre-password metadata disclosure:** `GET /api/public/shares/{token}`
  returns filename, size, and content type *before* the password is entered. Someone with
  the link (but not the password) learns what the file is.
- **F-10 (Medium) — unlimited audience fan-out = spam relay:** `configureAudience` lets an
  owner enqueue up to `MAX_AUDIENCE_MEMBERS` emails; with mail enabled this is an open
  spam/phishing relay from the app's domain. *(AI assessment: high abuse potential.)*

---

## AI self-summary

Highest-priority items I would fix first: **F-2 (no rate limiting)** and **F-6
(email-claim spoofing)**, then **F-7 (scanner gaps)**. The burn fix in this tag looks
correct and complete for the paths it touches; I found no new defect inside the diff.
