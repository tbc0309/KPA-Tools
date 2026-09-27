#!/system/bin/sh
# Use one shell implementation for boot, UI actions, and monitor recovery.
if [ "$KPA_HELPER_SHELL" != "1" ]; then
  export KPA_HELPER_SHELL=1 ASH_STANDALONE=1
  exec /data/adb/magisk/busybox sh "$0" "$@"
fi
# Patch the OTA target, then restore the previous slot's matching stock boot.
MODDIR=${0%/*}
STATE=/data/adb/kpa_root_helper
INTERNAL=/storage/emulated/0/KPA-Tools
MANAGED=$INTERNAL/kpa_root_helper
ARCHIVE=$MANAGED/boot/backup
STOCK_DIR=$MANAGED/boot/original
CURRENT_REPO_DIR=$STOCK_DIR/current
PATCHED_DIR=$MANAGED/boot/patched
OTA_CACHE=$MANAGED/ota-cache
LOG_DIR=$MANAGED/logs
MANIFEST_DIR=$MANAGED/manifests
LOG=$STATE/helper.log
PIDFILE=$STATE/monitor.pid
BASE_SOURCE=$MODDIR/boot_0730_stock.img
BASE_STOCK=$STATE/boot_0730_stock.img
BASE_SHA=735e1d3855dc0165762006cdcb1136d3047b8e999a559fbd259b2adb58a32487
STOCK_0828_SHA=f25e0d5115e4e382983f9acb47b3a2b8aefb8c8c866d5a07bb888e48553d4039
CURRENT_STOCK=$STATE/boot_current_stock.img
CURRENT_FIRMWARE=$STATE/boot_current_firmware.txt
PENDING_STOCK=$STATE/boot_pending_stock.img
PENDING_TARGET=$STATE/boot_pending_target.txt
PENDING_SHA=$STATE/boot_pending_sha.txt
APPUID=
mkdir -p "$STATE"
chmod 0700 "$STATE"
BUSYBOX=/data/adb/magisk/busybox
. "$MODDIR/boot_io.sh" || exit 1

# Kernel locks are released on exit; never unlink lock files while in use.
operation_lock() {
  exec 9>"$STATE/operation.flock"
  "$BUSYBOX" flock -n 9
}

known_stock_hash() {
  case "$1" in
    *20260730*) echo "$BASE_SHA" ;;
    *20260813*) echo 66919aa93f4d1ce9055f2f08b20034e031e63444c2b77e0d2fa3eb186817a71a ;;
    *20260828*) echo "$STOCK_0828_SHA" ;;
  esac
}

log() {
  printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >> "$LOG"
  tail -n 200 "$LOG" > "$LOG.tmp" && mv -f "$LOG.tmp" "$LOG"
}

# Keep OTA archives in the same route-based layout used by KPA Root.
ota_cache_folder() {
  SOURCE=$(printf '%s' "$1" | sed 's/[^A-Za-z0-9._-]/_/g')
  TARGET=$(printf '%s' "$2" | sed 's/[^A-Za-z0-9._-]/_/g')
  printf '%s/%s__to__%s' "$OTA_CACHE" "$SOURCE" "$TARGET"
}

prepare_base_stock() {
  CURRENT=$(sha256sum "$BASE_STOCK" 2>/dev/null | awk '{print $1}')
  if [ "$CURRENT" != "$BASE_SHA" ]; then
    SOURCE=$(sha256sum "$BASE_SOURCE" 2>/dev/null | awk '{print $1}')
    if [ "$SOURCE" != "$BASE_SHA" ]; then
      log 'ERROR 0730 base boot is missing or invalid'
      resetprop -n kpa.root_helper.base ERROR
      return 1
    fi
    cp "$BASE_SOURCE" "$BASE_STOCK.tmp" && sync && mv -f "$BASE_STOCK.tmp" "$BASE_STOCK"
    CURRENT=$(sha256sum "$BASE_STOCK" 2>/dev/null | awk '{print $1}')
  fi
  if [ "$CURRENT" = "$BASE_SHA" ]; then
    chmod 0600 "$BASE_STOCK"
    resetprop -n kpa.root_helper.base 0730_VERIFIED
    resetprop -n kpa.root_helper.base_path "$BASE_STOCK"
    return 0
  fi
  resetprop -n kpa.root_helper.base ERROR
  return 1
}

firmware_key() { printf '%s' "$1" | sed 's/[^A-Za-z0-9._-]/_/g'; }

import_repository_stock() {
  FIRMWARE=$1
  KEY=$(firmware_key "$FIRMWARE")
  SOURCE=$CURRENT_REPO_DIR/$KEY/boot_stock.img
  HASH_FILE=$CURRENT_REPO_DIR/$KEY/boot_stock.sha256
  if [ ! -s "$SOURCE" ] || [ ! -s "$HASH_FILE" ]; then
    UUID=$(sm list-volumes public 2>/dev/null | awk '$2=="mounted" && $3!="null" {print $3; exit}')
    TF_CURRENT=/storage/$UUID/KPA-Tools/kpa_root_helper/boot/original/current/$KEY
    [ -n "$UUID" ] && [ -s "$TF_CURRENT/boot_stock.img" ] && [ -s "$TF_CURRENT/boot_stock.sha256" ] && {
      SOURCE=$TF_CURRENT/boot_stock.img
      HASH_FILE=$TF_CURRENT/boot_stock.sha256
    }
  fi
  EXPECTED=$(cat "$HASH_FILE" 2>/dev/null | tr -d '\r\n ')
  ACTUAL=$(sha256sum "$SOURCE" 2>/dev/null | awk '{print $1}')
  KNOWN=$(known_stock_hash "$FIRMWARE")
  [ -z "$KNOWN" ] || [ "$ACTUAL" = "$KNOWN" ] || return 1
  if [ -n "$EXPECTED" ] && [ "$ACTUAL" = "$EXPECTED" ] && [ "$(wc -c < "$SOURCE" 2>/dev/null)" = 33554432 ]; then
    cp "$SOURCE" "$CURRENT_STOCK.tmp" && sync && mv -f "$CURRENT_STOCK.tmp" "$CURRENT_STOCK" || return 1
    printf '%s\n' "$FIRMWARE" > "$CURRENT_FIRMWARE"
    printf '%s\n' "$ACTUAL" > "$CURRENT_STOCK.sha256"
    chmod 0600 "$CURRENT_STOCK"
    log "Imported stock boot for $FIRMWARE from KPA-Tools, sha256=$ACTUAL"
    return 0
  fi
  return 1
}

prepare_current_stock() {
  FIRMWARE=$(getprop ro.build.display.id)
  ACTIVE=$(getprop ro.boot.slot_suffix)
  if [ -s "$PENDING_STOCK" ] && [ "$(cat "$PENDING_TARGET" 2>/dev/null)" = "$ACTIVE" ]; then
    EXPECTED=$(cat "$PENDING_SHA" 2>/dev/null)
    ACTUAL=$(sha256sum "$PENDING_STOCK" 2>/dev/null | awk '{print $1}')
    KNOWN=$(known_stock_hash "$FIRMWARE")
    if [ -n "$EXPECTED" ] && [ "$ACTUAL" = "$EXPECTED" ] &&
       [ "$(wc -c < "$PENDING_STOCK")" = 33554432 ] &&
       { [ -z "$KNOWN" ] || [ "$ACTUAL" = "$KNOWN" ]; }; then
      cp "$PENDING_STOCK" "$CURRENT_STOCK.tmp" && sync && mv -f "$CURRENT_STOCK.tmp" "$CURRENT_STOCK" || return 1
      printf '%s\n' "$FIRMWARE" > "$CURRENT_FIRMWARE"
      printf '%s\n' "$ACTUAL" > "$CURRENT_STOCK.sha256"
      rm -f "$PENDING_STOCK" "$PENDING_TARGET" "$PENDING_SHA"
      log "Promoted stock boot for $FIRMWARE on $ACTIVE, sha256=$ACTUAL"
    fi
  fi
  SAVED_FIRMWARE=$(cat "$CURRENT_FIRMWARE" 2>/dev/null)
  if [ "$SAVED_FIRMWARE" = "$FIRMWARE" ] && valid_current_stock; then
    chmod 0600 "$CURRENT_STOCK"
    return 0
  fi
  import_repository_stock "$FIRMWARE" && return 0
  case "$FIRMWARE" in
    *20260730*) SOURCE=$BASE_STOCK ;;
    *) rebuild_current_stock "$FIRMWARE"; return $? ;;
  esac
  cp "$SOURCE" "$CURRENT_STOCK.tmp" && sync && mv -f "$CURRENT_STOCK.tmp" "$CURRENT_STOCK" || return 1
  printf '%s\n' "$FIRMWARE" > "$CURRENT_FIRMWARE"
  printf '%s\n' "$BASE_SHA" > "$CURRENT_STOCK.sha256"
  chmod 0600 "$CURRENT_STOCK"
}

valid_current_stock() {
  [ "$(wc -c < "$CURRENT_STOCK" 2>/dev/null)" = 33554432 ] || return 1
  EXPECTED=$(cat "$CURRENT_STOCK.sha256" 2>/dev/null)
  case "$(cat "$CURRENT_FIRMWARE" 2>/dev/null)" in
    *20260730*) EXPECTED=$BASE_SHA ;;
    *20260813*) EXPECTED=66919aa93f4d1ce9055f2f08b20034e031e63444c2b77e0d2fa3eb186817a71a ;;
    *20260828*) EXPECTED=$STOCK_0828_SHA ;;
  esac
  [ -n "$EXPECTED" ] && [ "$(sha256sum "$CURRENT_STOCK" 2>/dev/null | awk '{print $1}')" = "$EXPECTED" ]
}

rebuild_current_stock() (
  BUILD_FIRMWARE=$1
  exec 8>"$STATE/rebuild.flock"
  "$BUSYBOX" flock -n 8 || return 1
  publish_status BUILDING_STOCK
  log "Reconstructing stock boot for $BUILD_FIRMWARE from the 0730 OTA chain"
  PHYSICAL=/data/media/0/KPA-Tools/kpa_root_helper
  CLASSPATH="$MODDIR/stock-builder.dex" app_process /system/bin cn.pegasus.setup.StockBootBuilder \
    "$PHYSICAL" "$BASE_STOCK" "$MODDIR/payload_dumper" "$BUILD_FIRMWARE" "$(getprop ro.serialno)" >> "$LOG" 2>&1 || {
      publish_status ERROR_STOCK_BUILD
      log 'ERROR stock boot reconstruction failed'
      return 1
    }
  BUILD_KEY=$(printf '%s' "$BUILD_FIRMWARE" | sed -n 's/^\(BW03_[0-9]\{8\}\).*/\1/p')
  BUILT="$PHYSICAL/boot/original/current/$BUILD_KEY/boot_stock.img"
  [ "$(wc -c < "$BUILT" 2>/dev/null)" = 33554432 ] || return 1
  EXPECTED=$(cat "${BUILT%.img}.sha256" 2>/dev/null)
  [ "$(sha256sum "$BUILT" | awk '{print $1}')" = "$EXPECTED" ] || return 1
  cp "$BUILT" "$CURRENT_STOCK.tmp" && sync && mv -f "$CURRENT_STOCK.tmp" "$CURRENT_STOCK" || return 1
  printf '%s\n' "$BUILD_FIRMWARE" > "$CURRENT_FIRMWARE"
  printf '%s\n' "$EXPECTED" > "$CURRENT_STOCK.sha256"
  chmod 0600 "$CURRENT_STOCK"
  chown -R media_rw:media_rw "$PHYSICAL/boot/original/current" "$PHYSICAL/ota-cache"
  log "Stock reconstruction verified: $BUILD_FIRMWARE sha256=$EXPECTED"
)

copy_stock_to_repository() {
  SOURCE=$1
  NAME=$2
  EXPECTED=$3
  SAVED=$(su "$APPUID" -c "sha256sum '$STOCK_DIR/$NAME'" 2>/dev/null | awk '{print $1}')
  if [ "$SAVED" != "$EXPECTED" ]; then
    RC=1
    COUNT=0
    while [ "$RC" -ne 0 ] && [ "$COUNT" -lt 30 ]; do
      cat "$SOURCE" | su "$APPUID" -c "cat > '$STOCK_DIR/$NAME'"
      RC=$?
      [ "$RC" -eq 0 ] && break
      COUNT=$((COUNT + 1))
      sleep 2
    done
    [ "$RC" -eq 0 ] || return 1
    SAVED=$(su "$APPUID" -c "sha256sum '$STOCK_DIR/$NAME'" 2>/dev/null | awk '{print $1}')
  fi
  [ "$SAVED" = "$EXPECTED" ]
}

prepare_internal_storage() {
  COUNT=0
  while [ -z "$APPUID" ] && [ "$COUNT" -lt 60 ]; do
    APPUID=$(cmd package list packages -U com.imnks.kpatools 2>/dev/null | sed -n 's/.* uid://p' | head -n 1)
    [ -n "$APPUID" ] && break
    COUNT=$((COUNT + 1))
    sleep 2
  done
  resetprop -n kpa.root_helper.app_uid "$APPUID"
  [ -n "$APPUID" ] || { resetprop -n kpa.root_helper.storage_error NO_UID; return 1; }
  RC=1
  COUNT=0
  while [ "$RC" -ne 0 ] && [ "$COUNT" -lt 60 ]; do
    su "$APPUID" -c "mkdir -p '$ARCHIVE' '$STOCK_DIR' '$CURRENT_REPO_DIR' '$PATCHED_DIR' '$OTA_CACHE' '$LOG_DIR' '$MANIFEST_DIR'"
    RC=$?
    [ "$RC" -eq 0 ] && break
    COUNT=$((COUNT + 1))
    sleep 2
  done
  [ "$RC" -eq 0 ] || { resetprop -n kpa.root_helper.storage_error "MKDIR_$RC"; return 1; }
  copy_stock_to_repository "$BASE_STOCK" boot_0730_stock.img "$BASE_SHA" || { resetprop -n kpa.root_helper.storage_error COPY_0730; return 1; }
  resetprop -n kpa.root_helper.storage_error ''
  return 0
}

persist_current_stock() {
  FIRMWARE=$(cat "$CURRENT_FIRMWARE" 2>/dev/null)
  [ -n "$FIRMWARE" ] && [ -s "$CURRENT_STOCK" ] || return 0
  HASH=$(sha256sum "$CURRENT_STOCK" 2>/dev/null | awk '{print $1}')
  [ -n "$HASH" ] || return 1
  KEY=$(firmware_key "$FIRMWARE")
  DEST=$CURRENT_REPO_DIR/$KEY
  SAVED=$(su "$APPUID" -c "sha256sum '$DEST/boot_stock.img'" 2>/dev/null | awk '{print $1}')
  SAVED_META=$(su "$APPUID" -c "cat '$DEST/boot_stock.sha256'" 2>/dev/null)
  [ "$SAVED" = "$HASH" ] && [ "$SAVED_META" = "$HASH" ] && return 0
  COUNT=0
  while [ "$COUNT" -lt 30 ]; do
    if su "$APPUID" -c "mkdir -p '$DEST'" &&
       cat "$CURRENT_STOCK" | su "$APPUID" -c "cat > '$DEST/boot_stock.img.tmp'" &&
       su "$APPUID" -c "mv -f '$DEST/boot_stock.img.tmp' '$DEST/boot_stock.img'"; then
      SAVED=$(su "$APPUID" -c "sha256sum '$DEST/boot_stock.img'" 2>/dev/null | awk '{print $1}')
      if [ "$SAVED" = "$HASH" ]; then
        printf '%s\n' "$HASH" | su "$APPUID" -c "cat > '$DEST/boot_stock.sha256'" || return 1
        printf '%s\n' "$FIRMWARE" | su "$APPUID" -c "cat > '$DEST/firmware.txt'" || return 1
        return 0
      fi
    fi
    COUNT=$((COUNT + 1))
    sleep 2
  done
  return 1
}

write_manifest() {
  {
    echo "generated=$(date '+%Y-%m-%d %H:%M:%S')"
    echo "firmware=$(getprop ro.build.display.id)"
    echo "active=$(getprop ro.boot.slot_suffix)"
    echo "base_0730_sha256=$BASE_SHA"
    echo "ota_cache_layout=ota-cache/source_version__to__target_version/original_filename.zip"
    su "$APPUID" -c "find '$MANAGED/boot' -type f -exec sha256sum {} \\;" 2>/dev/null | sed "s#$INTERNAL/##"
  } > "$STATE/SHA256SUMS.txt"
  cat "$STATE/SHA256SUMS.txt" | su "$APPUID" -c "cat > '$MANIFEST_DIR/SHA256SUMS.txt'"
}

sync_to_tf() {
  UUID=$(sm list-volumes public 2>/dev/null | awk '$2=="mounted" && $3!="null" {print $3; exit}')
  if [ -z "$UUID" ] || [ ! -d "/mnt/media_rw/$UUID" ]; then
    resetprop -n kpa.root_helper.tf NOT_PRESENT
    resetprop -n kpa.root_helper.tf_path ''
    resetprop -n kpa.root_helper.tf_error ''
    return 0
  fi
  TFROOT="/storage/$UUID/KPA-Tools"
  su "$APPUID" -c "mkdir -p '$TFROOT' && cp -Ru '$INTERNAL/.' '$TFROOT/'"
  RESULT=$?
  if [ "$RESULT" -eq 0 ]; then
    resetprop -n kpa.root_helper.tf SYNCED
    resetprop -n kpa.root_helper.tf_path "/storage/$UUID/KPA-Tools"
    resetprop -n kpa.root_helper.tf_time "$(date '+%Y-%m-%d %H:%M:%S')"
    resetprop -n kpa.root_helper.tf_error ''
  else
    resetprop -n kpa.root_helper.tf ERROR
    resetprop -n kpa.root_helper.tf_error "COPY_EXIT_$RESULT"
  fi
  return "$RESULT"
}

update_repository() {
  prepare_internal_storage || { resetprop -n kpa.root_helper.storage ERROR; return 1; }
  persist_current_stock || { resetprop -n kpa.root_helper.storage_error SAVE_CURRENT; return 1; }
  cat "$LOG" | su "$APPUID" -c "cat > '$LOG_DIR/helper.log'" 2>/dev/null
  write_manifest || { resetprop -n kpa.root_helper.storage_error MANIFEST; return 1; }
  resetprop -n kpa.root_helper.storage READY
  resetprop -n kpa.root_helper.storage_path "$INTERNAL"
}

notify_ota_state() {
  case "$1" in
    PREPARING_OTA|OTA_PREPARED|OTA_RUNNING|PATCHING|READY_TO_REBOOT)
      resetprop -n kpa.root_helper.ota_notice 1 ;;
    ERROR*) [ "$(getprop kpa.root_helper.ota_notice)" = 1 ] || return 0 ;;
    READY)
      if [ "$(getprop kpa.root_helper.ota_notice)" = 1 ]; then
        am stop-service --user 0 -n com.imnks.kpatools/cn.pegasus.setup.OtaNoticeService >/dev/null 2>&1
        resetprop -n kpa.root_helper.ota_notice 0
      fi
      return 0 ;;
    *) return 0 ;;
  esac
  # An event-driven banner, not a second monitor. Notification remains the fallback.
  cmd appops set com.imnks.kpatools SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1
  if ! "$BUSYBOX" timeout 5 am start-foreground-service --user 0 -n com.imnks.kpatools/cn.pegasus.setup.OtaNoticeService >/dev/null 2>&1; then
    log 'WARNING OTA notice could not be displayed'
  fi
}

publish_status() {
  PREVIOUS_STATUS=$(getprop kpa.root_helper.state)
  ACTIVE=$(getprop ro.boot.slot_suffix)
  [ "$ACTIVE" = "_a" ] && INACTIVE=_b || INACTIVE=_a
  ACTIVE_SHA=$(sha256sum "/dev/block/by-name/boot${ACTIVE}" 2>/dev/null | awk '{print $1}')
  INACTIVE_SHA=$(sha256sum "/dev/block/by-name/boot${INACTIVE}" 2>/dev/null | awk '{print $1}')
  resetprop -n kpa.root_helper.ready V2
  resetprop -n kpa.root_helper.version 1.0.0
  resetprop -n kpa.root_helper.stock_builder 2
  resetprop -n kpa.root_helper.lifecycle 1
  resetprop -n kpa.root_helper.state "${1:-READY}"
  resetprop -n kpa.root_helper.active "$ACTIVE"
  resetprop -n kpa.root_helper.inactive "$INACTIVE"
  resetprop -n kpa.root_helper.active_sha "$ACTIVE_SHA"
  resetprop -n kpa.root_helper.inactive_sha "$INACTIVE_SHA"
  resetprop -n kpa.root_helper.firmware "$(getprop ro.build.display.id)"
  resetprop -n kpa.root_helper.archive "$ARCHIVE"
  if [ "$PREVIOUS_STATUS" != "${1:-READY}" ]; then notify_ota_state "${1:-READY}"; fi
}

active_stock_image() {
  FIRMWARE=$(getprop ro.build.display.id)
  if [ "$(cat "$CURRENT_FIRMWARE" 2>/dev/null)" = "$FIRMWARE" ] && valid_current_stock; then
    printf '%s\n' "$CURRENT_STOCK"
    return 0
  fi
  case "$FIRMWARE" in
    *20260730*) printf '%s\n' "$BASE_STOCK" ;;
    *) prepare_current_stock && printf '%s\n' "$CURRENT_STOCK" || return 1 ;;
  esac
}

restore_active_stock() {
  ACTIVE=$1
  SOURCE=$(active_stock_image) || { log 'ERROR no matching stock boot for active firmware'; return 1; }
  EXPECTED=$(sha256sum "$SOURCE" 2>/dev/null | awk '{print $1}')
  [ -n "$EXPECTED" ] || return 1
  BLOCK="/dev/block/by-name/boot${ACTIVE}"
  CURRENT=$(sha256sum "$BLOCK" 2>/dev/null | awk '{print $1}')
  if [ "$CURRENT" = "$EXPECTED" ]; then
    log "Active boot $ACTIVE is already stock, sha256=$EXPECTED"
    resetprop -n kpa.root_helper.restored_active "$ACTIVE"
    resetprop -n kpa.root_helper.restored_sha "$EXPECTED"
    return 0
  fi
  STAMP=$(date '+%Y%m%d_%H%M%S')
  BACKUP="$ARCHIVE/boot${ACTIVE}_before_stock_restore_${STAMP}_${CURRENT}.img"
  cat "$BLOCK" | su "$APPUID" -c "cat > '$BACKUP'" || { log 'ERROR unable to back up active boot before restore'; return 1; }
  SAVED=$(su "$APPUID" -c "sha256sum '$BACKUP'" | awk '{print $1}')
  [ "$SAVED" = "$CURRENT" ] || { log 'ERROR active boot backup verification failed'; return 1; }
  [ "$(getprop ro.boot.slot_suffix)" = "$ACTIVE" ] || return 1
  write_boot_verified "$SOURCE" "$BLOCK" "$BACKUP" "$EXPECTED" "$CURRENT"
  RESTORE_RESULT=$?
  sync
  READBACK=$(sha256sum "$BLOCK" 2>/dev/null | awk '{print $1}')
  if [ "$RESTORE_RESULT" -ne 0 ] || [ "$READBACK" != "$EXPECTED" ]; then
    log "ERROR active restore failed, result=$RESTORE_RESULT"
    return 1
  fi
  log "SUCCESS active boot $ACTIVE restored to stock, sha256=$READBACK"
  resetprop -n kpa.root_helper.restored_active "$ACTIVE"
  resetprop -n kpa.root_helper.restored_sha "$READBACK"
}

patch_inactive() (
  operation_lock || return 0
  [ -f "$MODDIR/module.prop" ] && [ ! -f "$MODDIR/disable" ] && [ ! -f "$MODDIR/remove" ] || return 1
  ACTIVE=$(getprop ro.boot.slot_suffix)
  case "$ACTIVE" in _a|_b) ;; *) publish_status ERROR_SLOT; return 1 ;; esac
  [ "$ACTIVE" = "_a" ] && TARGET=_b || TARGET=_a
  scheduled_target "$TARGET" || { log "ERROR inactive slot $TARGET is not scheduled"; publish_status ERROR_TARGET_NOT_SCHEDULED; return 1; }
  # Both sides must be recoverable before the first partition write.
  OLD_STOCK=$(active_stock_image) || { publish_status ERROR_STOCK_MISSING; return 1; }
  [ "$(wc -c < "$OLD_STOCK" 2>/dev/null)" = 33554432 ] || { publish_status ERROR_STOCK_SIZE; return 1; }
  BLOCK="/dev/block/by-name/boot${TARGET}"
  EXISTING_SHA=$(sha256sum "$BLOCK" 2>/dev/null | awk '{print $1}')
  if [ "$(cat "$STATE/completed_target" 2>/dev/null)" = "$TARGET:$EXISTING_SHA" ]; then
    restore_active_stock "$ACTIVE" || { publish_status ERROR_RESTORE_ACTIVE; return 1; }
    publish_status READY_TO_REBOOT
    return 0
  fi
  WORK="$STATE/work${TARGET}"
  rm -rf "$WORK"
  mkdir -p "$WORK"
  BEFORE=$(sha256sum "$BLOCK" 2>/dev/null | awk '{print $1}')
  [ -n "$BEFORE" ] || { log 'ERROR unable to read inactive boot'; publish_status ERROR_READ; return 1; }
  STAMP=$(date '+%Y%m%d_%H%M%S')
  STOCK="$WORK/boot.img"
  STOCK_NAME="boot${TARGET}_stock_${STAMP}_${BEFORE}.img"
  dd if="$BLOCK" of="$STOCK" bs=4M conv=fsync >> "$LOG" 2>&1 || { publish_status ERROR_BACKUP; return 1; }
  [ "$(sha256sum "$STOCK" | awk '{print $1}')" = "$BEFORE" ] || { publish_status ERROR_BACKUP; return 1; }
  cat "$STOCK" | su "$APPUID" -c "cat > '$ARCHIVE/$STOCK_NAME'" || { publish_status ERROR_BACKUP; return 1; }
  [ "$(su "$APPUID" -c "sha256sum '$ARCHIVE/$STOCK_NAME'" | awk '{print $1}')" = "$BEFORE" ] || { publish_status ERROR_BACKUP; return 1; }
  cat "$STOCK" | su "$APPUID" -c "cat > '$STOCK_DIR/boot_pending_slot${TARGET}_stock_${BEFORE}.img'" || { publish_status ERROR_BACKUP; return 1; }
  cp "$STOCK" "$PENDING_STOCK.tmp" && sync && mv -f "$PENDING_STOCK.tmp" "$PENDING_STOCK" || { publish_status ERROR_BACKUP; return 1; }
  printf '%s\n' "$TARGET" > "$PENDING_TARGET"
  printf '%s\n' "$BEFORE" > "$PENDING_SHA"
  for FILE in boot_patch.sh busybox init-ld magisk magiskboot magiskinit stub.apk util_functions.sh; do
    cp "/data/adb/magisk/$FILE" "$WORK/$FILE" || { log "ERROR missing Magisk asset: $FILE"; publish_status ERROR_ASSET; return 1; }
  done
  chmod 0755 "$WORK/busybox" "$WORK/magisk" "$WORK/magiskboot" "$WORK/magiskinit"
  publish_status PATCHING
  log "Patching inactive boot $TARGET, stock sha256=$BEFORE"
  (
    cd "$WORK" || exit 1
    export BOOTMODE=true KEEPVERITY=true KEEPFORCEENCRYPT=true PATCHVBMETAFLAG=false RECOVERYMODE=false LEGACYSAR=false ASH_STANDALONE=1
    ./busybox sh ./boot_patch.sh ./boot.img
  ) >> "$LOG" 2>&1 || { publish_status ERROR_PATCH; return 1; }
  PATCHED="$WORK/new-boot.img"
  [ -s "$PATCHED" ] || { log 'ERROR patched boot missing'; publish_status ERROR_PATCH; return 1; }
  [ "$(wc -c < "$PATCHED")" = "$(wc -c < "$STOCK")" ] || { publish_status ERROR_PATCH_SIZE; return 1; }
  PATCHED_SHA=$(sha256sum "$PATCHED" | awk '{print $1}')
  [ "$PATCHED_SHA" != "$BEFORE" ] || { log 'ERROR patched image unchanged'; publish_status ERROR_PATCH; return 1; }
  SAVED_PATCH="$PATCHED_DIR/boot${TARGET}_magisk_${STAMP}_${PATCHED_SHA}.img"
  cat "$PATCHED" | su "$APPUID" -c "cat > '$SAVED_PATCH'" || { publish_status ERROR_BACKUP; return 1; }
  [ "$(su "$APPUID" -c "sha256sum '$SAVED_PATCH'" | awk '{print $1}')" = "$PATCHED_SHA" ] || { publish_status ERROR_BACKUP; return 1; }
  scheduled_target "$TARGET" && [ "$(getprop ro.boot.slot_suffix)" = "$ACTIVE" ] || { publish_status ERROR_SLOT_CHANGED; return 1; }
  write_boot_verified "$PATCHED" "$BLOCK" "$STOCK" "$PATCHED_SHA" "$BEFORE"
  WRITE_RESULT=$?
  sync
  READBACK=$(sha256sum "$BLOCK" | awk '{print $1}')
  if [ "$WRITE_RESULT" -ne 0 ] || [ "$READBACK" != "$PATCHED_SHA" ]; then
    log "ERROR inactive write failed, result=$WRITE_RESULT"
    if [ "$WRITE_RESULT" = 3 ]; then
      publish_status ERROR_PROTECTION
    elif [ "$(sha256sum "$BLOCK" | awk '{print $1}')" = "$BEFORE" ]; then
      publish_status ERROR_VERIFY
    else
      publish_status ERROR_ROLLBACK
    fi
    return 1
  fi
  log "SUCCESS inactive boot $TARGET patched, sha256=$PATCHED_SHA"
  printf '%s\n' "$TARGET:$PATCHED_SHA" > "$STATE/completed_target"
  restore_active_stock "$ACTIVE" || { publish_status ERROR_RESTORE_ACTIVE; update_repository; sync_to_tf; return 1; }
  publish_status READY_TO_REBOOT
  rm -rf "$WORK"
  update_repository
  sync_to_tf
)

if [ "$1" != "--sync" ] && [ "$1" != "--patch-pending" ] && [ "$1" != "--prepare-ota" ]; then
  exec 7>"$STATE/monitor.flock"
  "$BUSYBOX" flock -n 7 || exit 0
  echo $$ > "$PIDFILE"
  # Start before UID/storage setup, and release the inherited monitor lock in the child.
  (exec 7>&-; exec "$BUSYBOX" sh "$MODDIR/lifecycle.sh") >/dev/null 2>&1 &
  while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 5; done
  cmd package list packages --user 0 2>/dev/null | grep -qx 'package:com.imnks.kpatools' || exit 0
fi
operation_lock || exit 1
[ -f "$MODDIR/module.prop" ] && [ ! -f "$MODDIR/disable" ] && [ ! -f "$MODDIR/remove" ] || exit 1
if [ "$1" = "--prepare-ota" ]; then
  case "$(getprop kpa.root_helper.state)" in READY|OTA_PREPARED) ;; *) exit 1 ;; esac
fi
prepare_base_stock || { publish_status ERROR_BASE_STOCK; exit 1; }
prepare_internal_storage || { publish_status ERROR_STORAGE; exit 1; }
prepare_current_stock || { publish_status ERROR_STOCK_BUILD; exit 1; }
if ! update_repository; then
  publish_status ERROR_STORAGE
  log 'ERROR root management storage is not ready'
  exit 1
fi
if [ "$1" = "--prepare-ota" ]; then
  # The running slot is authoritative before OTA. A line flash may leave equal
  # A/B priorities in misc, so next-slot metadata is not required at this stage.
  # The monitor still requires a unique scheduled inactive target before patching.
  ACTIVE=$(getprop ro.boot.slot_suffix)
  case "$ACTIVE" in _a|_b) ;; *) exit 1 ;; esac
  if ! scheduled_target "$ACTIVE"; then
    log "INFO boot metadata has no unique current target; using running slot $ACTIVE for OTA preparation"
  fi
  MONITOR_PID=$(cat "$PIDFILE" 2>/dev/null)
  case "$MONITOR_PID" in ''|*[!0-9]*) exit 1 ;; esac
  grep -q 'kpa_root_helper/service.sh' "/proc/$MONITOR_PID/cmdline" || exit 1
  publish_status PREPARING_OTA
  if ! restore_active_stock "$ACTIVE"; then
    publish_status ERROR_PREPARE_OTA
    exit 1
  fi
  publish_status OTA_PREPARED
  resetprop -n kpa.root_helper.prepared 1
  update_repository || exit 1
  # External storage is optional; the verified internal backup is authoritative.
  sync_to_tf
  echo OTA_PREPARED
  exit 0
fi
if [ "$1" = "--patch-pending" ]; then
  # Explicit recovery of an already completed OTA; all target checks still apply.
  exec 9>&-
  patch_inactive
  exit $?
fi
if [ "$1" = "--sync" ]; then
  log 'Root management storage synchronization requested'
  sync_to_tf
  # Release the repository lock before starting a replacement monitor.
  exec 9>&-
  MONITOR_PID=$(cat "$PIDFILE" 2>/dev/null)
  if [ -z "$MONITOR_PID" ] || ! grep -q 'kpa_root_helper/service.sh' "/proc/$MONITOR_PID/cmdline" 2>/dev/null; then
    (exec 9>&-; exec "$BUSYBOX" sh "$MODDIR/service.sh") >/dev/null 2>&1 &
  fi
  exit 0
fi
publish_status READY
exec 9>&-
log 'OTA monitor started'

logcat -b all -v brief -T 1 2>/dev/null | while IFS= read -r LINE; do
  case "$LINE" in
    *"update status,percent ="*"status=3"*|*"update status,percent ="*"status=4"*|*"update status,percent ="*"status=5"*)
      if [ "$(getprop kpa.root_helper.state)" != OTA_RUNNING ]; then publish_status OTA_RUNNING; fi
      ;;
    *"update status,percent ="*"status=0"*)
      if [ "$(getprop kpa.root_helper.state)" = OTA_RUNNING ]; then
        if [ "$(getprop kpa.root_helper.prepared)" = 1 ]; then publish_status OTA_PREPARED
        else publish_status READY
        fi
      fi
      ;;
    *"update status,percent ="*"status=6"*)
      log 'FotaApp reported UpdateEngine status 6'
      sleep 3
      patch_inactive
      ;;
  esac
done
