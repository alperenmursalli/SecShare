package org.example.secshare.file;

/**
 * Distinguishes the two sharing models a {@link FileShare} can represent.
 *
 * <ul>
 *   <li>{@link #LINK} — a public, tokenized URL anyone with the link can use to download.</li>
 *   <li>{@link #USER} — a grant to a specific registered recipient user.</li>
 *   <li>{@link #AUDIENCE} — a grant to a whole {@link Audience} (email list), reaching many
 *       recipients through a single share.</li>
 * </ul>
 */
public enum ShareType {
    LINK,
    USER,
    AUDIENCE
}
