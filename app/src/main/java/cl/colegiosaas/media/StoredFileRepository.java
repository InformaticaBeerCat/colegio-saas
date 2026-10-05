package cl.colegiosaas.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    /** Detecta si un archivo idéntico ya se subió (carga masiva, MED-03). */
    Optional<StoredFile> findFirstBySha256(String sha256);
}
