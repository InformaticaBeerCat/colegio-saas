-- Fase 1.3 — Medios: archivos almacenados, biblioteca (carpetas, etiquetas), revisión de
-- autorización de imagen, difuminado, retiro y álbumes. Agrega logo y favicon al sitio.
-- Las llaves foráneas usadas en un CHECK no llevan ON DELETE (restricción de MySQL).

CREATE TABLE stored_file (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    version        BIGINT        NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    storage_key    VARCHAR(500)  NOT NULL,
    original_name  VARCHAR(255),
    content_type   VARCHAR(100)  NOT NULL,
    size_bytes     BIGINT        NOT NULL,
    sha256         VARCHAR(64)   NOT NULL,
    width          INT,
    height         INT,
    variants       JSON          NOT NULL,
    scan_status    VARCHAR(20)   NOT NULL,
    CONSTRAINT pk_stored_file PRIMARY KEY (id),
    CONSTRAINT uk_stored_file_storage_key UNIQUE (storage_key)
);

CREATE INDEX ix_stored_file_sha256 ON stored_file (sha256);

CREATE TABLE media_folder (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    version     BIGINT        NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    name        VARCHAR(100)  NOT NULL,
    parent_id   BIGINT,
    CONSTRAINT pk_media_folder PRIMARY KEY (id),
    CONSTRAINT fk_media_folder_parent FOREIGN KEY (parent_id) REFERENCES media_folder (id)
);

CREATE TABLE media_tag (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    version     BIGINT        NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    name        VARCHAR(60)   NOT NULL,
    CONSTRAINT pk_media_tag PRIMARY KEY (id),
    CONSTRAINT uk_media_tag_name UNIQUE (name)
);

CREATE TABLE media_asset (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    version           BIGINT        NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    kind              VARCHAR(20)   NOT NULL,
    file_id           BIGINT,
    embed_url         VARCHAR(500),
    alt_text          VARCHAR(300),
    caption           VARCHAR(500),
    credits           VARCHAR(200),
    folder_id         BIGINT,
    taken_on          DATE,
    uploaded_by_id    BIGINT,
    review_status     VARCHAR(20)   NOT NULL,
    reviewed_by_id    BIGINT,
    reviewed_at       DATETIME(6),
    review_note       VARCHAR(500),
    blur_regions      JSON          NOT NULL,
    blurred_file_id   BIGINT,
    withdrawn_at      DATETIME(6),
    withdrawal_reason VARCHAR(300),
    CONSTRAINT pk_media_asset PRIMARY KEY (id),
    CONSTRAINT fk_media_asset_file FOREIGN KEY (file_id) REFERENCES stored_file (id),
    CONSTRAINT fk_media_asset_folder FOREIGN KEY (folder_id) REFERENCES media_folder (id) ON DELETE SET NULL,
    CONSTRAINT fk_media_asset_uploaded_by FOREIGN KEY (uploaded_by_id) REFERENCES user_account (id),
    CONSTRAINT fk_media_asset_reviewed_by FOREIGN KEY (reviewed_by_id) REFERENCES user_account (id),
    CONSTRAINT fk_media_asset_blurred_file FOREIGN KEY (blurred_file_id) REFERENCES stored_file (id),
    -- Video embebido: solo URL. Todo lo demás: solo archivo.
    CONSTRAINT ck_media_asset_source CHECK (
        (kind = 'EMBEDDED_VIDEO' AND embed_url IS NOT NULL AND file_id IS NULL)
        OR (kind <> 'EMBEDDED_VIDEO' AND file_id IS NOT NULL AND embed_url IS NULL)
    )
);

CREATE INDEX ix_media_asset_review_status ON media_asset (review_status, created_at);

CREATE TABLE media_asset_tag (
    media_asset_id  BIGINT NOT NULL,
    media_tag_id    BIGINT NOT NULL,
    CONSTRAINT pk_media_asset_tag PRIMARY KEY (media_asset_id, media_tag_id),
    CONSTRAINT fk_media_asset_tag_asset FOREIGN KEY (media_asset_id) REFERENCES media_asset (id) ON DELETE CASCADE,
    CONSTRAINT fk_media_asset_tag_tag FOREIGN KEY (media_tag_id) REFERENCES media_tag (id) ON DELETE CASCADE
);

CREATE TABLE album (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    version           BIGINT        NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    slug              VARCHAR(120)  NOT NULL,
    title             VARCHAR(200)  NOT NULL,
    description       VARCHAR(1000),
    taken_on          DATE,
    visibility        VARCHAR(20)   NOT NULL,
    course_id         BIGINT,
    status            VARCHAR(20)   NOT NULL,
    published_at      DATETIME(6),
    cover_id          BIGINT,
    download_allowed  BOOLEAN       NOT NULL,
    CONSTRAINT pk_album PRIMARY KEY (id),
    CONSTRAINT uk_album_slug UNIQUE (slug),
    CONSTRAINT fk_album_course FOREIGN KEY (course_id) REFERENCES course (id),
    CONSTRAINT fk_album_cover FOREIGN KEY (cover_id) REFERENCES media_asset (id),
    -- Visibilidad por curso exige curso; las demás no admiten curso.
    CONSTRAINT ck_album_course CHECK (
        (visibility = 'COURSE' AND course_id IS NOT NULL)
        OR (visibility <> 'COURSE' AND course_id IS NULL)
    )
);

-- asset_id sin ON DELETE: un medio en uso no se borra; el camino normal es retirarlo (MED-09).
CREATE TABLE album_item (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    version     BIGINT        NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    album_id    BIGINT        NOT NULL,
    asset_id    BIGINT        NOT NULL,
    sort_order  INT           NOT NULL,
    CONSTRAINT pk_album_item PRIMARY KEY (id),
    CONSTRAINT uk_album_item_album_asset UNIQUE (album_id, asset_id),
    CONSTRAINT fk_album_item_album FOREIGN KEY (album_id) REFERENCES album (id) ON DELETE CASCADE,
    CONSTRAINT fk_album_item_asset FOREIGN KEY (asset_id) REFERENCES media_asset (id)
);

ALTER TABLE site_settings ADD COLUMN logo_id BIGINT;
ALTER TABLE site_settings ADD COLUMN favicon_id BIGINT;
ALTER TABLE site_settings ADD CONSTRAINT fk_site_settings_logo FOREIGN KEY (logo_id) REFERENCES media_asset (id);
ALTER TABLE site_settings ADD CONSTRAINT fk_site_settings_favicon FOREIGN KEY (favicon_id) REFERENCES media_asset (id);
