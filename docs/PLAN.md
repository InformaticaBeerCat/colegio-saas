# Plan de desarrollo — Colegio SaaS

Hoja de ruta técnica del MVP (requerimientos v2, oct-2026). Cada fase termina con código que compila,
tests en verde y algo demostrable. Los IDs entre corchetes (`PUB-01`, `MED-06`…) remiten al documento de requerimientos.

## Decisiones de arquitectura

| Tema | Decisión | Por qué |
|---|---|---|
| Stack | Java 21 LTS, Spring Boot 4.1, Hibernate 7, Maven (wrapper incluido) | Versiones estables actuales; Java 21 es el JDK instalado |
| Forma | Monolito modular: un solo deployable, paquetes por funcionalidad (`platform`, `identity`, `media`…) | Un equipo chico no necesita microservicios; los límites por paquete permiten separar después |
| Despliegue | **Una instalación = un colegio** (modelo Nextcloud): el mismo código se despliega una vez por colegio, en su servidor o alojado por el proveedor (un contenedor por colegio) | Datos de cada colegio físicamente separados; sin riesgo de cruce entre colegios; el colegio puede ser dueño de su servidor |
| Personalización | Lo propio del colegio vive en la base de datos (perfil, marca, contenido) y en la configuración del despliegue (dominio, BD, almacenamiento, licencia) | Un solo código base para todos los clientes (OPS-02) |
| Super Admin | Usuario con rol `SUPER_ADMIN` dentro de cada instalación (como el admin de Nextcloud) | El proveedor instala, actualiza y da soporte; su acceso queda en la auditoría |
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
- Las entidades extienden `BaseEntity`; las de una sola fila (perfil del colegio, configuración del sitio) extienden `SingletonEntity` (id = 1, con `CHECK` en la tabla).
- Una entidad ya cargada se modifica dentro de la transacción **sin llamar `save()`**: Hibernate detecta el cambio solo (dirty checking).
  `save()` es solo para entidades nuevas. Motivo: en Hibernate 7.4, un `save()` (merge) que deja vacía una colección pierde el `DELETE`.
- Una migración Flyway publicada no se edita nunca; los cambios van en una versión nueva.
- Columnas de orden: `sort_order` (no `position`, que es palabra clave en algunos motores).
- Valores estructurados (diseño, bloques) como `record` inmutable en columna JSON con `@JdbcTypeCode(SqlTypes.JSON)`.
- Textos largos (HTML, mensajes): columna `LONGTEXT` y campo `String` sin anotaciones (no `@Lob`: H2 y MySQL lo validan distinto).
- Datos personales que escribe el público: `@Convert(converter = EncryptedStringConverter.class)`; si hay que buscar por
  email, columna `*_hash` calculada con `BlindIndex` en el constructor de la entidad.
- Fechas de calendario y citas en hora local del colegio (`LocalDateTime`); marcas técnicas en UTC (`Instant`).
- Tokens de enlaces públicos: se entrega el token, se guarda solo su hash (`SecureTokens`).
- Tests de persistencia con `@RepositoryTest` (H2 + cifrado + `TestFixtures`); `MySqlCompatibilityTest` contra MySQL real.
- Tests web extienden `WebTestSupport` (MockMvc, transacción revertida, eventos registrados). Ojo: `.param()` agrega
  valores, no los reemplaza.
- El código pregunta por **permisos**, nunca por roles (`hasAuthority('USERS')`, `user.can(...)`).
- Correos: el servicio publica `OutgoingMail` y se envía después del commit; sin SMTP se escriben en el log.
- Textos de interfaz para enums en `messages.properties` (`role.EDITOR`, `status.ACTIVE`…), base para multilingüe.

## Estructura del repositorio

```
colegio-saas/
├── README.md
├── docs/               plan, modelo de dominio, decisiones
└── app/                aplicación Spring Boot (Maven)
    ├── compose.yaml    MySQL + Mailpit para desarrollo
    └── src/main/java/cl/colegiosaas/
        ├── shared/     persistencia base, cifrado, tokens, almacenamiento de archivos, HTML seguro
        ├── platform/   perfil del colegio, planes y módulos
        ├── identity/   usuarios, roles, permisos, invitaciones (web/ = pantallas)
        ├── security/   login, MFA, bloqueos, sesiones, cabeceras
        ├── setup/      instalador de primer arranque
        ├── admin/      portada del panel
        ├── publicsite/ sitio público: páginas, noticias, calendario, documentos…
        ├── audit/      registro de auditoría y su consulta
        ├── site/       diseño, accesos rápidos, alerta global
        ├── page/       páginas por bloques y menús
        ├── structure/  niveles y cursos
        ├── media/      archivos, biblioteca, revisión de imagen y álbumes
        ├── news/       noticias y comunicados
        ├── calendar/   calendario, eventos e inscripciones
        ├── info/       preguntas frecuentes, talleres, útiles/uniforme/menú
        ├── documents/  documentos institucionales con versiones
        ├── privacy/    textos legales, consentimientos, derechos, retención, brechas
        ├── consent/    estudiantes y autorizaciones de imagen
        ├── contact/    consultas con enrutamiento por área
        ├── scheduling/ tipos de cita, disponibilidad y citas
        └── admissions/ modo SAE/propio, hitos, vacantes y prospectos
```

---

## Fases

### Fase 0 — Fundaciones ✅
Proyecto Maven, perfiles (H2 por defecto / `mysql`), Flyway, `compose.yaml`, Testcontainers listo para MySQL.

### Fase 1 — Modelo de dominio ✅
Diseño completo en [domain-model.md](domain-model.md). Se implementa por iteraciones; cada una agrega
entidades, repositorios, su migración Flyway y tests de persistencia.

| Iteración | Contenido | Estado |
|---|---|---|
| 1.1 Núcleo | `BaseEntity`, `SingletonEntity`, `School` (perfil), `Feature`/`Plan`, `UserAccount`/`Role`, `AuditLogEntry` | ✅ |
| 1.2 Sitio y estructura | `SiteSettings` (diseño JSON), `Page` (bloques JSON), `MenuItem`, `QuickLink`, `SiteAlert`, `GradeLevel`, `Course` | ✅ |
| 1.3 Medios | `StoredFile`, `MediaFolder`, `MediaAsset`, `MediaTag`, `Album`, `AlbumItem`; logo y favicon del sitio | ✅ |
| 1.4 Contenido y documentos | `NewsArticle`, `NewsCategory`, `Announcement`, `Event`, `FaqCategory`, `FaqEntry`, `Workshop`, `InfoSheet`, `InstitutionalDocument`, `DocumentVersion` | ✅ |
| 1.5 Privacidad y consentimientos | Cifrado de columnas e índice ciego, `LegalText`, `ConsentRecord`, `DataSubjectRequest`, `RetentionPolicy`, `SecurityIncident`, `Student`, `ImageConsent`, `StudentAppearance` | ✅ |
| 1.6 Interacción | `ContactArea`, `Inquiry`, `InquiryNote`, `AppointmentType`, `AvailabilityRule`, `AvailabilityBlock`, `Holiday`, `Appointment`, `EventRegistration`, `AdmissionSettings`, `AdmissionMilestone`, `Vacancy`, `Prospect` | ✅ |

**Listo cuando:** todas las tablas MVP existen, Hibernate valida contra MySQL real y cada agregado tiene tests de persistencia de sus reglas.

> Pendiente: correr `MySqlCompatibilityTest` con Docker encendido (se salta solo sin Docker). Hasta entonces, todo está probado sobre H2 en modo MySQL.

### Fase 2 — Seguridad e identidad ✅
- Instalador de primer arranque, como Nextcloud [CFG-01]: mientras no hay instalación todo redirige a `/setup`,
  que exige el token que aparece en el log (o `APP_SETUP_TOKEN`). Crea perfil del colegio, diseño y admisión por
  defecto, y la cuenta `SUPER_ADMIN`. Después `/setup` responde 404.
- Login de panel en `/admin/login` con mensajes genéricos (no revela qué correos existen), CSRF, cookie de sesión
  `HttpOnly` + `SameSite=Lax` (+ `Secure` con `APP_SECURE_COOKIES=true`), cabeceras CSP, Referrer-Policy y
  Permissions-Policy [SEG-01].
- MFA TOTP **opcional**: cada persona lo activa o desactiva (con su contraseña) desde "Mi cuenta"; el panel lo
  recomienda a los roles sensibles. Con `APP_ENFORCE_MFA=true` pasa a ser obligatorio para `SUPER_ADMIN`,
  `SCHOOL_ADMIN` y `CONSENT_MANAGER`, como pide USR-02. QR en SVG sin JavaScript, anti-repetición de códigos y
  10 códigos de recuperación de un solo uso. Mientras falta el código, la sesión es `MfaPendingAuthentication`
  y no abre el panel.
- Permisos por rol (`Role.permissions()` → `Permission`) y `@PreAuthorize` por sección [USR-01]; módulos
  contratados con `@RequiresFeature` (404 si no está activo) [CFG-07].
- Usuarios: invitación por correo (7 días), aceptar, recuperar contraseña (1 hora), cambiar roles, reiniciar MFA y
  baja inmediata que cierra las sesiones abiertas [USR-04].
- Fuerza bruta: bloqueo de cuenta 15 min tras 5 fallos y límite por IP [SEG-06].
- Auditoría de ingresos, fallos, bloqueos, invitaciones, permisos, bajas y MFA, con página de consulta [USR-03].

### Fase 3 — Marca y motor de temas ✅
- Asistente de marca [CFG-01] en `/admin/welcome`, segunda parte del primer arranque: se elige tema y variante y
  se crean la portada (publicada) y las páginas institucionales (en borrador, ya en el menú). Nunca se publica
  texto de ejemplo: las páginas en borrador no aparecen en el menú público.
- 3 temas base con 3 variantes cada uno [CFG-02]: **Institucional** (`classic`: serif, barra de menú en el color
  del colegio), **Moderno** (`modern`: geométrica, encabezado blanco fijo) y **Cercano** (`friendly`: formas
  redondeadas, pensado para básica y párvulos). El tema define la estructura (`static/css/site.css`); los tokens,
  los colores.
- Tokens de diseño [CFG-03] servidos como `/site/theme.css?v=<huella>` (la CSP no permite estilos en línea;
  caché de un año mientras la huella no cambie). `DesignReview` verifica contraste AA (4,5:1) de cada par que el
  tema combina y rechaza el diseño que no cumple [ACC-01]; el texto sobre colores se elige solo (blanco o negro).
  Modo "Automático" con versión oscura derivada que también cumple AA.
- Fuentes con licencia libre (OFL/Apache) alojadas en el propio sitio (`static/fonts`, con sus licencias): no se
  llama a Google Fonts, así los visitantes no entregan su IP a terceros.
- Sitio público por bloques [PUB-01, CFG-04]: portada en `/`, páginas en `/{slug}`, direcciones reservadas para
  el sistema y las próximas fases. HTML de los editores saneado con jsoup (sin scripts, estilos ni imágenes:
  las fotos pasan por la biblioteca y su revisión). Enlaces revisados (`SafeUrls`: nada de `javascript:`).
  Bloques que dependen de un módulo no contratado o sin contenido se omiten.
- Menú principal y del pie con dos niveles, y pie con contacto, dirección, WhatsApp y redes [CFG-05].
- Vista previa del borrador de diseño y de páginas en `/admin/preview` [CFG-08], solo para el panel y `noindex`.
- Accesibilidad base [ACC-04]: `lang="es-CL"`, landmarks, salto al contenido, foco visible 3:1, un solo `h1`,
  submenús con teclado (Escape cierra) y funcionando sin JavaScript. Móvil primero, áreas táctiles de 44 px [UX-01].

> Pendiente para la fase 5: logo e imagen/video de la portada y el bloque galería (necesitan servir archivos de la
> biblioteca de medios). Caché del contexto del sitio: fase 8, si las mediciones lo piden.

### Fase 4 — Contenido y documentos ✅
- Noticias [NOT-01, NOT-02]: borrador → revisión → publicada o programada. Quien escribe envía a revisión (aviso por
  correo a quien aprueba); quien tiene `NEWS_PUBLISH` aprueba, programa o devuelve con comentario (aviso al autor).
  Una tarea cada minuto pasa a publicadas las programadas. Categorías y niveles filtrables en `/noticias`.
  Los editores satélite solo escriben en su sección [PUB-07]. Lo publicado no se borra: se archiva.
- Comunicados y circulares [NOT-03] dirigidos a todo el colegio, niveles o cursos, con PDF adjunto (`/comunicados`).
- Calendario [NOT-04] por mes, filtrable por tipo y nivel, con página por evento y exportación `.ics` del calendario
  completo o de un evento [NOT-05] (horas en UTC, días completos como fechas: funciona en cualquier aplicación).
- Preguntas frecuentes [PUB-09], talleres [PUB-06] y útiles/uniforme/minuta con vista web y PDF, con vigencia [PUB-10].
- Documentos institucionales [DOC-01..05]: cada publicación crea una versión y archiva la anterior, que sigue
  descargable en el historial. El Reglamento Interno, sus protocolos y anexos exigen año académico y RBD y muestran
  nombre, RBD y fecha de actualización (REX 781). Alerta en el panel a los 12 meses sin actualizar. Si el PDF no es
  accesible, el sitio lo advierte y ofrece pedirlo en otro formato. Un documento publicado nunca se borra.
- Página de convivencia [DOC-07] con dos bloques nuevos: lista de documentos por categoría y canal de denuncia.
- Avisos urgentes en todo el sitio [PUB-12], activables en un clic y con ventana de tiempo.
- Niveles y cursos editables desde el panel (con carga de los niveles chilenos en un clic): son la base de los filtros.
- PDF subidos a un almacenamiento de archivos (`FileStorage`, hoy en carpeta local `APP_STORAGE_DIR`; S3/MinIO en la
  fase 5). Se valida la firma `%PDF-` y el tamaño (20 MB). Solo se descargan (`/archivos/<sha256>/<nombre>`) los
  archivos de contenido publicado.

> Pendiente para la fase 5: imagen destacada y galería en noticias, antivirus de archivos (SEG-03; hoy la revisión es
> de tipo y tamaño, y solo suben archivos cuentas del panel). Formulario propio del canal de denuncia: fase 7 (contacto).

### Fase 5 — Medios y autorización de imagen ✅
- Biblioteca de medios [MED-01] con carpetas y etiquetas; subida masiva de hasta 50 fotos [MED-03] donde cada
  archivo informa su resultado. Tipo por firma (JPG, PNG, WebP; HEIC con mensaje), 15 MB, tope de 50 megapíxeles
  contra bombas de descompresión.
- Al subir, la foto se gira según su EXIF y se vuelve a codificar desde los píxeles: no queda GPS, cámara ni fecha
  [MED-10]. Versiones WebP y JPEG a 480, 960 y 1600 px más la maestra (hasta 2560 px) [MED-02]; el sitio usa
  `<picture>` con `srcset`, dimensiones y carga diferida. AVIF queda pendiente: no hay codificador Java confiable.
- Antivirus ClamAV (`APP_CLAMD_HOST`) para fotos y PDF [SEG-03]; si no responde, la subida se rechaza. Sin
  configurarlo, la app lo avisa en el log y en el panel.
- Almacenamiento S3/MinIO (`APP_STORAGE_TYPE=s3`) o carpeta local [OPS-07]; `compose.yaml` trae MinIO y ClamAV.
- Revisión obligatoria por el gestor de consentimientos [MED-06]: marca qué estudiantes aparecen (sin
  reconocimiento facial) y el sistema revisa sus autorizaciones para el sitio web. Con alguien sin autorización
  solo se aprueba si quedó difuminado y se confirma. Sin texto alternativo no se aprueba [ACC-02].
- Difuminado manual con editor visual (o zonas escritas a mano) [MED-07]: pixelado irreversible sobre la original;
  el público recibe solo la versión difuminada.
- Retiro de todo el sitio en un clic [MED-09], y automático al revocar la autorización del sitio web de un
  estudiante. Una foto subida dos veces se retira en todas sus copias, y un archivo con alguna copia retirada no se
  sirve.
- Pies de foto y textos alternativos sin nombre y apellido de estudiantes [MED-12].
- Registro mínimo de estudiantes (nombre y curso, cifrados, sin RUN) y autorizaciones por canal con evidencia;
  formulario base de autorización publicable (el editor de textos legales llega en la fase 6).
- Álbumes con visibilidad pública, de comunidad o de curso [MED-05]; no se publican con fotos pendientes. Galerías
  públicas en `/galerias`; videos de YouTube (sin cookies) y Vimeo.
- Integración: foto en la portada (al lado del texto, para no depender del contraste), bloque galería, imagen
  destacada en noticias (solo fotos aprobadas) y logo y favicon del colegio.

> Pendiente: AVIF; descarga de álbumes (MED-11, v2); detección facial asistida (premium).

### Fase 6 — Privacidad (Ley 21.719) ✅
- Textos legales versionados [DOC-06] en `/admin/legal`: política de privacidad, cookies, términos, autorización de
  imagen y un aviso por formulario. El borrador parte de una plantilla con los datos del colegio (nombre, RBD,
  contacto y el plazo de conservación de ese formulario) o de la versión vigente; al publicarse queda inmutable y
  las anteriores siguen en `/privacidad/<texto>/v<n>`. El HTML pasa por el sanitizador.
- Aviso de tratamiento en cada formulario [PRV-01] (fragmento `public/privacy/fragments :: notice`, desplegable,
  con enlace a la versión exacta). `ConsentService` guarda cada casilla aparte, también el "no", con la versión
  del aviso, ruta, IP y navegador [PRV-02, PRV-04]; sin aviso publicado el formulario no recibe datos.
- Banner de cookies [PRV-03] sin JavaScript ni sesión: "Solo necesarias" y "Aceptar analítica" con el mismo peso;
  la elección vive en una cookie con la versión de la política y vuelve a preguntarse si la política cambia. La
  fase 8 carga la analítica solo con `cookies.analyticsAllowed`. Preferencias editables en `/privacidad/cookies`.
- Solicitudes de derechos [PRV-05]: formulario público en `/privacidad/derechos` (con campo trampa para bots),
  código de seguimiento, plazo (`APP_PRIVACY_RESPONSE_DAYS`, 30 días corridos por defecto: validar con abogado),
  correo al titular y a quienes tienen el permiso de privacidad, consulta de estado sin datos personales, bandeja
  con vencidas y por vencer, acreditar identidad, responder o rechazar con fundamento.
- Datos de una persona [PRV-07]: búsqueda por índice ciego del email (por POST, sin dejar el email en registros),
  exportación JSON y supresión: borra consultas, citas, inscripciones y registros de admisión, quita el email de
  apoderado de los estudiantes y anonimiza los consentimientos. Todo queda en auditoría.
- Retención [PRV-06]: tarea diaria (04:15) y botón "Aplicar ahora"; plazos y acción editables por tipo de dato
  (cada tipo admite solo las acciones que tienen sentido). Los consentimientos de boletín vigentes no se tocan.
- Brechas [PRV-09]: procedimiento en el panel, registro con hora de detección, contador contra la meta de
  notificación a la Agencia (`APP_PRIVACY_INCIDENT_HOURS`, 72 h), borradores de notificación a la Agencia y a las
  familias, aviso por correo y alerta en el panel mientras no se cierre.

> Pendiente: borrar del almacenamiento los archivos de evidencia de un estudiante eliminado por retención (requiere
> borrado en `FileStorage`, fase 9). Los plazos y textos de plantilla deben revisarse con la asesoría legal.

> La ley entra en vigencia el 1-dic-2026 (salvo postergación). Las tablas de privacidad se crean en la iteración 1.5,
> antes de cualquier formulario público, así ningún formulario sale sin consentimiento registrado.

### Fase 7 — Interacción ✅
- Antispam común a todos los formularios públicos [COM-08], sin captcha ni servicios externos: campo trampa, sello
  firmado con la hora en que se mostró el formulario (sin sesión) y límite de envíos por IP. A un bot se le responde
  como si todo hubiera salido bien. También protege el formulario de derechos de la fase 6.
- Contacto [COM-01, COM-02]: el mensaje va al área elegida (la instalación crea "Secretaría"; se administran en
  `/admin/contact-areas`), recibe número de ticket por correo y el área un aviso sin datos personales. Bandeja con
  carpetas y filtro por área, responder por correo (cuenta como primera respuesta), notas internas, derivar, resolver,
  spam, y el promedio de horas hasta la primera respuesta. WhatsApp click-to-chat en la página de contacto [COM-03].
- Eventos con inscripción [EVE-01, EVE-02]: aforo, lista de espera y cierre automático (fecha o inicio del evento).
  El cupo se decide con la fila del evento bloqueada; una familia no se separa entre confirmados y espera. Al
  cancelar (con el enlace secreto del correo o desde el panel) o ampliar el aforo, sube la lista en orden y se avisa.
  Planilla CSV de inscritos y registro de asistencia.
- Reuniones de apoderados por curso [AGE-08]: piden el curso al inscribirse y se listan por curso en
  `/calendario/reuniones`.
- Agenda [AGE-01..05, AGE-10]: tipos de cita con duración, pausa, público, modalidad y funcionarios; horario semanal
  por funcionario (y por tipo), bloqueos personales o de todo el colegio y feriados (carga desde la API oficial de
  gob.cl, `APP_HOLIDAYS_URL`, o a mano). Horas libres con anticipación mínima, reserva con la fila del funcionario
  bloqueada, correo con `.ics` (con alarma) y enlace secreto para reprogramar o cancelar, recordatorio 24 h antes.
  Panel del gestor: su agenda (o la de todos con el permiso general), ficha de la cita con registro de acceso,
  asistencia, cancelación con aviso a la familia y notas.
- Admisión SAE [ADM-01..03, ADM-07, ADM-08]: página `/admision` según el modo (SAE o propio con Admisión Pro), hitos
  del proceso, niveles abiertos con fechas de nacimiento y vacantes configurables, visitas guiadas y jornadas de
  puertas abiertas, y registro de interés con consentimiento propio y otro separado para el seguimiento (ADM-06),
  plazo de conservación y datos de campaña (`utm_source`, `utm_campaign`).
- Cada formulario pide su consentimiento sobre el aviso publicado; sin aviso, el formulario no se muestra.

> Pendiente: enlace de videollamada automático para citas en línea (AGE-06, v2); postulación propia (v2); embudo y
> correos automáticos de seguimiento (Admisión Pro).

### Fase 8 — SEO, búsqueda y rendimiento ✅
- Datos estructurados schema.org en JSON-LD [SEO-01]: `School` en la portada (dirección, contacto, redes, logo),
  `Event` en cada evento, `NewsArticle` en cada noticia y `FAQPage` en preguntas frecuentes. El texto no puede cerrar
  el `<script>` (se escapa `</`).
- `sitemap.xml` con todo lo público e indexable (páginas, noticias, eventos, documentos, galerías y secciones de los
  módulos activos) y `robots.txt` que deja fuera el panel y los enlaces personales [SEO-02].
- URL canónica, Open Graph y tarjeta de Twitter en cada página pública; `noindex` en vista previa, búsqueda y enlaces
  personales, y cabecera `X-Robots-Tag` en el panel, citas e inscripciones (también para sus PDF) [SEO-03].
- Búsqueda interna en `/buscar` y en el menú [UX-06]: sin índice que mantener (recorre el contenido público), sin
  distinguir mayúsculas ni tildes, todas las palabras deben aparecer y pesa más el título; incluye las secciones fijas.
- Analítica respetuosa de la privacidad [REP-01]: conteo propio de visitas por página y día y del sitio de origen
  (solo el dominio), sin cookies, IP ni navegador; respeta "No rastrear" y el Control global de privacidad y no cuenta
  bots. Panel en "Visitas del sitio". Opcional: una herramienta externa (`APP_ANALYTICS_SCRIPT_URL`) que se carga solo
  con consentimiento; el banner de cookies aparece solo si existe (sin ella no hay cookies opcionales que consentir).
- Rendimiento [UX-02]: HTML, CSS y JS comprimidos; CSS y JS con la huella del contenido en la URL y caché de un año.
  `PerformanceBudgetTest` revisa en cada build el peso del HTML, CSS y JS, que ningún script bloquee el pintado ni venga
  de afuera y que toda imagen tenga tamaño reservado. CI (`.github/workflows/ci.yml`) mide LCP, CLS, TBT y peso en un
  teléfono emulado con 4G lento (`app/perf/web-vitals.mjs`) y falla sobre 2,5 s, 0,1, 200 ms o 500 KB.

> Pendiente: caché del HTML público tras un proxy/CDN (fase 9); búsqueda con índice si un colegio supera los miles
> de publicaciones.

### Fase 9 — Operación
- Imagen Docker y `compose` de producción: un colegio se instala con un comando y variables de entorno
  (dominio, base de datos, almacenamiento, llave de cifrado, licencia) [OPS-01, OPS-09].
- Licencia firmada por instalación: define plan y add-ons; los módulos activables quedan limitados por ella [OPS-05].
- Canal de actualización con migraciones automáticas [OPS-03, OPS-04].
- Respaldos diarios con restauración probada [SEG-05]; monitoreo y alertas [OPS-06].
- Exportación completa del colegio [OPS-10]; escaneo de dependencias en CI [SEG-02].

### Después del MVP
v2: zona comunidad con login, newsletter y push, agenda con sincronización de calendarios, QR en eventos, multilingüe, PWA, reportes.
Premium: admisión propia y CRM, chatbot, pagos, detección facial asistida.
Sostenedores con varios colegios (CFG-10): una instalación por colegio más exportar/importar el tema para compartir diseño.
