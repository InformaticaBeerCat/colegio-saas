#!/usr/bin/env bash
# Restaura un respaldo de la base: restaurar.sh <base-AAAA-MM-DD_HHMM.sql.gz> [base_destino]
# Sin base destino restaura sobre la del sitio (detén la app antes: docker compose stop app).
set -euo pipefail
FILE="$1"
TARGET="${2:-$DB_NAME}"
export MYSQL_PWD="$DB_ROOT_PASSWORD"
mysql -h "$DB_HOST" -u root -e "drop database if exists \`$TARGET\`; create database \`$TARGET\` character set utf8mb4 collate utf8mb4_0900_ai_ci"
gunzip -c "$FILE" | mysql -h "$DB_HOST" -u root "$TARGET"
echo "Restaurado $FILE en $TARGET"
