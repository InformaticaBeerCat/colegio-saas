package cl.colegiosaas.platform;

import cl.colegiosaas.shared.persistence.BaseEntity;
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

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * El tenant: un establecimiento con su propio sitio. Todo lo que extiende
 * {@code TenantEntity} cuelga de un colegio. Esta tabla no se filtra por colegio.
 */
@Entity
@Table(name = "school")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class School extends BaseEntity {

    @NotBlank
    private String name;

    /** Rol Base de Datos Mineduc con dígito verificador, p. ej. "8485-1". */
    @Pattern(regexp = "\\d{1,5}-[\\dK]")
    private String rbd;

    @Enumerated(EnumType.STRING)
    private SchoolDependency dependency;

    /** Sitio provisional: {subdomain}.dominio-del-proveedor (CFG-06). */
    @Pattern(regexp = "[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?")
    @Setter(AccessLevel.NONE)
    private String subdomain;

    /** Dominio propio del colegio, p. ej. "www.colegiosanjose.cl". */
    @Setter(AccessLevel.NONE)
    private String customDomain;

    @Enumerated(EnumType.STRING)
    private SchoolStatus status = SchoolStatus.ONBOARDING;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private Plan plan;

    private String timeZone = "America/Santiago";

    private Address address;

    private String phone;

    @Email
    private String contactEmail;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "school_feature", joinColumns = @JoinColumn(name = "school_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "feature")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Set<Feature> features = new HashSet<>();

    public School(String name, SchoolDependency dependency, String subdomain, Plan plan) {
        this.name = name;
        this.dependency = dependency;
        this.subdomain = subdomain.toLowerCase(Locale.ROOT);
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

    public void assignCustomDomain(String domain) {
        customDomain = domain == null ? null : domain.toLowerCase(Locale.ROOT);
    }
}
