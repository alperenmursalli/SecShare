package org.example.secshare.file;

/** How a recorded download reached the file. */
public enum DownloadSource {
    /** Public tokenized link ({@link ShareType#LINK}); downloader is anonymous. */
    LINK,
    /** A grant to a specific registered user ({@link ShareType#USER}). */
    USER,
    /** An audience member, via their account-less token or by signing in. */
    AUDIENCE
}
