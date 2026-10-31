#!/usr/bin/env bash
# Boots a real Forge 1.12.2 dedicated server with FermiumBooter + pelt, runs
# "skin reload" on the console, stops it, and fails on mixin errors/exceptions.
#
# usage: ci/smoke.sh <server-dir> <pelt.jar> <fermiumbooter.jar>
#   <server-dir> must contain an installed Forge server (forge-*.jar + libraries/).
set -uo pipefail

DIR="$1"; PELT="$2"; FERMIUM="$3"
HERE="$(cd "$(dirname "$0")" && pwd)"
BOOT_TIMEOUT="${BOOT_TIMEOUT:-600}"

cd "$DIR"
rm -rf mods logs world config/pelt config/pelt.cfg crash-reports
mkdir -p mods
cp "$FERMIUM" mods/
cp "$PELT" mods/
echo "eula=true" > eula.txt
cat > server.properties <<'PROPS'
online-mode=false
level-type=FLAT
generate-structures=false
spawn-npcs=false
spawn-animals=false
spawn-monsters=false
view-distance=3
max-tick-time=-1
server-port=25565
snooper-enabled=false
motd=pelt smoke test
PROPS

FORGE_JAR=$(ls forge-1.12.2-*.jar | grep -v installer | head -1)
echo "Starting $FORGE_JAR with mods: $(ls mods | tr '\n' ' ')"

rm -f stdin.fifo; mkfifo stdin.fifo
java -Xms1G -Xmx2G -Dfml.queryResult=confirm -jar "$FORGE_JAR" nogui < stdin.fifo > console.log 2>&1 &
PID=$!
exec 3> stdin.fifo   # keep the pipe open for the server's lifetime

LOG=console.log
wait_for() { # pattern timeout
  local t=0
  while ! grep -qE "$1" "$LOG" 2>/dev/null; do
    if ! kill -0 "$PID" 2>/dev/null; then echo "::error::server exited while waiting for: $1"; return 1; fi
    sleep 2; t=$((t+2))
    if [ "$t" -ge "$2" ]; then echo "::error::timed out after ${2}s waiting for: $1"; return 1; fi
  done
}

status=0
if wait_for 'Done \([0-9.]+s\)! For help' "$BOOT_TIMEOUT"; then
  echo "Server is up; running 'skin reload'"
  echo "skin reload" >&3
  wait_for '\[pelt\]: Reload complete' 60 || status=1
else
  status=1
fi

echo "stop" >&3
for _ in $(seq 1 90); do kill -0 "$PID" 2>/dev/null || break; sleep 2; done
if kill -0 "$PID" 2>/dev/null; then echo "::error::server did not stop"; kill -9 "$PID"; status=1; fi
exec 3>&-
wait "$PID" 2>/dev/null

echo "---------------- checks ----------------"
ALL=$(mktemp)
cat console.log logs/latest.log 2>/dev/null > "$ALL"

require() {
  if grep -qE "$1" "$ALL"; then echo "ok:   $2"; else echo "::error::missing: $2 ($1)"; status=1; fi
}
require '\[pelt\]: Enqueued mixins\.pelt\.json with FermiumBooter' "pelt enqueued its early mixins"
require 'FermiumBooter adding "mixins\.pelt\.json" for early mixin application' "FermiumBooter accepted the config"
require '\[pelt\]: Mixin applied: pelt\.mixin\.MixinPlayerList' "PlayerList mixin applied"
require '\[pelt\]: Mixin applied: pelt\.mixin\.EntityTrackerAccessor' "EntityTracker accessor applied"
require '\[pelt\]: Reload complete' "/skin reload ran"
require 'Stopping server' "server stopped cleanly"

# Errors: mixin failures, ERROR/FATAL log lines, any exception text. Known-harmless lines are allow-listed.
BAD=$(grep -nE 'Mixin(Apply|Transformer)Error|InvalidMixinException|InvalidInjectionException|InvalidAccessorException|Mixin apply.*failed|/(ERROR|FATAL)\]|Exception|^\s+at [a-zA-Z_$]' "$ALL" \
      | grep -vEf "$HERE/log-allowlist.txt" || true)
if [ -n "$BAD" ]; then
  echo "::error::problems found in server log:"
  echo "$BAD" | head -80
  status=1
else
  echo "ok:   no mixin errors or exceptions in log"
fi
# FermiumBooter 1.4.x mirrors mixin problems into logs/fermiumbooter.log
if [ -f logs/fermiumbooter.log ] && grep -qE 'Mixin Error' logs/fermiumbooter.log; then
  echo "::error::logs/fermiumbooter.log reports mixin errors:"; head -60 logs/fermiumbooter.log; status=1
fi
[ -d crash-reports ] && { echo "::error::crash report written"; cat crash-reports/* | head -80; status=1; }

exit $status
