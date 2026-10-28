# NR-01 test runner. Every NR-01 agent runs Maven only through this script.
#
#   powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent C -Tests "AccessExpiryIT,StudentAccessIT"
#   powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent C -Full
#   powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent C -ResetDb -Tests "AccessExpiryIT"
#
# Concurrency (v2, 28 October 2026):
#   - A targeted run (-Tests) shares the machine with one other targeted run: up to -Slots runs at once (default 2),
#     and only while at least -MinFreeGb of RAM is free (default 3). One run uses about 1.2 GB.
#   - A full run (-Full) is exclusive: it takes the machine-wide F:\coaching-center-platform\.heavy.lock, waits for the
#     running targeted runs to finish, and blocks new ones. Web builds use the same lock file.
#   - Waiting runs are served in arrival order (a ticket per run), so nobody is starved by retry timing.
# Each agent has its own database cc_test_nr01_<agent>, created if missing (-ResetDb drops it first, for a Flyway
# checksum error after a migration changed). The PostgreSQL user and password come from the main checkout's
# api\.env and are never printed. Everything taken is released in the finally block.
param(
    [Parameter(Mandatory = $true)][ValidateSet("F", "A", "B", "C", "D", "INT", "W1", "W2", "W3")][string]$Agent,
    [string]$Tests = "",
    [switch]$Full,
    [switch]$ResetDb,
    [string]$Goal = "test",
    [int]$Slots = 2,
    [double]$MinFreeGb = 3.0
)

$ErrorActionPreference = "Stop"
$base = "F:\coaching-center-platform"
$exclusive = "$base\.heavy.lock"
$slotDir = "$base\.nr01-slots"
$queueDir = "$base\.nr01-queue"
$envFile = "$base\api\.env"
$psql = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
$database = "cc_test_nr01_" + $Agent.ToLower()

if (-not $Full -and $Tests -eq "" -and $Goal -eq "test") {
    Write-Error "Pass -Tests ""ClassA,ClassB"" or -Full."
    exit 2
}

$root = (git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) { Write-Error "Not inside a git worktree."; exit 2 }

New-Item -ItemType Directory -Force $slotDir, $queueDir | Out-Null

# A marker file holds "<agent> <pid>". It is stale when that process is gone. A marker in the old format (no pid, or
# empty) is stale only after three hours.
function Test-Stale([string]$path) {
    $text = (Get-Content $path -ErrorAction SilentlyContinue | Out-String).Trim()
    $parts = $text -split '\s+'
    $id = 0
    if ($parts.Count -ge 2 -and [int]::TryParse($parts[1], [ref]$id)) {
        return -not [bool](Get-Process -Id $id -ErrorAction SilentlyContinue)
    }
    $item = Get-Item $path -ErrorAction SilentlyContinue
    return ($null -ne $item) -and ($item.LastWriteTime -lt (Get-Date).AddHours(-3))
}

function Clear-Stale {
    foreach ($dir in @($slotDir, $queueDir)) {
        foreach ($file in Get-ChildItem $dir -File -ErrorAction SilentlyContinue) {
            if (Test-Stale $file.FullName) { Remove-Item $file.FullName -ErrorAction SilentlyContinue }
        }
    }
    if ((Test-Path $exclusive) -and (Test-Stale $exclusive)) { Remove-Item $exclusive -ErrorAction SilentlyContinue }
}

function Get-FreeGb { return (Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB }

$mine = "$Agent $PID"
$ticket = Join-Path $queueDir ("{0:D19}_{1}" -f (Get-Date).Ticks, $Agent)
Set-Content -Path $ticket -Value $mine
$slot = $null
$holdsExclusive = $false
$exitCode = 1

try {
    $acquired = $false
    for ($i = 0; $i -lt 1440 -and -not $acquired; $i++) {
        Clear-Stale
        $head = Get-ChildItem $queueDir -File | Sort-Object Name | Select-Object -First 1
        if ($null -ne $head -and $head.FullName -eq $ticket) {
            if ($Full) {
                if (-not $holdsExclusive) {
                    try { New-Item -ItemType File -Path $exclusive -Value $mine -ErrorAction Stop | Out-Null; $holdsExclusive = $true } catch { }
                }
                if ($holdsExclusive -and @(Get-ChildItem $slotDir -File -ErrorAction SilentlyContinue).Count -eq 0) { $acquired = $true }
            }
            elseif (-not (Test-Path $exclusive) -and (Get-FreeGb) -ge $MinFreeGb) {
                for ($s = 1; $s -le $Slots -and $null -eq $slot; $s++) {
                    try { New-Item -ItemType File -Path "$slotDir\slot$s" -Value $mine -ErrorAction Stop | Out-Null; $slot = "$slotDir\slot$s" } catch { }
                }
                if ($null -ne $slot) { $acquired = $true }
            }
        }
        if (-not $acquired) {
            if ($i % 4 -eq 0) {
                $queued = @(Get-ChildItem $queueDir -File).Count
                $running = @(Get-ChildItem $slotDir -File -ErrorAction SilentlyContinue).Count
                Write-Host ("waiting: {0} queued, {1}/{2} runs active, exclusive lock: {3}, free RAM {4:N1} GB" -f $queued, $running, $Slots, (Test-Path $exclusive), (Get-FreeGb))
            }
            Start-Sleep -Seconds 15
        }
    }
    if (-not $acquired) { Write-Error "Gave up waiting after 6 hours. Tell the orchestrator."; exit 3 }
    Remove-Item $ticket -ErrorAction SilentlyContinue

    $values = @{}
    foreach ($line in Get-Content $envFile) {
        if ($line -match '^(DB_URL|DB_USERNAME|DB_PASSWORD)=(.*)$') { $values[$Matches[1]] = $Matches[2].Trim().Trim('"') }
    }
    $hostPort = $values["DB_URL"] -replace '^jdbc:postgresql://([^/]+)/.*$', '$1'
    $pgHost, $pgPort = $hostPort -split ':'
    if (-not $pgPort) { $pgPort = "5432" }

    $env:PGPASSWORD = $values["DB_PASSWORD"]
    if ($ResetDb) {
        & $psql -h $pgHost -p $pgPort -U $values["DB_USERNAME"] -d postgres -q -c "DROP DATABASE IF EXISTS $database WITH (FORCE)" | Out-Null
    }
    $exists = & $psql -h $pgHost -p $pgPort -U $values["DB_USERNAME"] -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$database'"
    if ("$exists".Trim() -ne "1") {
        & $psql -h $pgHost -p $pgPort -U $values["DB_USERNAME"] -d postgres -q -c "CREATE DATABASE $database" | Out-Null
        Write-Host "created database $database"
    }
    Remove-Item Env:\PGPASSWORD

    $env:TEST_DB_URL = "jdbc:postgresql://$hostPort/$database"
    $env:TEST_DB_USER = $values["DB_USERNAME"]
    $env:TEST_DB_PASSWORD = $values["DB_PASSWORD"]

    Push-Location (Join-Path $root "api")
    try {
        if ($Full) {
            & mvn -q $Goal
        }
        elseif ($Tests -ne "") {
            & mvn -q $Goal "-Dtest=$Tests" "-Dsurefire.failIfNoSpecifiedTests=false"
        }
        else {
            & mvn -q $Goal
        }
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
}
finally {
    Remove-Item Env:\TEST_DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Item $ticket -ErrorAction SilentlyContinue
    if ($null -ne $slot) { Remove-Item $slot -ErrorAction SilentlyContinue }
    if ($holdsExclusive) { Remove-Item $exclusive -ErrorAction SilentlyContinue }
}
exit $exitCode
