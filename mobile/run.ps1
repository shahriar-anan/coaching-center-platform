# Runs the dev flavor against this PC's Wi-Fi address.
# From mobile/: .\run.ps1
# Extra arguments are passed through to flutter run.

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$wifi = Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias 'Wi-Fi' -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '169.254.*' } |
    Select-Object -First 1 -ExpandProperty IPAddress

if (-not $wifi) {
    $wifi = Get-NetIPConfiguration |
        Where-Object { $_.NetAdapter.Status -eq 'Up' -and $_.IPv4Address } |
        ForEach-Object { $_.IPv4Address.IPAddress } |
        Where-Object { $_ -and $_ -notlike '127.*' -and $_ -notlike '169.254.*' } |
        Select-Object -First 1
}

if (-not $wifi) {
    Write-Error 'No Wi-Fi IPv4 address found. Connect this PC to the same network as the phone.'
}

$baseUrl = "http://${wifi}:8080"
Write-Host "flutter run --flavor dev --dart-define=API_BASE_URL=$baseUrl"
flutter run --flavor dev "--dart-define=API_BASE_URL=$baseUrl" @args
