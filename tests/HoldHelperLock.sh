#!/system/bin/sh
# Device regression fixture: hold the existing operation lock without touching boot.
exec 9>/data/adb/kpa_root_helper/operation.flock
/data/adb/magisk/busybox flock -n 9 || exit 1
echo LOCK_ACQUIRED
sleep 110
echo LOCK_RELEASED
