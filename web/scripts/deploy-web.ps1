# Uploads a release from web/scripts/release-build.sh and swaps it in on the VPS.
# Windows OpenSSH (this PowerShell), not Git Bash ssh:
#
#   powershell -NoProfile -File web\scripts\deploy-web.ps1 -Package <out.tgz> [-DryRun]
#
# -DryRun stages and checks the release on the box without swapping it.
param(
    [Parameter(Mandatory = $true)][string]$Package,
    [string]$Box = "exam-kvm",
    [switch]$DryRun
)
# Exit codes are checked by hand: with "Stop", Windows PowerShell 5.1 can abort
# on the first stderr line of ssh, half way through the swap.
if (-not (Test-Path $Package)) { throw "no package at $Package" }

$sha = (Get-FileHash -Algorithm SHA256 $Package).Hash.ToLower()
$remote = "/tmp/web-release-$sha.tgz"
scp -o BatchMode=yes $Package "${Box}:$remote"
if ($LASTEXITCODE -ne 0) { throw "upload failed" }

$dry = if ($DryRun) { "1" } else { "0" }
$scripts = Split-Path -Parent $MyInvocation.MyCommand.Path
$body = (Get-Content -Raw (Join-Path $scripts "release-checks.sh")) + "`n" +
    "PKG=$remote`nEXPECTED_SHA=$sha`nDRY_RUN=$dry`n" +
    (Get-Content -Raw (Join-Path $scripts "deploy-on-box.sh"))
# Strip the CRs and the BOM PowerShell adds before bash reads the script.
$body | ssh -o BatchMode=yes $Box "tr -d '\r' | sed '1s/^\xEF\xBB\xBF//' | bash -s"
$code = $LASTEXITCODE
if ($DryRun) { ssh -o BatchMode=yes $Box "rm -f $remote" | Out-Null }
if ($code -ne 0) { throw "deploy refused or failed (exit $code); the running release is unchanged unless it says rolled back" }
