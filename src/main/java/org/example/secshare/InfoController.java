package org.example.secshare;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
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

    @Value("${app.scan.clamav.enabled:false}")
    private boolean clamavEnabled;

    @Value("${app.jwt.expiration-minutes:60}")
    private int tokenLifetimeMinutes;

    @GetMapping
    public Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "SecShare");
        info.put("version", "1.0.0");
        info.put("mailEnabled", mailEnabled);
        info.put("malwareScanning", clamavEnabled ? "built-in + ClamAV" : "built-in");
        info.put("description", "SecShare is a self-hosted, authenticated file-sharing service. "
                + "Users register an account, sign in to receive a JWT, and then upload, list, "
                + "download and delete their own files over a REST API. Files are private to the "
                + "owner; nobody else can read or delete them. Owners can then share a file as a "
                + "public tokenized link, as a direct grant to another user, or to a whole email "
                + "audience. Uploads are scanned for malware before they are stored.");
        info.put("features", Map.of(
                "malwareScanning", "Every upload is scanned synchronously (built-in heuristics, plus "
                        + "ClamAV when enabled); infected files are rejected before being persisted.",
                "sharing", "Three models: public links (/s/<token>), direct user grants, and email "
                        + "audiences that fan out to many recipients from a single share.",
                "linkProtections", "Public links can require a password and carry an expiry and/or a "
                        + "maximum download count.",
                "selfDestruct", "Grants support burn-after-reading: destroy on the first open (FIRST) "
                        + "or once every recipient has opened it (ALL).",
                "downloadAudit", "Owners can see who downloaded a file, when, and through which channel.",
                "email", "When enabled, per-recipient audience links are delivered via a durable, "
                        + "retrying SMTP outbox; owners may opt into download notifications."
        ));
        info.put("authentication", Map.of(
                "type", "Bearer JWT",
                "howTo", "Call POST /api/auth/login and send the returned accessToken as "
                        + "an 'Authorization: Bearer <token>' header on every protected request.",
                "tokenLifetimeMinutes", tokenLifetimeMinutes
        ));
        info.put("limits", Map.of(
                "maxFileSize", "50 MB",
                "allowedExtensions", List.of("pdf", "png", "jpg", "jpeg", "txt", "doc", "docx", "xlsx", "zip")
        ));
        info.put("endpoints", List.of(
                endpoint("POST", "/api/auth/register", false, "Create a new account with an email and password (min 8 chars)."),
                endpoint("POST", "/api/auth/login", false, "Exchange credentials for a JWT access token."),
                endpoint("GET", "/api/auth/me", true, "Return the authenticated user's profile (id, email, roles, join date)."),
                endpoint("GET", "/api/files", true, "List the files owned by the authenticated user."),
                endpoint("POST", "/api/files/upload", true, "Upload a file (multipart/form-data field 'file'); rejected if the malware scan flags it."),
                endpoint("GET", "/api/files/{id}", true, "Download one of your files by id."),
                endpoint("DELETE", "/api/files/{id}", true, "Delete one of your files by id."),
                endpoint("GET", "/api/files/all", true, "List every file in the system (ADMIN role only)."),
                endpoint("POST", "/api/files/{id}/shares", true, "Create a share on your file: a public LINK, a USER grant, or an AUDIENCE (email list)."),
                endpoint("GET", "/api/files/{id}/shares", true, "List the shares you have created for one of your files."),
                endpoint("DELETE", "/api/files/shares/{shareId}", true, "Revoke a share you created."),
                endpoint("GET", "/api/files/{id}/downloads", true, "Download audit log for one of your files (who, when, channel)."),
                endpoint("GET", "/api/files/shares/{shareId}/members", true, "List the recipients of an audience share and their download state."),
                endpoint("GET", "/api/files/shared-with-me", true, "List files other users have shared directly with you."),
                endpoint("GET", "/api/public/shares/{token}", false, "Public metadata for a shared link (filename, size, whether a password is required)."),
                endpoint("POST", "/api/public/shares/{token}/download", false, "Download a file via its public link; send the password in the body when required."),
                endpoint("GET", "/api/info", false, "This usage guide."),
                endpoint("GET", "/health", false, "Liveness probe; returns {\"status\":\"ok\"}.")
        ));
        info.put("guideUrl", "/guide.html");
        return info;
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
