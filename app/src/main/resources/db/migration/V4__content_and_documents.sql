-- Fase 1.4 — Contenido: noticias, comunicados, calendario, preguntas frecuentes, talleres,
-- útiles/uniforme/menú y documentos institucionales con versiones.
-- Textos largos (HTML) en LONGTEXT, mapeados con @Lob.

CREATE TABLE news_category (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    version     BIGINT        NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    name        VARCHAR(80)   NOT NULL,
    slug        VARCHAR(80)   NOT NULL,
    CONSTRAINT pk_news_category PRIMARY KEY (id),
    CONSTRAINT uk_news_category_name UNIQUE (name),
    CONSTRAINT uk_news_category_slug UNIQUE (slug)
);

CREATE TABLE news_article (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    version            BIGINT        NOT NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    slug               VARCHAR(120)  NOT NULL,
    title              VARCHAR(200)  NOT NULL,
    summary            VARCHAR(500),
    body               LONGTEXT      NOT NULL,
    featured_image_id  BIGINT,
    video_id           BIGINT,
    album_id           BIGINT,
    category_id        BIGINT,
    section            VARCHAR(20)   NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    publish_at         DATETIME(6),
    published_at       DATETIME(6),
    author_id          BIGINT        NOT NULL,
    reviewer_id        BIGINT,
    review_note        VARCHAR(500),
    meta_title         VARCHAR(120),
    meta_description   VARCHAR(300),
    CONSTRAINT pk_news_article PRIMARY KEY (id),
    CONSTRAINT uk_news_article_slug UNIQUE (slug),
    CONSTRAINT fk_news_article_featured_image FOREIGN KEY (featured_image_id) REFERENCES media_asset (id),
    CONSTRAINT fk_news_article_video FOREIGN KEY (video_id) REFERENCES media_asset (id),
    CONSTRAINT fk_news_article_album FOREIGN KEY (album_id) REFERENCES album (id) ON DELETE SET NULL,
    CONSTRAINT fk_news_article_category FOREIGN KEY (category_id) REFERENCES news_category (id) ON DELETE SET NULL,
    CONSTRAINT fk_news_article_author FOREIGN KEY (author_id) REFERENCES user_account (id),
    CONSTRAINT fk_news_article_reviewer FOREIGN KEY (reviewer_id) REFERENCES user_account (id)
);

CREATE INDEX ix_news_article_status ON news_article (status, publish_at);

CREATE TABLE news_article_grade_level (
    news_article_id  BIGINT NOT NULL,
    grade_level_id   BIGINT NOT NULL,
    CONSTRAINT pk_news_article_grade_level PRIMARY KEY (news_article_id, grade_level_id),
    CONSTRAINT fk_news_article_grade_level_article FOREIGN KEY (news_article_id) REFERENCES news_article (id) ON DELETE CASCADE,
    CONSTRAINT fk_news_article_grade_level_level FOREIGN KEY (grade_level_id) REFERENCES grade_level (id) ON DELETE CASCADE
);

CREATE TABLE announcement (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    version         BIGINT        NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    title           VARCHAR(200)  NOT NULL,
    body            LONGTEXT      NOT NULL,
    audience        VARCHAR(20)   NOT NULL,
    community_only  BOOLEAN       NOT NULL,
    attachment_id   BIGINT,
    author_id       BIGINT        NOT NULL,
    published_at    DATETIME(6),
    CONSTRAINT pk_announcement PRIMARY KEY (id),
    CONSTRAINT fk_announcement_attachment FOREIGN KEY (attachment_id) REFERENCES stored_file (id),
    CONSTRAINT fk_announcement_author FOREIGN KEY (author_id) REFERENCES user_account (id)
);

CREATE INDEX ix_announcement_published_at ON announcement (published_at);

CREATE TABLE announcement_grade_level (
    announcement_id  BIGINT NOT NULL,
    grade_level_id   BIGINT NOT NULL,
    CONSTRAINT pk_announcement_grade_level PRIMARY KEY (announcement_id, grade_level_id),
    CONSTRAINT fk_announcement_grade_level_announcement FOREIGN KEY (announcement_id) REFERENCES announcement (id) ON DELETE CASCADE,
    CONSTRAINT fk_announcement_grade_level_level FOREIGN KEY (grade_level_id) REFERENCES grade_level (id) ON DELETE CASCADE
);

CREATE TABLE announcement_course (
    announcement_id  BIGINT NOT NULL,
    course_id        BIGINT NOT NULL,
    CONSTRAINT pk_announcement_course PRIMARY KEY (announcement_id, course_id),
    CONSTRAINT fk_announcement_course_announcement FOREIGN KEY (announcement_id) REFERENCES announcement (id) ON DELETE CASCADE,
    CONSTRAINT fk_announcement_course_course FOREIGN KEY (course_id) REFERENCES course (id) ON DELETE CASCADE
);

-- "event" es palabra clave en MySQL: la tabla se llama calendar_event.
CREATE TABLE calendar_event (
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    version                 BIGINT         NOT NULL,
    created_at              DATETIME(6)    NOT NULL,
    updated_at              DATETIME(6)    NOT NULL,
    slug                    VARCHAR(120)   NOT NULL,
    title                   VARCHAR(200)   NOT NULL,
    description             VARCHAR(2000),
    kind                    VARCHAR(20)    NOT NULL,
    starts_at               DATETIME(6)    NOT NULL,
    ends_at                 DATETIME(6)    NOT NULL,
    all_day                 BOOLEAN        NOT NULL,
    location                VARCHAR(200),
    album_id                BIGINT,
    published_at            DATETIME(6),
    registration_enabled    BOOLEAN        NOT NULL,
    capacity                INT,
    waitlist_enabled        BOOLEAN        NOT NULL,
    registration_closes_at  DATETIME(6),
    CONSTRAINT pk_calendar_event PRIMARY KEY (id),
    CONSTRAINT uk_calendar_event_slug UNIQUE (slug),
    CONSTRAINT fk_calendar_event_album FOREIGN KEY (album_id) REFERENCES album (id) ON DELETE SET NULL,
    CONSTRAINT ck_calendar_event_dates CHECK (ends_at >= starts_at),
    CONSTRAINT ck_calendar_event_capacity CHECK (capacity IS NULL OR capacity > 0)
);

CREATE INDEX ix_calendar_event_starts_at ON calendar_event (starts_at);

CREATE TABLE calendar_event_grade_level (
    calendar_event_id  BIGINT NOT NULL,
    grade_level_id     BIGINT NOT NULL,
    CONSTRAINT pk_calendar_event_grade_level PRIMARY KEY (calendar_event_id, grade_level_id),
    CONSTRAINT fk_calendar_event_grade_level_event FOREIGN KEY (calendar_event_id) REFERENCES calendar_event (id) ON DELETE CASCADE,
    CONSTRAINT fk_calendar_event_grade_level_level FOREIGN KEY (grade_level_id) REFERENCES grade_level (id) ON DELETE CASCADE
);

CREATE TABLE calendar_event_course (
    calendar_event_id  BIGINT NOT NULL,
    course_id          BIGINT NOT NULL,
    CONSTRAINT pk_calendar_event_course PRIMARY KEY (calendar_event_id, course_id),
    CONSTRAINT fk_calendar_event_course_event FOREIGN KEY (calendar_event_id) REFERENCES calendar_event (id) ON DELETE CASCADE,
    CONSTRAINT fk_calendar_event_course_course FOREIGN KEY (course_id) REFERENCES course (id) ON DELETE CASCADE
);

CREATE TABLE faq_category (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    version     BIGINT        NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    name        VARCHAR(80)   NOT NULL,
    sort_order  INT           NOT NULL,
    CONSTRAINT pk_faq_category PRIMARY KEY (id),
    CONSTRAINT uk_faq_category_name UNIQUE (name)
);

CREATE TABLE faq_entry (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    version      BIGINT         NOT NULL,
    created_at   DATETIME(6)    NOT NULL,
    updated_at   DATETIME(6)    NOT NULL,
    category_id  BIGINT         NOT NULL,
    question     VARCHAR(300)   NOT NULL,
    answer       VARCHAR(4000)  NOT NULL,
    sort_order   INT            NOT NULL,
    published    BOOLEAN        NOT NULL,
    CONSTRAINT pk_faq_entry PRIMARY KEY (id),
    CONSTRAINT fk_faq_entry_category FOREIGN KEY (category_id) REFERENCES faq_category (id) ON DELETE CASCADE
);

CREATE TABLE workshop (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    version        BIGINT         NOT NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    name           VARCHAR(120)   NOT NULL,
    description    VARCHAR(2000),
    schedule       VARCHAR(200),
    instructor     VARCHAR(120),
    capacity       INT,
    image_id       BIGINT,
    academic_year  INT            NOT NULL,
    active         BOOLEAN        NOT NULL,
    CONSTRAINT pk_workshop PRIMARY KEY (id),
    CONSTRAINT fk_workshop_image FOREIGN KEY (image_id) REFERENCES media_asset (id)
);

CREATE TABLE workshop_grade_level (
    workshop_id     BIGINT NOT NULL,
    grade_level_id  BIGINT NOT NULL,
    CONSTRAINT pk_workshop_grade_level PRIMARY KEY (workshop_id, grade_level_id),
    CONSTRAINT fk_workshop_grade_level_workshop FOREIGN KEY (workshop_id) REFERENCES workshop (id) ON DELETE CASCADE,
    CONSTRAINT fk_workshop_grade_level_level FOREIGN KEY (grade_level_id) REFERENCES grade_level (id) ON DELETE CASCADE
);

CREATE TABLE info_sheet (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    version         BIGINT        NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    kind            VARCHAR(20)   NOT NULL,
    title           VARCHAR(200)  NOT NULL,
    grade_level_id  BIGINT,
    academic_year   INT           NOT NULL,
    content         LONGTEXT,
    file_id         BIGINT,
    valid_from      DATE,
    valid_until     DATE,
    published       BOOLEAN       NOT NULL,
    CONSTRAINT pk_info_sheet PRIMARY KEY (id),
    CONSTRAINT fk_info_sheet_grade_level FOREIGN KEY (grade_level_id) REFERENCES grade_level (id) ON DELETE SET NULL,
    CONSTRAINT fk_info_sheet_file FOREIGN KEY (file_id) REFERENCES stored_file (id)
);

CREATE TABLE institutional_document (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    version      BIGINT         NOT NULL,
    created_at   DATETIME(6)    NOT NULL,
    updated_at   DATETIME(6)    NOT NULL,
    category     VARCHAR(30)    NOT NULL,
    title        VARCHAR(200)   NOT NULL,
    slug         VARCHAR(120)   NOT NULL,
    description  VARCHAR(1000),
    parent_id    BIGINT,
    sort_order   INT            NOT NULL,
    CONSTRAINT pk_institutional_document PRIMARY KEY (id),
    CONSTRAINT uk_institutional_document_slug UNIQUE (slug),
    CONSTRAINT fk_institutional_document_parent FOREIGN KEY (parent_id) REFERENCES institutional_document (id)
);

-- Las versiones son evidencia (DOC-03): un documento con versiones no se borra.
CREATE TABLE document_version (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    version           BIGINT         NOT NULL,
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    document_id       BIGINT         NOT NULL,
    file_id           BIGINT         NOT NULL,
    academic_year     INT,
    school_name       VARCHAR(150)   NOT NULL,
    rbd               VARCHAR(10),
    last_updated_on   DATE           NOT NULL,
    current_version   BOOLEAN        NOT NULL,
    accessible_pdf    BOOLEAN        NOT NULL,
    change_notes      VARCHAR(1000),
    published_by_id   BIGINT,
    CONSTRAINT pk_document_version PRIMARY KEY (id),
    CONSTRAINT fk_document_version_document FOREIGN KEY (document_id) REFERENCES institutional_document (id),
    CONSTRAINT fk_document_version_file FOREIGN KEY (file_id) REFERENCES stored_file (id),
    CONSTRAINT fk_document_version_published_by FOREIGN KEY (published_by_id) REFERENCES user_account (id)
);

CREATE INDEX ix_document_version_current ON document_version (document_id, current_version);
