-- Fase 1.1 — Núcleo: perfil del colegio, módulos activos, usuarios con roles y registro de auditoría.
-- Una instalación = un colegio: no hay columna de colegio en las tablas.
-- Dialecto MySQL 8.4+; también corre en H2 con MODE=MySQL (tests).
-- Convención: los enums se guardan como VARCHAR (agregar un valor no exige ALTER TABLE).

-- Una sola fila (id = 1), creada por el asistente de primer arranque.
CREATE TABLE school (
    id                  BIGINT       NOT NULL,
    version             BIGINT       NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    name                VARCHAR(150) NOT NULL,
    rbd                 VARCHAR(10),
    dependency          VARCHAR(30),
    plan                VARCHAR(20)  NOT NULL,
    time_zone           VARCHAR(40)  NOT NULL,
    street              VARCHAR(200),
    commune             VARCHAR(80),
    region              VARCHAR(80),
    latitude            DOUBLE,
    longitude           DOUBLE,
    phone               VARCHAR(30),
    contact_email       VARCHAR(254),
    setup_completed_at  DATETIME(6),
    CONSTRAINT pk_school PRIMARY KEY (id),
    CONSTRAINT ck_school_single_row CHECK (id = 1)
);

CREATE TABLE school_feature (
    school_id  BIGINT      NOT NULL,
    feature    VARCHAR(30) NOT NULL,
    CONSTRAINT pk_school_feature PRIMARY KEY (school_id, feature),
    CONSTRAINT fk_school_feature_school FOREIGN KEY (school_id) REFERENCES school (id) ON DELETE CASCADE
);

CREATE TABLE user_account (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    version         BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    email           VARCHAR(254) NOT NULL,
    name            VARCHAR(150) NOT NULL,
    password_hash   VARCHAR(100),
    status          VARCHAR(20)  NOT NULL,
    deactivated_at  DATETIME(6),
    last_login_at   DATETIME(6),
    CONSTRAINT pk_user_account PRIMARY KEY (id),
    CONSTRAINT uk_user_account_email UNIQUE (email)
);

CREATE TABLE user_account_role (
    user_account_id  BIGINT      NOT NULL,
    role             VARCHAR(30) NOT NULL,
    CONSTRAINT pk_user_account_role PRIMARY KEY (user_account_id, role),
    CONSTRAINT fk_user_account_role_user FOREIGN KEY (user_account_id) REFERENCES user_account (id) ON DELETE CASCADE
);

CREATE TABLE audit_log (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    occurred_at  DATETIME(6)   NOT NULL,
    actor_type   VARCHAR(30)   NOT NULL,
    actor_id     BIGINT,
    actor_name   VARCHAR(150),
    action       VARCHAR(30)   NOT NULL,
    entity_type  VARCHAR(60),
    entity_id    VARCHAR(40),
    details      VARCHAR(2000),
    ip_address   VARCHAR(45),
    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);

CREATE INDEX ix_audit_log_occurred_at ON audit_log (occurred_at);
CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id);
