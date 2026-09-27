#!/system/bin/sh
export ASH_STANDALONE=1
. /data/local/tmp/kpa-boot-io.sh
pass=0
check() {
  if "$@"; then pass=$((pass + 1)); else echo "FAIL: $*"; exit 1; fi
}
valid='95 97 0 0 66 67 65 66 1 2 0 0 158 0 111 0 0 0 0 0 0 0 0 0 0 0 0 0 169 34 121 159'
check test "$(boot_target_from_bytes $valid)" = _b
check test -z "$(boot_target_from_bytes $valid 0)"
check test -z "$(boot_target_from_bytes 95 97 0 0 66 67 65 66 1 2 0 0 158 0 111 0 0 0 0 0 0 0 0 0 0 0 0 0 0 34 121 159)"
selected=${1:-_b}
if [ "$selected" = _a ]; then other=_b; else other=_a; fi
check scheduled_target "$selected"
if scheduled_target "$other"; then echo 'FAIL: nonselected slot accepted'; exit 1; fi
pass=$((pass + 1))
if scheduled_target _c; then echo 'FAIL: invalid slot accepted'; exit 1; fi
pass=$((pass + 1))
echo "PASS: $pass read-only boot-control checks"
