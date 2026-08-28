#Requires -Version 5.1
<#
=============================================================================
Fraud Detection Platform - tear down the local stack (Windows PowerShell).

  .\scripts\down.ps1             stop and remove containers and the network
  .\scripts\down.ps1 -Volumes    also delete the MySQL / Cassandra / ES / Kafka
                                 volumes (next start re-initialises from scratch)

Always passes --profile consoles so Kibana and Kafka UI are removed too;
without it Compose considers profiled services out of scope and leaves them
running. Runnable from anywhere in the repo.
=============================================================================
#>
[CmdletBinding()]
param(
    [switch]$Volumes
)

$ErrorActionPreference = 'Stop'
$ProjectName = 'fraud-detection-platform'

$RepoRoot   = Split-Path -Parent $PSScriptRoot
$ComposeDir = Join-Path (Join-Path $RepoRoot 'deploy') 'docker'
if (-not (Test-Path (Join-Path $ComposeDir 'docker-compose.yml'))) {
    Write-Host 'cannot find deploy/docker/docker-compose.yml' -ForegroundColor Red
    exit 1
}

Push-Location $ComposeDir
try {
    # PowerShell 5.1 turns a native command's stderr into a terminating ErrorRecord
    # once the command sits in a consumed pipeline and $ErrorActionPreference is
    # 'Stop'. Capturing under a function-scoped 'Continue' keeps $LASTEXITCODE as
    # the only signal, so the guidance below actually gets printed.
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Host 'docker not found on PATH.' -ForegroundColor Red
        exit 1
    }
    $probe = & {
        $ErrorActionPreference = 'Continue'
        & docker info 2>&1 | Out-Null
        $LASTEXITCODE
    }
    if ($probe -ne 0) {
        Write-Host 'the Docker daemon is not reachable, so there is nothing to stop. Start Docker Desktop first if you expected containers to be running.' -ForegroundColor Red
        exit 1
    }

    $downArgs = @('down', '--remove-orphans')
    if ($Volumes) {
        $downArgs += '--volumes'
        Write-Host 'Removing containers AND volumes - all MySQL, Cassandra, Elasticsearch and Kafka data will be lost.' -ForegroundColor Yellow
    }

    & docker compose -p $ProjectName --profile consoles @downArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "docker compose down failed (exit $LASTEXITCODE)." -ForegroundColor Red
        exit $LASTEXITCODE
    }
    Write-Host 'Stack down.'
} finally {
    Pop-Location
}
