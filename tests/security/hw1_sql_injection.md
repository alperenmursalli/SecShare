# HW1 exploit test — SQL injection

**Test:** [`Hw1SqlInjectionE2ETest.java`](./Hw1SqlInjectionE2ETest.java) (JUnit 5 + Testcontainers Postgres, full HTTP stack).

This directory is registered as an extra Maven test-source root via `build-helper-maven-plugin`
(see `pom.xml`), so the test compiles and runs with the normal test phase and in CI
(`.github/workflows/security.yml`).

## Run

```bash
./mvnw test -Dtest=Hw1SqlInjectionE2ETest
```

## What it proves

- `benign_search_returns_only_my_own_matching_files` — the `GET /api/files?q=` search is a real,
  working feature.
- `sql_injection_cannot_exfiltrate_other_users_credentials` — sends a UNION-based payload through
  the search term and asserts the **safe** behaviour: the response must not contain any other
  user's e-mail or BCrypt hash.

Pattern (A6): the test asserts safe behaviour, so it **FAILS at `hw1-vulnerable`** (the attack
leaks `users.email` + `users.password_hash`) and **PASSES at `hw1-fixed`** (parameterised query).
