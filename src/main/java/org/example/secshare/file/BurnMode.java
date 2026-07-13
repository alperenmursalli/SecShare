package org.example.secshare.file;

/**
 * Optional self-destruct policy for a {@link ShareType#USER} grant. Controls whether, and
 * when, the underlying file is irreversibly destroyed as recipients open it.
 *
 * <ul>
 *   <li>{@link #NONE}: no auto-destroy; recipients may keep downloading while the grant is active.</li>
 *   <li>{@link #FIRST}: the file is destroyed the moment any one recipient opens it (one-time).</li>
 *   <li>{@link #ALL}: the file is destroyed only once every active recipient has opened it
 *       at least once.</li>
 * </ul>
 */
public enum BurnMode {
    NONE,
    FIRST,
    ALL
}
