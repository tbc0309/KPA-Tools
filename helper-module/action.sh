#!/system/bin/sh
# Synchronize the internal KPA-Tools repository to an inserted TF card.
MODDIR=${0%/*}
exec /system/bin/sh "$MODDIR/service.sh" --sync
