package org.example.secshare.file.dto;

/**
 * Request body for creating a share on a file.
 *
 * <p>For {@code type = "LINK"}, all of {@code password}, {@code expiresInMinutes} and
 * {@code maxDownloads} are optional link protections. For {@code type = "USER"},
 * {@code recipientEmail} is required and the link fields are ignored.</p>
 */
public record CreateShareRequest(
        String type,
        String recipientEmail,
        String password,
        Long expiresInMinutes,
        Integer maxDownloads
) {
}
