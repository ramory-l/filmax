#!/bin/sh
# Установка и обновление хаба на Маке. Сборка — `./gradlew :hub:server:distTar`, на Маке:
#   tar -xf filmax-hub.tar && sh filmax-hub/deploy/install.sh
# Настройки (~/.config/filmax-hub/env) создаются один раз и при обновлении не трогаются.
set -eu

LABEL="local.filmax-hub"
SOURCE="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="$HOME/services/filmax-hub"
CONFIG="$HOME/.config/filmax-hub"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"

mkdir -p "$TARGET" "$CONFIG" "$HOME/services/logs" "$HOME/Library/LaunchAgents"

if [ ! -f "$CONFIG/env" ]; then
    # Адрес TorrServer — как его видит телевизор: по IP Мака в домашней сети, а не localhost.
    address="$(ipconfig getifaddr en0 || ipconfig getifaddr en1 || echo 127.0.0.1)"
    cat > "$CONFIG/env" <<EOF
# Настройки домашнего хаба «Плёнки». Токен TMDB (API Read Access Token) — только здесь, не в репозитории.
FILMAX_HUB_NAME="Домашний хаб"
TORRSERVER_URL=http://$address:8092
TMDB_TOKEN=
EOF
    chmod 600 "$CONFIG/env"
fi

launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null || true
# bootout выгружает сервис асинхронно: bootstrap, пока старый процесс ещё жив, падает с
# «Bootstrap failed: 5». Ждём выгрузки — до десяти секунд.
attempt=0
while launchctl print "gui/$(id -u)/$LABEL" >/dev/null 2>&1 && [ "$attempt" -lt 20 ]; do
    sleep 0.5
    attempt=$((attempt + 1))
done
rm -rf "$TARGET/app"
cp -R "$SOURCE" "$TARGET/app"
sed "s|@HOME@|$HOME|g" "$SOURCE/deploy/$LABEL.plist" > "$PLIST"
launchctl bootstrap "gui/$(id -u)" "$PLIST"
echo "Домашний хаб установлен в $TARGET, лог — $HOME/services/logs/$LABEL.log"
