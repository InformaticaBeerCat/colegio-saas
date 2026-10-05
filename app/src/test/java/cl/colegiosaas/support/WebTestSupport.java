package cl.colegiosaas.support;

import cl.colegiosaas.identity.Role;
import cl.colegiosaas.identity.UserAccount;
import cl.colegiosaas.identity.UserAccountRepository;
import cl.colegiosaas.platform.Plan;
import cl.colegiosaas.platform.SchoolDependency;
import cl.colegiosaas.security.SchoolUser;
import cl.colegiosaas.security.Totp;
import cl.colegiosaas.setup.Installation;
import cl.colegiosaas.setup.InstallationService;
import cl.colegiosaas.shared.mail.OutgoingMail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base de los tests web: la app completa con MockMvc, cada test en una transacción que se revierte
 * (MockMvc corre en el mismo hilo, así que las peticiones participan de ella).
 * El límite por IP se sube porque todos los tests "vienen" de 127.0.0.1.
 */
@SpringBootTest(properties = "app.security.max-failed-logins-per-ip=1000")
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
public abstract class WebTestSupport {

    protected static final String PASSWORD = "la cordillera nevada en julio";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected InstallationService installation;

    @Autowired
    protected UserAccountRepository users;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected ApplicationEvents events;

    protected UserAccount install() {
        return installation.install(new Installation("Colegio San José", "8485-1", SchoolDependency.PRIVATE_SUBSIDIZED,
                Plan.COMMUNITY, "contacto@colegio.cl", "Super Admin", "super@colegio.cl", PASSWORD));
    }

    protected UserAccount activeUser(String email, Role... roles) {
        UserAccount user = new UserAccount(email, email.substring(0, email.indexOf('@')), roles);
        user.changePassword(passwordEncoder.encode(PASSWORD));
        user.activate();
        return users.saveAndFlush(user);
    }

    /** Sesión ya autenticada, sin pasar por el formulario ni el MFA: para probar permisos de controladores. */
    protected static RequestPostProcessor as(UserAccount user) {
        SchoolUser principal = SchoolUser.of(user, Instant.now());
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    /** Ingreso real por el formulario; devuelve la sesión y la redirección para seguir el flujo. */
    protected MvcResult submitLogin(String email, String password) throws Exception {
        return mvc.perform(post("/admin/login").param("email", email).param("password", password).with(csrf())).andReturn();
    }

    protected static MockHttpSession sessionOf(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession();
    }

    /** Código válido para el siguiente intervalo: el actual ya lo consumió el enrolamiento. */
    protected static String nextCode(String secret) {
        return Totp.codeAt(secret, Totp.stepAt(Instant.now()) + 1);
    }

    protected List<OutgoingMail> mails() {
        return events.stream(OutgoingMail.class).toList();
    }

    /** Ruta del enlace que viene en un correo, p. ej. "/admin/invitation/abc…". */
    protected static String linkIn(OutgoingMail mail) {
        Matcher matcher = Pattern.compile("https?://[^/\\s]+(/admin/\\S+)").matcher(mail.body());
        if (!matcher.find()) {
            throw new AssertionError("El correo no trae enlace: " + mail.body());
        }
        return matcher.group(1);
    }
}
