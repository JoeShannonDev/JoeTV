<#
.SYNOPSIS
    Turns a downloaded Google service account JSON key into local.properties lines.

.DESCRIPTION
    Run this once after creating the service account. It prints the three values
    JoeTV needs, with the PEM private key flattened to a single line so it
    survives both a .properties file and a Kotlin string literal.

    PowerShell version, so no Python install is required.

.EXAMPLE
    .\tools\prepare_service_account.ps1 $HOME\Downloads\joetv-504907-b5cab7fad8c5.json

.EXAMPLE
    .\tools\prepare_service_account.ps1 key.json -CalendarId you@gmail.com
#>

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$KeyPath,

    [string]$CalendarId
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $KeyPath)) {
    Write-Host ""
    Write-Host "No such file: $KeyPath" -ForegroundColor Red
    Write-Host "Pass the real path to the JSON key you downloaded, e.g.:" -ForegroundColor Yellow
    Write-Host "  .\tools\prepare_service_account.ps1 `"$HOME\Downloads\your-key.json`""
    Write-Host ""
    exit 1
}

try {
    $key = Get-Content -LiteralPath $KeyPath -Raw | ConvertFrom-Json
}
catch {
    Write-Host "`nThat file is not valid JSON: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

if ($key.type -ne "service_account") {
    Write-Host "`nThat JSON is not a service account key (type is '$($key.type)')." -ForegroundColor Red
    Write-Host "Download it from IAM & Admin > Service Accounts > Keys > Add key > JSON."
    exit 1
}

if (-not $key.client_email -or -not $key.private_key) {
    Write-Host "`nKey file is missing client_email or private_key." -ForegroundColor Red
    exit 1
}

$header = "-----BEGIN PRIVATE KEY-----"
$footer = "-----END PRIVATE KEY-----"

if ($key.private_key -notlike "*$header*") {
    Write-Host "`nThat key is not in PKCS#8 PEM format (no '$header' line)." -ForegroundColor Red
    Write-Host "Make sure you downloaded a JSON key, not a P12."
    exit 1
}

# Strip the PEM armor and every line break. The app feeds this straight into
# PKCS8EncodedKeySpec, and a .properties value cannot hold real newlines.
$body = $key.private_key
$body = $body.Substring($body.IndexOf($header) + $header.Length)
$body = $body.Substring(0, $body.IndexOf($footer))
$flattened = ($body -replace '\s', '')

# Fail loudly here rather than letting Android throw InvalidKeySpecException at
# runtime, where the only symptom is an empty calendar card.
try {
    [void][Convert]::FromBase64String($flattened)
}
catch {
    Write-Host "`nPrivate key body is not valid base64: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

if (-not $CalendarId) {
    Write-Host ""
    Write-Host "Which calendar should JoeTV read?"
    Write-Host "This is normally your own Gmail address."
    Write-Host ""
    $CalendarId = (Read-Host "  calendar ID").Trim()
}

$line = "=" * 72

Write-Host ""
Write-Host $line
Write-Host "1. Share your calendar with this address, 'See all event details':"
Write-Host ""
Write-Host "   $($key.client_email)" -ForegroundColor Cyan
Write-Host ""
Write-Host "2. Add these lines to local.properties, then rebuild JoeTV:"
Write-Host ""
Write-Host "JOETV_GOOGLE_SERVICE_ACCOUNT_EMAIL=$($key.client_email)"
Write-Host "JOETV_GOOGLE_SERVICE_ACCOUNT_KEY=$flattened"
Write-Host "JOETV_GOOGLE_CALENDAR_ID=$CalendarId"
Write-Host $line
Write-Host ""
Write-Host "Step 1 is not optional -- without it the service account can reach" -ForegroundColor Yellow
Write-Host "the API but sees none of your events, and the card stays empty." -ForegroundColor Yellow
Write-Host ""
