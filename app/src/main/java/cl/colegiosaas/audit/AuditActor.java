package cl.colegiosaas.audit;

/**
 * Quien hace algo que queda auditado. Lo implementan la cuenta ({@code UserAccount}) y el usuario
 * con sesión ({@code SchoolUser}); así auditoría no depende de esos paquetes.
 */
public interface AuditActor {

    Long getId();

    String getName();
}
