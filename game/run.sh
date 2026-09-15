#!/usr/bin/env bash
# Запуск игры. Пересобирает проект, если классы устарели.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f shooter.jar ] || [ -n "$(find src -name '*.java' -newer shooter.jar 2>/dev/null)" ]; then
  ./build.sh
fi

exec java \
  -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
  -Xms256m -Xmx1g \
  -jar shooter.jar "$@"
