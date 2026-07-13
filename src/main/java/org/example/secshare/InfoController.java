package org.example.secshare;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Public, machine-readable usage guide for the SecShare API.
 * Describes what the service is for and how to consume each endpoint.
 */
@RestController
@RequestMapping("/api/info")
public class InfoController {

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @GetMapping
    public Map<String, Object> info() {
        return Map.of(
                "name", "SecShare",
                "version", "1.0.0",
                "mailEnabled", mailEnabled,
                "description", "SecShare is a self-hosted, authenticated file-sharing service. "
                        + "Users register an account, sign in to receive a JWT, and then upload, "
                        + "list, download and delete their own files over a REST API. Files are "
                        + "private to the owner; nobody else can read or delete them.",
                "authentication", Map.of(
                        "type", "Bearer JWT",
                        "howTo", "Call POST /api/auth/login and send the returned accessToken as "
                                + "an 'Authorization: Bearer <token>' header on every protected request.",
                        "tokenLifetimeMinutes", 60
                ),
                "limits", Map.of(
                        "maxFileSize", "50 MB",
                        "allowedExtensions", List.of("pdf", "png", "jpg", "jpeg", "txt", "doc", "docx", "xlsx", "zip")
                ),
                "endpoints", List.of(
                        endpoint("POST", "/api/auth/register", false, "Create a new account with an email and password (min 8 chars)."),
                        endpoint("POST", "/api/auth/login", false, "Exchange credentials for a JWT access token."),
                        endpoint("GET", "/api/auth/me", true, "Return the authenticated user's profile (id, email, roles, join date)."),
                        endpoint("GET", "/api/files", true, "List the files owned by the authenticated user."),
                        endpoint("POST", "/api/files/upload", true, "Upload a file (multipart/form-data field 'file')."),
                        endpoint("GET", "/api/files/{id}", true, "Download one of your files by id."),
                        endpoint("DELETE", "/api/files/{id}", true, "Delete one of your files by id."),
                        endpoint("GET", "/api/files/all", true, "List every file in the system (ADMIN role only)."),
                        endpoint("GET", "/api/info", false, "This usage guide."),
                        endpoint("GET", "/health", false, "Liveness probe; returns {\"status\":\"ok\"}.")
                ),
                "guideUrl", "/guide.html"
        );
    }

    private static Map<String, Object> endpoint(String method, String path, boolean authRequired, String summary) {
        return Map.of(
                "method", method,
                "path", path,
                "authRequired", authRequired,
                "summary", summary
        );
    }
}
