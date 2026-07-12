package org.example.secshare.file.dto;

/**
 * Public, unauthenticated metadata for a share link's download page. Never exposes the
 * password hash, owner, or storage details.
 */
public record PublicShareMetaResponse(
        String fileName,
        long sizeBytes,
        String contentType,
        boolean needsPassword,
        boolean available
) {
}
