[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location $repoRoot

if (-not (Test-Path '.env')) {
    Copy-Item '.env.example' '.env'
    Write-Host 'Created .env from .env.example. Add your LocalStack auth token, then rerun this command.'
    exit 1
}

$localstackToken = $env:LOCALSTACK_AUTH_TOKEN
if ([string]::IsNullOrWhiteSpace($localstackToken)) {
    $tokenLine = Get-Content '.env' | Where-Object { $_ -match '^LOCALSTACK_AUTH_TOKEN=' } | Select-Object -Last 1
    if ($tokenLine) {
        $localstackToken = ($tokenLine -replace '^LOCALSTACK_AUTH_TOKEN=', '').Trim().Trim('"').Trim("'")
    }
}
if ([string]::IsNullOrWhiteSpace($localstackToken)) {
    throw 'LocalStack requires a developer auth token. Set LOCALSTACK_AUTH_TOKEN in .env; the token is intentionally absent from .env.example.'
}

docker compose up --detach --wait
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
docker compose ps
