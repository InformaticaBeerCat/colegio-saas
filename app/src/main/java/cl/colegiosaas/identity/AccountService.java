package cl.colegiosaas.identity;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.platform.School;
import cl.colegiosaas.platform.SchoolRepository;
import cl.colegiosaas.shared.mail.OutgoingMail;
import cl.colegiosaas.shared.security.SecureTokens;
import cl.colegiosaas.shared.web.AppProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ciclo de vida de las cuentas del panel: invitar, aceptar, recuperar contraseña, cambiar roles,
 * dar de baja (USR-04) y reiniciar el MFA. Cada acción queda en la auditoría.
 *
 * Reglas: nadie cambia sus propios roles ni se da de baja a sí mismo (evita quedar sin administradores),
 * y solo un Super Admin puede otorgar o tocar a otro Super Admin.
 */
@Service
@Transactional
public class AccountService {

    private final UserAccountRepository users;
    private final AccountTokenRepository tokens;
    private final SchoolRepository schools;
    private final PasswordEncoder passwordEncoder;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final AppProperties app;
    private final Clock clock;

    AccountService(UserAccountRepository users, AccountTokenRepository tokens, SchoolRepository schools,
                   PasswordEncoder passwordEncoder, AuditTrail audit, ApplicationEventPublisher events,
                   AppProperties app, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.schools = schools;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.events = events;
        this.app = app;
        this.clock = clock;
    }

    // --- Invitaciones ---

    public UserAccount invite(String email, String name, Set<Role> roles, long actorId) {
        UserAccount actor = load(actorId);
        requireCanAssign(actor, roles);
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        UserAccount user = users.findByEmail(normalized)
                .map(existing -> {
                    if (existing.getStatus() != UserStatus.DEACTIVATED) {
                        throw new AccountException("Ya existe una cuenta con ese correo");
                    }
                    // Funcionario que vuelve: se reutiliza su cuenta para no perder la historia de auditoría.
                    existing.reinvite(roles);
                    existing.setName(name.strip());
                    return existing;
                })
                .orElseGet(() -> users.save(new UserAccount(normalized, name.strip(), roles.toArray(Role[]::new))));
        sendInvitation(user);
        audit.record(AuditAction.INVITE, "UserAccount", user.getId(), "Roles: " + roles);
        return user;
    }

    public void resendInvitation(long userId, long actorId) {
        UserAccount user = load(userId);
        requireCanManage(load(actorId), user);
        if (user.getStatus() != UserStatus.INVITED) {
            throw new AccountException("La cuenta ya fue activada");
        }
        sendInvitation(user);
        audit.record(AuditAction.INVITE, "UserAccount", user.getId(), "Invitación reenviada");
    }

    public void acceptInvitation(String rawToken, String password) {
        AccountToken token = usableToken(rawToken, AccountTokenPurpose.INVITATION);
        UserAccount user = token.getUser();
        user.changePassword(encodeValid(password, user.getEmail()));
        user.activate();
        token.markUsed(now());
        audit.recordFor(user, AuditAction.PASSWORD_CHANGED, "UserAccount", user.getId(), "Aceptó la invitación");
    }

    // --- Contraseñas ---

    /** Siempre responde igual, exista o no la cuenta: no revela qué correos están registrados. */
    public void requestPasswordReset(String email) {
        users.findByEmail(email.strip().toLowerCase(Locale.ROOT))
                .filter(UserAccount::canLogIn)
                .ifPresent(user -> {
                    String link = issueLink(user, AccountTokenPurpose.PASSWORD_RESET, "/admin/password/reset/");
                    events.publishEvent(new OutgoingMail(user.getEmail(), "Restablecer tu contraseña", """
                            Hola %s:

                            Pediste restablecer tu contraseña del panel de %s. Usa este enlace dentro de la próxima hora:

                            %s

                            Si no fuiste tú, ignora este correo: tu contraseña no cambia.
                            """.formatted(user.getName(), schoolName(), link)));
                });
    }

    public void resetPassword(String rawToken, String password) {
        AccountToken token = usableToken(rawToken, AccountTokenPurpose.PASSWORD_RESET);
        UserAccount user = token.getUser();
        user.changePassword(encodeValid(password, user.getEmail()));
        token.markUsed(now());
        events.publishEvent(new UserAccessRevoked(user.getId()));
        audit.recordFor(user, AuditAction.PASSWORD_CHANGED, "UserAccount", user.getId(), "Restableció su contraseña");
    }

    /** Cambio desde "Mi cuenta": pide la contraseña actual y cierra las demás sesiones abiertas. */
    public void changePassword(long userId, String currentPassword, String newPassword, String currentSessionId) {
        UserAccount user = load(userId);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AccountException("La contraseña actual no es correcta");
        }
        user.changePassword(encodeValid(newPassword, user.getEmail()));
        events.publishEvent(new UserAccessRevoked(user.getId(), currentSessionId));
        audit.record(AuditAction.PASSWORD_CHANGED, "UserAccount", user.getId(), null);
    }

    // --- Administración ---

    public void changeRoles(long userId, Set<Role> roles, long actorId) {
        UserAccount actor = load(actorId);
        UserAccount user = load(userId);
        requireNotSelf(actor, user, "cambiar tus propios roles");
        requireCanManage(actor, user);
        requireCanAssign(actor, roles);
        Set<Role> before = user.getRoles();
        user.replaceRoles(roles);
        events.publishEvent(new UserAccessRevoked(user.getId()));
        audit.record(AuditAction.PERMISSIONS_CHANGED, "UserAccount", user.getId(), before + " → " + roles);
    }

    public void deactivate(long userId, long actorId) {
        UserAccount actor = load(actorId);
        UserAccount user = load(userId);
        requireNotSelf(actor, user, "darte de baja a ti mismo");
        requireCanManage(actor, user);
        user.deactivate();
        tokens.findByUserAndUsedAtIsNull(user).forEach(token -> token.markUsed(now()));
        events.publishEvent(new UserAccessRevoked(user.getId()));
        audit.record(AuditAction.DEACTIVATE, "UserAccount", user.getId(), null);
    }

    /** Para quien perdió su teléfono: en su próximo ingreso vuelve a configurar el MFA. */
    public void resetMfa(long userId, long actorId) {
        UserAccount actor = load(actorId);
        UserAccount user = load(userId);
        requireNotSelf(actor, user, "reiniciar tu propio segundo factor");
        requireCanManage(actor, user);
        user.resetMfa();
        events.publishEvent(new UserAccessRevoked(user.getId()));
        audit.record(AuditAction.MFA_RESET, "UserAccount", user.getId(), null);
    }

    @Transactional(readOnly = true)
    public List<UserAccount> listAll() {
        return users.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public UserAccount get(long userId) {
        return load(userId);
    }

    /** Roles que este actor puede asignar desde el panel. */
    @Transactional(readOnly = true)
    public Set<Role> assignableRoles(long actorId) {
        UserAccount actor = load(actorId);
        EnumSet<Role> roles = EnumSet.noneOf(Role.class);
        for (Role role : Role.values()) {
            if (role.isStaff() && (role != Role.SUPER_ADMIN || actor.hasRole(Role.SUPER_ADMIN))) {
                roles.add(role);
            }
        }
        return roles;
    }

    // --- Apoyo ---

    private void sendInvitation(UserAccount user) {
        String link = issueLink(user, AccountTokenPurpose.INVITATION, "/admin/invitation/");
        events.publishEvent(new OutgoingMail(user.getEmail(), "Invitación al panel de " + schoolName(), """
                Hola %s:

                Te invitaron al panel de administración del sitio de %s. Para activar tu cuenta y elegir
                tu contraseña, abre este enlace (vence en 7 días):

                %s
                """.formatted(user.getName(), schoolName(), link)));
    }

    /** Invalida los enlaces anteriores del mismo tipo y emite uno nuevo. */
    private String issueLink(UserAccount user, AccountTokenPurpose purpose, String path) {
        Instant now = now();
        tokens.findByUserAndPurposeAndUsedAtIsNull(user, purpose).forEach(old -> old.markUsed(now));
        AccountToken.Issued issued = AccountToken.issue(user, purpose, now);
        tokens.save(issued.token());
        return app.url(path + issued.rawToken());
    }

    /** Para mostrar "enlace vencido" de inmediato en vez de recién al enviar el formulario. */
    @Transactional(readOnly = true)
    public boolean isLinkValid(String rawToken, AccountTokenPurpose purpose) {
        return tokens.findByTokenHashAndPurpose(SecureTokens.hash(rawToken), purpose)
                .filter(token -> token.isUsableAt(now()))
                .isPresent();
    }

    private AccountToken usableToken(String rawToken, AccountTokenPurpose purpose) {
        return tokens.findByTokenHashAndPurpose(SecureTokens.hash(rawToken), purpose)
                .filter(token -> token.isUsableAt(now()))
                .orElseThrow(() -> new AccountException("El enlace no es válido o ya venció. Pide uno nuevo."));
    }

    private String encodeValid(String password, String email) {
        List<String> problems = PasswordPolicy.problems(password, email);
        if (!problems.isEmpty()) {
            throw new AccountException(String.join(" ", problems));
        }
        return passwordEncoder.encode(password);
    }

    private static void requireCanAssign(UserAccount actor, Set<Role> roles) {
        if (roles.isEmpty()) {
            throw new AccountException("Elige al menos un rol");
        }
        if (roles.stream().anyMatch(role -> !role.isStaff())) {
            throw new AccountException("Los roles de apoderado y estudiante se usan en la zona comunidad");
        }
        if (roles.contains(Role.SUPER_ADMIN) && !actor.hasRole(Role.SUPER_ADMIN)) {
            throw new AccessDeniedException("Solo un Super Admin puede otorgar ese rol");
        }
    }

    private static void requireCanManage(UserAccount actor, UserAccount target) {
        if (target.hasRole(Role.SUPER_ADMIN) && !actor.hasRole(Role.SUPER_ADMIN)) {
            throw new AccessDeniedException("Solo un Super Admin puede modificar a otro Super Admin");
        }
    }

    private static void requireNotSelf(UserAccount actor, UserAccount target, String what) {
        if (actor.getId().equals(target.getId())) {
            throw new AccountException("No puedes " + what + ". Pídeselo a otro administrador.");
        }
    }

    private UserAccount load(long id) {
        return users.findById(id).orElseThrow(() -> new AccountException("La cuenta no existe"));
    }

    private String schoolName() {
        return schools.findSingleton().map(School::getName).orElse("tu colegio");
    }

    private Instant now() {
        return clock.instant();
    }
}
