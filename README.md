# Colegio SaaS

Sitio web público configurable para colegios chilenos: identidad de marca por colegio, módulos por plan,
y cumplimiento de publicación (Reglamento Interno, SAE) y de datos personales (Ley 21.719).

Como Nextcloud, **cada colegio corre su propia instalación**: el mismo código se despliega una vez por colegio
y se personaliza desde el panel, sin tocar el código.

- [Plan de desarrollo](docs/PLAN.md): decisiones de arquitectura, convenciones y fases.
- [Modelo de dominio](docs/domain-model.md): entidades y relaciones por iteración.

## Estructura

```
docs/   documentación del proyecto
app/    aplicación Spring Boot 4 (Java 21, Maven)
```

## Desarrollo

Requisitos: JDK 21+. Docker es opcional (solo para usar MySQL en contenedor).

```bash
cd app
./mvnw test                 # tests sobre H2 en modo MySQL
./mvnw spring-boot:run      # arranca con H2 en memoria
```

Con MySQL:

```bash
cd app
docker compose up -d                                         # MySQL en el puerto 3307
SPRING_PROFILES_ACTIVE=mysql ./mvnw spring-boot:run
```

Para un MySQL propio, definir `DB_URL`, `DB_USER` y `DB_PASSWORD`.
