package org.example.secshare.vuln.legacy;

import org.example.secshare.auth.security.UserPrincipal;
import org.example.secshare.file.FileService;
import org.example.secshare.file.SharedFile;
import org.example.secshare.vuln.VulnProperties;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * VULN modulu: "legacy" indirme uc noktalari.
 *  - /api/files/legacy/{id} : sahiplik kontrolu bayrakla atlanir (IDOR)
 *  - /api/files/raw?path=   : normalize edilmeyen yol ile ham okuma (LFI)
 *
 * SADECE YETKILI PENTEST / EGITIM ORTAMI ICIN.
 */
@RestController
@RequestMapping("/api/files")
public class LegacyFileController {

    private final FileService fileService;
    private final VulnProperties vuln;

    public LegacyFileController(FileService fileService, VulnProperties vuln) {
        this.fileService = fileService;
        this.vuln = vuln;
    }

    /** IDOR: bayrak acikken baskasinin dosyasi da indirilebilir. */
    @GetMapping("/legacy/{id}")
    public ResponseEntity<Resource> legacyDownload(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal UserPrincipal user
    ) {
        boolean bypass = vuln.isEnabled() && vuln.getAccess().isLegacyDownload();
        SharedFile file = fileService.getFileForDownload(id, user, bypass);
        Resource resource = fileService.toResource(file);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getOriginalFilename() + "\"")
                .body(resource);
    }

    /** Path traversal / LFI: bayrak acikken depo disina cikilabilir. */
    @GetMapping("/raw")
    public ResponseEntity<Resource> raw(@RequestParam("path") String path) {
        boolean allowTraversal = vuln.isEnabled() && vuln.getFile().isPathTraversal();
        Resource resource = fileService.readRawPath(path, allowTraversal);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"raw\"")
                .body(resource);
    }
}
