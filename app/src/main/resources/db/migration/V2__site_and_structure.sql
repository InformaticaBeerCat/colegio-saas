-- Fase 1.2 — Sitio (diseño, páginas por bloques, menú, accesos rápidos, alerta global)
-- y estructura del colegio (niveles y cursos).
-- Las columnas JSON guardan records Java (SiteDesign, List<Block>…); Hibernate las serializa con Jackson.

-- Una sola fila (id = 1). Logo y favicon se agregan en la 1.3, junto con los medios.
CREATE TABLE site_settings (
    id                BIGINT        NOT NULL,
    version           BIGINT        NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    published_design  JSON          NOT NULL,
    draft_design      JSON          NOT NULL,
    social_links      JSON          NOT NULL,
    whatsapp_number   VARCHAR(16),
    footer_text       VARCHAR(500),
    CONSTRAINT pk_site_settings PRIMARY KEY (id),
    CONSTRAINT ck_site_settings_single_row CHECK (id = 1)
);

CREATE TABLE page (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    version           BIGINT        NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    slug              VARCHAR(120)  NOT NULL,
    title             VARCHAR(200)  NOT NULL,
    kind              VARCHAR(20)   NOT NULL,
    section           VARCHAR(20)   NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    draft_blocks      JSON          NOT NULL,
    published_blocks  JSON,
    published_at      DATETIME(6),
    meta_title        VARCHAR(120),
    meta_description  VARCHAR(300),
    noindex           BOOLEAN       NOT NULL,
    CONSTRAINT pk_page PRIMARY KEY (id),
    CONSTRAINT uk_page_slug UNIQUE (slug)
);

CREATE INDEX ix_page_kind ON page (kind);

-- page_id sin ON DELETE: MySQL no permite acciones referenciales en columnas usadas por un CHECK,
-- y además así no se borra una página que todavía está en el menú.
CREATE TABLE menu_item (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    version     BIGINT        NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    menu        VARCHAR(20)   NOT NULL,
    label       VARCHAR(80)   NOT NULL,
    page_id     BIGINT,
    url         VARCHAR(500),
    parent_id   BIGINT,
    sort_order  INT           NOT NULL,
    CONSTRAINT pk_menu_item PRIMARY KEY (id),
    CONSTRAINT fk_menu_item_page FOREIGN KEY (page_id) REFERENCES page (id),
    CONSTRAINT fk_menu_item_parent FOREIGN KEY (parent_id) REFERENCES menu_item (id) ON DELETE CASCADE,
    CONSTRAINT ck_menu_item_target CHECK (
        (page_id IS NOT NULL AND url IS NULL) OR (page_id IS NULL AND url IS NOT NULL)
    )
);

CREATE INDEX ix_menu_item_menu ON menu_item (menu, sort_order);

CREATE TABLE quick_link (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    version     BIGINT        NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    label       VARCHAR(60)   NOT NULL,
    url         VARCHAR(500)  NOT NULL,
    icon        VARCHAR(40),
    sort_order  INT           NOT NULL,
    active      BOOLEAN       NOT NULL,
    CONSTRAINT pk_quick_link PRIMARY KEY (id)
);

CREATE TABLE site_alert (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    version     BIGINT        NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    message     VARCHAR(300)  NOT NULL,
    severity    VARCHAR(20)   NOT NULL,
    link_url    VARCHAR(500),
    link_label  VARCHAR(60),
    active      BOOLEAN       NOT NULL,
    starts_at   DATETIME(6),
    ends_at     DATETIME(6),
    CONSTRAINT pk_site_alert PRIMARY KEY (id)
);

CREATE TABLE grade_level (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    version     BIGINT        NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    name        VARCHAR(60)   NOT NULL,
    stage       VARCHAR(20)   NOT NULL,
    track       VARCHAR(30),
    sort_order  INT           NOT NULL,
    CONSTRAINT pk_grade_level PRIMARY KEY (id),
    CONSTRAINT uk_grade_level_name UNIQUE (name)
);

-- grade_level_id sin ON DELETE: no se borra un nivel que todavía tiene cursos.
CREATE TABLE course (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    version         BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    grade_level_id  BIGINT       NOT NULL,
    section         VARCHAR(5)   NOT NULL,
    academic_year   INT          NOT NULL,
    CONSTRAINT pk_course PRIMARY KEY (id),
    CONSTRAINT uk_course_level_section_year UNIQUE (grade_level_id, section, academic_year),
    CONSTRAINT fk_course_grade_level FOREIGN KEY (grade_level_id) REFERENCES grade_level (id)
);

CREATE INDEX ix_course_academic_year ON course (academic_year);
