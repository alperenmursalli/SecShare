package org.example.secshare.file.dto;

/** Request body for downloading via a share link; {@code password} required only if the link is protected. */
public record DownloadShareRequest(String password) {
}
