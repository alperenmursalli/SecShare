package org.example.secshare.file.scan;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Dependency-free scanner that runs everywhere (no external daemon). It catches the three
 * things most relevant to a file-sharing service used for education/portfolio purposes:
 *
 * <ol>
 *   <li><b>The EICAR test file</b> — the industry-standard, harmless string every AV
 *       flags. Lets us demonstrate a real "infected" rejection safely.</li>
 *   <li><b>Executable / script payloads</b> — a Windows PE ({@code MZ}), a Linux ELF, or a
 *       {@code #!} shebang appearing at the start of the content, regardless of the
 *       declared extension.</li>
 *   <li><b>Extension/content mismatch</b> — a file claiming to be a {@code pdf}/{@code png}/
 *       {@code jpg} whose magic bytes say otherwise (a classic way to smuggle a disguised
 *       payload past an extension allow-list).</li>
 * </ol>
 *
 * <p>It is intentionally conservative: it only flags mismatches for a small set of formats
 * with unambiguous signatures, to avoid false positives on legitimate files.</p>
 */
@Component
public class BuiltInFileScanner implements FileScanner {

    /** The canonical EICAR anti-virus test string (harmless). */
    private static final byte[] EICAR = (
            "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
    ).getBytes(StandardCharsets.US_ASCII);

    // Magic-byte signatures.
    private static final byte[] SIG_PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] SIG_PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] SIG_JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] SIG_PE = {'M', 'Z'};                 // Windows executable
    private static final byte[] SIG_ELF = {0x7F, 'E', 'L', 'F'};     // Linux executable
    private static final byte[] SIG_SHEBANG = {'#', '!'};            // shell/interpreter script

    @Override
    public ScanResult scan(byte[] content, String filename) {
        if (content == null || content.length == 0) {
            return ScanResult.clean(name());
        }

        if (contains(content, EICAR)) {
            return ScanResult.infected("EICAR-Test-Signature", name());
        }

        // Disguised executables/scripts, whatever the extension claims to be.
        if (startsWith(content, SIG_PE)) {
            return ScanResult.infected("Suspicious.Executable.PE", name());
        }
        if (startsWith(content, SIG_ELF)) {
            return ScanResult.infected("Suspicious.Executable.ELF", name());
        }
        if (startsWith(content, SIG_SHEBANG)) {
            return ScanResult.infected("Suspicious.Script.Shebang", name());
        }

        String mismatch = extensionContentMismatch(content, extensionOf(filename));
        if (mismatch != null) {
            return ScanResult.infected(mismatch, name());
        }

        return ScanResult.clean(name());
    }

    /**
     * Returns a signature name when the declared extension has a well-known magic number
     * that the content does not match, otherwise null. Only checks formats whose signatures
     * are unambiguous, to keep false positives near zero.
     */
    private String extensionContentMismatch(byte[] content, String ext) {
        switch (ext) {
            case "pdf":
                return startsWith(content, SIG_PDF) ? null : "Heuristic.ExtensionMismatch.PDF";
            case "png":
                return startsWith(content, SIG_PNG) ? null : "Heuristic.ExtensionMismatch.PNG";
            case "jpg":
            case "jpeg":
                return startsWith(content, SIG_JPG) ? null : "Heuristic.ExtensionMismatch.JPEG";
            default:
                // txt / doc / docx / xlsx / zip have looser or container formats — skip.
                return null;
        }
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || haystack.length < needle.length) {
            return false;
        }
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot == -1 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    @Override
    public String name() {
        return "built-in";
    }
}
