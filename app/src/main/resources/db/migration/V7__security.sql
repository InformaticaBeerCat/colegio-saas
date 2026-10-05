-- Fase 2 — Seguridad: segundo factor TOTP, códigos de recuperación, bloqueo por intentos
-- fallidos y enlaces de un solo uso (invitación, restablecer contraseña).

ALTER TABLE user_account ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE user_account ADD COLUMN locked_until DATETIME(6);
-- Secreto TOTP cifrado (EncryptedStringConverter).
ALTER TABLE user_account ADD COLUMN mfa_secret VARCHAR(256);
ALTER TABLE user_account ADD COLUMN mfa_enabled_at DATETIME(6);
ALTER TABLE user_account ADD COLUMN mfa_last_used_step BIGINT;

CREATE TABLE user_account_recovery_code (
    user_account_id  BIGINT       NOT NULL,
    code_hash        VARCHAR(64)  NOT NULL,
    CONSTRAINT pk_user_account_recovery_code PRIMARY KEY (user_account_id, code_hash),
    CONSTRAINT fk_user_account_recovery_code_user FOREIGN KEY (user_account_id) REFERENCES user_account (id) ON DELETE CASCADE
);

CREATE TABLE account_token (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    version          BIGINT       NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    user_account_id  BIGINT       NOT NULL,
    purpose          VARCHAR(20)  NOT NULL,
    token_hash       VARCHAR(64)  NOT NULL,
    expires_at       DATETIME(6)  NOT NULL,
    used_at          DATETIME(6),
    CONSTRAINT pk_account_token PRIMARY KEY (id),
    CONSTRAINT uk_account_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_account_token_user FOREIGN KEY (user_account_id) REFERENCES user_account (id) ON DELETE CASCADE
);
