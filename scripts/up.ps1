#Requires -Version 5.1
<#
=============================================================================
Fraud Detection Platform - one-command local launch (Windows PowerShell).

  .\scripts\up.ps1              lean stack: infra + Jaeger + the nine services
  .\scripts\up.ps1 -Full        also starts Kibana and Kafka UI (~2 GB more)
  .\scripts\up.ps1 -NoBuild     reuse existing images, skip the image builds
  .\scripts\up.ps1 -Recreate    force-recreate containers (config changed)

Runnable from anywhere in the repo. Before launching it checks the things that
actually break a first run on an unfamiliar machine - Docker daemon reachable,
Compose v2, enough memory allocated, host ports free - and names the exact fix
for each. Then it waits for every container to report healthy and prints the
endpoints.

If PowerShell refuses to run this file, the execution policy is blocking it:
  powershell -ExecutionPolicy Bypass -File .\scripts\up.ps1

Linux / macOS / Git Bash users: run scripts/up.sh instead.
=============================================================================
#>
[CmdletBinding()]
param(
    [switch]$Full,
    [switch]$NoBuild,
    [switch]$Recreate
)

$ErrorActionPreference = 'Stop'

$ProjectName          = 'fraud-detection-platform'
$HealthTimeoutSeconds = 600
# Memory floors (GiB) - warnings, not hard failures.
$MinMemGiBLean = 8
$MinMemGiBFull = 12

# ------------------------------------------------------------------ helpers --
function Write-Ok    { param([string]$Text) Write-Host '  ok  ' -ForegroundColor Green  -NoNewline; Write-Host $Text }
function Write-Warn  { param([string]$Text) Write-Host 'warn  ' -ForegroundColor Yellow -NoNewline; Write-Host $Text }
function Write-Fail  { param([string]$Text) Write-Host 'fail  ' -ForegroundColor Red    -NoNewline; Write-Host $Text }
function Write-Dim   { param([string]$Text) Write-Host $Text -ForegroundColor DarkGray }
function Stop-WithError {
    param([string]$Text)
    Write-Fail $Text
    exit 1
}

# TcpClient rather than Get-NetTCPConnection: the latter needs the Windows-only
# NetTCPIP module, which is absent on PowerShell 7 for Linux/macOS.
function Test-PortInUse {
    param([int]$Port)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        if ($async.AsyncWaitHandle.WaitOne(300)) {
            $client.EndConnect($async)   # throws when the port is closed
            return $true
        }
        return $false
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

# Every docker call whose output we capture goes through here.
#
# PowerShell 5.1 turns a native command's stderr into an ErrorRecord as soon as
# the command sits in a pipeline someone consumes, and the script-level
# $ErrorActionPreference = 'Stop' makes that record terminating - so `docker info`
# against a stopped daemon would kill the script before the friendly message
# below ever printed. 'Continue' here is function-scoped, so it relaxes only
# these captured calls; 2>&1 folds stderr into the captured text instead of the
# console. $LASTEXITCODE stays the single source of truth.
function Invoke-Docker {
    param([Parameter(Mandatory)][AllowEmptyCollection()][string[]]$DockerArgs)
    $ErrorActionPreference = 'Continue'
    $raw  = & docker @DockerArgs 2>&1
    $code = $LASTEXITCODE
    $lines = @($raw | ForEach-Object { [string]$_ })
    return [pscustomobject]@{
        ExitCode = $code
        Lines    = $lines
        Text     = ($lines -join "`n").Trim()
    }
}

# ------------------------------------------------- locate the compose dir ----
$RepoRoot    = Split-Path -Parent $PSScriptRoot
$ComposeDir  = Join-Path (Join-Path $RepoRoot 'deploy') 'docker'
if (-not (Test-Path (Join-Path $ComposeDir 'docker-compose.yml'))) {
    Stop-WithError "cannot find deploy/docker/docker-compose.yml under $RepoRoot"
}

# Run from the compose directory so Compose discovers docker-compose.yml and
# .env itself, instead of passing -f and --project-directory.
Push-Location $ComposeDir
try {

# The consoles profile must be passed to every subcommand that should see those
# two containers - including `down` - or Compose treats them as out of scope.
$ComposeBase = @('compose', '-p', $ProjectName)
if ($Full) { $ComposeBase += @('--profile', 'consoles') }

# --------------------------------------------------------------- preflight ---
Write-Host 'Preflight' -ForegroundColor White

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Stop-WithError 'docker not found on PATH. Install Docker Desktop and make sure it is running.'
}

# Native-command stderr is captured, never piped raw - see Invoke-Docker above.
if ((Invoke-Docker -DockerArgs @('info')).ExitCode -ne 0) {
    Stop-WithError 'the Docker daemon is not reachable. Start Docker Desktop, wait for the whale icon to settle, and retry.'
}
Write-Ok 'Docker daemon reachable'

# Compose v2 is required: docker-compose.yml uses `condition:
# service_completed_successfully` and top-level `name:`, neither of which the
# legacy Python docker-compose understands.
$composeProbe   = Invoke-Docker -DockerArgs @('compose', 'version', '--short')
$composeVersion = $composeProbe.Text
if ($composeProbe.ExitCode -ne 0 -or -not $composeVersion) {
    Stop-WithError "'docker compose' (v2) is unavailable. The legacy 'docker-compose' binary cannot run this file - update Docker Desktop."
}
if ($composeVersion.StartsWith('1.')) {
    Stop-WithError "Compose $composeVersion is too old; v2.0+ is required."
}
Write-Ok "Docker Compose v$composeVersion"

# BuildKit carries the shared Maven cache mount in the Dockerfiles.
if (-not $NoBuild -and $env:DOCKER_BUILDKIT -eq '0') {
    Stop-WithError 'DOCKER_BUILDKIT=0 is set, but the service Dockerfiles need BuildKit for the shared Maven cache. Remove it and retry.'
}

# Memory: the single most common cause of containers dying with exit code 137.
$minMemGiB = $MinMemGiBLean
if ($Full) { $minMemGiB = $MinMemGiBFull }
$memRaw = (Invoke-Docker -DockerArgs @('info', '--format', '{{.MemTotal}}')).Text
[int64]$memBytes = 0
if ([int64]::TryParse($memRaw, [ref]$memBytes) -and $memBytes -gt 0) {
    $memGiB = [math]::Floor($memBytes / 1GB)
    if ($memGiB -lt $minMemGiB) {
        Write-Warn "Docker has $memGiB GiB of memory; this stack wants at least $minMemGiB GiB."
        Write-Warn '  Containers may be OOM-killed (exit code 137). Raise it in'
        Write-Warn '  Docker Desktop -> Settings -> Resources -> Memory, or drop -Full to skip the consoles.'
    } else {
        Write-Ok "Docker memory: $memGiB GiB (floor $minMemGiB GiB)"
    }
} else {
    Write-Warn 'could not read the daemon''s memory allocation; skipping that check'
}

# Host ports. Skipped when our own stack is already up, since those containers
# legitimately hold the ports. Filtering on the hex-id shape means a docker
# error message on the captured stream is never mistaken for a container id.
$alreadyUp = @((Invoke-Docker -DockerArgs ($ComposeBase + @('ps', '-q'))).Lines |
              Where-Object { $_ -match '^[0-9a-f]{12,}$' })
if ($alreadyUp.Count -gt 0) {
    Write-Dim '      stack already running - skipping the port scan'
} else {
    function Get-EffectivePort {
        param([string]$Name, [int]$Default)
        $value = [Environment]::GetEnvironmentVariable($Name)
        if ($value) { return [int]$value }
        return $Default
    }

    $portChecks = @(
        @{ Var = 'MYSQL_PORT';           Default = 3306;  What = 'MySQL' }
        @{ Var = 'CASSANDRA_PORT';       Default = 9042;  What = 'Cassandra' }
        @{ Var = 'KAFKA_INTERNAL_PORT';  Default = 9092;  What = 'Kafka (in-network listener)' }
        @{ Var = 'KAFKA_HOST_PORT';      Default = 29092; What = 'Kafka (host listener)' }
        @{ Var = 'SCHEMA_REGISTRY_PORT'; Default = 8090;  What = 'Schema Registry' }
        @{ Var = 'ES_PORT';              Default = 9200;  What = 'Elasticsearch' }
        @{ Var = 'OTLP_HTTP_PORT';       Default = 4318;  What = 'OTel Collector (HTTP)' }
        @{ Var = 'OTLP_GRPC_PORT';       Default = 4317;  What = 'OTel Collector (gRPC)' }
        @{ Var = 'JAEGER_UI_PORT';       Default = 16686; What = 'Jaeger UI' }
        @{ Var = 'GATEWAY_PORT';         Default = 8080;  What = 'api-gateway' }
        @{ Var = 'CUSTOMER_PORT';        Default = 8081;  What = 'customer-service' }
        @{ Var = 'ACCOUNT_PORT';         Default = 8082;  What = 'account-service' }
        @{ Var = 'TRANSACTION_PORT';     Default = 8083;  What = 'transaction-service' }
        @{ Var = 'FRAUD_PORT';           Default = 8084;  What = 'fraud-detection-service' }
        @{ Var = 'RISK_HTTP_PORT';       Default = 8085;  What = 'risk-scoring-service (REST)' }
        @{ Var = 'RISK_GRPC_PORT';       Default = 9095;  What = 'risk-scoring-service (gRPC)' }
        @{ Var = 'ALERT_PORT';           Default = 8086;  What = 'alert-service' }
        @{ Var = 'NOTIFICATION_PORT';    Default = 8087;  What = 'notification-service' }
        @{ Var = 'AUDIT_PORT';           Default = 8088;  What = 'audit-service' }
    )
    if ($Full) {
        $portChecks += @{ Var = 'KIBANA_PORT';   Default = 5601; What = 'Kibana' }
        $portChecks += @{ Var = 'KAFKA_UI_PORT'; Default = 8100; What = 'Kafka UI' }
    }

    $conflicts = 0
    foreach ($check in $portChecks) {
        $port = Get-EffectivePort -Name $check.Var -Default $check.Default
        if (Test-PortInUse -Port $port) {
            $conflicts++
            Write-Fail "port $port is already in use (needed by $($check.What))"
            Write-Host "        fix: add $($check.Var)=<free port> to deploy\docker\.env"
        }
    }
    if ($conflicts -gt 0) {
        Stop-WithError "$conflicts port conflict(s). Free the ports or override them as shown above, then retry."
    }
    Write-Ok 'all host ports free'
}

if (Test-Path '.env') {
    Write-Ok '.env found (overrides the compose defaults)'
} else {
    Write-Dim '      no .env - using the dev defaults baked into docker-compose.yml'
}

# ------------------------------------------------------------------ launch ---
$upArgs = @('up', '-d')
if (-not $NoBuild) { $upArgs += '--build' }
if ($Recreate)     { $upArgs += '--force-recreate' }

Write-Host ''
if ($Full) {
    Write-Host 'Starting the full stack (with consoles)' -ForegroundColor White
} else {
    Write-Host 'Starting the lean stack' -ForegroundColor White -NoNewline
    Write-Dim '  (-Full adds Kibana + Kafka UI)'
}
if (-not $NoBuild) { Write-Dim 'First build compiles nine images from source; expect several minutes.' }
Write-Host ''

& docker @ComposeBase @upArgs
if ($LASTEXITCODE -ne 0) { Stop-WithError "docker compose up failed (exit $LASTEXITCODE)." }

# ------------------------------------------------------- wait for health ----
Write-Host ''
Write-Host 'Waiting for containers to become healthy' -ForegroundColor White -NoNewline
Write-Dim " (timeout ${HealthTimeoutSeconds}s)"

$inspectFormat = '{{.Name}} {{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}} {{.State.ExitCode}}'
$deadline   = (Get-Date).AddSeconds($HealthTimeoutSeconds)
$lastReport = ''

while ($true) {
    $pending = @()
    $broken  = @()

    foreach ($id in @((Invoke-Docker -DockerArgs ($ComposeBase + @('ps', '-q'))).Lines |
                      Where-Object { $_ -match '^[0-9a-f]{12,}$' })) {
        $line  = (Invoke-Docker -DockerArgs @('inspect', '--format', $inspectFormat, $id)).Text
        $parts = $line -split '\s+'
        # Anything shorter than the four fields the format asks for is a docker
        # error rather than a container state - skip it and retry next tick.
        if ($parts.Count -lt 4) { continue }
        $name     = $parts[0].TrimStart('/')
        $status   = $parts[1]
        $health   = $parts[2]
        $exitCode = $parts[3]

        if ($status -eq 'running' -and ($health -eq 'healthy' -or $health -eq 'none')) {
            continue                                   # ready
        } elseif ($status -eq 'running' -and $health -eq 'starting') {
            $pending += $name
        } elseif ($status -eq 'running') {
            $pending += "$name($health)"
        } elseif ($status -eq 'exited') {
            # cassandra-init is a one-shot: exit 0 is success, anything else is not.
            if ($exitCode -ne '0') { $broken += "$name(exit $exitCode)" }
        } else {
            $pending += "$name($status)"
        }
    }

    if ($broken.Count -gt 0) {
        Write-Host ''
        Write-Fail ("container(s) failed: " + ($broken -join ' '))
        Write-Host "        logs:  docker compose -p $ProjectName logs --tail=80"
        Write-Host '        exit code 137 means out of memory - raise Docker''s memory allocation.'
        exit 1
    }

    if ($pending.Count -eq 0) {
        Write-Host ''
        Write-Ok 'all containers healthy'
        break
    }

    if ((Get-Date) -ge $deadline) {
        Write-Host ''
        Write-Fail ("timed out after ${HealthTimeoutSeconds}s; still not ready: " + ($pending -join ' '))
        Write-Host "        status: docker compose -p $ProjectName ps"
        Write-Host "        logs:   docker compose -p $ProjectName logs --tail=80"
        exit 1
    }

    $report = $pending -join ' '
    if ($report -ne $lastReport) {
        Write-Dim "      waiting for: $report"
        $lastReport = $report
    }
    Start-Sleep -Seconds 5
}

# ---------------------------------------------------------------- endpoints --
# Ask Compose for the actually-published ports so the table stays correct under
# any .env override.
function Get-PublishedPort {
    param([string]$Service, [int]$ContainerPort)
    $mapping = (Invoke-Docker -DockerArgs ($ComposeBase + @('port', $Service, "$ContainerPort"))).Text
    # Expect "0.0.0.0:8080"; a docker error on the captured stream will not match.
    if ($mapping -match ':(\d+)\s*$') { return $Matches[1] }
    return '?'
}

Write-Host ''
Write-Host 'Endpoints' -ForegroundColor White
Write-Host ('  {0,-24}http://localhost:{1}' -f 'api-gateway',     (Get-PublishedPort 'api-gateway' 8080))
Write-Host ('  {0,-24}http://localhost:{1}/swagger-ui.html' -f 'transaction-service', (Get-PublishedPort 'transaction-service' 8083))
Write-Host ('  {0,-24}http://localhost:{1}' -f 'Jaeger (traces)', (Get-PublishedPort 'jaeger' 16686))
Write-Host ('  {0,-24}http://localhost:{1}/subjects' -f 'Schema Registry', (Get-PublishedPort 'schema-registry' 8081))
Write-Host ('  {0,-24}http://localhost:{1}' -f 'Elasticsearch',   (Get-PublishedPort 'elasticsearch' 9200))
if ($Full) {
    Write-Host ('  {0,-24}http://localhost:{1}' -f 'Kibana',   (Get-PublishedPort 'kibana' 5601))
    Write-Host ('  {0,-24}http://localhost:{1}' -f 'Kafka UI', (Get-PublishedPort 'kafka-ui' 8080))
} else {
    Write-Dim '  Kibana / Kafka UI       not started - re-run with -Full'
}

$gatewayPort = Get-PublishedPort 'api-gateway' 8080
$adminUser   = $env:ADMIN_USERNAME; if (-not $adminUser) { $adminUser = 'admin' }
$adminPass   = $env:ADMIN_PASSWORD; if (-not $adminPass) { $adminPass = 'admin-change-me' }

Write-Host ''
Write-Host 'Next' -ForegroundColor White
# Built with -f and a single-quoted template so the JSON keeps plain double quotes.
$loginBody = '{{"username":"{0}","password":"{1}"}}' -f $adminUser, $adminPass
Write-Host ("  Get a token:  curl.exe -s localhost:{0}/api/v1/auth/login -H 'Content-Type: application/json' -d '{1}'" -f $gatewayPort, $loginBody)
Write-Host '  Tear down:    .\scripts\down.ps1          (add -Volumes to wipe the data)'

} finally {
    Pop-Location
}
