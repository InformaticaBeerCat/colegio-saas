package cl.colegiosaas.identity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Persona con acceso al panel o a la zona comunidad.
 * Se llama UserAccount para no chocar con {@code User} de Spring Security ni con la palabra reservada "user".
 */
@Entity
@Table(name = "user_account")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount extends BaseEntity {

    @Email
    @NotBlank
    @Setter(AccessLevel.NONE)
    private String email;

    @NotBlank
    private String name;

    /** Nulo si entra solo con SSO o enlace mágico (ZON-05). */
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private UserStatus status = UserStatus.INVITED;

    @Setter(AccessLevel.NONE)
    private Instant deactivatedAt;

    private Instant lastLoginAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_account_role", joinColumns = @JoinColumn(name = "user_account_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Set<Role> roles = new HashSet<>();

    public UserAccount(String email, String name, Role... roles) {
        this.email = email.toLowerCase(Locale.ROOT);
        this.name = name;
        Collections.addAll(this.roles, roles);
    }

    public void grantRole(Role role) {
        roles.add(role);
    }

    public void revokeRole(Role role) {
        roles.remove(role);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public Set<Role> getRoles() {
        return roles.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(roles));
    }

    public boolean requiresMfa() {
        return roles.stream().anyMatch(Role::requiresMfa);
    }

    public void activate() {
        status = UserStatus.ACTIVE;
    }

    /** USR-04: baja inmediata. Quita roles y contraseña para que nada viejo conserve acceso. */
    public void deactivate() {
        status = UserStatus.DEACTIVATED;
        deactivatedAt = Instant.now();
        roles.clear();
        passwordHash = null;
    }

    public boolean canLogIn() {
        return status == UserStatus.ACTIVE;
    }
}
