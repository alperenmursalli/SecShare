package org.example.secshare.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scheduled reaper that enforces the "self-destruct" guarantee for burn-after-reading
 * links. A burn link that is downloaded is destroyed immediately on the download path; this
 * job covers the other case — a burn link that <em>expires unread</em> — so those bytes do
 * not linger on disk. Enabled by default; disable with {@code app.cleanup.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "app.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class ShareCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ShareCleanupService.class);

    private final FileShareRepository fileShareRepository;
    private final FileService fileService;

    public ShareCleanupService(FileShareRepository fileShareRepository, FileService fileService) {
        this.fileShareRepository = fileShareRepository;
        this.fileService = fileService;
    }

    @Scheduled(fixedDelayString = "${app.cleanup.interval-ms:60000}",
            initialDelayString = "${app.cleanup.interval-ms:60000}")
    @Transactional
    public void purgeExpiredBurnLinks() {
        List<FileShare> candidates = fileShareRepository.findByBurnAfterAccessTrueAndRevokedFalse();
        int purged = 0;

        for (FileShare share : candidates) {
            SharedFile file = share.getFile();

            if (file.isDeleted()) {
                // Bytes already gone (e.g. owner deleted the file); just retire the link.
                share.setRevoked(true);
                fileShareRepository.save(share);
                continue;
            }

            if (share.isExpired() || share.isDownloadLimitReached()) {
                fileService.purge(file);
                share.setRevoked(true);
                fileShareRepository.save(share);
                purged++;
            }
        }

        if (purged > 0) {
            log.info("Self-destruct reaper purged {} expired burn-after-reading file(s)", purged);
        }
    }
}
