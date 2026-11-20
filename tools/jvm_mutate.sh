#!/bin/zsh
# usage: jvm_mutate.sh <log-prefix> <label> <file> <old> <new> -- <gradle --tests args...>   ("base" = no change). JVM only, never touches a device.
# Replaces exactly ONE occurrence of <old> by <new> in <file> (it refuses otherwise), runs the given Gradle tests, ALWAYS puts the file back and
# compares it byte for byte, then prints one line: label, gradle exit, compile errors, failing tests, restore result.
prefix="$1"; label="$2"; FILE="${3-}"; old="${4-}"; new="${5-}"
shift 5 2>/dev/null; [ "$1" = "--" ] && shift
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
BACKUP="/tmp/${prefix}-mutbackup.bin"; rest=none
if [ "$label" != base ]; then
  cp -p "$FILE" "$BACKUP"
  restore() { cp -p "$BACKUP" "$FILE"; cmp -s "$BACKUP" "$FILE" && rest=restored || rest=NOT-RESTORED; }
  trap restore EXIT INT TERM
  python3 - "$FILE" "$old" "$new" <<'PY'
import sys
f, o, n = sys.argv[1:4]
s = open(f, encoding="utf-8").read()
assert s.count(o) == 1, "pattern missing or not unique"
open(f, "w", encoding="utf-8").write(s.replace(o, n, 1))
PY
  [ $? -eq 0 ] || { echo "$label: PATTERN-PROBLEM"; exit 91; }
fi
./gradlew testDevDebugUnitTest "$@" > "/tmp/${prefix}-mut-$label.log" 2>&1
rc=$?
[ "$label" != base ] && { restore; trap - EXIT INT TERM; }
failing=$(grep -E "^com\.mckimquyen.* > .* FAILED" "/tmp/${prefix}-mut-$label.log" | sed -E 's/^.* > ([A-Za-z0-9_`. ,()-]+) FAILED.*/\1/' | sort -u | tr '\n' ',')
compile=$(grep -c "^e: " "/tmp/${prefix}-mut-$label.log")
echo "$label: gradle_exit=$rc compile_errors=$compile failing=[$failing] source=$rest"
