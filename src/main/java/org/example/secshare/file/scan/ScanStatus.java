package org.example.secshare.file.scan;

/**
 * Result of a malware/content scan for a stored file.
 *
 * <p>With synchronous scanning on upload, persisted files are always {@link #CLEAN}
 * (infected uploads are rejected before they ever reach disk). {@link #PENDING} and
 * {@link #INFECTED} exist so the model can also support asynchronous scanning later.</p>
 */
public enum ScanStatus {
    PENDING,
    CLEAN,
    INFECTED
}
