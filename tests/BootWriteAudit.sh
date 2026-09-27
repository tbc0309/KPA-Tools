#!/system/bin/sh
export ASH_STANDALONE=1
. /data/local/tmp/kpa-boot-io.sh
LOG=/dev/null
# Replace every device operation; no real block device is opened by these tests.
test_case() (
  mode=$1; mock_ro=1; disk=old; writes=0
  wc() { echo 33554432; }
  sha256sum() {
    case "$1" in /dev/null) echo 'new image';; /dev/zero) echo 'old backup';; *) echo "$disk block";; esac
  }
  blockdev() {
    case "$1" in
      --getro) echo "$mock_ro";;
      --setrw) if test "$mode" = readonly; then return 1; fi; mock_ro=0;;
      --setro) mock_ro=1; echo "PROTECTED:$disk";;
      *) return 1;;
    esac
  }
  dd() {
    writes=$((writes + 1))
    if test "$writes" = 1; then
      disk=new
      case "$mode" in failure|rollback_failure) disk=partial; return 1;; esac
    else
      if test "$mode" = rollback_failure; then disk=partial; return 1; fi
      disk=old
    fi
  }
  log() { echo "$*"; }
  write_boot_verified /dev/null /dev/block/by-name/boot_b /dev/zero new old
)
for mode in success failure rollback_failure readonly; do
  output=$(test_case "$mode")
  result=$?
  case "$mode" in
    success) expected=0; state=new;;
    failure) expected=1; state=old;;
    rollback_failure) expected=2; state=partial;;
    readonly) expected=1; state=old;;
  esac
  if [ "$result" != "$expected" ]; then echo "FAIL $mode exit=$result"; exit 1; fi
  if ! echo "$output" | grep -q "PROTECTED:$state"; then echo "FAIL $mode protection"; exit 1; fi
  echo "PASS $mode"
done
