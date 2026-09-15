#!/usr/bin/env bash
# Сборка игры. Нужен только JDK 17+ (внешних зависимостей нет).
set -euo pipefail
cd "$(dirname "$0")"

OUT=build
JAR=shooter.jar

rm -rf "$OUT"
mkdir -p "$OUT"

find src -name '*.java' > "$OUT/sources.txt"
javac -encoding UTF-8 -d "$OUT" "@$OUT/sources.txt"

# Вспомогательные инструменты (тесты и снимки экрана) — опционально
if [ "${WITH_TOOLS:-0}" = "1" ]; then
  find tools -name '*.java' > "$OUT/tools.txt"
  javac -encoding UTF-8 -cp "$OUT" -d "$OUT" "@$OUT/tools.txt"
fi

jar --create --file "$JAR" --main-class com.sosijis.shooter.Main -C "$OUT" . >/dev/null

echo "Собрано: $JAR"
echo "Запуск:  ./run.sh   (или java -jar $JAR)"
