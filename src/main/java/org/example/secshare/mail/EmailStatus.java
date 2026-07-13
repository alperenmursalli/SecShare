package org.example.secshare.mail;

/** Lifecycle of a queued outbound email. */
public enum EmailStatus {
    PENDING,
    SENT,
    FAILED
}
