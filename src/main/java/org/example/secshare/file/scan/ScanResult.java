package org.example.secshare.file.scan;

/**
 * Outcome of a single scan.
 *
 * @param clean     true when no threat was detected
 * @param signature name of the threat/rule that matched (null when {@code clean})
 * @param engine    which engine produced the verdict (e.g. {@code "built-in"}, {@code "clamav"})
 */
public record ScanResult(boolean clean, String signature, String engine) {

    public static ScanResult clean(String engine) {
        return new ScanResult(true, null, engine);
    }

    public static ScanResult infected(String signature, String engine) {
        return new ScanResult(false, signature, engine);
    }
}
