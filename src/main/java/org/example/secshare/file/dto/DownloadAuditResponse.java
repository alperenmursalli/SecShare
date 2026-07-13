package org.example.secshare.file.dto;

import java.time.Instant;

/**
 * One download event shown to the file owner. {@code downloaderEmail} is null for an anonymous
 * public-link download (the UI renders that as "Anonymous link").
 */
public record DownloadAuditResponse(
        String source,
        String downloaderEmail,
        String ipAddress,
        boolean burned,
        Instant downloadedAt
) {
}
