package cl.colegiosaas.media;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Cliente de clamd (ClamAV) por el comando INSTREAM: el archivo viaja en bloques de 64 KB, cada uno
 * precedido por su largo en 4 bytes, y un bloque de largo cero cierra el envío. La respuesta es
 * {@code stream: OK} o {@code stream: <firma> FOUND}.
 */
public class ClamAvScanner implements FileScanner {

    private static final int CHUNK = 64 * 1024;

    private final String host;
    private final int port;
    private final Duration timeout;

    public ClamAvScanner(String host, int port, Duration timeout) {
        this.host = host;
        this.port = port;
        this.timeout = timeout;
    }

    @Override
    public Verdict scan(byte[] content) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) timeout.toMillis());
            socket.setSoTimeout((int) timeout.toMillis());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for (int offset = 0; offset < content.length; offset += CHUNK) {
                int length = Math.min(CHUNK, content.length - offset);
                out.writeInt(length);
                out.write(content, offset, length);
            }
            out.writeInt(0);
            out.flush();
            String reply = readReply(socket.getInputStream());
            if (reply.endsWith("OK")) {
                return Verdict.ok();
            }
            if (reply.endsWith("FOUND")) {
                String signature = reply.substring(reply.indexOf(':') + 1, reply.length() - "FOUND".length()).strip();
                return Verdict.infected(signature);
            }
            throw new IllegalStateException("Respuesta inesperada del antivirus: " + reply);
        } catch (IOException e) {
            // Sin antivirus disponible no se acepta el archivo: es más seguro reintentar que publicar sin revisar.
            throw new UncheckedIOException("El antivirus no responde; intenta de nuevo en unos minutos", e);
        }
    }

    @Override
    public boolean isActive() {
        return true;
    }

    private static String readReply(InputStream in) throws IOException {
        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) > 0) {
            reply.write(b);
        }
        return reply.toString(StandardCharsets.US_ASCII).strip();
    }
}
