# HW1 — Injection (SQL injection)

## 1. Vulnerable feature

The "search my files" feature: `GET /api/files?q=<term>`. A logged-in user can filter their
own uploaded files by name from the file dashboard. It is part of the normal
`GET /api/files` endpoint, not a separate demo endpoint.

At tag `hw1-vulnerable`:

- Entry point: `FileController.java:38-48`. When `q` is present, it calls
  `fileService.searchMyFiles(user, q)` (line 46).
- Vulnerable code: `FileService.java:154-169` (`searchMyFiles`). The term `q` is put straight
  into a SQL string (line 162: `... ILIKE '%" + q + "%' ...`) and run with
  `entityManager.createNativeQuery(sql)` (line 166).

## 2. Attacker

Any registered user of SecShare. They can sign up and get a valid token, but they are not
allowed to see other users' files or passwords. Their goal is to read data that belongs to
other accounts, especially the stored credentials, so they can take those accounts over.

This is the "malicious insider" from our threat model: a normal logged-in user who tries to
reach everyone else's data, not just their own.

## 3. Exploit

Because `q` is concatenated into the SQL, we close the `ILIKE '%...%'` string and add a
`UNION SELECT` on the `users` table.

Payload (the value of `q`):

```
' UNION SELECT id, email || ':' || password_hash, 0, 'x'::varchar, created_at, 'CLEAN' FROM users -- 
```

The columns line up with what the search returns
(`id, original_filename, size_bytes, content_type, created_at, scan_status`), so each user
row comes back as a "file" whose name is `email:password_hash`.

Run the test:

```bash
./mvnw test -Dtest=Hw1SqlInjectionE2ETest
```

Expected vs actual at `hw1-vulnerable`:

- Expected (safe): the search returns only the attacker's own files.
- Actual: the response contains every user's email and BCrypt hash, for example
  `{"name":"user-...@example.com:$2a$12$tvPfiXdJyB7N...","scanStatus":"CLEAN"}`. The test's
  safe-behaviour checks fail, which shows the leak.

## 4. Impact

With one request an attacker reads any table they want. We show it by dumping
`users.email` and `users.password_hash` for every account. The leaked emails plus the BCrypt
hashes let an attacker crack passwords offline and log in as other users. The same trick can
read file metadata or any other table.

Rating: Critical. An ordinary user with no special rights gets full read access to the
password table. (CWE-89: SQL Injection.)

## 5. Fix

See the diff between `hw1-vulnerable` and `hw1-fixed`. We rewrote the query. The endpoint and
its behaviour stay the same.

The root problem was that the term was part of the SQL text. The fix makes it just data, and
we added a few more checks on top:

1. Parameterised query (the main fix). `SharedFileRepository.searchOwnedByFilename`
   (`SharedFileRepository.java:14-25`) is a JPA `@Query` where the term is a bound `:term`
   parameter. The database gets it as a value, never as SQL, so `UNION`/`--` is just a normal
   search string that matches nothing.
2. Owner check inside the query. It also binds `:owner` and filters `f.owner = :owner`, so
   results can only ever be the caller's own files.
3. Input validation. `FileService.searchMyFiles` (`FileService.java:159-176`) rejects terms
   longer than 100 characters with `400 Bad Request`.
4. LIKE wildcard escaping. `%`, `_` and `\` in the term are escaped and matched with
   `ESCAPE '\'`, so they are treated as plain characters.

We also deleted the old native-SQL method and its row-mapping helper.

## 6. Verification

Test: `tests/security/Hw1SqlInjectionE2ETest.java` (JUnit 5 with a Testcontainers Postgres,
running the full HTTP stack). It is added to the Maven test phase with
`build-helper-maven-plugin` (see `pom.xml`) and runs in CI (`.github/workflows/security.yml`).

We used the recommended pattern: the test checks for the safe behaviour, so the same test
fails at `hw1-vulnerable` (the response contains a victim email and a `$2a$` hash) and passes
at `hw1-fixed` (the same payload returns nothing from other users). A second test,
`benign_search_returns_only_my_own_matching_files`, confirms the real search still works.

CI is green for the fixed commit (the security workflow runs the test):
https://github.com/alperenmursalli/SecShare/actions/workflows/security.yml

## 7. Residual risk

- After escaping, a crafted term can at most match or miss the caller's own file names. It
  cannot reach another user's data.
- We fixed this at the application level only. The database user can still read the `users`
  table. With more time we would run the app under a DB role that can only touch the tables
  it needs, so a future bug is harder to abuse.
- We rely on using parameters at every query. A good next step is a CI check that blocks
  `createNativeQuery` / string-built SQL so nobody reintroduces this.
- We checked the other endpoints; they use Spring Data methods or bound `@Query` parameters.
  This search was the only place with string-built SQL.

## 8. Contribution

- Alperen Mursallı (05220000313): built the search feature and the exploit test, wrote the
  report.
- Özgür Say (05220001004): wrote the parameterised fix and the extra checks (owner filter,
  length limit, wildcard escaping) and the CI workflow.

Both of us can explain every line of the vulnerable and fixed code.
