package cl.colegiosaas.support;

import cl.colegiosaas.shared.crypto.CryptoConfig;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test de persistencia: solo JPA + Flyway sobre H2 en modo MySQL, cada test en una transacción
 * que se revierte al final. Incluye el cifrado (Hibernate lo necesita para arrancar) y
 * {@link TestFixtures} para crear datos de prueba.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CryptoConfig.class, TestFixtures.class})
public @interface RepositoryTest {
}
