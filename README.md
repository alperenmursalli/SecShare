# SecureShare

A modern and secure file-sharing platform built with **Spring Boot**, **React**, and **PostgreSQL**. SecureShare allows users to upload, manage, and securely share files through an intuitive web interface while following modern security best practices.

🌐 **Live Demo:** https://sec-share.duckdns.org

---

## Features

- 🔐 Secure user authentication with JWT
- 👤 User registration and login
- 📁 File upload and download
- 🔗 Secure shareable file links
- 🛡️ Role-based authorization
- 🔒 Password hashing with Spring Security
- 📦 PostgreSQL database integration
- 🐳 Docker & Docker Compose support
- 🚀 Automated deployment with GitHub Actions
- ☁️ Oracle Cloud deployment

---

## Tech Stack

### Backend

- Java
- Spring Boot
- Spring Security
- JWT (JSON Web Token)
- Maven

### Frontend

- React
- TypeScript
- Vite

### Database

- PostgreSQL

### DevOps

- Docker
- Docker Compose
- Nginx
- GitHub Actions
- Oracle Cloud Infrastructure
- DuckDNS

---

## Architecture

```
                 Browser
                     │
                     ▼
                 Nginx Reverse Proxy
                  ┌───────────────┐
                  ▼               ▼
            React Frontend   Spring Boot API
                                      │
                                      ▼
                                PostgreSQL
```

---

## Live Demo

Visit the application:

**https://sec-share.duckdns.org**

---

## Getting Started

### Clone the repository

```bash
git clone https://github.com/alperenmursalli/Secure-Share.git
cd Secure-Share
```

### Configure environment variables

Create a `.env` file and configure the required environment variables.

Example:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/secureshare
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_password

JWT_SECRET=your_base64_secret
```

### Run with Docker

```bash
docker-compose up -d --build
```

View running containers:

```bash
docker-compose ps
```

View logs:

```bash
docker-compose logs -f
```

Stop the application:

```bash
docker-compose down
```

---

## Authentication

SecureShare uses JWT authentication for protecting API endpoints.

Example:

```http
Authorization: Bearer <JWT_TOKEN>
```

Passwords are securely hashed before storage using Spring Security.

---

## Deployment

Deployment is fully automated through **GitHub Actions**.

Each push to the `main` branch triggers:

1. GitHub Actions workflow
2. Secure SSH connection to the Oracle Cloud VM
3. Pulling the latest source code
4. Docker image rebuild
5. Automatic container restart

---

## Security

SecureShare follows common security best practices, including:

- JWT Authentication
- Password Hashing
- Role-Based Access Control (RBAC)
- Protected REST API Endpoints
- Environment-based Secret Management
- Reverse Proxy Configuration
- Containerized Deployment
- Input Validation

---

## Roadmap

- Email verification
- Password reset
- File versioning
- Expiring download links
- File encryption at rest
- Activity logs
- Storage quota management
- Multi-file sharing
- Virus scanning integration

---

## Project Status

🚧 This project is actively under development, and new features and improvements are continuously being added.

---

## License

This project is available for educational and portfolio purposes.
