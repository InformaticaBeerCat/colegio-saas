package cl.colegiosaas.identity;

import cl.colegiosaas.audit.AuditActor;
import cl.colegiosaas.shared.crypto.EncryptedStringConverter;
import cl.colegiosaas.shared.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
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
public class UserAccount extends BaseEntity implements AuditActor {

    @Email
    @NotBlank
    @Setter(AccessLevel.NONE)
    private String email;

    @NotBlank
    private String name;

    /** Hash con prefijo de algoritmo ({@code {bcrypt}…}). Nulo mientras no acepta la invitación. */
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    private UserStatus status = UserStatus.INVITED;

    @Setter(AccessLevel.NONE)
    private Instant deactivatedAt;

    @Setter(AccessLevel.NONE)
    private Instant lastLoginAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_account_role", joinColumns = @JoinColumn(name = "user_account_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Set<Role> roles = new HashSet<>();

    // --- Bloqueo por intentos fallidos (SEG-06) ---

    @Setter(AccessLevel.NONE)
    private int failedLoginAttempts;

    @Setter(AccessLevel.NONE)
    private Instant lockedUntil;

    // --- Segundo factor TOTP (USR-02) ---

    /** Secreto TOTP en Base32, cifrado en la base. */
    @Convert(converter = EncryptedStringConverter.class)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private String mfaSecret;

    @Setter(AccessLevel.NONE)
    private Instant mfaEnabledAt;

    /** Último intervalo TOTP aceptado: impide reutilizar el mismo código dos veces. */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Long mfaLastUsedStep;

    /** Hashes de los códigos de recuperación; cada uno sirve una sola vez. */
    @ElementCollection
    @CollectionTable(name = "user_account_recovery_code", joinColumns = @JoinColumn(name = "user_account_id"))
    @Column(name = "code_hash")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Set<String> recoveryCodeHashes = new HashSet<>();

    public UserAccount(String email, String name, Role... roles) {
        this.email = email.toLowerCase(Locale.ROOT);
        this.name = name;
        Collections.addAll(this.roles, roles);
    }

    // --- Roles y permisos ---

    public void grantRole(Role role) {
        roles.add(role);
    }

    public void revokeRole(Role role) {
        roles.remove(role);
    }

    public void replaceRoles(Collection<Role> newRoles) {
        roles.retainAll(newRoles);
        roles.addAll(newRoles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public Set<Role> getRoles() {
        return roles.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(roles));
    }

    public Set<Permission> permissions() {
        EnumSet<Permission> all = EnumSet.noneOf(Permission.class);
        roles.forEach(role -> all.addAll(role.permissions()));
        return all;
    }

    public boolean can(Permission permission) {
        return roles.stream().anyMatch(role -> role.permissions().contains(permission));
    }

    // --- Ciclo de vida ---

    public void activate() {
        status = UserStatus.ACTIVE;
    }

    /** Un funcionario que vuelve: queda invitado de nuevo, con los roles que se le den ahora. */
    public void reinvite(Collection<Role> newRoles) {
        status = UserStatus.INVITED;
        deactivatedAt = null;
        replaceRoles(newRoles);
    }

    /** USR-04: baja inmediata. Quita roles, contraseña y MFA para que nada viejo conserve acceso. */
    public void deactivate() {
        status = UserStatus.DEACTIVATED;
        deactivatedAt = Instant.now();
        roles.clear();
        passwordHash = null;
        resetMfa();
    }

    public boolean canLogIn() {
        return status == UserStatus.ACTIVE;
    }

    // --- Ingreso ---

    public void recordSuccessfulLogin(Instant at) {
        lastLoginAt = at;
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    /** Suma un intento fallido; al llegar al máximo bloquea la cuenta por {@code lockDuration}. */
    public boolean recordFailedLogin(Instant at, int maxAttempts, Duration lockDuration) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = at.plus(lockDuration);
            failedLoginAttempts = 0;
            return true;
        }
        return false;
    }

    public boolean isLockedAt(Instant at) {
        return status == UserStatus.LOCKED || (lockedUntil != null && lockedUntil.isAfter(at));
    }

    public void changePassword(String newHash) {
        passwordHash = Objects.requireNonNull(newHash, "passwordHash");
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    // --- MFA ---

    /** Tiene algún rol sensible: se le recomienda (o exige, si la instalación lo pide) el MFA. */
    public boolean mfaRecommended() {
        return roles.stream().anyMatch(Role::mfaRecommended);
    }

    public boolean isMfaEnabled() {
        return mfaEnabledAt != null;
    }

    public void enableMfa(String secret, long firstStep, Set<String> recoveryHashes, Instant at) {
        mfaSecret = Objects.requireNonNull(secret, "secret");
        mfaLastUsedStep = firstStep;
        mfaEnabledAt = at;
        recoveryCodeHashes.clear();
        recoveryCodeHashes.addAll(recoveryHashes);
    }

    /**
     * Apaga el MFA: lo hace la persona desde "Mi cuenta", o un administrador si perdió su teléfono.
     * Borra secreto y códigos de recuperación: volver a activarlo genera unos nuevos.
     */
    public void resetMfa() {
        mfaSecret = null;
        mfaEnabledAt = null;
        mfaLastUsedStep = null;
        recoveryCodeHashes.clear();
    }

    /** Solo lo usa el verificador TOTP; nunca se muestra. */
    public String mfaSecret() {
        return mfaSecret;
    }

    /** Acepta un intervalo TOTP solo si es posterior al último usado (anti-repetición). */
    public boolean consumeTotpStep(long step) {
        if (mfaLastUsedStep != null && step <= mfaLastUsedStep) {
            return false;
        }
        mfaLastUsedStep = step;
        return true;
    }

    public boolean consumeRecoveryCode(String codeHash) {
        return recoveryCodeHashes.remove(codeHash);
    }

    public int remainingRecoveryCodes() {
        return recoveryCodeHashes.size();
    }
}
