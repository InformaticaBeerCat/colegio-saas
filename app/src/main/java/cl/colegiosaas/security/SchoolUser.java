package cl.colegiosaas.security;

import cl.colegiosaas.audit.AuditActor;
import cl.colegiosaas.identity.Permission;
import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Usuario con sesión iniciada, tal como lo ve Spring Security. Es una foto tomada al ingresar:
 * vive en la sesión HTTP, por eso es serializable e independiente de Hibernate.
 * Sus autoridades son "ROLE_X" por cada rol y el nombre de cada {@link Permission}.
 */
public final class SchoolUser implements UserDetails, CredentialsContainer, AuditActor {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long id;
    private final String email;
    private final String name;
    private String passwordHash;
    private final Set<Role> roles;
    private final Set<GrantedAuthority> authorities;
    private final boolean active;
    private final boolean locked;
    private final boolean mfaRequired;
    private final boolean mfaEnabled;

    private SchoolUser(UserAccount account, Instant now) {
        this.id = account.getId();
        this.email = account.getEmail();
        this.name = account.getName();
        this.passwordHash = account.getPasswordHash();
        this.roles = account.getRoles();
        this.active = account.canLogIn();
        this.locked = account.isLockedAt(now);
        this.mfaRequired = account.requiresMfa();
        this.mfaEnabled = account.isMfaEnabled();
        Set<GrantedAuthority> granted = new LinkedHashSet<>();
        roles.forEach(role -> granted.add(new SimpleGrantedAuthority("ROLE_" + role.name())));
        account.permissions().forEach(permission -> granted.add(new SimpleGrantedAuthority(permission.name())));
        this.authorities = Set.copyOf(granted);
    }

    public static SchoolUser of(UserAccount account, Instant now) {
        return new SchoolUser(account, now);
    }

    public long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String name() {
        return name;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    public Set<Role> roles() {
        return roles;
    }

    public boolean can(Permission permission) {
        return authorities.contains(new SimpleGrantedAuthority(permission.name()));
    }

    /** Pasa por el segundo factor si su rol lo exige o si lo activó voluntariamente. */
    public boolean needsSecondFactor() {
        return mfaRequired || mfaEnabled;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    /** Invitados, suspendidos y dados de baja no pueden ingresar. */
    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    /** Spring Security borra el hash de la memoria apenas termina de verificar la contraseña. */
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    // Igualdad por id: el registro de sesiones la usa para encontrar todas las sesiones de una persona.
    @Override
    public boolean equals(Object o) {
        return o instanceof SchoolUser other && other.id == id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "SchoolUser[" + id + "]";
    }
}
