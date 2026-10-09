# SecShare

A self-hosted, security-focused file-sharing service built with **Spring Boot** and
**PostgreSQL**. SecShare gives every user a private space to upload files and then share
them deliberately — through expiring public links, direct grants to other users, or bulk
email audiences — with **malware scanning on upload** and **burn-after-reading
self-destruct** baked in.

🌐 **Live Demo:** https://sec-share.duckdns.org

---

## Features

- 🔐 JWT authentication — register, sign in, BCrypt-hashed passwords
- 👤 Per-user ownership isolation — nobody can list, download, or delete another user's files
- 🛡️ **Malware & content scanning on upload** — a built-in heuristic scanner plus optional
  **ClamAV**; infected uploads are rejected *before* they ever touch disk
- 🔗 **Three sharing models**
  - **Public links** — tokenized short URLs (`/s/<token>`), optionally protected with a
    password, an expiry, and/or a maximum download count
  - **Direct user grants** — share with a specific registered recipient
  - **Audiences** — share with a whole email list in one action (fans out to thousands of
    recipients via batched inserts)
- 💥 **Burn-after-reading self-destruct** — destroy the file on the first recipient's open
  (`FIRST`) or once every recipient has opened it (`ALL`); a background reaper purges
  expired, unread links
- 📜 **Download audit log** — who downloaded what, when, and through which channel
- ✉️ **Email delivery** — per-recipient audience links mailed via a durable outbox (SMTP,
  with retries); optional owner notifications on download
- 🪪 Account-less recipient links — audience members can download without signing up
- 🌐 **Bilingual UI (TR / EN)** with auto-detection and a persistent toggle
- 📊 Storage usage tracking per account
- 📦 PostgreSQL + on-disk file storage
- 🐳 Docker & Docker Compose support
- 🚀 Automated deployment via GitHub Actions
- 🧪 Full-stack end-to-end tests (Testcontainers)

---

## Tech Stack

### Backend
- Java 17
- Spring Boot (Spring Security, Spring Data JPA, Spring Mail)
- JWT (JSON Web Token)
- Maven

### Frontend
- Vanilla HTML / CSS / JavaScript served as static pages by Spring Boot
  (`index.html`, `share.html`, `guide.html`) — no build step, no SPA framework
- Lightweight custom i18n engine (`static/i18n.js`) for TR/EN

### Database
- PostgreSQL 16

### Malware scanning
- Built-in heuristic scanner (always on)
- Optional [ClamAV](https://www.clamav.net/) daemon over TCP

### DevOps
- Docker & Docker Compose
- Nginx reverse proxy
- GitHub Actions
- Oracle Cloud Infrastructure (primary) / Render blueprint (`render.yaml`)
- DuckDNS

---

## Architecture

```
                         Browser / curl
                              │  (HTTP + JWT Bearer token)
                              ▼
                        Nginx Reverse Proxy
                              │
                              ▼
          ┌─────────────────────────────────────────┐
          │           Spring Boot API                │
          │  AuthController     /api/auth            │
          │  FileController     /api/files           │
          │  FileShareController /api/files/.../shares│
          │  PublicShareController /api/public/shares │
          │  Static UI          index/share/guide.html│
          │  ── Malware scan (built-in + ClamAV)     │
          │  ── Self-destruct reaper (scheduled)     │
          │  ── Email outbox (SMTP, retrying)        │
          └───────┬───────────────────┬───────┬──────┘
                  │                   │       │
            ┌─────▼─────┐      ┌──────▼───┐  ┌▼──────────┐
            │PostgreSQL │      │ Disk:    │  │ ClamAV    │
            │users,file │      │ uploads/ │  │ (optional)│
            │shares,...  │      └──────────┘  └───────────┘
            └───────────┘
```

---

## Getting Started

### Clone the repository

```bash
git clone https://github.com/alperenmursalli/SecShare.git
cd SecShare
```

### Configure environment variables

Create a `.env` file (consumed by `docker-compose`). The most common variables:

| Variable | Default | Purpose |
|----------|---------|---------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/secshare` | Postgres JDBC URL |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | — | DB credentials |
| `JWT_SECRET` | *(dev fallback)* | Base64 HMAC secret — **set your own in production** |
| `JWT_EXPIRATION_MINUTES` | `60` | Access-token lifetime |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | — | If both set, an ADMIN account is seeded on startup |
| `STORAGE_PATH` | `uploads` | On-disk directory for stored files |
| `CLAMAV_ENABLED` | `false` | Run ClamAV in addition to the built-in scanner |
| `CLAMAV_HOST` / `CLAMAV_PORT` / `CLAMAV_TIMEOUT_MS` | `localhost` / `3310` / `5000` | ClamAV daemon connection |
| `CLEANUP_ENABLED` / `CLEANUP_INTERVAL_MS` | `true` / `60000` | Self-destruct reaper for expired links |
| `MAIL_ENABLED` | `false` | Enable outbound audience emails |
| `MAIL_FROM` | `no-reply@secshare.local` | Envelope/from address |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | — / `587` / — / — | SMTP server (Gmail, SES, SendGrid, …) |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | `true` / `true` | SMTP transport options |
| `PUBLIC_BASE_URL` | — | Absolute base (e.g. `https://secshare.example.com`) for links in emails |
| `PORT` | `8080` | HTTP listen port |

Example:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/secshare
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_password

JWT_SECRET=your_base64_secret

# Optional: real virus scanning
CLAMAV_ENABLED=true
CLAMAV_HOST=clamav

# Optional: email out audience links
MAIL_ENABLED=true
MAIL_HOST=smtp.example.com
MAIL_USERNAME=apikey
MAIL_PASSWORD=your_smtp_password
PUBLIC_BASE_URL=https://secshare.example.com
```

### Run with Docker

```bash
docker-compose up -d --build   # start
docker-compose ps              # view containers
docker-compose logs -f         # tail logs
docker-compose down            # stop
```

The app serves both the API and the web UI at `http://localhost:8080`.

---

## Usage

- **Web UI** — open the app, create an account, sign in, upload files, and create shares from
  the dashboard. A built-in usage guide lives at `/guide.html`.
- **API** — a machine-readable description of the service and every endpoint is always
  available at `GET /api/info`.

### Limits & rules

- **Maximum file size:** 50 MB per file
- **Allowed extensions:** `pdf`, `png`, `jpg`, `jpeg`, `txt`, `doc`, `docx`, `xlsx`, `zip`
- **Password:** minimum 8 characters at registration
- **Isolation:** you can only access files your account owns (ADMIN can list all)

---

## Testing

End-to-end tests live under `src/test/java/org/example/secshare/e2e`. They boot the **real
application** on a random port against a throwaway **PostgreSQL** container
([Testcontainers](https://testcontainers.com)) and drive the same HTTP stack a browser hits —
JWT security, JPA, and on-disk file storage. Covered journeys include register → login → `/me`,
upload → create share link → anonymous public download, password-protected links, and
burn-after-reading self-destruct.

### Requirements

- A running **Docker** engine (the tests start a Postgres container automatically).

### Run

```bash
./mvnw test
```

To run only the E2E suite:

```bash
./mvnw -Dtest='org.example.secshare.e2e.*E2ETest' test
```

> Docker Engine 25+ requires API version 1.44+. The Maven Surefire config pins the Testcontainers
> client to `api.version=1.44` so tests connect to the socket on modern Docker versions.

---

## Deployment

Deployment is automated through **GitHub Actions**. Each push to the `main` branch:

1. Opens a secure SSH connection to the Oracle Cloud VM
2. Pulls the latest source (`git reset --hard origin/main`)
3. Rebuilds the Docker image and restarts containers (`docker-compose up -d --build`)

A `render.yaml` blueprint is also included for one-click deployment to [Render](https://render.com).

---

## Security

SecShare follows common security best practices:

- JWT authentication with configurable expiry
- BCrypt password hashing
- Role-based access control (USER / ADMIN)
- Per-user ownership isolation on every file operation
- Malware/content scanning that rejects infected uploads before they are persisted
- Burn-after-reading self-destruct and expiring, download-capped public links
- Environment-based secret management
- Reverse-proxy + containerized deployment
- Input validation on all requests

> ⚠️ This project was built for educational and security-testing purposes. Review it before
> exposing it to the public internet, and don't store genuinely sensitive data on a demo
> instance.

---

## Roadmap

- Asynchronous (post-upload) scanning with quarantine state
- File versioning
- File encryption at rest
- Email verification & password reset
- Per-account storage quotas (hard limits)

---

## Project Status

🚧 Actively developed — new features and improvements are added continuously.

---

## License

This project is available for educational and portfolio purposes.
