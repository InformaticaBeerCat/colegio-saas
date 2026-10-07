-- Fase 8 — Analítica respetuosa de la privacidad (REP-01).
-- Solo agregados por día: ni IP, ni cookies, ni identificadores de visitante.

CREATE TABLE page_view_daily (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    view_date  DATE          NOT NULL,
    path       VARCHAR(300)  NOT NULL,
    views      INT           NOT NULL,
    CONSTRAINT pk_page_view_daily PRIMARY KEY (id),
    CONSTRAINT uk_page_view_daily UNIQUE (view_date, path)
);

-- Sitio de origen (solo el dominio) de quienes llegan desde afuera: buscadores, redes, campañas.
CREATE TABLE referrer_daily (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    view_date  DATE          NOT NULL,
    source     VARCHAR(100)  NOT NULL,
    visits     INT           NOT NULL,
    CONSTRAINT pk_referrer_daily PRIMARY KEY (id),
    CONSTRAINT uk_referrer_daily UNIQUE (view_date, source)
);
