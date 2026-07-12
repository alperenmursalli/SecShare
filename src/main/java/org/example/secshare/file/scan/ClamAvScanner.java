package org.example.secshare.file.scan;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Real anti-virus engine backed by a ClamAV daemon (clamd) spoken to over TCP using the
 * {@code INSTREAM} command. Enabled only when {@code app.scan.clamav.enabled=true}; when
 * the daemon is unreachable, {@link #scan} throws so the orchestrator can fall back to the
 * built-in scanner rather than failing the upload.
 *
 * <p>INSTREAM protocol: send {@code zINSTREAM\0}, then a sequence of chunks each prefixed
 * by a 4-byte big-endian length, terminated by a zero-length chunk. The daemon replies
 * with {@code stream: OK} or {@code stream: <signature> FOUND}.</p>
 */
@Component
public class ClamAvScanner implements FileScanner {

    private final boolean enabled;
    private final String host;
    private final int port;
    private final int timeoutMs;
    private final int chunkSize = 8192;

    public ClamAvScanner(
            @Value("${app.scan.clamav.enabled:false}") boolean enabled,
            @Value("${app.scan.clamav.host:localhost}") String host,
            @Value("${app.scan.clamav.port:3310}") int port,
            @Value("${app.scan.clamav.timeout-ms:5000}") int timeoutMs
    ) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public ScanResult scan(byte[] content, String filename) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 InputStream in = socket.getInputStream()) {

                out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));

                for (int offset = 0; offset < content.length; offset += chunkSize) {
                    int len = Math.min(chunkSize, content.length - offset);
                    out.writeInt(len);           // 4-byte big-endian length
                    out.write(content, offset, len);
                }
                out.writeInt(0);                 // terminating zero-length chunk
                out.flush();

                String response = readResponse(in).trim();
                if (response.endsWith("OK")) {
                    return ScanResult.clean(name());
                }
                if (response.contains("FOUND")) {
                    // Format: "stream: Eicar-Test-Signature FOUND"
                    String sig = response
                            .replaceFirst("^stream:\\s*", "")
                            .replaceFirst("\\s*FOUND$", "");
                    return ScanResult.infected(sig, name());
                }
                // Unexpected reply (e.g. "INSTREAM size limit exceeded") — treat as engine error.
                throw new IOException("Unexpected clamd response: " + response);
            }
        }
    }

    private String readResponse(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == 0) {   // clamd null-terminates replies
                break;
            }
            sb.append((char) b);
        }
        return sb.toString();
    }

    @Override
    public String name() {
        return "clamav";
    }
}
