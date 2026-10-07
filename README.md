# Colegio SaaS

Sitio web público configurable para colegios chilenos: identidad de marca por colegio, módulos por plan,
y cumplimiento de publicación (Reglamento Interno, SAE) y de datos personales (Ley 21.719).

Como Nextcloud, **cada colegio corre su propia instalación**: el mismo código se despliega una vez por colegio
y se personaliza desde el panel, sin tocar el código.

- [Plan de desarrollo](docs/PLAN.md): decisiones de arquitectura, convenciones y fases.
- [Modelo de dominio](docs/domain-model.md): entidades y relaciones por iteración.

## Estructura

```
docs/   documentación del proyecto
app/    aplicación Spring Boot 4 (Java 21, Maven)
```

## Desarrollo

Requisitos: JDK 21+. Docker es opcional (solo para usar MySQL en contenedor).

```bash
cd app
./mvnw test                 # tests sobre H2 en modo MySQL
./mvnw spring-boot:run      # arranca con H2 en memoria
```

Con MySQL:

```bash
cd app
docker compose up -d                                         # MySQL en el puerto 3307
SPRING_PROFILES_ACTIVE=mysql ./mvnw spring-boot:run
```

Para un MySQL propio, definir `DB_URL`, `DB_USER` y `DB_PASSWORD`.

Presupuesto de Core Web Vitals (lo mismo que corre en CI), con el sitio levantado y Node 22:

```bash
cd app
APP_SETUP_TOKEN=ci java -jar target/colegio-saas-*.jar &
cd perf && npm install && npx playwright install chromium
APP_SETUP_TOKEN=ci node web-vitals.mjs http://localhost:8080
```

## Primer arranque

1. Arranca la app. Mientras no esté instalada, el log muestra un aviso con un **token de instalación**:
   ```
    Instalación pendiente. Abre http://localhost:8080/setup e ingresa este token:
      8JSGTR5MFNVT
   ```
   (Con H2 en memoria la instalación se pierde al detener la app; con MySQL queda guardada.)
2. Abre `http://localhost:8080/setup`, ingresa el token, los datos del colegio y tu cuenta.
3. Ingresa en `http://localhost:8080/admin/login`. La verificación en dos pasos es opcional: actívala desde
   "Mi cuenta" con una app autenticadora (Google Authenticator, 1Password…).
4. El panel ofrece el **asistente inicial**: elige un tema y crea la portada y las páginas institucionales.
   Después se ajustan colores y fuentes en "Diseño" (con verificación de contraste AA), las páginas en "Páginas"
   y el menú en "Menús"; todo se revisa en la vista previa antes de publicar.

Los correos (invitaciones, recuperar contraseña) se escriben en el log. Para verlos como correos reales, levanta
Mailpit con `docker compose up -d` y arranca con `SPRING_MAIL_HOST=localhost SPRING_MAIL_PORT=1025`;
quedan en `http://localhost:8025`.

| Variable | Para qué |
|---|---|
| `APP_BASE_URL` | URL pública, para los enlaces de los correos |
| `APP_SETUP_TOKEN` | Token de instalación fijo (instalaciones automatizadas) |
| `APP_FIELD_KEY`, `APP_INDEX_KEY` | Llaves de cifrado de datos personales (`openssl rand -base64 32`) |
| `APP_SECURE_COOKIES` | `true` en producción (HTTPS) |
| `APP_STORAGE_TYPE` | `local` (por defecto) o `s3` para S3/MinIO |
| `APP_STORAGE_DIR` | Carpeta de los archivos con `local` (por defecto `./data/files`; en producción, un volumen persistente) |
| `APP_S3_ENDPOINT`, `APP_S3_BUCKET`, `APP_S3_ACCESS_KEY`, `APP_S3_SECRET_KEY` | Conexión S3; con el MinIO de `compose.yaml`: `http://localhost:9000`, `colegio`, `minio`, `minio-secreto` |
| `APP_CLAMD_HOST`, `APP_CLAMD_PORT` | Antivirus ClamAV para archivos subidos (con `compose.yaml`: `localhost`, `3310`) |
| `APP_ENFORCE_MFA` | `true` para exigir verificación en dos pasos a administradores y gestor de consentimientos |
| `APP_PRIVACY_RESPONSE_DAYS` | Días corridos para responder una solicitud de derechos (por defecto `30`) |
| `APP_PRIVACY_INCIDENT_HOURS` | Meta en horas para notificar una brecha a la Agencia (por defecto `72`) |
| `APP_RETENTION_ENABLED` | `false` para apagar el borrado automático por plazos de conservación |
| `APP_HOLIDAYS_URL` | Fuente de feriados de Chile para la agenda (por defecto la API de gob.cl; `{year}` = año) |
| `APP_AGENDA_MIN_NOTICE`, `APP_AGENDA_HORIZON_DAYS` | Anticipación mínima para reservar (`2h`) y días que se ofrecen (`30`) |
| `APP_ANALYTICS_ENABLED` | `false` para apagar el conteo anónimo de visitas |
| `APP_ANALYTICS_SCRIPT_URL` | Script de analítica externa (Plausible, Matomo…); solo se carga con consentimiento |
| `APP_FORMS_MAX_PER_IP` | Envíos de un mismo formulario público por IP cada 10 minutos (por defecto `5`) |
