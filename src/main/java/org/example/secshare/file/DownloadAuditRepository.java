package org.example.secshare.file;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DownloadAuditRepository extends JpaRepository<DownloadAudit, UUID> {

    List<DownloadAudit> findByFileIdOrderByCreatedAtDesc(UUID fileId, Pageable pageable);
}
