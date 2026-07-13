package org.example.secshare.file;

/**
 * Result of resolving a share link for download.
 *
 * @param file the file to serve
 * @param burn true when this download exhausted a burn link (its last allowed download);
 *             the caller must destroy the file's bytes immediately after capturing them
 *             for the response
 */
public record LinkDownload(SharedFile file, boolean burn) {
}
