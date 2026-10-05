package cl.colegiosaas.shared.storage;

import java.io.InputStream;

/**
 * Dónde viven los bytes de los archivos (OPS-07). La aplicación solo conoce la llave
 * ({@code StoredFile.storageKey}); la implementación decide si es disco local o S3/MinIO (fase 5).
 */
public interface FileStorage {

    void put(String key, byte[] content);

    /** El llamador cierra el stream. */
    InputStream open(String key);

    boolean exists(String key);
}
