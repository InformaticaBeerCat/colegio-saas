package cl.colegiosaas.privacy;

import org.springframework.web.util.HtmlUtils;

/**
 * Plantillas iniciales de los textos legales (DOC-06). Son un punto de partida razonable para un colegio
 * chileno bajo la Ley 21.719, no asesoría legal: el panel pide revisarlas con la asesoría del colegio antes
 * de publicarlas. Los datos del colegio se escriben en el texto al crear el borrador, así cada versión
 * publicada queda fija.
 */
public final class LegalTemplates {

    private LegalTemplates() {
    }

    /**
     * Datos que se completan en las plantillas.
     *
     * @param retentionDays plazo de conservación de los datos de ese formulario; nulo si no aplica
     */
    public record SchoolInfo(String name, String rbd, String address, String contactEmail, Integer retentionDays) {
    }

    public static String title(LegalTextKind kind) {
        return switch (kind) {
            case PRIVACY_POLICY -> "Política de privacidad";
            case COOKIE_POLICY -> "Política de cookies";
            case TERMS -> "Términos de uso del sitio";
            case IMAGE_CONSENT_FORM -> "Autorización de uso de imagen";
            case NOTICE_CONTACT -> "Aviso de privacidad: formulario de contacto";
            case NOTICE_SCHEDULING -> "Aviso de privacidad: agenda de citas";
            case NOTICE_EVENTS -> "Aviso de privacidad: inscripción a eventos";
            case NOTICE_ADMISSIONS -> "Aviso de privacidad: admisión";
            case NOTICE_NEWSLETTER -> "Aviso de privacidad: boletín";
            case NOTICE_DATA_REQUESTS -> "Aviso de privacidad: solicitudes de derechos";
        };
    }

    public static String content(LegalTextKind kind, SchoolInfo school) {
        String name = esc(school.name());
        String responsible = "<p><strong>Responsable:</strong> " + name
                + (school.rbd() == null ? "" : " (RBD " + esc(school.rbd()) + ")")
                + (school.address() == null ? "" : ", " + esc(school.address()))
                + (school.contactEmail() == null ? "" : ". Contacto: " + esc(school.contactEmail())) + ".</p>";
        String rights = "<p><strong>Tus derechos:</strong> puedes pedir acceso, rectificación, supresión, oposición, "
                + "portabilidad o bloqueo de tus datos en <a href=\"/privacidad/derechos\">/privacidad/derechos</a>. "
                + "Si representas a un estudiante menor de edad, puedes ejercerlos en su nombre.</p>";
        String retention = school.retentionDays() == null ? ""
                : "<p><strong>Plazo de conservación:</strong> " + retentionText(school.retentionDays())
                + "; después se eliminan o anonimizan automáticamente.</p>";
        return switch (kind) {
            case PRIVACY_POLICY -> responsible + """
                    <h2>Qué datos tratamos y para qué</h2>
                    <p>Tratamos solo los datos que nos entregas en los formularios del sitio (contacto, agenda, inscripciones,
                    admisión) y los necesarios para que el sitio funcione. Cada formulario indica su finalidad, su plazo de
                    conservación y pide tu consentimiento en una casilla separada y sin marcar.</p>
                    <h2>Datos de estudiantes</h2>
                    <p>No publicamos el nombre completo de estudiantes junto a sus fotos. Las imágenes de estudiantes solo se
                    publican con autorización del apoderado, que puede revocarla en cualquier momento; al hacerlo retiramos
                    las fotos del sitio.</p>
                    <h2>Seguridad</h2>
                    <p>Los datos personales se guardan cifrados, el acceso del personal queda registrado y los archivos se
                    revisan antes de guardarse. Si ocurre una brecha de seguridad que te afecte, te lo informaremos.</p>
                    <h2>Con quién compartimos datos</h2>
                    <p>No vendemos ni cedemos tus datos. Solo los comparten los proveedores que alojan el sitio, bajo contrato y
                    con las mismas obligaciones de confidencialidad.</p>
                    """ + rights;
            case COOKIE_POLICY -> """
                    <p>Usamos <strong>cookies necesarias</strong> para que el sitio funcione: recordar tu preferencia de cookies y,
                    si eres parte del equipo del colegio, mantener tu sesión en el panel. No requieren consentimiento.</p>
                    <p>Con tu permiso usaríamos además <strong>cookies de analítica</strong> para saber qué páginas se visitan,
                    sin identificarte ni compartir datos con terceros con fines publicitarios. Puedes aceptar o rechazarlas, y
                    cambiar de opinión cuando quieras en <a href="/privacidad/cookies">/privacidad/cookies</a>.</p>
                    """ + responsible;
            case TERMS -> """
                    <p>Este sitio es el canal oficial de información de %s. Los contenidos (textos, fotos, documentos) son del
                    colegio o de sus autores y se publican con fines informativos. No se pueden reutilizar fotos de estudiantes.</p>
                    <p>Los documentos oficiales vigentes son los publicados en la sección Documentos institucionales.</p>
                    """.formatted(name) + responsible;
            case IMAGE_CONSENT_FORM -> """
                    <p>Autorizo a %s a publicar fotografías y videos en que aparezca mi pupilo(a), tomados en actividades
                    escolares, en los canales que marco a continuación: sitio web del colegio, redes sociales del colegio y
                    material impreso. Cada canal se autoriza por separado.</p>
                    <p>El colegio no publicará junto a las imágenes el nombre completo del estudiante. Puedo revocar esta
                    autorización en cualquier momento; al hacerlo, el colegio retirará del sitio web las imágenes en que aparece.</p>
                    """.formatted(name) + responsible;
            case NOTICE_CONTACT -> notice("responder tu consulta y derivarla al área que corresponde", responsible, retention, rights);
            case NOTICE_SCHEDULING -> notice("agendar, recordar y gestionar tu cita con el colegio", responsible, retention, rights);
            case NOTICE_EVENTS -> notice("gestionar tu inscripción al evento, los cupos y los avisos sobre él", responsible, retention, rights);
            case NOTICE_ADMISSIONS -> notice("informarte sobre el proceso de admisión que solicitaste. Los correos de "
                    + "seguimiento solo se envían si los autorizas en una casilla aparte", responsible, retention, rights);
            case NOTICE_NEWSLETTER -> notice("enviarte el boletín del colegio. Puedes darte de baja en cada correo", responsible, retention, rights);
            case NOTICE_DATA_REQUESTS -> notice("tramitar tu solicitud de derechos, verificar tu identidad y responderte "
                    + "dentro del plazo legal", LEGAL_OBLIGATION, responsible, retention, rights);
        };
    }

    private static final String CONSENT = "tu consentimiento, que puedes retirar en cualquier momento";
    /** Responder solicitudes de derechos es una obligación del colegio, no depende del consentimiento. */
    private static final String LEGAL_OBLIGATION = "el cumplimiento de una obligación legal (Ley 21.719)";

    private static String notice(String purpose, String responsible, String retention, String rights) {
        return notice(purpose, CONSENT, responsible, retention, rights);
    }

    private static String notice(String purpose, String basis, String responsible, String retention, String rights) {
        return responsible + "<p><strong>Finalidad:</strong> " + purpose + ".</p>"
                + "<p><strong>Base legal:</strong> " + basis + ".</p>"
                + "<p><strong>Destinatarios:</strong> solo el personal del colegio que atiende el tema. No se ceden a terceros.</p>"
                + retention + rights;
    }

    static String retentionText(int days) {
        if (days % 365 == 0) {
            int years = days / 365;
            return years == 1 ? "1 año" : years + " años";
        }
        return days + " días";
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
