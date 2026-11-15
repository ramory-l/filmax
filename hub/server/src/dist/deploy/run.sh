#!/bin/sh
# Запуск хаба под LaunchAgent. Настройки и токен TMDB — в ~/.config/filmax-hub/env: файл живёт
# только на Маке (права 600), в репозиторий и APK токен не попадает.
set -eu

ENV_FILE="$HOME/.config/filmax-hub/env"
if [ -f "$ENV_FILE" ]; then
    set -a
    . "$ENV_FILE"
    set +a
fi

# Хаб собран под Java 17; JBR из Android Studio на Маке тоже подойдёт, но Temurin — системная.
export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home}"
exec "$(dirname "$0")/../bin/filmax-hub"
