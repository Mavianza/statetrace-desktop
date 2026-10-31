#!/bin/sh
cd "$(dirname "$0")" || exit 2
exec java -jar dist/statetrace.jar "$@"
