package org.example.secshare.file;

/**
 * Distinguishes the two sharing models a {@link FileShare} can represent.
 *
 * <ul>
 *   <li>{@link #LINK} — a public, tokenized URL anyone with the link can use to download.</li>
 *   <li>{@link #USER} — a grant to a specific registered recipient user.</li>
 * </ul>
 */
public enum ShareType {
    LINK,
    USER
}
