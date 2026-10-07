package cl.colegiosaas.platform.ops;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.shared.crypto.FieldCipher;
import cl.colegiosaas.shared.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exportación completa del colegio (OPS-10): un ZIP con cada tabla en JSON legible (los datos cifrados van
 * descifrados) y todos los archivos subidos. Sirve para cambiarse de proveedor o guardar una copia propia: el
 * colegio es dueño de sus datos. Quedan fuera solo los secretos de acceso (contraseñas, segundo factor, enlaces
 * de un solo uso), que no sirven fuera de esta instalación.
 */
@Component
public class SchoolExport {

    private static final Logger log = LoggerFactory.getLogger(SchoolExport.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> SKIPPED_TABLES = Set.of("account_token", "user_account_recovery_code",
            "flyway_schema_history");
    private static final Map<String, Set<String>> SKIPPED_COLUMNS = Map.of("user_account", Set.of("password_hash", "mfa_secret"));

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final FileStorage storage;
    private final FieldCipher cipher;
    private final AuditTrail audit;

    SchoolExport(DataSource dataSource, JdbcTemplate jdbc, FileStorage storage, FieldCipher cipher, AuditTrail audit) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.storage = storage;
        this.cipher = cipher;
        this.audit = audit;
    }

    /** Escribe el ZIP en la salida, tabla por tabla y archivo por archivo, sin cargarlo entero en memoria. */
    public void write(OutputStream out) throws IOException {
        audit.record(AuditAction.EXPORT, "School", 1, "Exportación completa del colegio");
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("LEEME.txt"));
            zip.write(readme().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (String table : tables()) {
                zip.putNextEntry(new ZipEntry("datos/" + table + ".json"));
                writeTable(table, zip);
                zip.closeEntry();
            }
            for (String key : jdbc.queryForList("select storage_key from stored_file order by id", String.class)) {
                if (!storage.exists(key)) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry("archivos/" + key));
                try (InputStream in = storage.open(key)) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
    }

    List<String> tables() {
        List<String> tables = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getTables(connection.getCatalog(), connection.getSchema(), "%", new String[] {"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                    if (!SKIPPED_TABLES.contains(name)) {
                        tables.add(name);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo leer la lista de tablas", e);
        }
        tables.sort(String::compareTo);
        return tables;
    }

    private void writeTable(String table, OutputStream out) {
        Set<String> skipped = SKIPPED_COLUMNS.getOrDefault(table, Set.of());
        // El generador no cierra la salida: es la entrada del ZIP.
        JsonGenerator json = JSON.createGenerator(new NonClosing(out));
        json.writeStartArray();
        jdbc.query("select * from " + table, rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            json.writeStartObject();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String column = meta.getColumnLabel(i).toLowerCase(Locale.ROOT);
                if (skipped.contains(column)) {
                    continue;
                }
                json.writeName(column);
                writeValue(json, rs.getObject(i));
            }
            json.writeEndObject();
        });
        json.writeEndArray();
        json.close();
    }

    private void writeValue(JsonGenerator json, Object value) {
        switch (value) {
            case null -> json.writeNull();
            case String text when text.startsWith("v1:") -> json.writeString(decrypt(text));
            case String text -> json.writeString(text);
            case Boolean bool -> json.writeBoolean(bool);
            case Integer number -> json.writeNumber(number);
            case Long number -> json.writeNumber(number);
            case Number number -> json.writeNumber(number.toString());
            case Timestamp timestamp -> json.writeString(timestamp.toInstant().toString());
            case byte[] bytes -> json.writeString(Base64.getEncoder().encodeToString(bytes));
            default -> json.writeString(value.toString());
        }
    }

    private String decrypt(String text) {
        try {
            return cipher.decrypt(text);
        } catch (IllegalStateException e) {
            log.warn("Exportación: un valor cifrado no se pudo descifrar y va tal cual");
            return text;
        }
    }

    private static String readme() {
        return """
                Exportación completa del colegio
                Generada el %s

                datos/<tabla>.json  Cada tabla de la base de datos, una fila por objeto. Los datos personales que la
                                    instalación guarda cifrados (nombres, correos, mensajes) van descifrados: trata
                                    este archivo con el mismo cuidado que la base de datos.
                archivos/<ruta>     Cada archivo subido (fotos originales y difuminadas, PDF, evidencias), con la ruta
                                    de la columna storage_key de datos/stored_file.json. Las versiones reducidas de
                                    las fotos se pueden regenerar y no se incluyen.

                No se incluyen las contraseñas, los secretos del segundo factor ni los enlaces de un solo uso: no
                sirven fuera de esta instalación.
                """.formatted(Instant.now());
    }

    /** Evita que el generador JSON cierre la entrada del ZIP al terminar una tabla. */
    private static final class NonClosing extends OutputStream {
        private final OutputStream out;

        NonClosing(OutputStream out) {
            this.out = out;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            out.flush();
        }
    }
}
