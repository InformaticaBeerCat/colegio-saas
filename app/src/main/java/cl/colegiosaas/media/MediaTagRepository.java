package cl.colegiosaas.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MediaTagRepository extends JpaRepository<MediaTag, Long> {

    Optional<MediaTag> findByName(String name);
}
