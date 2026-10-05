package cl.colegiosaas.consent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImageConsentRepository extends JpaRepository<ImageConsent, Long> {

    Optional<ImageConsent> findFirstByStudentAndChannelAndRevokedAtIsNull(Student student, ConsentChannel channel);

    /** Historia completa de un estudiante, lo más reciente primero. */
    List<ImageConsent> findByStudentOrderByGrantedAtDesc(Student student);

    /** Estudiantes (de los dados) con autorización vigente para el canal. */
    @Query("select c.student.id from ImageConsent c where c.student in :students and c.channel = :channel and c.revokedAt is null")
    List<Long> studentIdsWithActiveConsent(Collection<Student> students, ConsentChannel channel);
}
