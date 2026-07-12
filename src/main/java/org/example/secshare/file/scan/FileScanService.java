package org.example.secshare.file.scan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the available scanners. The built-in scanner always runs as a cheap first
 * layer; when ClamAV is enabled it runs additionally for real-world coverage. A file is
 * rejected if <em>any</em> engine flags it. If ClamAV is enabled but unreachable, the scan
 * gracefully degrades to the built-in result (logged as a warning) so uploads keep working
 * in environments without a running daemon.
 */
@Service
public class FileScanService {

    private static final Logger log = LoggerFactory.getLogger(FileScanService.class);

    private final BuiltInFileScanner builtInScanner;
    private final ClamAvScanner clamAvScanner;

    public FileScanService(BuiltInFileScanner builtInScanner, ClamAvScanner clamAvScanner) {
        this.builtInScanner = builtInScanner;
        this.clamAvScanner = clamAvScanner;
    }

    /**
     * Scans the given content and returns the first "infected" verdict, or a clean result
     * when every active engine passes it.
     */
    public ScanResult scan(byte[] content, String filename) {
        ScanResult builtIn = safeScan(builtInScanner, content, filename);
        if (builtIn != null && !builtIn.clean()) {
            return builtIn;
        }

        if (clamAvScanner.isEnabled()) {
            ScanResult clam = safeScan(clamAvScanner, content, filename);
            if (clam != null && !clam.clean()) {
                return clam;
            }
        }

        return ScanResult.clean(clamAvScanner.isEnabled() ? "built-in+clamav" : "built-in");
    }

    /**
     * Runs one engine, converting engine failures into null (meaning "no verdict") so the
     * caller can fall back. A null from the built-in scanner should never happen, but is
     * handled defensively.
     */
    private ScanResult safeScan(FileScanner scanner, byte[] content, String filename) {
        try {
            return scanner.scan(content, filename);
        } catch (Exception e) {
            log.warn("Scanner '{}' unavailable, skipping ({}). Falling back.",
                    scanner.name(), e.getMessage());
            return null;
        }
    }
}
