package org.example.secshare.file;

import org.example.secshare.auth.security.UserPrincipal;
import org.example.secshare.config.HttpRequestUtils;
import org.example.secshare.file.dto.DownloadAuditResponse;
import org.example.secshare.mail.EmailOutboxService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Records a download-audit row for every recipient download and, when warranted, notifies the file
 * owner by email (reusing {@link EmailOutboxService}). Also serves the owner's download history.
 *
 * <p>An owner is emailed when the download <em>burned</em> the file, or when the share opted in via
 * {@code notifyOnDownload} — but never for the owner's own download, and only when mail is
 * configured.</p>
 */
@Service
public class DownloadAuditService {

    private static final int HISTORY_LIMIT = 200;

    private final DownloadAuditRepository auditRepository;
    private final EmailOutboxService emailOutboxService;
    private final FileService fileService;

    public DownloadAuditService(
            DownloadAuditRepository auditRepository,
            EmailOutboxService emailOutboxService,
            FileService fileService
    ) {
        this.auditRepository = auditRepository;
        this.emailOutboxService = emailOutboxService;
        this.fileService = fileService;
    }

    /**
     * Records one download and, if appropriate, enqueues an owner notification. Must be called
     * inside the download transaction (both the audit row and any notification participate in it).
     */
    public void record(SharedFile file, FileShare share, DownloadSource source,
                        String downloaderEmail, boolean burned) {
        DownloadAudit audit = new DownloadAudit();
        audit.setId(UUID.randomUUID());
        audit.setFileId(file.getId());
        audit.setFileName(file.getOriginalFilename());
        audit.setShareId(share != null ? share.getId() : null);
        audit.setSource(source);
        audit.setDownloaderEmail(downloaderEmail);
        audit.setIpAddress(HttpRequestUtils.clientIp());
        audit.setUserAgent(HttpRequestUtils.userAgent());
        audit.setBurned(burned);
        audit.setCreatedAt(Instant.now());
        auditRepository.save(audit);

        boolean wantsNotify = burned || (share != null && share.isNotifyOnDownload());
        String ownerEmail = file.getOwner().getEmail();
        boolean downloaderIsOwner = downloaderEmail != null && downloaderEmail.equalsIgnoreCase(ownerEmail);
        if (wantsNotify && !downloaderIsOwner && emailOutboxService.isConfigured()) {
            emailOutboxService.enqueue(ownerEmail, notifySubject(burned), notifyBody(audit, burned));
        }
    }

    @Transactional(readOnly = true)
    public List<DownloadAuditResponse> listForFile(UUID fileId, UserPrincipal principal) {
        fileService.getOwnedFileOrThrow(fileId, principal); // enforces ownership
        return auditRepository
                .findByFileIdOrderByCreatedAtDesc(fileId, PageRequest.of(0, HISTORY_LIMIT))
                .stream()
                .map(a -> new DownloadAuditResponse(
                        a.getSource().name(),
                        a.getDownloaderEmail(),
                        a.getIpAddress(),
                        a.isBurned(),
                        a.getCreatedAt()))
                .toList();
    }

    private static String notifySubject(boolean burned) {
        return burned
                ? "Your file was downloaded and self-destructed on SecShare"
                : "Your file was downloaded on SecShare";
    }

    private static String notifyBody(DownloadAudit audit, boolean burned) {
        String who = audit.getDownloaderEmail() != null
                ? audit.getDownloaderEmail()
                : "someone via a public link";
        String ip = audit.getIpAddress() != null ? audit.getIpAddress() : "unknown";
        StringBuilder body = new StringBuilder()
                .append("Your file \"").append(audit.getFileName()).append("\" was downloaded on SecShare.\n\n")
                .append("By: ").append(who).append("\n")
                .append("When: ").append(audit.getCreatedAt()).append("\n")
                .append("IP: ").append(ip).append("\n");
        if (burned) {
            body.append("\nThis was its last allowed download, so the file has now been permanently destroyed.\n");
        }
        body.append("\nThis is an automated message; please do not reply.");
        return body.toString();
    }
}
