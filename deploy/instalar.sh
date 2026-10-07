#!/usr/bin/env bash
# Instala un colegio en un servidor con Docker (OPS-09): genera las llaves y contraseñas, escribe .env y levanta
# todo. Uso: ./instalar.sh colegiosanjose.cl "<licencia>"
set -euo pipefail
cd "$(dirname "$0")"

DOMAIN="${1:-}"
LICENSE="${2:-}"
if [ -z "$DOMAIN" ] || [ -z "$LICENSE" ]; then
  echo "Uso: ./instalar.sh <dominio> <licencia>" >&2
  exit 2
fi
if [ -f .env ]; then
  echo "Ya existe .env: esta instalación ya está hecha. Para actualizar usa ./actualizar.sh" >&2
  exit 1
fi
command -v docker > /dev/null || { echo "Falta Docker: https://docs.docker.com/engine/install/" >&2; exit 1; }

secret() { openssl rand -base64 "$1" | tr -d '\n'; }
SETUP_TOKEN=$(openssl rand -hex 16)

sed -e "s|^DOMAIN=.*|DOMAIN=$DOMAIN|" \
    -e "s|^APP_LICENSE=.*|APP_LICENSE=$LICENSE|" \
    -e "s|^APP_FIELD_KEY=.*|APP_FIELD_KEY=$(secret 32)|" \
    -e "s|^APP_INDEX_KEY=.*|APP_INDEX_KEY=$(secret 32)|" \
    -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$(secret 24 | tr -d '/+=')|" \
    -e "s|^DB_ROOT_PASSWORD=.*|DB_ROOT_PASSWORD=$(secret 24 | tr -d '/+=')|" \
    -e "s|^APP_SETUP_TOKEN=.*|APP_SETUP_TOKEN=$SETUP_TOKEN|" \
    -e "s|^APP_MAIL_FROM=.*|APP_MAIL_FROM=no-responder@$DOMAIN|" \
    .env.example > .env
chmod 600 .env

docker compose pull
docker compose up -d --build

cat <<MSG

Listo. En unos minutos el sitio responde en https://$DOMAIN (el certificado se emite solo).

1. Completa el asistente en https://$DOMAIN/setup con este token: $SETUP_TOKEN
2. Configura el correo saliente (SMTP_*) y APP_ALERTS_EMAIL en .env y aplica con: docker compose up -d
3. Guarda una copia de .env fuera del servidor: sin APP_FIELD_KEY los datos cifrados no se pueden recuperar.
MSG
