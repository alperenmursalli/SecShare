package org.example.secshare.file.scan;

/**
 * A single scanning engine. Implementations must be safe to call with untrusted content
 * and must never throw for a merely "infected" verdict — that is expressed via the
 * returned {@link ScanResult}. Throwing is reserved for engine failures (e.g. the ClamAV
 * daemon being unreachable), which the orchestrator treats as "engine unavailable".
 */
public interface FileScanner {

    /**
     * @param content  the full file bytes
     * @param filename the original filename (used for extension/content checks)
     * @return the verdict for this engine
     * @throws Exception when the engine itself fails (not when content is infected)
     */
    ScanResult scan(byte[] content, String filename) throws Exception;

    /** Short engine name for logging and {@link ScanResult#engine()}. */
    String name();
}
