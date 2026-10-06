package cl.colegiosaas.privacy;

import cl.colegiosaas.identity.Permission;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Quienes administran la privacidad del colegio (permiso PRIVACY): reciben los avisos de solicitudes y brechas. */
@Component
class PrivacyStaff {

    private final UserAccountRepository users;

    PrivacyStaff(UserAccountRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    List<String> emails() {
        return users.findAllByOrderByNameAsc().stream()
                .filter(u -> u.canLogIn() && u.can(Permission.PRIVACY))
                .map(UserAccount::getEmail)
                .toList();
    }
}
