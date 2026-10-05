package cl.colegiosaas.admissions;

import cl.colegiosaas.platform.SchoolDependency;
import cl.colegiosaas.shared.persistence.SingletonEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;

/** Configuración del proceso de admisión (una fila por instalación). */
@Entity
@Table(name = "admission_settings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdmissionSettings extends SingletonEntity {

    public static final String SAE_URL = "https://www.sistemadeadmisionescolar.cl/";

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private AdmissionMode mode;

    /** Enlace a la plataforma del SAE (ADM-02). */
    private String saeUrl = SAE_URL;

    /** Año de ingreso al que apunta el proceso vigente, p. ej. 2027. */
    private int processYear;

    /** Texto de la página de admisión. Columna LONGTEXT. */
    private String introText;

    /** Mostrar las vacantes por nivel (ADM-07). */
    private boolean showVacancies = true;

    @JdbcTypeCode(SqlTypes.JSON)
    private AdmissionRules rules = AdmissionRules.none();

    public AdmissionSettings(AdmissionMode mode, int processYear) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.processYear = processYear;
    }

    /** Modo por defecto según la dependencia: los particulares pagados no participan del SAE. */
    public static AdmissionSettings defaultFor(SchoolDependency dependency, int processYear) {
        return new AdmissionSettings(dependency.admitsViaSae() ? AdmissionMode.SAE : AdmissionMode.OWN, processYear);
    }

    /** El modo propio exige el módulo OWN_ADMISSIONS; eso lo valida el servicio (fase 7). */
    public void switchMode(AdmissionMode newMode) {
        mode = Objects.requireNonNull(newMode, "mode");
    }
}
