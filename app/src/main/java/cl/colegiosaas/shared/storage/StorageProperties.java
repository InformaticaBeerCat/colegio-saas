package cl.colegiosaas.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dónde se guardan los archivos de esta instalación.
 *
 * @param type     {@code local} (carpeta del servidor) o {@code s3} (S3 o MinIO)
 * @param localDir carpeta para {@code local}
 * @param s3       conexión para {@code s3}
 */
@ConfigurationProperties("app.storage")
public record StorageProperties(String type, String localDir, S3 s3) {

    /**
     * @param endpoint  vacío = AWS; con MinIO, su URL (http://localhost:9000)
     * @param pathStyle MinIO necesita direcciones tipo {@code endpoint/bucket/llave}
     */
    public record S3(String endpoint, String region, String bucket, String accessKey, String secretKey, boolean pathStyle) {
    }
}
