# NR-01 test runner. Every NR-01 agent runs Maven only through this script.
#
#   powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent C -Tests "AccessExpiryIT,StudentAccessIT"
#   powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent C -Full
#   powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent C -ResetDb -Tests "AccessExpiryIT"
#
# It takes the machine-wide heavy lock (one Maven run at a time on this PC; the suite crashes when two run together),
# points the tests at the agent's own database cc_test_nr01_<agent>, creating it if missing (-ResetDb drops it first,
# for a Flyway checksum error after a migration changed), loads the PostgreSQL user and password from the main
# checkout's api\.env without printing them, runs Maven in the current worktree's api folder, and always releases
# the lock.
param(
    [Parameter(Mandatory = $true)][ValidateSet("F", "A", "B", "C", "D", "INT")][string]$Agent,
    [string]$Tests = "",
    [switch]$Full,
    [switch]$ResetDb,
    [string]$Goal = "test"
)

$ErrorActionPreference = "Stop"
$lock = "F:\coaching-center-platform\.heavy.lock"
$envFile = "F:\coaching-center-platform\api\.env"
$psql = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
$database = "cc_test_nr01_" + $Agent.ToLower()

if (-not $Full -and $Tests -eq "" -and $Goal -eq "test") {
    Write-Error "Pass -Tests ""ClassA,ClassB"" or -Full."
    exit 2
}

$root = (git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) { Write-Error "Not inside a git worktree."; exit 2 }

# Wait for the lock: one minute per try, up to 90 minutes.
$taken = $false
for ($i = 0; $i -lt 90; $i++) {
    try {
        New-Item -ItemType File -Path $lock -Value $Agent -ErrorAction Stop | Out-Null
        $taken = $true
        break
    }
    catch {
        $holder = (Get-Content $lock -ErrorAction SilentlyContinue)
        Write-Host "heavy lock held by '$holder', waiting 60 s ($($i + 1)/90)"
        Start-Sleep -Seconds 60
    }
}
if (-not $taken) { Write-Error "Gave up waiting for $lock. Tell the orchestrator."; exit 3 }

$exitCode = 1
try {
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
    if ((Get-Content $lock -ErrorAction SilentlyContinue) -eq $Agent) { Remove-Item $lock -ErrorAction SilentlyContinue }
}
exit $exitCode
