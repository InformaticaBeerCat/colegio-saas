package cl.colegiosaas.platform;

import cl.colegiosaas.shared.persistence.SingletonEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Perfil del colegio dueño de esta instalación (una instalación = un colegio, como Nextcloud).
 * Lo crea el asistente de primer arranque (CFG-01) y luego solo se edita.
 */
@Entity
@Table(name = "school")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class School extends SingletonEntity {

    @NotBlank
    private String name;

    /** Rol Base de Datos Mineduc con dígito verificador, p. ej. "8485-1". */
    @Pattern(regexp = "\\d{1,5}-[\\dK]")
    private String rbd;

    /** Se completa en el asistente; nula mientras la instalación no está configurada. */
    @Enumerated(EnumType.STRING)
    private SchoolDependency dependency;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private Plan plan;

    /** Chile continental, Magallanes e Isla de Pascua tienen zonas distintas. */
    private String timeZone = "America/Santiago";

    private Address address;

    private String phone;

    @Email
    private String contactEmail;

    @Setter(AccessLevel.NONE)
    private Instant setupCompletedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "school_feature", joinColumns = @JoinColumn(name = "school_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "feature")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Set<Feature> features = new HashSet<>();

    public School(String name, Plan plan) {
        this.name = name;
        changePlan(plan);
    }

    /** Cambia el plan y activa exactamente sus módulos; los add-ons contratados se conservan. */
    public void changePlan(Plan newPlan) {
        if (plan != null) {
            features.removeAll(plan.includedFeatures());
        }
        plan = newPlan;
        features.addAll(newPlan.includedFeatures());
    }

    public void enableFeature(Feature feature) {
        features.add(feature);
    }

    public void disableFeature(Feature feature) {
        features.remove(feature);
    }

    public boolean hasFeature(Feature feature) {
        return features.contains(feature);
    }

    public Set<Feature> getFeatures() {
        return Collections.unmodifiableSet(features);
    }

    /** Cierra el asistente de primer arranque; antes de esto el sitio público no se muestra. */
    public void completeSetup() {
        if (dependency == null) {
            throw new IllegalStateException("Falta la dependencia administrativa del colegio");
        }
        setupCompletedAt = Instant.now();
    }

    public boolean isSetupCompleted() {
        return setupCompletedAt != null;
    }
}
