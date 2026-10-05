# Plan de desarrollo — Colegio SaaS

Hoja de ruta técnica del MVP (requerimientos v2, oct-2026). Cada fase termina con código que compila,
tests en verde y algo demostrable. Los IDs entre corchetes (`PUB-01`, `MED-06`…) remiten al documento de requerimientos.

## Decisiones de arquitectura

| Tema | Decisión | Por qué |
|---|---|---|
| Stack | Java 21 LTS, Spring Boot 4.1, Hibernate 7, Maven (wrapper incluido) | Versiones estables actuales; Java 21 es el JDK instalado |
| Forma | Monolito modular: un solo deployable, paquetes por funcionalidad (`platform`, `identity`, `media`…) | Un equipo chico no necesita microservicios; los límites por paquete permiten separar después |
| Multi-tenant | Una base de datos, columna `school_id` en cada tabla del colegio, `@TenantId` de Hibernate | Sirve igual para alojado (muchos colegios), on-premise (uno) y sostenedores con varios colegios (CFG-10) |
| Aislamiento | Sin colegio en contexto, las consultas no devuelven nada y los inserts fallan (falla cerrado) | Un olvido no puede filtrar datos de otro colegio |
| Base de datos | MySQL 8.4+ como objetivo; H2 en modo MySQL para tests y arranque rápido | PostgreSQL queda para cuando un cliente lo pida (migraciones en `db/migration/postgresql`) |
| Esquema | Flyway versionado; Hibernate solo valida (`ddl-auto=validate`) | Migraciones reproducibles y con rollback planificado (OPS-03) |
| IDs | `BIGINT` autoincremental, nunca expuestos en URLs públicas | Afuera se usan slugs y tokens aleatorios |
| Vistas | Thymeleaf para sitio público y panel, con htmx para interactividad sin SPA | Server-side rendering = buen SEO y Core Web Vitals (UX-02) |
| API | REST en `/api/v1` para integraciones | Requerimiento de integración con otros sistemas |
| Archivos | Interfaz de almacenamiento con implementación S3 (MinIO en desarrollo) | Mismo código local o en nube (OPS-07) |
| Datos personales | Cifrado AES-GCM por columna (`AttributeConverter`) + hash HMAC para búsquedas | SEG-04 y Ley 21.719; el hash permite buscar por email sin descifrar |
| Contenido flexible | Bloques de página y tokens de diseño como columnas JSON, mapeados a `record`s Java | El constructor de páginas (CFG-04) no necesita una tabla por tipo de bloque |

## Convenciones

- **Código en inglés** (paquetes, clases, campos, tablas, columnas, enums); **comentarios y documentación en español**.
- Excepciones de nombres: `UserAccount` (no `User`, choca con Spring Security y es palabra reservada), `Feature` (no `Module`, choca con `java.lang.Module`).
- Términos chilenos sin traducción natural se mantienen como sigla: `rbd`, `SAE`, `SLEP`.
- Entidades: Lombok solo `@Getter`, `@Setter` puntuales y `@NoArgsConstructor(access = PROTECTED)`. Nunca `@Data` (rompe equals/hashCode con Hibernate).
- Colecciones expuestas como solo lectura; se modifican con métodos de dominio (`grantRole`, `enableFeature`).
- Enums guardados como `VARCHAR` (agregar un valor no exige migración).
- Tablas en singular snake_case; constraints con prefijo (`pk_`, `uk_`, `fk_`, `ix_`).
- Toda entidad de un colegio extiende `TenantEntity`; las de plataforma, `BaseEntity`.
- El colegio se fija con `TenantContext.use(id)` **antes** de abrir la transacción.

## Estructura del repositorio

```
colegio-saas/
├── README.md
├── docs/               plan, modelo de dominio, decisiones
└── app/                aplicación Spring Boot (Maven)
    ├── compose.yaml    MySQL + Mailpit para desarrollo
    └── src/main/java/cl/colegiosaas/
        ├── shared/     persistencia base y multi-tenant
        ├── platform/   colegios, planes, módulos, operadores
        ├── identity/   usuarios y roles
        └── audit/      registro de auditoría
```

---

## Fases

### Fase 0 — Fundaciones ✅
Proyecto Maven, perfiles (H2 por defecto / `mysql`), Flyway, `compose.yaml`, Testcontainers listo para MySQL.

### Fase 1 — Modelo de dominio
Diseño completo en [domain-model.md](domain-model.md). Se implementa por iteraciones; cada una agrega
entidades, repositorios, su migración Flyway y tests de persistencia.

| Iteración | Contenido | Estado |
|---|---|---|
| 1.1 Núcleo | `BaseEntity`, `TenantEntity`, `TenantContext`, `School`, `Feature`/`Plan`, `PlatformOperator`, `UserAccount`/`Role`, `AuditLogEntry` | ✅ |
| 1.2 Sitio y estructura | `SiteSettings` (tokens JSON), `Page` (bloques JSON), `MenuItem`, `QuickLink`, `SiteAlert`, `GradeLevel`, `Course` | ⬜ |
| 1.3 Medios | `StoredFile`, `MediaFolder`, `MediaAsset`, `MediaTag`, `Album`, `AlbumItem` | ⬜ |
| 1.4 Contenido y documentos | `NewsArticle`, `NewsCategory`, `Announcement`, `Event`, `FaqCategory`, `FaqEntry`, `Workshop`, `InfoSheet`, `InstitutionalDocument`, `DocumentVersion` | ⬜ |
| 1.5 Privacidad y consentimientos | Cifrado de columnas, `LegalText`, `ConsentRecord`, `DataSubjectRequest`, `RetentionPolicy`, `SecurityIncident`, `Student`, `ImageConsent` | ⬜ |
| 1.6 Interacción | `ContactArea`, `Inquiry`, `InquiryNote`, `AppointmentType`, `AvailabilityRule`, `AvailabilityBlock`, `Holiday`, `Appointment`, `EventRegistration`, `AdmissionSettings`, `AdmissionMilestone`, `Vacancy`, `Prospect` | ⬜ |

**Listo cuando:** todas las tablas MVP existen, Hibernate valida contra MySQL real y hay un test de aislamiento por colegio para cada agregado con datos personales.

### Fase 2 — Seguridad e identidad
- Resolución del colegio por host (dominio propio o subdominio) en un filtro web, con caché.
- Spring Security: login de panel, sesiones, CSRF, cabeceras (CSP, HSTS) [SEG-01].
- Permisos por rol y módulo [USR-01]; respetar `Feature` contratadas [CFG-07].
- MFA TOTP obligatorio para `SCHOOL_ADMIN`, `CONSENT_MANAGER` y operadores [USR-02].
- Invitación de usuarios, baja rápida [USR-04], rate limiting de login [SEG-06].
- Auditoría automática de acciones del panel [USR-03].

### Fase 3 — Marca y motor de temas
- Asistente inicial [CFG-01]; 3 temas base con variantes [CFG-02].
- Tokens de diseño con verificación de contraste AA [CFG-03, ACC-01]; fuentes con licencia libre.
- Render del sitio público por bloques [PUB-01, CFG-04]; menú y pie [CFG-05]; vista previa [CFG-08].
- Base de accesibilidad: landmarks, foco visible, idioma, navegación por teclado [ACC-04]. Mobile-first [UX-01].

### Fase 4 — Contenido y documentos
- Noticias con flujo borrador → revisión → publicado y programación [NOT-01, NOT-02].
- Comunicados, calendario filtrable, `.ics` [NOT-03..05]; FAQ, talleres, útiles/uniforme/menú [PUB-06, 09, 10].
- Documentos institucionales con versiones, campos obligatorios del Reglamento Interno y alerta a 12 meses [DOC-01..05].
- Páginas de convivencia con canal de denuncia [DOC-07]; banner de alerta global [PUB-12].

### Fase 5 — Medios y autorización de imagen
- Subida masiva a S3/MinIO, validación de tipo y tamaño, borrado de EXIF, WebP/AVIF responsivo [MED-01..03, MED-10, SEG-03].
- Álbumes con visibilidad y revisión obligatoria por el gestor de consentimientos [MED-05, MED-06].
- Difuminado manual de rostros [MED-07]; retiro de una foto de todo el sitio [MED-09].
- Validación: sin nombres completos de estudiantes junto a fotos públicas [MED-12]; alt obligatorio [ACC-02].

### Fase 6 — Privacidad (Ley 21.719)
- Textos legales versionados desde plantilla [DOC-06]; aviso de tratamiento en cada formulario [PRV-01].
- Consentimientos separados y registrados con versión del texto [PRV-02, PRV-04]; banner de cookies [PRV-03].
- Solicitudes de derechos con plazos [PRV-05]; retención con borrado automático [PRV-06]; exportar/borrar a una persona [PRV-07].
- Procedimiento de brechas [PRV-09].

> La ley entra en vigencia el 1-dic-2026 (salvo postergación). Las tablas de privacidad se crean en la iteración 1.5,
> antes de cualquier formulario público, así ningún formulario sale sin consentimiento registrado.

### Fase 7 — Interacción
- Contacto con enrutamiento por área, número de ticket y bandeja [COM-01, COM-02]; antispam [COM-08]; WhatsApp [COM-03].
- Eventos con cupos, lista de espera y cierre automático [EVE-01, EVE-02]; reuniones de apoderados por curso [AGE-08].
- Agenda: tipos de cita, disponibilidad, feriados, reserva, recordatorios `.ics`, reprogramar/cancelar con enlace seguro, panel del gestor [AGE-01..05, AGE-10].
- Admisión modo SAE: página informativa, hitos, vacantes, registro de interés [ADM-01..03, ADM-07, ADM-08].

### Fase 8 — SEO, búsqueda y rendimiento
- schema.org (`School`, `Event`, `NewsArticle`, `FAQPage`), sitemap, robots, Open Graph, `noindex` en lo privado [SEO-01..03].
- Búsqueda interna [UX-06]; analítica respetuosa de la privacidad [REP-01].
- Presupuesto de Core Web Vitals verificado en CI [UX-02].

### Fase 9 — Operación
- Imagen Docker y `compose` de producción con instalación en un comando [OPS-01, OPS-09].
- Licencias por instalación [OPS-05]; canal de actualización [OPS-04].
- Respaldos diarios con restauración probada [SEG-05]; monitoreo y alertas [OPS-06].
- Exportación completa del colegio [OPS-10]; escaneo de dependencias en CI [SEG-02].

### Después del MVP
v2: zona comunidad con login, newsletter y push, agenda con sincronización de calendarios, QR en eventos, multilingüe, PWA, reportes.
Premium: admisión propia y CRM, chatbot, pagos, detección facial asistida, multi-colegio para sostenedores.
