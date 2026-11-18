[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location $repoRoot

if ($PSCmdlet.ShouldProcess('FulfillX local PostgreSQL, LocalStack, and Mailpit volumes', 'DELETE ALL PERSISTED DATA')) {
    docker compose down --volumes --remove-orphans
    exit $LASTEXITCODE
}
