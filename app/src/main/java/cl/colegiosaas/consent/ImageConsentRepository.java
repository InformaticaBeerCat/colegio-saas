package cl.colegiosaas.consent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImageConsentRepository extends JpaRepository<ImageConsent, Long> {

    Optional<ImageConsent> findFirstByStudentAndChannelAndRevokedAtIsNull(Student student, ConsentChannel channel);

    /** Historia completa de un estudiante, lo más reciente primero. */
    List<ImageConsent> findByStudentOrderByGrantedAtDesc(Student student);
}
