package cl.colegiosaas.media;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contra un clamd falso que habla el protocolo INSTREAM y marca como amenaza todo lo que contenga "EICAR". */
class ClamAvScannerTest {

    ServerSocket server;
    Thread thread;

    @BeforeEach
    void startFakeClamd() throws IOException {
        server = new ServerSocket(0);
        thread = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    byte[] command = new byte[10];
                    in.readFully(command);
                    assertThat(new String(command, StandardCharsets.US_ASCII)).isEqualTo("zINSTREAM\0");
                    ByteArrayOutputStream content = new ByteArrayOutputStream();
                    for (int length = in.readInt(); length > 0; length = in.readInt()) {
                        content.write(in.readNBytes(length));
                    }
                    String reply = content.toString(StandardCharsets.US_ASCII).contains("EICAR")
                            ? "stream: Eicar-Test-Signature FOUND\0" : "stream: OK\0";
                    socket.getOutputStream().write(reply.getBytes(StandardCharsets.US_ASCII));
                } catch (IOException e) {
                    return;
                }
            }
        });
        thread.start();
    }

    @AfterEach
    void stop() throws IOException {
        server.close();
    }

    @Test
    void cleanFilesPassAndThreatsAreNamed() {
        ClamAvScanner scanner = new ClamAvScanner("localhost", server.getLocalPort(), Duration.ofSeconds(5));

        assertThat(scanner.scan(new byte[200_000]).clean()).isTrue();
        FileScanner.Verdict verdict = scanner.scan("X5O!P%@AP EICAR-STANDARD-ANTIVIRUS-TEST-FILE".getBytes(StandardCharsets.US_ASCII));
        assertThat(verdict.clean()).isFalse();
        assertThat(verdict.signature()).isEqualTo("Eicar-Test-Signature");
    }

    @Test
    void anUnreachableAntivirusRejectsTheUploadInsteadOfLettingItThrough() throws IOException {
        int port = server.getLocalPort();
        server.close();
        ClamAvScanner scanner = new ClamAvScanner("localhost", port, Duration.ofSeconds(1));

        assertThatThrownBy(() -> scanner.scan(new byte[10])).isInstanceOf(UncheckedIOException.class);
        assertThatThrownBy(() -> UploadChecks.requireClean(scanner, new byte[10]))
                .isInstanceOf(FileUploadException.class).hasMessageContaining("antivirus no responde");
    }
}
