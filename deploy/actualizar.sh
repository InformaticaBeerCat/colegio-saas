#!/usr/bin/env bash
# Actualiza a la versión indicada en .env (APP_VERSION) o a la última (OPS-03): respalda antes, descarga la imagen
# y reinicia. Las migraciones de la base corren solas al arrancar (OPS-04); si alguna falla, la app no arranca y se
# puede volver a la versión anterior con el respaldo recién hecho (ver README).
set -euo pipefail
cd "$(dirname "$0")"

echo "1/3 Respaldo previo"
docker compose exec -T backup respaldar.sh

echo "2/3 Descarga de la nueva versión"
docker compose pull app

echo "3/3 Reinicio con migraciones automáticas"
docker compose up -d app
for i in $(seq 1 60); do
  status=$(docker inspect --format '{{.State.Health.Status}}' "$(docker compose ps -q app)" 2>/dev/null || echo starting)
  if [ "$status" = "healthy" ]; then
    echo "Actualización lista."
    exit 0
  fi
  sleep 5
done
echo "La aplicación no quedó sana en 5 minutos. Revisa: docker compose logs app" >&2
exit 1
