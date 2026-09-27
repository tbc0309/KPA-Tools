$ErrorActionPreference = 'Stop'
$Service = Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\helper-module\service.sh') -Raw

function Assert-Match([string]$Pattern, [string]$Name) {
    if ($Service -notmatch $Pattern) { throw "Missing safety rule: $Name" }
    Write-Host "PASS $Name"
}

function Assert-NoMatch([string]$Text, [string]$Pattern, [string]$Name) {
    if ($Text -match $Pattern) { throw "Unexpected rule: $Name" }
    Write-Host "PASS $Name"
}

$PrepareGuard = [regex]::Match(
    $Service,
    '(?s)if \[ "\$1" = "--prepare-ota" \]; then\s+case .*?fi\s+prepare_base_stock'
).Value
if (-not $PrepareGuard) { throw 'Unable to locate the OTA preparation guard.' }
Assert-NoMatch $PrepareGuard 'scheduled_target' 'OTA preparation does not depend on misc slot priority'

Assert-Match '(?s)if \[ "\$1" = "--prepare-ota" \]; then\s+# The running slot is authoritative.*?ACTIVE=\$\(getprop ro\.boot\.slot_suffix\).*?case "\$ACTIVE" in _a\|_b\)' 'running Android slot is authoritative before OTA'
$PatchBody = [regex]::Match($Service, '(?s)patch_inactive\(\).*?^\)', 'Multiline').Value
if (-not $PatchBody) { throw 'Unable to locate patch_inactive.' }
$TargetChecks = [regex]::Matches($PatchBody, 'scheduled_target "\$TARGET"')
if ($TargetChecks.Count -lt 2) { throw 'Scheduled OTA target must be verified before backup and again before writing.' }
if ($PatchBody.LastIndexOf('scheduled_target "$TARGET"') -gt $PatchBody.IndexOf('write_boot_verified')) { throw 'Final scheduled-target verification must precede the boot write.' }
Write-Host 'PASS scheduled OTA target is verified before backup and again before boot write'
Assert-Match '(?s)patch_inactive\(\).*?\[ "\$\(getprop ro\.boot\.slot_suffix\)" = "\$ACTIVE" \]' 'running slot must remain unchanged before boot write'
Assert-Match 'SUCCESS inactive boot \$TARGET patched' 'successful target write is recorded'

Write-Host 'Helper slot policy checks passed.'
