package cl.colegiosaas.shared.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Almacenamiento en una carpeta del servidor. Sirve para desarrollo y para colegios con servidor
 * propio; la implementación S3/MinIO llega en la fase 5 con la misma interfaz.
 */
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, byte[] content) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            // Escribe a un temporal y lo mueve: nunca queda un archivo a medio escribir con la llave final.
            Path temp = Files.createTempFile(target.getParent(), ".subida-", ".tmp");
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar " + key, e);
        }
    }

    @Override
    public InputStream open(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    /** La llave la arma la aplicación, pero igual se impide salir de la carpeta raíz. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Llave de archivo inválida: " + key);
        }
        return path;
    }
}
