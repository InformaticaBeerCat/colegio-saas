#!/usr/bin/env bash
# Respaldo de la base y de los archivos subidos, con prueba de restauración (SEG-05).
# Deja el resultado en /backups/last-backup.json: la aplicación lo lee y alerta si falla o se atrasa.
set -uo pipefail

DIR=/backups
STAMP=$(date +%Y-%m-%d_%H%M)
DB_FILE="$DIR/base-$STAMP.sql.gz"
FILES_FILE="$DIR/archivos-$STAMP.tar.gz"
STATUS="$DIR/last-backup.json"
export MYSQL_PWD="$DB_ROOT_PASSWORD"

fail() {
  echo "ERROR: $1" >&2
  printf '{"finishedAt": "%s", "ok": false, "error": "%s"}\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$1" > "$STATUS.tmp"
  mv "$STATUS.tmp" "$STATUS"
  exit 1
}

mkdir -p "$DIR"

# 1. Base de datos: copia consistente sin bloquear el sitio.
mysqldump -h "$DB_HOST" -u root --single-transaction --routines --triggers --set-gtid-purged=OFF "$DB_NAME" \
  | gzip -9 > "$DB_FILE" || fail "mysqldump no terminó"
[ -s "$DB_FILE" ] || fail "el respaldo de la base quedó vacío"

# 2. Archivos subidos (con almacenamiento S3, el respaldo de archivos lo da el versionado del bucket).
if [ -d /data/files ] && [ -n "$(ls -A /data/files 2>/dev/null)" ]; then
  tar -czf "$FILES_FILE" -C /data/files . || fail "no se pudieron empaquetar los archivos"
  tar -tzf "$FILES_FILE" > /dev/null || fail "el paquete de archivos está dañado"
fi

# 3. Prueba de restauración: se carga en una base aparte del mismo servidor y se cuentan filas clave.
restaurar.sh "$DB_FILE" colegio_prueba_restauracion > /tmp/restauracion.log 2>&1 || fail "la prueba de restauración falló"
SCHOOLS=$(mysql -h "$DB_HOST" -u root -N -e "select count(*) from colegio_prueba_restauracion.school")
MIGRATION=$(mysql -h "$DB_HOST" -u root -N -e "select max(version) from colegio_prueba_restauracion.flyway_schema_history where success = 1")
mysql -h "$DB_HOST" -u root -e "drop database colegio_prueba_restauracion"
[ "$SCHOOLS" = "1" ] || fail "la base restaurada no tiene el colegio (school = $SCHOOLS)"

# 4. Se borran los respaldos antiguos.
find "$DIR" -name 'base-*.sql.gz' -mtime +"${BACKUP_KEEP_DAYS:-14}" -delete
find "$DIR" -name 'archivos-*.tar.gz' -mtime +"${BACKUP_KEEP_DAYS:-14}" -delete

SIZE=$(( $(stat -c %s "$DB_FILE") + $( [ -f "$FILES_FILE" ] && stat -c %s "$FILES_FILE" || echo 0) ))
printf '{"finishedAt": "%s", "ok": true, "sizeBytes": %s, "file": "%s", "restoreTested": true, "schemaVersion": "%s"}\n' \
  "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$SIZE" "$(basename "$DB_FILE")" "$MIGRATION" > "$STATUS.tmp"
mv "$STATUS.tmp" "$STATUS"
echo "Respaldo listo: $DB_FILE ($SIZE bytes, restauración probada, migración $MIGRATION)"
