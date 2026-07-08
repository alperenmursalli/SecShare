package org.example.secshare.vuln.serve;

import org.example.secshare.file.FileService;
import org.example.secshare.file.SharedFile;
import org.example.secshare.vuln.VulnProperties;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * VULN modulu: "public share link" ile dosya onizleme.
 *  - vuln.serve.stored-xss=true  -> Content-Disposition: inline + DB'deki
 *    kullanici kontrollu content-type. Yuklenen .svg/.html tarayicida
 *    calisir -> stored XSS (app origin'inde JS, token calma).
 *  - false -> guvenli: octet-stream + attachment + nosniff.
 *
 * Bu uc nokta SecurityConfig'te permitAll (tokensiz erisim).
 * SADECE YETKILI PENTEST / EGITIM ORTAMI ICIN.
 */
@RestController
@RequestMapping("/api/files")
public class ViewController {

    private final FileService fileService;
    private final VulnProperties vuln;

    public ViewController(FileService fileService, VulnProperties vuln) {
        this.fileService = fileService;
        this.vuln = vuln;
    }

    @GetMapping("/view/{id}")
    public ResponseEntity<Resource> view(@PathVariable("id") UUID id) {
        // public link: sahiplik aranmaz (paylasim linki mantigi)
        SharedFile file = fileService.getFileForDownload(id, null, true);
        Resource resource = fileService.toResource(file);

        boolean storedXss = vuln.isEnabled() && vuln.getServe().isStoredXss();

        if (storedXss) {
            // VULN: kullanici content-type'i ile inline servis -> XSS calisir
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(file.getContentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + file.getOriginalFilename() + "\"")
                    .body(resource);
        }

        // Guvenli: tarayicinin calistirmasini engelle
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getOriginalFilename() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                .body(resource);
    }
}
