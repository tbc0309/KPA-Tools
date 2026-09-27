#!/system/bin/sh
# Uninstall handling must not depend on the app UID or repository initialization.
export ASH_STANDALONE=1
BUSYBOX=/data/adb/magisk/busybox
STATE=/data/adb/kpa_root_helper
MODULE=/data/adb/modules/kpa_root_helper
STAGED=/data/adb/modules_update/kpa_root_helper
mkdir -p "$STATE"
exec 6>"$STATE/lifecycle.flock"
"$BUSYBOX" flock -n 6 || exit 0

# Stop only descendants of the verified helper process, excluding this watcher.
stop_tree() {
  [ "$1" = "$$" ] && return
  for child in $(/system/bin/ps -A -o PID,PPID | awk -v parent="$1" '$2==parent {print $1}'); do
    stop_tree "$child"
  done
  kill "$1" 2>/dev/null || true
}

while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 5; done
misses=0
while [ -f "$MODULE/module.prop" ]; do
  packages=$(cmd package list packages --user 0 2>/dev/null)
  result=$?
  # A failed or incomplete package query is not evidence of an uninstall.
  if [ "$result" -ne 0 ] || ! printf '%s\n' "$packages" | grep -qx 'package:android'; then
    misses=0
  elif printf '%s\n' "$packages" | grep -qx 'package:com.imnks.kpatools'; then
    misses=0
  elif [ -f "$STATE/keep_on_uninstall" ]; then
    misses=0
  else
    misses=$((misses + 1))
    if [ "$misses" -ge 3 ]; then
      # Never stop an in-progress backup, synchronization or partition write.
      exec 9>"$STATE/operation.flock"
      if "$BUSYBOX" flock -n 9; then
        # Recheck after acquiring the lock in case the app was reinstalled.
        packages=$(cmd package list packages --user 0 2>/dev/null)
        result=$?
        if [ "$result" -eq 0 ] && printf '%s\n' "$packages" | grep -qx 'package:android' &&
           ! printf '%s\n' "$packages" | grep -qx 'package:com.imnks.kpatools' &&
           [ ! -f "$STATE/keep_on_uninstall" ]; then
          # This service-only module has no mounted overlays. Preserve STATE and KPA-Tools.
          [ ! -L "$MODULE" ] && [ ! -L "$STAGED" ] || exit 1
          touch "$MODULE/disable" "$MODULE/remove" || exit 1
          resetprop -n kpa.root_helper.ready ''
          resetprop -n kpa.root_helper.state APP_REMOVED
          pid=$(cat "$STATE/monitor.pid" 2>/dev/null)
          case "$pid" in ''|*[!0-9]*) ;; *)
            if [ "$pid" -gt 1 ] && grep -q '/data/adb/modules/kpa_root_helper/service.sh' "/proc/$pid/cmdline" 2>/dev/null; then
              stop_tree "$pid"
            fi
          esac
          printf '%s App removed; helper removed; backups retained\n' "$(date '+%Y-%m-%d %H:%M:%S')" >> "$STATE/helper.log"
          rm -rf -- /data/adb/modules/kpa_root_helper /data/adb/modules_update/kpa_root_helper
          exit 0
        fi
      fi
      exec 9>&-
    fi
  fi
  sleep 30
done
