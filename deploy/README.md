# Despliegue en producción

Un colegio = un servidor (o VM) con Docker y un dominio que apunte a él. Todo corre en contenedores:
el sitio, MySQL, ClamAV, Caddy (HTTPS automático) y el respaldo diario.

## Instalar (OPS-01, OPS-09)

```bash
git clone https://github.com/InformaticaBeerCat/colegio-saas.git && cd colegio-saas/deploy
./instalar.sh colegiosanjose.cl "<licencia del proveedor>"
```

`instalar.sh` genera las llaves de cifrado y las contraseñas en `.env`, descarga la imagen y levanta todo. Al
terminar muestra el token del asistente (`https://DOMINIO/setup`). Después:

- Completar `SMTP_*` y `APP_ALERTS_EMAIL` en `.env` y aplicar con `docker compose up -d`.
- **Guardar una copia de `.env` fuera del servidor.** Sin `APP_FIELD_KEY` los datos personales cifrados no se recuperan.
- Borrar `APP_SETUP_TOKEN` de `.env` una vez instalado.

La aplicación se niega a arrancar si la configuración no es segura (llaves de desarrollo, sin HTTPS, sin licencia
o con una licencia de otro dominio) y lo explica en `docker compose logs app`.

## Licencia (OPS-05)

La emite el proveedor con su llave privada; el sitio solo tiene la pública (`app/src/main/resources/license/proveedor.pub`).
Define el plan, los add-ons, el dominio y el vencimiento. Vencida, hay 30 días de gracia; después quedan solo los
módulos del plan Base (el sitio sigue en línea). Para renovarla: reemplazar `APP_LICENSE` en `.env` y `docker compose up -d`.

Emitir (proveedor):

```bash
java -cp colegio-saas.jar -Dloader.main=cl.colegiosaas.platform.license.LicenseTool \
     org.springframework.boot.loader.launch.PropertiesLauncher \
     issue --key proveedor.key --school "Colegio San José" --domain colegiosanjose.cl \
           --plan COMMUNITY --addons WHATSAPP_SMS --expires 2027-12-31
```

Las llaves del proveedor se generan una sola vez con `... LicenseTool ... keys ./llaves`. La pública reemplaza a
`proveedor.pub` antes de construir la imagen; la privada no entra nunca al repositorio.

## Actualizar (OPS-03, OPS-04)

```bash
./actualizar.sh
```

Respalda, descarga la imagen (`APP_VERSION` en `.env`: `latest` o una versión fija) y reinicia. Las migraciones
de la base corren solas al arrancar. El panel del proveedor (`/admin/platform`) avisa cuando hay versión nueva si
`APP_UPDATES_FEED_URL` apunta al canal (por ejemplo, la API de la última versión publicada en GitHub).

Volver atrás: fijar la versión anterior en `APP_VERSION`, restaurar el respaldo previo (abajo) y `docker compose up -d`.

## Respaldos (SEG-05)

El contenedor `backup` respalda cada día a las `BACKUP_HOUR` (por defecto 3:00) la base y los archivos en el
volumen `backups`, y **prueba la restauración** cargando la copia en una base aparte y revisando que el colegio esté.
Guarda `BACKUP_KEEP_DAYS` días (14). El resultado queda en `last-backup.json`; el sitio alerta por correo si el
respaldo falla o tiene más de 26 horas.

Copiar los respaldos fuera del servidor (recomendado, a otro proveedor o disco):

```bash
docker run --rm -v colegio_backups:/backups -v "$PWD":/out alpine cp -r /backups /out/respaldos
```

Restaurar:

```bash
docker compose stop app
docker compose exec backup restaurar.sh /backups/base-AAAA-MM-DD_HHMM.sql.gz
docker run --rm -v colegio_files:/data/files -v colegio_backups:/backups alpine \
       sh -c 'rm -rf /data/files/* && tar -xzf /backups/archivos-AAAA-MM-DD_HHMM.tar.gz -C /data/files'
docker compose start app
```

Respaldo manual en cualquier momento: `docker compose exec backup respaldar.sh`.

## Monitoreo (OPS-06)

- `https://DOMINIO/actuator/health/liveness`: para el monitoreo externo de disponibilidad (UptimeRobot, etc.).
- `/admin/platform` (permiso de plataforma): versión, licencia, módulos, base de datos, almacenamiento, antivirus,
  respaldos y disco. Lo mismo, en JSON, en `/actuator/health` con sesión del proveedor.
- Cada hora el sitio revisa lo anterior y envía por correo a `APP_ALERTS_EMAIL` (y a los usuarios con permiso de
  plataforma) lo que falla, una vez al día por problema.

## Exportar el colegio (OPS-10)

`/admin/platform/export` (administración del colegio o proveedor) descarga un ZIP con todas las tablas en JSON
legible (los datos cifrados van descifrados) y todos los archivos. Sirve para cambiarse de proveedor.
