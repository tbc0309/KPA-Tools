#!/system/bin/sh
# Decode one boot_control snapshot; slot_suffix is not the next-slot selector.
boot_target_from_bytes() (
  [ "$#" = 32 ] || return 1
  [ "$5 $6 $7 $8 $9" = '66 67 65 66 1' ] || return 1
  [ "$(( ${10} & 7 ))" = 2 ] || return 1
  crc=4294967295
  index=0
  for byte in "$@"; do
    [ "$index" -lt 28 ] || break
    crc=$((crc ^ byte))
    bit=0
    while [ "$bit" -lt 8 ]; do
      if [ "$((crc & 1))" = 1 ]; then
        crc=$(((crc >> 1) ^ 3988292384))
      else
        crc=$((crc >> 1))
      fi
      bit=$((bit + 1))
    done
    index=$((index + 1))
  done
  expected=$((${29} | (${30} << 8) | (${31} << 16) | (${32} << 24)))
  [ "$((crc ^ 4294967295))" = "$expected" ] || return 1
  a=$((${13} & 15)); b=$((${15} & 15))
  if [ "$((${14} & 1))" != 0 ] || [ "$((${13} & 240))" = 0 ]; then a=0; fi
  if [ "$((${16} & 1))" != 0 ] || [ "$((${15} & 240))" = 0 ]; then b=0; fi
  # Refuse ties and unbootable slots rather than guessing a bootloader tie-break.
  if [ "$a" -gt "$b" ]; then echo _a
  elif [ "$b" -gt "$a" ]; then echo _b
  else return 1
  fi
)

scheduled_target() (
  case "$1" in _a|_b) ;; *) return 1 ;; esac
  bytes=$(od -An -v -tu1 -j 2048 -N 32 /dev/block/by-name/misc) || return 1
  next=$(boot_target_from_bytes $bytes) || return 1
  [ "$next" = "$1" ]
)

# Backups and readback hashes are checked before protection is restored.
write_boot_verified() (
  image=$1; block=$2; rollback=$3; expected=$4; previous=$5
  case "$block" in /dev/block/by-name/boot_a|/dev/block/by-name/boot_b) ;; *) return 1 ;; esac
  [ -b "$block" ] || return 1
  [ "$(wc -c < "$image")" = 33554432 ] || return 1
  [ "$(wc -c < "$rollback")" = 33554432 ] || return 1
  [ "$(sha256sum "$image" | awk '{print $1}')" = "$expected" ] || return 1
  [ "$(sha256sum "$rollback" | awk '{print $1}')" = "$previous" ] || return 1
  [ "$(sha256sum "$block" | awk '{print $1}')" = "$previous" ] || return 1
  ro=$(blockdev --getro "$block") || return 1
  case "$ro" in 0|1) ;; *) return 1 ;; esac
  restore_protection() {
    if [ "$ro" = 1 ]; then blockdev --setro "$block" || return 1; fi
    [ "$(blockdev --getro "$block")" = "$ro" ]
  }
  trap 'restore_protection' EXIT
  trap 'exit 1' HUP INT TERM
  if [ "$ro" = 1 ]; then blockdev --setrw "$block" || return 1; fi
  [ "$(blockdev --getro "$block")" = 0 ] || return 1
  result=0
  dd if="$image" of="$block" bs=4M conv=fsync >> "$LOG" 2>&1 || result=1
  if [ "$(sha256sum "$block" | awk '{print $1}')" != "$expected" ]; then result=1; fi
  if [ "$result" != 0 ]; then
    log 'ERROR boot write failed; restoring verified backup'
    dd if="$rollback" of="$block" bs=4M conv=fsync >> "$LOG" 2>&1
    if [ "$(sha256sum "$block" | awk '{print $1}')" != "$previous" ]; then
      log 'ERROR boot rollback verification failed'
      result=2
    fi
  fi
  if ! restore_protection; then
    log 'ERROR boot read-only protection could not be restored'
    return 3
  fi
  trap - EXIT HUP INT TERM
  return "$result"
)
