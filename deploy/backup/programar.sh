#!/usr/bin/env bash
# Espera hasta la hora configurada (BACKUP_HOUR, hora local) y respalda una vez al día.
set -euo pipefail
echo "Respaldos programados a las ${BACKUP_HOUR:-3}:00 (${TZ:-UTC})"
while true; do
  now=$(date +%s)
  next=$(date -d "today ${BACKUP_HOUR:-3}:00" +%s)
  if [ "$next" -le "$now" ]; then
    next=$(date -d "tomorrow ${BACKUP_HOUR:-3}:00" +%s)
  fi
  sleep $((next - now))
  respaldar.sh || echo "El respaldo falló; quedó registrado en last-backup.json"
done
