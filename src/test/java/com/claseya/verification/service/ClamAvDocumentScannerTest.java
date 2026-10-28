package com.claseya.verification.service;

import com.claseya.common.exception.BadRequestException;
import com.claseya.common.exception.ServiceUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClamAvDocumentScannerTest {

    private ServerSocket server;

    @AfterEach
    void stopServer() throws IOException {
        if (server != null) {
            server.close();
        }
    }

    private int startFakeClamAv(String response) throws IOException {
        server = new ServerSocket(0);
        Thread worker = new Thread(() -> {
            try (Socket socket = server.accept()) {
                InputStream in = socket.getInputStream();
                for (int i = 0; i < 10; i++) {
                    if (in.read() < 0) return;
                }
                while (true) {
                    byte[] lengthBytes = in.readNBytes(4);
                    if (lengthBytes.length < 4) return;
                    int length = ByteBuffer.wrap(lengthBytes).getInt();
                    if (length <= 0) break;
                    in.skipNBytes(length);
                }
                OutputStream out = socket.getOutputStream();
                out.write(response.getBytes(StandardCharsets.US_ASCII));
                out.flush();
            } catch (IOException ignored) {
                // The test closes the server after the assertion.
            }
        });
        worker.setDaemon(true);
        worker.start();
        return server.getLocalPort();
    }

    private ClamAvDocumentScanner scanner(int port) {
        return new ClamAvDocumentScanner("127.0.0.1", port, 3000);
    }

    @Test
    void acceptsCleanContent() throws IOException {
        int port = startFakeClamAv("stream: OK\0");

        assertThatCode(() -> scanner(port).scan("hello".getBytes(StandardCharsets.UTF_8)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInfectedContent() throws IOException {
        int port = startFakeClamAv("stream: Eicar-Test-Signature FOUND\0");

        assertThatThrownBy(() -> scanner(port).scan("x".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void failsClosedWhenTheScannerIsUnavailable() throws IOException {
        int port;
        try (ServerSocket freePort = new ServerSocket(0)) {
            port = freePort.getLocalPort();
        }

        assertThatThrownBy(() -> scanner(port).scan("x".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
