package cl.colegiosaas.media;

import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * Archivo físico en el almacenamiento S3/MinIO (OPS-07). Lo usan medios, documentos y adjuntos.
 * El pipeline de subida (fase 5) borra el EXIF ANTES de guardarlo (MED-10): aquí nunca hay GPS.
 */
@Entity
@Table(name = "stored_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFile extends BaseEntity {

    /** Ruta dentro del bucket, p. ej. "media/2026/10/3f2a….jpg". Nunca el nombre original. */
    @NotBlank
    private String storageKey;

    /** Nombre que subió el usuario, ya saneado; solo se usa como nombre de descarga. */
    private String originalName;

    @NotBlank
    private String contentType;

    private long sizeBytes;

    /** Permite detectar archivos duplicados en la carga masiva. */
    @Pattern(regexp = "[0-9a-f]{64}")
    private String sha256;

    private Integer width;

    private Integer height;

    /** Versiones optimizadas (WebP/AVIF por ancho) generadas al subir (MED-02). */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<ImageVariant> variants = List.of();

    @Enumerated(EnumType.STRING)
    private FileScanStatus scanStatus = FileScanStatus.PENDING;

    public StoredFile(String storageKey, String originalName, String contentType, long sizeBytes, String sha256) {
        this.storageKey = storageKey;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
    }

    public void recordDimensions(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void replaceVariants(List<ImageVariant> newVariants) {
        variants = List.copyOf(newVariants);
    }

    public void markClean() {
        scanStatus = FileScanStatus.CLEAN;
    }

    public void markInfected() {
        scanStatus = FileScanStatus.INFECTED;
    }

    /** Solo se entrega a visitantes un archivo ya escaneado (SEG-03). */
    public boolean isServable() {
        return scanStatus == FileScanStatus.CLEAN;
    }

    public record ImageVariant(String format, int width, String storageKey, long sizeBytes) {
    }
}
