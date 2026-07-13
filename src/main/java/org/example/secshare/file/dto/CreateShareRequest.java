package org.example.secshare.file.dto;

/**
 * Request body for creating a share on a file.
 *
 * <p>For {@code type = "LINK"}, all of {@code password}, {@code expiresInMinutes} and
 * {@code maxDownloads} are optional link protections. For {@code type = "USER"},
 * {@code recipientEmail} is required. For {@code type = "AUDIENCE"}, {@code recipientEmails}
 * (the email list) is required and {@code name} optionally labels the group. {@code burnMode}
 * is an optional self-destruct policy for USER/AUDIENCE grants ({@code NONE}/{@code FIRST}/
 * {@code ALL}, see {@link org.example.secshare.file.BurnMode}); link fields are ignored for
 * both grant types.</p>
 */
public record CreateShareRequest(
        String type,
        String recipientEmail,
        java.util.List<String> recipientEmails,
        String name,
        String password,
        Long expiresInMinutes,
        Integer maxDownloads,
        Boolean burnAfterAccess,
        String burnMode
) {
}
