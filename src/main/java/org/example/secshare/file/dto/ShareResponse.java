package org.example.secshare.file.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Describes a share back to its owner. {@code url} and link fields are populated only for
 * LINK shares; {@code recipientEmail} only for USER shares.
 */
public record ShareResponse(
        UUID id,
        String type,
        UUID fileId,
        String fileName,
        String url,
        String recipientEmail,
        boolean passwordProtected,
        Instant expiresAt,
        Integer maxDownloads,
        int downloadCount,
        boolean revoked,
        boolean active,
        Instant createdAt
) {
}
