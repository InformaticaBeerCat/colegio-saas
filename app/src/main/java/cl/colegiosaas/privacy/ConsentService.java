package cl.colegiosaas.privacy;

import cl.colegiosaas.audit.AuditAction;
import cl.colegiosaas.audit.AuditTrail;
import cl.colegiosaas.shared.crypto.BlindIndex;
import cl.colegiosaas.shared.web.NotFound;
import cl.colegiosaas.shared.web.RuleViolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de consentimientos de los formularios públicos (PRV-02, PRV-04). Cada finalidad es una casilla
 * aparte; se guarda la respuesta (también el "no") junto con la versión exacta del aviso que se mostró.
 */
@Service
public class ConsentService {

    private final ConsentRecordRepository records;
    private final LegalTextService legalTexts;
    private final BlindIndex index;
    private final AuditTrail audit;

    ConsentService(ConsentRecordRepository records, LegalTextService legalTexts, BlindIndex index, AuditTrail audit) {
        this.records = records;
        this.legalTexts = legalTexts;
        this.index = index;
        this.audit = audit;
    }

    /**
     * Guarda la respuesta a una casilla sobre el aviso vigente de su finalidad.
     *
     * @throws RuleViolation si el colegio todavía no publica ese aviso: ningún formulario recibe datos sin él
     */
    @Transactional
    public ConsentRecord record(DataSubject subject, ConsentPurpose purpose, boolean granted, RequestOrigin origin) {
        LegalText notice = legalTexts.current(purpose.notice())
                .orElseThrow(() -> new RuleViolation("Este formulario no está disponible: falta publicar su aviso de privacidad"));
        return records.save(new ConsentRecord(subject, purpose, notice, granted, origin, index));
    }

    /** Retiro del consentimiento (desde el panel, a pedido del titular). Retirar es tan fácil como dar. */
    @Transactional
    public void withdraw(long id) {
        ConsentRecord consent = records.findById(id).orElseThrow(() -> new NotFound("El consentimiento no existe"));
        consent.withdraw();
        audit.record(AuditAction.UPDATE, "ConsentRecord", id, "Consentimiento retirado: " + consent.getPurpose());
    }
}
