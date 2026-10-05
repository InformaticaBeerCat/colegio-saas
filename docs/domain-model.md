# Modelo de dominio

Diseño de entidades del MVP, agrupado por iteración de la Fase 1. Los diagramas muestran solo los campos
que definen el modelo; todas las entidades heredan además `id`, `version`, `created_at` y `updated_at`.

**Leyenda:** 🔒 indica columnas cifradas. Cada instalación atiende a un solo colegio, por eso ninguna tabla lleva columna de colegio.

---

## 1.1 Núcleo ✅

```mermaid
erDiagram
    SCHOOL ||--o{ SCHOOL_FEATURE : "módulos activos"
    USER_ACCOUNT ||--o{ USER_ACCOUNT_ROLE : ""

    SCHOOL {
        bigint id PK "siempre 1"
        varchar name
        varchar rbd "8485-1"
        varchar dependency "MUNICIPAL, SLEP, PRIVATE_SUBSIDIZED…"
        varchar plan "BASE, COMMUNITY, ADMISSIONS_PRO"
        varchar time_zone
        datetime setup_completed_at
    }
    SCHOOL_FEATURE {
        bigint school_id PK
        varchar feature PK "NEWS, SCHEDULING, PAYMENTS…"
    }
    USER_ACCOUNT {
        varchar email UK
        varchar name
        varchar password_hash
        varchar status "INVITED, ACTIVE, LOCKED, DEACTIVATED"
    }
    USER_ACCOUNT_ROLE {
        bigint user_account_id PK
        varchar role PK "SUPER_ADMIN, SCHOOL_ADMIN, EDITOR, CONSENT_MANAGER…"
    }
    AUDIT_LOG {
        datetime occurred_at
        varchar actor_type "USER, SYSTEM, ANONYMOUS"
        bigint actor_id
        varchar action "CREATE, PUBLISH, VIEW_PERSONAL_DATA…"
        varchar entity_type
        varchar entity_id
    }
```

- **Una instalación = un colegio.** `SCHOOL` es el perfil del colegio dueño de la instalación: una sola fila,
  creada por el asistente de primer arranque. El dominio, la base de datos y la licencia son configuración del despliegue, no datos.
- El Super Admin del proveedor es un `USER_ACCOUNT` con rol `SUPER_ADMIN`.
- `Plan` define los módulos por defecto; los add-ons se activan aparte y sobreviven a un cambio de plan.
  En la fase 9 la licencia firmada limita qué módulos se pueden activar.
- `audit_log` es solo-inserción y guarda una copia del nombre del actor.

---

## 1.2 Sitio y estructura del colegio ✅

```mermaid
erDiagram
    PAGE ||--o{ MENU_ITEM : "enlaza"
    MENU_ITEM ||--o{ MENU_ITEM : "submenú"
    GRADE_LEVEL ||--o{ COURSE : ""

    SITE_SETTINGS {
        bigint id PK "siempre 1 (SingletonEntity)"
        json published_design "SiteDesign: tema, paleta, fuentes, radios…"
        json draft_design "vista previa CFG-08"
        json social_links "List<SocialLink>"
        varchar whatsapp_number "COM-03, +569…"
        varchar footer_text
    }
    PAGE {
        varchar slug UK
        varchar title
        varchar kind "HOME, ABOUT, PEI, LEVELS, FACILITIES, COEXISTENCE, CUSTOM"
        varchar section "MAIN, PARENTS_CENTER, STUDENT_COUNCIL, ALUMNI"
        varchar status "DRAFT, PUBLISHED"
        json draft_blocks "List<Block>"
        json published_blocks "nulo si nunca se publicó"
        datetime published_at
        varchar meta_title
        varchar meta_description
        boolean noindex
    }
    MENU_ITEM {
        varchar menu "HEADER, FOOTER"
        varchar label
        bigint page_id FK "CHECK: página o url, no ambas"
        varchar url
        bigint parent_id FK
        int sort_order
    }
    QUICK_LINK {
        varchar label "Napsis, Classroom, Pago mensualidad…"
        varchar url
        varchar icon
        int sort_order
        boolean active
    }
    SITE_ALERT {
        varchar message
        varchar severity "INFO, WARNING, EMERGENCY"
        varchar link_url
        boolean active
        datetime starts_at
        datetime ends_at
    }
    GRADE_LEVEL {
        varchar name UK "1° Básico"
        varchar stage "EARLY_CHILDHOOD, PRIMARY, SECONDARY"
        varchar track "SCIENTIFIC_HUMANISTIC, TECHNICAL_PROFESSIONAL, ARTISTIC (solo media)"
        int sort_order
    }
    COURSE {
        bigint grade_level_id FK
        varchar section "A, B… o vacío"
        int academic_year "UK con nivel y sección"
    }
```

- **Diseño como JSON** (`SiteDesign`): tema, variante, paleta (`HexColor`), tipografías, radios, sombras y esquema de color
  en un solo `record` inmutable. Borrador y publicado se comparan con `equals`. Logo y favicon llegan en la 1.3.
- **Bloques como JSON**: `sealed interface Block` con un `record` por tipo (`Hero`, `RichText`, `QuickLinks`, `LatestNews`,
  `UpcomingEvents`, `Stats`, `Testimonials`, `CallToAction`, `Gallery`, `Timeline`, `Location`, `Faq`).
  Cada bloque se guarda con su tipo (`"type":"hero"`); agregar un bloque no exige migración, pero renombrar un tipo sí.
- Los bloques referencian medios y álbumes por id, sin llave foránea: al renderizar se ignoran los que ya no existan o estén retirados.
- Borrador y publicado separados (`draft_*` / `published_*`) permiten vista previa sin afectar el sitio (CFG-08).
- `section` + rol (`PARENTS_CENTER_EDITOR`…) restringe a los editores satélite (PUB-07).
- Una página que está en el menú no se puede borrar (llave foránea sin `ON DELETE`).
- Cada `PageKind` salvo `CUSTOM` existe a lo más una vez: lo controla el servicio de páginas (fase 3).
- `GradeLevel` y `Course` son **solo estructura** (para filtrar calendario, álbumes por curso, vacantes, reuniones).
  No hay notas, asistencia ni nada académico.

---

## 1.3 Medios ✅

```mermaid
erDiagram
    STORED_FILE ||--o{ MEDIA_ASSET : "archivo / difuminado"
    MEDIA_FOLDER ||--o{ MEDIA_ASSET : ""
    MEDIA_FOLDER ||--o{ MEDIA_FOLDER : "subcarpeta"
    MEDIA_ASSET }o--o{ MEDIA_TAG : "media_asset_tag"
    USER_ACCOUNT ||--o{ MEDIA_ASSET : "sube / revisa"
    ALBUM ||--o{ ALBUM_ITEM : ""
    MEDIA_ASSET ||--o{ ALBUM_ITEM : ""
    COURSE ||--o{ ALBUM : "visibilidad por curso"
    MEDIA_ASSET ||--o{ SITE_SETTINGS : "logo / favicon"

    STORED_FILE {
        varchar storage_key UK "ruta en S3/MinIO"
        varchar original_name
        varchar content_type
        bigint size_bytes
        varchar sha256 "detecta duplicados"
        int width
        int height
        json variants "WebP/AVIF por ancho (MED-02)"
        varchar scan_status "PENDING, CLEAN, INFECTED (SEG-03)"
    }
    MEDIA_ASSET {
        varchar kind "IMAGE, VIDEO, EMBEDDED_VIDEO, DOCUMENT"
        bigint file_id FK "CHECK: archivo o embed_url"
        varchar embed_url "solo YouTube/Vimeo"
        varchar alt_text "obligatorio para aprobar imágenes (ACC-02)"
        varchar caption
        varchar credits "Ley 17.336"
        bigint folder_id FK
        date taken_on
        bigint uploaded_by_id FK
        varchar review_status "NOT_REQUIRED, PENDING_REVIEW, APPROVED, REJECTED"
        bigint reviewed_by_id FK
        datetime reviewed_at
        varchar review_note
        json blur_regions "List<BlurRegion> (MED-07)"
        bigint blurred_file_id FK
        datetime withdrawn_at "MED-09"
        varchar withdrawal_reason
    }
    MEDIA_TAG {
        varchar name UK "en minúsculas"
    }
    MEDIA_FOLDER {
        varchar name
        bigint parent_id FK
    }
    ALBUM {
        varchar slug UK
        varchar title
        date taken_on
        varchar visibility "PUBLIC, COMMUNITY, COURSE"
        bigint course_id FK "CHECK: solo con COURSE"
        varchar status "DRAFT, PUBLISHED"
        datetime published_at
        bigint cover_id FK
        boolean download_allowed "MED-11"
    }
    ALBUM_ITEM {
        bigint album_id FK
        bigint asset_id FK "UK con album_id"
        int sort_order
    }
```

- **La revisión es por foto, no por álbum.** Una misma foto se usa en álbumes, noticias y bloques; su autorización y su retiro
  valen en todos lados. Toda imagen o video nace `PENDING_REVIEW`; los documentos, `NOT_REQUIRED`.
- `MediaAsset.isDisplayable()` es **la única regla** que consulta todo lo que muestra medios: aprobado o exento, y no retirado.
- Un álbum no se publica con fotos pendientes. Una foto agregada a un álbum ya publicado queda oculta hasta que la aprueben.
- Imágenes sin personas (logo, fachada) se eximen con `exemptFromReview()`; el asistente de primer arranque lo hace con el logo.
- El difuminado guarda las zonas en fracciones (0 a 1) y una versión ya procesada (`blurred_file`), que es la que se publica.
- El EXIF se borra **antes** de guardar el archivo (MED-10): `stored_file` nunca contiene GPS.
- Un medio que está en un álbum no se puede borrar (llave foránea): el camino normal es retirarlo.
- MED-12 (nombres de estudiantes junto a fotos) se valida en el servicio, cuando exista el registro de estudiantes (1.5).

---

## 1.4 Contenido y documentos

```mermaid
erDiagram
    NEWS_CATEGORY ||--o{ NEWS_ARTICLE : ""
    NEWS_ARTICLE }o--o{ GRADE_LEVEL : "etiquetas por nivel"
    NEWS_ARTICLE }o--o| ALBUM : "galería"
    USER_ACCOUNT ||--o{ NEWS_ARTICLE : "autor / revisor"
    ANNOUNCEMENT }o--o{ COURSE : "destinatarios"
    EVENT }o--o{ GRADE_LEVEL : "público"
    FAQ_CATEGORY ||--o{ FAQ_ENTRY : ""
    INSTITUTIONAL_DOCUMENT ||--o{ DOCUMENT_VERSION : ""
    INSTITUTIONAL_DOCUMENT ||--o{ INSTITUTIONAL_DOCUMENT : "protocolos y anexos"
    DOCUMENT_VERSION ||--|| STORED_FILE : ""

    NEWS_ARTICLE {
        varchar slug
        varchar title
        varchar summary
        longtext body "HTML saneado"
        bigint featured_image_id FK
        varchar status "DRAFT, IN_REVIEW, SCHEDULED, PUBLISHED, ARCHIVED"
        datetime publish_at "programación"
        varchar section
        bigint author_id FK
        bigint reviewer_id FK
    }
    ANNOUNCEMENT {
        varchar title
        longtext body
        varchar audience "ALL, GRADE_LEVELS, COURSES"
        varchar visibility "PUBLIC, COMMUNITY"
        bigint attachment_id FK
        datetime published_at
    }
    EVENT {
        varchar slug
        varchar title
        varchar kind "HOLIDAY, VACATION, PARENT_MEETING, CEREMONY, OPEN_HOUSE…"
        datetime starts_at
        datetime ends_at
        boolean all_day
        varchar location
        boolean registration_enabled
        int capacity
        boolean waitlist_enabled
        datetime registration_closes_at
    }
    FAQ_ENTRY {
        bigint category_id FK
        varchar question
        text answer
        int sort_order
    }
    WORKSHOP {
        varchar name
        varchar schedule
        varchar instructor
        int capacity
        int academic_year
    }
    INFO_SHEET {
        varchar kind "SUPPLY_LIST, UNIFORM, MENU"
        bigint grade_level_id FK
        int academic_year
        bigint file_id FK
    }
    INSTITUTIONAL_DOCUMENT {
        varchar category "INTERNAL_REGULATIONS, PROTOCOL, ANNEX, PISE…"
        varchar title
        varchar slug
        bigint parent_id FK
    }
    DOCUMENT_VERSION {
        bigint document_id FK
        bigint file_id FK
        int academic_year "DOC-02"
        varchar school_name "DOC-02 copia al publicar"
        varchar rbd "DOC-02"
        date last_updated_on "DOC-02, alerta a 12 meses"
        boolean current_version
        boolean accessible_pdf "DOC-05"
    }
```

- **Un solo `Event`** cubre calendario escolar (feriados, vacaciones), eventos con inscripción (puertas abiertas)
  y reuniones de apoderados por curso (AGE-08): cambia el `kind` y si tiene inscripción.
- `DOCUMENT_VERSION` copia nombre y RBD del colegio al publicar: la evidencia queda fija aunque el colegio cambie de nombre.
- La alerta de 12 meses (DOC-04) se calcula con `last_updated_on`; no necesita tabla.

---

## 1.5 Privacidad y consentimientos

```mermaid
erDiagram
    LEGAL_TEXT ||--o{ CONSENT_RECORD : "versión aceptada"
    COURSE ||--o{ STUDENT : ""
    STUDENT ||--o{ IMAGE_CONSENT : ""
    LEGAL_TEXT ||--o{ IMAGE_CONSENT : ""
    STUDENT }o--o{ MEDIA_ASSET : "media_asset_student (etiquetado manual)"

    LEGAL_TEXT {
        varchar kind "PRIVACY_POLICY, COOKIE_POLICY, TERMS, PROCESSING_NOTICE, IMAGE_CONSENT_FORM"
        varchar form_key "CONTACT, SCHEDULING, EVENTS, ADMISSIONS…"
        int version_number
        longtext content
        datetime effective_from
    }
    CONSENT_RECORD {
        varchar subject_email "🔒"
        varchar subject_email_hash "búsqueda"
        varchar subject_name "🔒"
        varchar purpose "CONTACT, NEWSLETTER, ANALYTICS, ADMISSIONS_FOLLOW_UP…"
        bigint legal_text_id FK
        boolean granted
        datetime recorded_at
        varchar source "formulario / url"
        datetime withdrawn_at
    }
    DATA_SUBJECT_REQUEST {
        varchar kind "ACCESS, RECTIFICATION, ERASURE, OBJECTION, PORTABILITY, BLOCKING"
        varchar requester_name "🔒"
        varchar requester_email "🔒"
        varchar status "RECEIVED, VERIFYING, IN_PROGRESS, COMPLETED, REJECTED"
        datetime due_at
        bigint handled_by FK
    }
    RETENTION_POLICY {
        varchar data_category "INQUIRIES, PROSPECTS, APPOINTMENTS…"
        int retention_days
        varchar action "DELETE, ANONYMIZE"
    }
    SECURITY_INCIDENT {
        datetime detected_at
        varchar severity
        text description
        datetime authority_notified_at
        datetime subjects_notified_at
    }
    STUDENT {
        bigint course_id FK
        varchar full_name "🔒"
        varchar guardian_email "🔒"
        varchar guardian_email_hash
        boolean active
    }
    IMAGE_CONSENT {
        bigint student_id FK
        varchar channel "WEBSITE, SOCIAL_MEDIA, PRINT"
        datetime granted_at
        datetime revoked_at
        varchar method "PAPER_FORM, COMMUNITY_AREA, EMAIL"
        bigint evidence_file_id FK
        bigint legal_text_id FK
        bigint recorded_by FK
    }
```

- `LEGAL_TEXT` es inmutable una vez publicado: un cambio crea una nueva versión, y cada consentimiento apunta a la versión exacta que se aceptó (PRV-04).
- `CONSENT_RECORD` es solo-inserción. Retirar un consentimiento llena `withdrawn_at`; nunca se borra la evidencia.
- `STUDENT` es el mínimo para gestionar autorizaciones de imagen: **sin RUN** (PRV-08), nombre cifrado.
- `IMAGE_CONSENT`: una fila por autorización otorgada. Revocar llena `revoked_at`; volver a autorizar crea otra fila.
  Así queda la historia completa y la vigente es la que no tiene `revoked_at`.
- `media_asset_student` es opcional y manual (sin reconocimiento facial): permite, ante una revocación, encontrar y retirar todas las fotos del estudiante (MED-09).

---

## 1.6 Interacción

```mermaid
erDiagram
    CONTACT_AREA ||--o{ INQUIRY : "enrutamiento"
    INQUIRY ||--o{ INQUIRY_NOTE : ""
    INQUIRY }o--|| CONSENT_RECORD : ""
    APPOINTMENT_TYPE }o--o{ USER_ACCOUNT : "gestores"
    USER_ACCOUNT ||--o{ AVAILABILITY_RULE : ""
    USER_ACCOUNT ||--o{ AVAILABILITY_BLOCK : ""
    APPOINTMENT_TYPE ||--o{ APPOINTMENT : ""
    USER_ACCOUNT ||--o{ APPOINTMENT : "atiende"
    EVENT ||--o{ EVENT_REGISTRATION : ""
    GRADE_LEVEL ||--o{ VACANCY : ""
    GRADE_LEVEL ||--o{ PROSPECT : "nivel de interés"

    CONTACT_AREA {
        varchar name "Admisión, Secretaría, Convivencia, Finanzas"
        varchar notify_email
        boolean active
    }
    INQUIRY {
        varchar ticket_number UK
        bigint area_id FK
        varchar name "🔒"
        varchar email "🔒"
        varchar phone "🔒"
        varchar subject
        text message "🔒"
        varchar status "NEW, IN_PROGRESS, RESOLVED, SPAM"
        bigint assigned_to FK
        datetime first_response_at
    }
    APPOINTMENT_TYPE {
        varchar name "Visita guiada, Entrevista profesor jefe…"
        int duration_minutes
        int buffer_minutes
        varchar audience "PROSPECTIVE_FAMILY, GUARDIAN, ANY"
        varchar mode "IN_PERSON, ONLINE, BOTH"
    }
    AVAILABILITY_RULE {
        bigint host_id FK
        bigint appointment_type_id FK
        varchar day_of_week
        time start_time
        time end_time
    }
    AVAILABILITY_BLOCK {
        bigint host_id FK "nulo = todo el colegio"
        datetime starts_at
        datetime ends_at
        varchar reason
    }
    HOLIDAY {
        date day UK
        varchar name
        boolean mandatory
    }
    APPOINTMENT {
        bigint type_id FK
        bigint host_id FK
        datetime starts_at
        datetime ends_at
        varchar status "CONFIRMED, CANCELLED, ATTENDED, NO_SHOW"
        varchar contact_name "🔒"
        varchar contact_email "🔒"
        varchar manage_token_hash "AGE-05"
        varchar staff_notes "no sensibles (AGE-10)"
    }
    EVENT_REGISTRATION {
        bigint event_id FK
        varchar name "🔒"
        varchar email "🔒"
        int attendees
        bigint course_id FK "reuniones por curso"
        varchar status "CONFIRMED, WAITLISTED, CANCELLED, ATTENDED"
        int waitlist_position
        varchar manage_token_hash
    }
    ADMISSION_SETTINGS {
        bigint id PK "siempre 1 (SingletonEntity)"
        varchar mode "SAE, OWN"
        varchar sae_url
        int process_year
        json rules "ADM-08, reglas configurables"
    }
    ADMISSION_MILESTONE {
        varchar name
        date starts_on
        date ends_on
    }
    VACANCY {
        bigint grade_level_id FK
        int academic_year
        int seats
    }
    PROSPECT {
        varchar guardian_name "🔒"
        varchar email "🔒"
        varchar email_hash
        varchar phone "🔒"
        bigint grade_level_id FK
        int entry_year
        varchar source "WEBSITE, OPEN_HOUSE, VISIT, REFERRAL, SOCIAL"
        varchar stage "INTERESTED, VISITED, APPLIED, ENROLLED, DISCARDED"
        date retain_until "PRV-06"
    }
```

- Las visitas guiadas son `Appointment` de un tipo con audiencia `PROSPECTIVE_FAMILY`; las jornadas de puertas abiertas son `Event` con inscripción.
- Los enlaces de reprogramar/cancelar llevan un token aleatorio; en la base solo se guarda su hash.
- Evitar doble reserva: bloqueo pesimista sobre el gestor al reservar (MySQL no tiene índices únicos parciales portables).
- `HOLIDAY` se precarga por migración con los feriados chilenos de cada año.

---

## Pendiente de decidir (antes de su iteración)

- **Fase 3:** nombre y estilo de los 3 temas base.
- **1.5:** gestión de la llave de cifrado (variable de entorno vs. KMS) y su rotación.
- **1.6:** plazo legal de respuesta a solicitudes de derechos (validar con abogado; dejarlo configurable).
