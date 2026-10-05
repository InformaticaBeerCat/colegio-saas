-- Fase 1.6 — Interacción: contacto con enrutamiento, agendamiento de citas, inscripción a eventos
-- y admisión (modo SAE/propio, hitos, vacantes, prospectos).
-- Cada formulario público referencia su consentimiento (consent_record).

CREATE TABLE contact_area (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    version       BIGINT        NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    name          VARCHAR(80)   NOT NULL,
    notify_email  VARCHAR(254)  NOT NULL,
    description   VARCHAR(300),
    active        BOOLEAN       NOT NULL,
    sort_order    INT           NOT NULL,
    CONSTRAINT pk_contact_area PRIMARY KEY (id),
    CONSTRAINT uk_contact_area_name UNIQUE (name)
);

CREATE TABLE inquiry (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    version            BIGINT         NOT NULL,
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    ticket_code        VARCHAR(20)    NOT NULL,
    area_id            BIGINT         NOT NULL,
    name               VARCHAR(1024),
    email              VARCHAR(1024)  NOT NULL,
    email_hash         VARCHAR(64)    NOT NULL,
    phone              VARCHAR(1024),
    subject            VARCHAR(1024),
    message            LONGTEXT       NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    assigned_to_id     BIGINT,
    first_response_at  DATETIME(6),
    resolved_at        DATETIME(6),
    consent_id         BIGINT         NOT NULL,
    CONSTRAINT pk_inquiry PRIMARY KEY (id),
    CONSTRAINT uk_inquiry_ticket_code UNIQUE (ticket_code),
    CONSTRAINT fk_inquiry_area FOREIGN KEY (area_id) REFERENCES contact_area (id),
    CONSTRAINT fk_inquiry_assigned_to FOREIGN KEY (assigned_to_id) REFERENCES user_account (id),
    CONSTRAINT fk_inquiry_consent FOREIGN KEY (consent_id) REFERENCES consent_record (id)
);

CREATE INDEX ix_inquiry_inbox ON inquiry (area_id, status, created_at);
CREATE INDEX ix_inquiry_email ON inquiry (email_hash);

CREATE TABLE inquiry_note (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    version     BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    inquiry_id  BIGINT       NOT NULL,
    author_id   BIGINT,
    body        LONGTEXT     NOT NULL,
    CONSTRAINT pk_inquiry_note PRIMARY KEY (id),
    CONSTRAINT fk_inquiry_note_inquiry FOREIGN KEY (inquiry_id) REFERENCES inquiry (id) ON DELETE CASCADE,
    CONSTRAINT fk_inquiry_note_author FOREIGN KEY (author_id) REFERENCES user_account (id)
);

CREATE TABLE appointment_type (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    version            BIGINT         NOT NULL,
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    name               VARCHAR(120)   NOT NULL,
    description        VARCHAR(1000),
    duration_minutes   INT            NOT NULL,
    buffer_minutes     INT            NOT NULL,
    audience           VARCHAR(30)    NOT NULL,
    in_person_allowed  BOOLEAN        NOT NULL,
    online_allowed     BOOLEAN        NOT NULL,
    active             BOOLEAN        NOT NULL,
    CONSTRAINT pk_appointment_type PRIMARY KEY (id),
    CONSTRAINT ck_appointment_type_duration CHECK (duration_minutes > 0 AND buffer_minutes >= 0),
    CONSTRAINT ck_appointment_type_mode CHECK (in_person_allowed = TRUE OR online_allowed = TRUE)
);

CREATE TABLE appointment_type_host (
    appointment_type_id  BIGINT NOT NULL,
    user_account_id      BIGINT NOT NULL,
    CONSTRAINT pk_appointment_type_host PRIMARY KEY (appointment_type_id, user_account_id),
    CONSTRAINT fk_appointment_type_host_type FOREIGN KEY (appointment_type_id) REFERENCES appointment_type (id) ON DELETE CASCADE,
    CONSTRAINT fk_appointment_type_host_user FOREIGN KEY (user_account_id) REFERENCES user_account (id) ON DELETE CASCADE
);

CREATE TABLE availability_rule (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    version              BIGINT       NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    host_id              BIGINT       NOT NULL,
    appointment_type_id  BIGINT,
    day_of_week          VARCHAR(10)  NOT NULL,
    start_time           TIME         NOT NULL,
    end_time             TIME         NOT NULL,
    valid_from           DATE,
    valid_until          DATE,
    CONSTRAINT pk_availability_rule PRIMARY KEY (id),
    CONSTRAINT fk_availability_rule_host FOREIGN KEY (host_id) REFERENCES user_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_availability_rule_type FOREIGN KEY (appointment_type_id) REFERENCES appointment_type (id) ON DELETE CASCADE,
    CONSTRAINT ck_availability_rule_window CHECK (end_time > start_time)
);

CREATE TABLE availability_block (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    version     BIGINT        NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    host_id     BIGINT,
    starts_at   DATETIME(6)   NOT NULL,
    ends_at     DATETIME(6)   NOT NULL,
    reason      VARCHAR(200),
    CONSTRAINT pk_availability_block PRIMARY KEY (id),
    CONSTRAINT fk_availability_block_host FOREIGN KEY (host_id) REFERENCES user_account (id) ON DELETE CASCADE,
    CONSTRAINT ck_availability_block_dates CHECK (ends_at > starts_at)
);

-- Feriados nacionales: se cargan desde fuente oficial en la fase 7, no a mano.
CREATE TABLE holiday (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    version       BIGINT        NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    holiday_date  DATE          NOT NULL,
    name          VARCHAR(120)  NOT NULL,
    mandatory     BOOLEAN       NOT NULL,
    CONSTRAINT pk_holiday PRIMARY KEY (id),
    CONSTRAINT uk_holiday_date UNIQUE (holiday_date)
);

-- Doble reserva: la evita el servicio con bloqueo pesimista sobre el gestor; MySQL no tiene
-- índices únicos parciales (que excluyan las canceladas).
CREATE TABLE appointment (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    version              BIGINT         NOT NULL,
    created_at           DATETIME(6)    NOT NULL,
    updated_at           DATETIME(6)    NOT NULL,
    appointment_type_id  BIGINT         NOT NULL,
    host_id              BIGINT         NOT NULL,
    starts_at            DATETIME(6)    NOT NULL,
    ends_at              DATETIME(6)    NOT NULL,
    mode                 VARCHAR(20)    NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    contact_name         VARCHAR(1024),
    contact_email        VARCHAR(1024)  NOT NULL,
    contact_email_hash   VARCHAR(64)    NOT NULL,
    contact_phone        VARCHAR(1024),
    student_name         VARCHAR(1024),
    manage_token_hash    VARCHAR(64)    NOT NULL,
    consent_id           BIGINT         NOT NULL,
    staff_notes          VARCHAR(1000),
    cancelled_at         DATETIME(6),
    cancellation_reason  VARCHAR(500),
    reminder_sent_at     DATETIME(6),
    CONSTRAINT pk_appointment PRIMARY KEY (id),
    CONSTRAINT uk_appointment_manage_token UNIQUE (manage_token_hash),
    CONSTRAINT fk_appointment_type FOREIGN KEY (appointment_type_id) REFERENCES appointment_type (id),
    CONSTRAINT fk_appointment_host FOREIGN KEY (host_id) REFERENCES user_account (id),
    CONSTRAINT fk_appointment_consent FOREIGN KEY (consent_id) REFERENCES consent_record (id),
    CONSTRAINT ck_appointment_dates CHECK (ends_at > starts_at)
);

CREATE INDEX ix_appointment_host_start ON appointment (host_id, starts_at);
CREATE INDEX ix_appointment_reminder ON appointment (status, reminder_sent_at, starts_at);

CREATE TABLE event_registration (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    version            BIGINT         NOT NULL,
    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    event_id           BIGINT         NOT NULL,
    name               VARCHAR(1024),
    email              VARCHAR(1024)  NOT NULL,
    email_hash         VARCHAR(64)    NOT NULL,
    phone              VARCHAR(1024),
    attendees          INT            NOT NULL,
    course_id          BIGINT,
    student_name       VARCHAR(1024),
    status             VARCHAR(20)    NOT NULL,
    waitlist_position  INT,
    manage_token_hash  VARCHAR(64)    NOT NULL,
    consent_id         BIGINT         NOT NULL,
    cancelled_at       DATETIME(6),
    CONSTRAINT pk_event_registration PRIMARY KEY (id),
    CONSTRAINT uk_event_registration_manage_token UNIQUE (manage_token_hash),
    CONSTRAINT fk_event_registration_event FOREIGN KEY (event_id) REFERENCES calendar_event (id),
    CONSTRAINT fk_event_registration_course FOREIGN KEY (course_id) REFERENCES course (id),
    CONSTRAINT fk_event_registration_consent FOREIGN KEY (consent_id) REFERENCES consent_record (id),
    CONSTRAINT ck_event_registration_attendees CHECK (attendees >= 1)
);

CREATE INDEX ix_event_registration_event ON event_registration (event_id, status);

-- Una sola fila (id = 1).
CREATE TABLE admission_settings (
    id              BIGINT        NOT NULL,
    version         BIGINT        NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    mode            VARCHAR(10)   NOT NULL,
    sae_url         VARCHAR(300),
    process_year    INT           NOT NULL,
    intro_text      LONGTEXT,
    show_vacancies  BOOLEAN       NOT NULL,
    rules           JSON          NOT NULL,
    CONSTRAINT pk_admission_settings PRIMARY KEY (id),
    CONSTRAINT ck_admission_settings_single_row CHECK (id = 1)
);

CREATE TABLE admission_milestone (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    version       BIGINT        NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    process_year  INT           NOT NULL,
    name          VARCHAR(150)  NOT NULL,
    starts_on     DATE          NOT NULL,
    ends_on       DATE,
    description   VARCHAR(500),
    link_url      VARCHAR(500),
    sort_order    INT           NOT NULL,
    CONSTRAINT pk_admission_milestone PRIMARY KEY (id),
    CONSTRAINT ck_admission_milestone_dates CHECK (ends_on IS NULL OR ends_on >= starts_on)
);

CREATE TABLE vacancy (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    version         BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    grade_level_id  BIGINT       NOT NULL,
    academic_year   INT          NOT NULL,
    seats           INT          NOT NULL,
    CONSTRAINT pk_vacancy PRIMARY KEY (id),
    CONSTRAINT uk_vacancy_level_year UNIQUE (grade_level_id, academic_year),
    CONSTRAINT fk_vacancy_grade_level FOREIGN KEY (grade_level_id) REFERENCES grade_level (id) ON DELETE CASCADE,
    CONSTRAINT ck_vacancy_seats CHECK (seats >= 0)
);

CREATE TABLE prospect (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    version               BIGINT         NOT NULL,
    created_at            DATETIME(6)    NOT NULL,
    updated_at            DATETIME(6)    NOT NULL,
    guardian_name         VARCHAR(1024),
    email                 VARCHAR(1024)  NOT NULL,
    email_hash            VARCHAR(64)    NOT NULL,
    phone                 VARCHAR(1024),
    grade_level_id        BIGINT,
    entry_year            INT,
    source                VARCHAR(20)    NOT NULL,
    stage                 VARCHAR(20)    NOT NULL,
    notes                 LONGTEXT,
    consent_id            BIGINT         NOT NULL,
    follow_up_consent_id  BIGINT,
    retain_until          DATE           NOT NULL,
    utm_source            VARCHAR(100),
    utm_campaign          VARCHAR(100),
    CONSTRAINT pk_prospect PRIMARY KEY (id),
    CONSTRAINT fk_prospect_grade_level FOREIGN KEY (grade_level_id) REFERENCES grade_level (id) ON DELETE SET NULL,
    CONSTRAINT fk_prospect_consent FOREIGN KEY (consent_id) REFERENCES consent_record (id),
    CONSTRAINT fk_prospect_follow_up_consent FOREIGN KEY (follow_up_consent_id) REFERENCES consent_record (id)
);

CREATE INDEX ix_prospect_stage ON prospect (entry_year, stage);
CREATE INDEX ix_prospect_retain_until ON prospect (retain_until);
CREATE INDEX ix_prospect_email ON prospect (email_hash);
