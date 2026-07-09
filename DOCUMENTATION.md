# SecShare — Documentation & User Guide

SecShare is a file-sharing application with **JWT-based authentication** that lets
users register, sign in, and then upload and manage their files. The backend is
built with Spring Boot (Java 17), the database is PostgreSQL, and files are stored
on the server's disk.

> ⚠️ **Security note:** This application was built for educational / security-testing
> purposes. Be careful when running it on the public internet — don't put sensitive
> data on it and restrict access.

---

## 1. How the application works

### Architecture

```
Browser / curl
      │  (HTTP + JWT Bearer token)
      ▼
┌─────────────────────────────┐
│  Spring Boot (port 8080)    │
│  ├─ AuthController  /api/auth│  → register & login, issues token
│  ├─ FileController  /api/files│ → upload, list, download, delete
│  ├─ JWT filter               │  → validates the token on every request
│  └─ Static pages             │  → files.html, test.html
└──────────┬──────────┬────────┘
           │          │
     ┌─────▼────┐  ┌──▼─────────────────┐
     │PostgreSQL│  │ Disk: /app/uploads │
     │ users,   │  │ (uploaded files)   │
     │ files    │  └────────────────────┘
     └──────────┘
```

### Authentication flow (JWT)

1. A user registers via `/api/auth/register`. The password is hashed with
   **BCrypt** and written to the `users` table.
2. The user logs in via `/api/auth/login`; if the credentials are valid, a
   **JWT access token** is returned.
3. On every subsequent protected request, this token is sent in the
   `Authorization: Bearer <token>` header.
4. `JwtAuthenticationFilter` validates the token on each request; if it's valid,
   the requesting user is identified. Tokens are valid for **60 minutes** by default.

The token contains the user id (`subject`), `email`, and `roles`. No session is kept
on the server (**stateless**) — identity comes entirely from the token.

### File storage

- An uploaded file is saved to disk with the name `<uuid>.<extension>` (no collisions).
- The original name, size, content type, and owner are stored in the `files` table.
- A file can only be downloaded/deleted by **its owner**. Accessing someone else's
  file returns `403 Forbidden`.
- Deletion is a **soft-delete** (the record is marked `deleted=true`) and the file
  is also removed from disk.

### Limits

| Rule | Value |
|------|-------|
| Maximum file size | 50 MB |
| Allowed extensions | `pdf, png, jpg, jpeg, txt, doc, docx, xlsx, zip` |
| Password length | 8–72 characters |
| Token lifetime | 60 minutes (default) |

---

## 2. How to run it (with Docker)

Prerequisite: Docker + docker-compose installed.

```bash
# 1) Prepare environment variables
cp .env.example .env
# Set a strong DB_PASSWORD and JWT_SECRET in .env.
# To generate a JWT secret:  openssl rand -base64 32

# 2) Start the app + PostgreSQL together
docker-compose up -d --build

# 3) Follow the logs
docker-compose logs -f app
```

It's ready once you see the line "Started SecshareApplication".

Open in a browser:
- **http://localhost:8080/test.html** — quick test UI
- **http://localhost:8080/files.html** — file management UI

Management commands:

```bash
docker-compose ps                # status
docker-compose down              # stop (data is kept)
docker-compose down -v           # stop + delete DB & files
docker-compose up -d --build     # rebuild after code changes
```

---

## 3. How to try it (step by step — curl)

You can run the commands below in your terminal in order.

### 3.1 Register

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"password123"}'
```
Success → `201 Created`. Same email again → `409 Conflict`.

### 3.2 Log in and get a token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"password123"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')

echo $TOKEN
```

### 3.3 Verify identity (test endpoint)

```bash
curl http://localhost:8080/hello -H "Authorization: Bearer $TOKEN"
# → Hello demo@example.com
```

### 3.4 Upload a file

```bash
echo "hello secshare" > sample.txt

curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@sample.txt"
# → {"id":"...","name":"sample.txt","sizeBytes":15,...}
```
Note the returned `id`.

### 3.5 List your files

```bash
curl http://localhost:8080/api/files -H "Authorization: Bearer $TOKEN"
```

### 3.6 Download a file

```bash
curl http://localhost:8080/api/files/<FILE_ID> \
  -H "Authorization: Bearer $TOKEN" -O -J
```

### 3.7 Delete a file

```bash
curl -X DELETE http://localhost:8080/api/files/<FILE_ID> \
  -H "Authorization: Bearer $TOKEN"
# → 204 No Content
```

### 3.8 Access attempt without a token

```bash
curl -i http://localhost:8080/api/files
# → 403 (not authenticated)
```

---

## 4. API Reference

Base URL: `http://localhost:8080`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | ✗ | Register. Body: `{email, password}`. → 201 |
| POST | `/api/auth/login` | ✗ | Log in. Body: `{email, password}`. → `{accessToken}` |
| GET | `/hello` | ✓ | Identity test. → `Hello <email>` |
| POST | `/api/files/upload` | ✓ | Upload with a multipart `file` field. → file info |
| GET | `/api/files` | ✓ | List your own files |
| GET | `/api/files/{id}` | ✓ | Download a file (owner only) |
| DELETE | `/api/files/{id}` | ✓ | Delete a file (owner only). → 204 |
| GET | `/api/files/all` | ✓ ADMIN | All files (ADMIN role only) |
| GET | `/health`, `/healthz` | ✗ | Health check. → `{"status":"ok"}` |

**Auth header:** `Authorization: Bearer <accessToken>`

### Request/response bodies

`RegisterRequest` / `LoginRequest`:
```json
{ "email": "demo@example.com", "password": "password123" }
```

`AuthResponse` (login response):
```json
{ "accessToken": "eyJhbGciOi..." }
```

`FileInfoResponse` (upload/list):
```json
{
  "id": "9e56a43a-...",
  "name": "sample.txt",
  "sizeBytes": 15,
  "contentType": "text/plain",
  "createdAt": "2026-07-09T20:04:26Z"
}
```

### Common HTTP status codes

| Code | Meaning |
|------|---------|
| 201 | Registration succeeded |
| 400 | Bad request (empty file, disallowed extension, short password) |
| 401 | Wrong email/password (login) |
| 403 | Missing/invalid token, or someone else's file |
| 409 | Email already registered |
| 413 | File larger than 50 MB |

---

## 5. Web interface

- **`/test.html`** — a quick test page for getting a token and trying simple requests.
- **`/files.html`** — a UI to log in and upload/list/download files.

To use it from the browser, log in through these pages first; the page stores the
token for you and attaches it to requests.

---

## 6. Configuration (environment variables)

Variables read from `.env` (by docker-compose) and supported by the application:

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/secshare` | Database address |
| `SPRING_DATASOURCE_USERNAME` | — | DB user (`DB_USER`) |
| `SPRING_DATASOURCE_PASSWORD` | — | DB password (`DB_PASSWORD`) |
| `JWT_SECRET` | (dev default) | Base64, must be at least 32 bytes |
| `JWT_EXPIRATION_MINUTES` | `60` | Token lifetime (minutes) |
| `STORAGE_PATH` | `/app/uploads` | Directory where files are stored |
| `PORT` | `8080` | HTTP port |
| `LOG_LEVEL` | `INFO` | Spring Security log level |

> `JWT_SECRET` must be at least 32 bytes (after base64 decoding), otherwise the
> application won't start. To generate one: `openssl rand -base64 32`

---

## 7. Troubleshooting

- **App won't start / DB error:** Check `docker-compose logs app` and
  `docker-compose logs db`. Wait for the DB to become `Up (healthy)` — the app
  won't start until the DB is ready.
- **404 at `/`:** Normal, there's no root route. Use `/test.html` or `/files.html`.
- **Upload returns 400:** Is the extension in the allow-list? (`pdf, png, jpg, jpeg,
  txt, doc, docx, xlsx, zip`) and the file must not be empty.
- **Request returns 403:** The token may be missing/expired; log in again.
- **Port conflict:** If 8080 or 5432 is taken, change the port mapping in
  `docker-compose.yml`.

---

## 8. Technology summary

- **Backend:** Spring Boot 3.4.2, Java 17
- **Security:** Spring Security, JWT (jjwt 0.12.5), BCrypt (strength 12)
- **Data:** Spring Data JPA + PostgreSQL 16
- **Storage:** Local file system (`/app/uploads`, persistent via Docker volume)
- **Packaging:** Docker (multi-stage build) + docker-compose
