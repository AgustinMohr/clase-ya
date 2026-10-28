package com.claseya.verification.service;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * STOR-01: ClamAV INSTREAM client. The scan is synchronous and fail-closed: an infected file is
 * rejected (400) and an unreachable scanner answers 503, so no unscanned evidence is stored.
 */
@Component
@ConditionalOnProperty(name = "app.storage.scan.enabled", havingValue = "true")
public class ClamAvDocumentScanner implements DocumentScanner {

    private static final Logger log = LoggerFactory.getLogger(ClamAvDocumentScanner.class);
    private static final int CHUNK = 8192;

    private final String host;
    private final int port;
    private final int timeoutMs;

    public ClamAvDocumentScanner(@Value("${app.storage.scan.host:localhost}") String host,
                                 @Value("${app.storage.scan.port:3310}") int port,
                                 @Value("${app.storage.scan.timeout-ms:5000}") int timeoutMs) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public void scan(byte[] content) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            OutputStream out = socket.getOutputStream();
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for (int offset = 0; offset < content.length; offset += CHUNK) {
                int length = Math.min(CHUNK, content.length - offset);
                out.write(ByteBuffer.allocate(4).putInt(length).array());
                out.write(content, offset, length);
            }
            out.write(new byte[4]);
            out.flush();

            String response = readNullTerminated(socket.getInputStream());
            if (response.contains("FOUND")) {
                throw new BadRequestException("The document failed the malware scan");
            }
            if (!response.contains("OK")) {
                throw new ServiceUnavailableException("Antivirus scan failed");
            }
        } catch (BadRequestException | ServiceUnavailableException ex) {
            throw ex;
        } catch (IOException ex) {
            log.warn("ClamAV is unavailable", ex);
            throw new ServiceUnavailableException("Antivirus scan is unavailable");
        }
    }

    private String readNullTerminated(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int value;
        while ((value = in.read()) != -1 && value != 0) {
            buffer.write(value);
        }
        return buffer.toString(StandardCharsets.US_ASCII);
    }
}
