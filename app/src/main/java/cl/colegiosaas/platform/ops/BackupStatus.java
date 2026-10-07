package cl.colegiosaas.platform.ops;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Resultado del último respaldo (SEG-05), tal como lo deja el script de respaldo en
 * {@code last-backup.json}: {@code {"finishedAt": "2026-10-07T07:00:00Z", "ok": true, "sizeBytes": 123, "file": "…"}}.
 */
public record BackupStatus(Instant finishedAt, boolean ok, long sizeBytes, String file, String error) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    static BackupStatus read(Path path) {
        try {
            JsonNode data = JSON.readTree(Files.readString(path));
            return new BackupStatus(Instant.parse(data.path("finishedAt").asString()), data.path("ok").asBoolean(false),
                    data.path("sizeBytes").asLong(0), data.path("file").asString(null), data.path("error").asString(null));
        } catch (IOException | JacksonException | DateTimeParseException e) {
            return new BackupStatus(null, false, 0, null, "No se pudo leer el estado del respaldo: " + e.getMessage());
        }
    }
}
