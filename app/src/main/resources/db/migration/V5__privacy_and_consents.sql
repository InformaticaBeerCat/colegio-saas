-- Fase 1.5 — Privacidad (Ley 21.719): textos legales versionados, consentimientos, solicitudes de
-- derechos, plazos de conservación y brechas. Autorizaciones de imagen de estudiantes.
--
-- Columnas cifradas (AES-GCM, ver FieldCipher): VARCHAR(1024) o LONGTEXT, porque el texto cifrado en
-- Base64 ocupa ~1,4 veces el original. Las columnas *_hash son índices ciegos HMAC-SHA256 (64 hex).

CREATE TABLE legal_text (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    version          BIGINT        NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    kind             VARCHAR(30)   NOT NULL,
    version_number   INT           NOT NULL,
    title            VARCHAR(200)  NOT NULL,
    content          LONGTEXT      NOT NULL,
    effective_from   DATETIME(6),
    published_by_id  BIGINT,
    CONSTRAINT pk_legal_text PRIMARY KEY (id),
    CONSTRAINT uk_legal_text_kind_version UNIQUE (kind, version_number),
    CONSTRAINT fk_legal_text_published_by FOREIGN KEY (published_by_id) REFERENCES user_account (id)
);

CREATE TABLE consent_record (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    version             BIGINT         NOT NULL,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    subject_name        VARCHAR(1024),
    subject_email       VARCHAR(1024)  NOT NULL,
    subject_email_hash  VARCHAR(64)    NOT NULL,
    purpose             VARCHAR(30)    NOT NULL,
    legal_text_id       BIGINT         NOT NULL,
    granted             BOOLEAN        NOT NULL,
    source              VARCHAR(300),
    ip_address          VARCHAR(256),
    user_agent          VARCHAR(300),
    withdrawn_at        DATETIME(6),
    CONSTRAINT pk_consent_record PRIMARY KEY (id),
    CONSTRAINT fk_consent_record_legal_text FOREIGN KEY (legal_text_id) REFERENCES legal_text (id)
);

CREATE INDEX ix_consent_record_subject ON consent_record (subject_email_hash);

CREATE TABLE data_subject_request (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    version               BIGINT         NOT NULL,
    created_at            DATETIME(6)    NOT NULL,
    updated_at            DATETIME(6)    NOT NULL,
    tracking_code         VARCHAR(20)    NOT NULL,
    requested_right       VARCHAR(20)    NOT NULL,
    requester_name        VARCHAR(1024),
    requester_email       VARCHAR(1024)  NOT NULL,
    requester_email_hash  VARCHAR(64)    NOT NULL,
    on_behalf_of_minor    BOOLEAN        NOT NULL,
    details               LONGTEXT,
    status                VARCHAR(30)    NOT NULL,
    due_on                DATE           NOT NULL,
    handled_by_id         BIGINT,
    resolved_at           DATETIME(6),
    resolution            VARCHAR(2000),
    CONSTRAINT pk_data_subject_request PRIMARY KEY (id),
    CONSTRAINT uk_data_subject_request_tracking_code UNIQUE (tracking_code),
    CONSTRAINT fk_data_subject_request_handled_by FOREIGN KEY (handled_by_id) REFERENCES user_account (id)
);

CREATE INDEX ix_data_subject_request_due ON data_subject_request (status, due_on);
CREATE INDEX ix_data_subject_request_requester ON data_subject_request (requester_email_hash);

CREATE TABLE retention_policy (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    version         BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    data_category   VARCHAR(30)  NOT NULL,
    retention_days  INT          NOT NULL,
    action          VARCHAR(20)  NOT NULL,
    CONSTRAINT pk_retention_policy PRIMARY KEY (id),
    CONSTRAINT uk_retention_policy_category UNIQUE (data_category),
    CONSTRAINT ck_retention_policy_days CHECK (retention_days > 0)
);

-- Valores iniciales razonables, editables desde el panel. Validar con abogado antes de producción.
INSERT INTO retention_policy (version, created_at, updated_at, data_category, retention_days, action) VALUES
    (0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'PROSPECTS',             365,  'DELETE'),
    (0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'INQUIRIES',             730,  'ANONYMIZE'),
    (0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'APPOINTMENTS',          365,  'DELETE'),
    (0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'EVENT_REGISTRATIONS',   365,  'DELETE'),
    (0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'DATA_SUBJECT_REQUESTS', 1825, 'ANONYMIZE'),
    (0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'CONSENT_RECORDS',       1825, 'ANONYMIZE'),
    (0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'STUDENTS',              365,  'DELETE'),
    (0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'AUDIT_LOG',             1825, 'DELETE');

CREATE TABLE security_incident (
    id                          BIGINT         NOT NULL AUTO_INCREMENT,
    version                     BIGINT         NOT NULL,
    created_at                  DATETIME(6)    NOT NULL,
    updated_at                  DATETIME(6)    NOT NULL,
    title                       VARCHAR(200)   NOT NULL,
    description                 LONGTEXT,
    detected_at                 DATETIME(6)    NOT NULL,
    severity                    VARCHAR(20)    NOT NULL,
    affected_data               VARCHAR(1000),
    affected_subjects_estimate  INT,
    status                      VARCHAR(20)    NOT NULL,
    contained_at                DATETIME(6),
    closed_at                   DATETIME(6),
    authority_notified_at       DATETIME(6),
    subjects_notified_at        DATETIME(6),
    actions_taken               LONGTEXT,
    reported_by_id              BIGINT,
    CONSTRAINT pk_security_incident PRIMARY KEY (id),
    CONSTRAINT fk_security_incident_reported_by FOREIGN KEY (reported_by_id) REFERENCES user_account (id)
);

CREATE TABLE student (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    version              BIGINT         NOT NULL,
    created_at           DATETIME(6)    NOT NULL,
    updated_at           DATETIME(6)    NOT NULL,
    full_name            VARCHAR(1024)  NOT NULL,
    course_id            BIGINT         NOT NULL,
    guardian_email       VARCHAR(1024),
    guardian_email_hash  VARCHAR(64),
    active               BOOLEAN        NOT NULL,
    CONSTRAINT pk_student PRIMARY KEY (id),
    CONSTRAINT fk_student_course FOREIGN KEY (course_id) REFERENCES course (id)
);

CREATE INDEX ix_student_course ON student (course_id, active);
CREATE INDEX ix_student_guardian ON student (guardian_email_hash);

-- Al borrar un estudiante (retención) se van sus autorizaciones y etiquetas.
CREATE TABLE image_consent (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    version           BIGINT         NOT NULL,
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    student_id        BIGINT         NOT NULL,
    channel           VARCHAR(20)    NOT NULL,
    granted_by_name   VARCHAR(1024),
    method            VARCHAR(20)    NOT NULL,
    evidence_file_id  BIGINT,
    legal_text_id     BIGINT         NOT NULL,
    recorded_by_id    BIGINT,
    granted_at        DATETIME(6)    NOT NULL,
    revoked_at        DATETIME(6),
    revocation_note   VARCHAR(500),
    CONSTRAINT pk_image_consent PRIMARY KEY (id),
    CONSTRAINT fk_image_consent_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE,
    CONSTRAINT fk_image_consent_evidence FOREIGN KEY (evidence_file_id) REFERENCES stored_file (id),
    CONSTRAINT fk_image_consent_legal_text FOREIGN KEY (legal_text_id) REFERENCES legal_text (id),
    CONSTRAINT fk_image_consent_recorded_by FOREIGN KEY (recorded_by_id) REFERENCES user_account (id)
);

CREATE INDEX ix_image_consent_student ON image_consent (student_id, channel);

CREATE TABLE student_appearance (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    version       BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    student_id    BIGINT       NOT NULL,
    asset_id      BIGINT       NOT NULL,
    tagged_by_id  BIGINT,
    CONSTRAINT pk_student_appearance PRIMARY KEY (id),
    CONSTRAINT uk_student_appearance UNIQUE (student_id, asset_id),
    CONSTRAINT fk_student_appearance_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE,
    CONSTRAINT fk_student_appearance_asset FOREIGN KEY (asset_id) REFERENCES media_asset (id) ON DELETE CASCADE,
    CONSTRAINT fk_student_appearance_tagged_by FOREIGN KEY (tagged_by_id) REFERENCES user_account (id)
);
