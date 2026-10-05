-- Fase 1.1 — Núcleo: colegios (tenants), módulos contratados, operadores de plataforma,
-- usuarios con roles y registro de auditoría.
-- Dialecto MySQL 8.4+; también corre en H2 con MODE=MySQL (tests).
-- Convención: los enums se guardan como VARCHAR (agregar un valor no exige ALTER TABLE).

CREATE TABLE school (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    version        BIGINT       NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    name           VARCHAR(150) NOT NULL,
    rbd            VARCHAR(10),
    dependency     VARCHAR(30)  NOT NULL,
    subdomain      VARCHAR(63)  NOT NULL,
    custom_domain  VARCHAR(253),
    status         VARCHAR(20)  NOT NULL,
    plan           VARCHAR(20)  NOT NULL,
    time_zone      VARCHAR(40)  NOT NULL,
    street         VARCHAR(200),
    commune        VARCHAR(80),
    region         VARCHAR(80),
    latitude       DOUBLE,
    longitude      DOUBLE,
    phone          VARCHAR(30),
    contact_email  VARCHAR(254),
    CONSTRAINT pk_school PRIMARY KEY (id),
    CONSTRAINT uk_school_rbd UNIQUE (rbd),
    CONSTRAINT uk_school_subdomain UNIQUE (subdomain),
    CONSTRAINT uk_school_custom_domain UNIQUE (custom_domain)
);

CREATE TABLE school_feature (
    school_id  BIGINT      NOT NULL,
    feature    VARCHAR(30) NOT NULL,
    CONSTRAINT pk_school_feature PRIMARY KEY (school_id, feature),
    CONSTRAINT fk_school_feature_school FOREIGN KEY (school_id) REFERENCES school (id) ON DELETE CASCADE
);

CREATE TABLE platform_operator (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    version        BIGINT       NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    email          VARCHAR(254) NOT NULL,
    name           VARCHAR(150) NOT NULL,
    password_hash  VARCHAR(100),
    active         BOOLEAN      NOT NULL,
    last_login_at  DATETIME(6),
    CONSTRAINT pk_platform_operator PRIMARY KEY (id),
    CONSTRAINT uk_platform_operator_email UNIQUE (email)
);

CREATE TABLE user_account (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    version         BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    school_id       BIGINT       NOT NULL,
    email           VARCHAR(254) NOT NULL,
    name            VARCHAR(150) NOT NULL,
    password_hash   VARCHAR(100),
    status          VARCHAR(20)  NOT NULL,
    deactivated_at  DATETIME(6),
    last_login_at   DATETIME(6),
    CONSTRAINT pk_user_account PRIMARY KEY (id),
    CONSTRAINT uk_user_account_school_email UNIQUE (school_id, email),
    CONSTRAINT fk_user_account_school FOREIGN KEY (school_id) REFERENCES school (id)
);

CREATE TABLE user_account_role (
    user_account_id  BIGINT      NOT NULL,
    role             VARCHAR(30) NOT NULL,
    CONSTRAINT pk_user_account_role PRIMARY KEY (user_account_id, role),
    CONSTRAINT fk_user_account_role_user FOREIGN KEY (user_account_id) REFERENCES user_account (id) ON DELETE CASCADE
);

CREATE TABLE audit_log (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    school_id    BIGINT        NOT NULL,
    occurred_at  DATETIME(6)   NOT NULL,
    actor_type   VARCHAR(30)   NOT NULL,
    actor_id     BIGINT,
    actor_name   VARCHAR(150),
    action       VARCHAR(30)   NOT NULL,
    entity_type  VARCHAR(60),
    entity_id    VARCHAR(40),
    details      VARCHAR(2000),
    ip_address   VARCHAR(45),
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_school FOREIGN KEY (school_id) REFERENCES school (id)
);

CREATE INDEX ix_audit_log_occurred_at ON audit_log (school_id, occurred_at);
CREATE INDEX ix_audit_log_entity ON audit_log (school_id, entity_type, entity_id);
