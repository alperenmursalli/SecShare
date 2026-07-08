package org.example.secshare.vuln.process;

import org.example.secshare.vuln.VulnProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * VULN modulu: yuklenen dosyayi "arsivle" (zip).
 *  - vuln.process.cmd-injection=true  -> filename kabuga birlestirilir
 *    (sh -c "zip ... " + filename) -> command injection / RCE.
 *    ornek: {"filename":"x; cat <path>/master-keys.txt"}
 *  - false -> ProcessBuilder arg dizisi (kabuk yok), metakarakterler literal.
 *
 * SADECE YETKILI PENTEST / EGITIM ORTAMI ICIN.
 */
@RestController
@RequestMapping("/api/files")
public class ArchiveController {

    private final VulnProperties vuln;
    private final String storageBasePath;

    public ArchiveController(VulnProperties vuln,
                             @Value("${app.storage.base-path:uploads}") String storageBasePath) {
        this.vuln = vuln;
        this.storageBasePath = storageBasePath;
    }

    public record ArchiveRequest(String filename) {}

    @PostMapping("/archive")
    public ResponseEntity<String> archive(@RequestBody ArchiveRequest request) {
        String filename = request.filename();
        if (filename == null || filename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filename gerekli");
        }

        boolean cmdInjection = vuln.isEnabled() && vuln.getProcess().isCmdInjection();
        File workDir = new File(storageBasePath);

        try {
            Process process;
            if (cmdInjection) {
                // VULN: kullanici girdisi kabuk komutuna birlestiriliyor
                String cmd = "zip -q archive.zip " + filename;
                process = new ProcessBuilder("sh", "-c", cmd)
                        .directory(workDir)
                        .redirectErrorStream(true)
                        .start();
            } else {
                // Guvenli: kabuk yok, arg dizisi -> metakarakterler literal
                process = new ProcessBuilder("zip", "-q", "archive.zip", filename)
                        .directory(workDir)
                        .redirectErrorStream(true)
                        .start();
            }

            byte[] out = process.getInputStream().readNBytes(64 * 1024);
            process.waitFor(10, TimeUnit.SECONDS);
            String output = new String(out, StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(output.isBlank() ? "(cikti yok, exit=" + process.exitValue() + ")" : output);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Arsivleme hatasi: " + e.getMessage());
        }
    }
}
