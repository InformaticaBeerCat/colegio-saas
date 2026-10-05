package cl.colegiosaas.shared.storage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

/**
 * Almacenamiento en un bucket S3 o compatible (MinIO en desarrollo y en servidores propios, OPS-07).
 * Los archivos no son públicos en el bucket: siempre se sirven a través de la aplicación, que decide
 * qué se puede ver.
 */
public class S3FileStorage implements FileStorage {

    private final S3Client s3;
    private final String bucket;

    public S3FileStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] content) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentLength((long) content.length).build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public InputStream open(String key) {
        return s3.getObject(builder -> builder.bucket(bucket).key(key));
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}
