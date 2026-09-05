[CmdletBinding()]
param(
    [ValidateSet('up', 'smoke', 'status', 'logs', 'down')]
    [string]$Action = 'up',
    [switch]$NoBuild,
    [switch]$NoBrowser
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $RepoRoot 'docker-compose-cloud.yml'
$EnvFile = Join-Path $RepoRoot '.env.demo'
$ProjectName = 'xiyouji-demo'
$BaseUrl = 'http://localhost:8080'

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function New-SecureHex([int]$Bytes = 32) {
    $buffer = New-Object byte[] $Bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($buffer)
    } finally {
        $rng.Dispose()
    }
    return ([System.BitConverter]::ToString($buffer) -replace '-', '').ToLowerInvariant()
}

function Ensure-DemoEnv {
    if (Test-Path -LiteralPath $EnvFile) {
        return
    }

    $dbPassword = New-SecureHex 24
    $jwtSecret = New-SecureHex 48
    $content = @"
APP_IMAGE=xiyouji:demo
DB_PASSWORD=$dbPassword
JWT_SECRET=$jwtSecret
HTTP_BIND=127.0.0.1
HTTP_PORT=8080
CORS_ORIGINS=http://localhost:8080,http://127.0.0.1:8080
PUBLIC_BASE_URL=http://localhost:8080
"@
    [System.IO.File]::WriteAllText($EnvFile, $content, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Created local secrets in .env.demo (ignored by Git)." -ForegroundColor DarkGray
}

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

    & docker compose --project-name $ProjectName --env-file $EnvFile --file $ComposeFile @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed with exit code $LASTEXITCODE"
    }
}

function Test-DockerEngine {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'SilentlyContinue'
        & docker info *> $null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    } finally {
        $ErrorActionPreference = $previousPreference
    }
}

function Ensure-DockerEngine {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker CLI was not found. Install Docker Desktop first.'
    }
    if (Test-DockerEngine) {
        return
    }

    $desktopCandidates = @(
        (Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'),
        (if (${env:ProgramFiles(x86)}) { Join-Path ${env:ProgramFiles(x86)} 'Docker\Docker\Docker Desktop.exe' }),
        (Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\Docker Desktop.exe'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Docker\Docker\Docker Desktop.exe')
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }
    $desktop = $desktopCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (-not $desktop) {
        throw 'Docker Engine is not running. Start Docker Desktop and retry.'
    }

    Write-Step 'Starting Docker Desktop'
    Start-Process -FilePath $desktop -WindowStyle Hidden | Out-Null
    foreach ($attempt in 1..60) {
        Start-Sleep -Seconds 3
        if (Test-DockerEngine) {
            return
        }
        if ($attempt % 10 -eq 0) {
            Write-Host "Waiting for Docker Engine... $($attempt * 3)s" -ForegroundColor DarkGray
        }
    }
    throw 'Docker Engine did not become ready within 180 seconds.'
}

function Get-DemoImageName {
    $match = Get-Content -LiteralPath $EnvFile | Where-Object { $_ -match '^APP_IMAGE=(.+)$' } | Select-Object -First 1
    if ($match -and $match -match '^APP_IMAGE=(.+)$') {
        return $Matches[1].Trim()
    }
    return 'xiyouji:demo'
}

function Test-DemoImageExists {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'SilentlyContinue'
        & docker image inspect (Get-DemoImageName) *> $null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    } finally {
        $ErrorActionPreference = $previousPreference
    }
}

function Open-DemoBrowser {
    try {
        Start-Process -FilePath $BaseUrl | Out-Null
        Write-Host "Opened the game in your default browser." -ForegroundColor Green
        return
    } catch {
        Write-Warning "The default browser could not be opened: $($_.Exception.Message)"
    }

    $browserCandidates = @(
        (Join-Path $env:ProgramFiles 'Google\Chrome\Application\chrome.exe'),
        (if (${env:ProgramFiles(x86)}) { Join-Path ${env:ProgramFiles(x86)} 'Microsoft\Edge\Application\msedge.exe' }),
        (Join-Path $env:ProgramFiles 'Microsoft\Edge\Application\msedge.exe')
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }

    $browser = $browserCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if ($browser) {
        Start-Process -FilePath $browser -ArgumentList $BaseUrl | Out-Null
        Write-Host "Opened the game in $browser" -ForegroundColor Green
        return
    }

    Write-Warning "No browser could be opened automatically. Open $BaseUrl manually."
}

function Assert-PortAvailable {
    $runningApp = (& docker compose --project-name $ProjectName --env-file $EnvFile --file $ComposeFile ps --status running --quiet app 2>$null)
    if ($runningApp) {
        return
    }

    $listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) {
        throw "Port 8080 is already in use by process $($listener.OwningProcess). Stop that program before starting the demo."
    }
}

function Invoke-DemoSmoke {
    Write-Step 'Checking health, home page, authentication and a complete single-player session'

    $health = Invoke-RestMethod -Method Get -Uri "$BaseUrl/actuator/health" -TimeoutSec 20
    if ($health.status -ne 'UP') {
        throw "Health status is '$($health.status)', expected 'UP'."
    }

    $homeResponse = Invoke-WebRequest -UseBasicParsing -Method Get -Uri $BaseUrl -TimeoutSec 20
    if ($homeResponse.StatusCode -ne 200 -or $homeResponse.Content.Length -lt 100) {
        throw 'Home page did not return the built frontend.'
    }

    $guest = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/guest" -Headers @{
        'X-Idempotency-Key' = [guid]::NewGuid().ToString()
    } -TimeoutSec 20
    if ([string]::IsNullOrWhiteSpace([string]$guest.token)) {
        throw 'Guest login did not return a JWT.'
    }

    $authHeaders = @{
        Authorization = "Bearer $($guest.token)"
        'Content-Type' = 'application/json'
        'X-Idempotency-Key' = [guid]::NewGuid().ToString()
    }
    $created = $null
    $deleted = $false
    try {
        $created = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/game/new" -Headers $authHeaders `
            -Body '{"characterClass":"SUN_WUKONG"}' -TimeoutSec 20
        if (-not $created.success -or [string]::IsNullOrWhiteSpace([string]$created.sessionId)) {
            throw 'Creating a single-player session did not return sessionId/success.'
        }

        $readHeaders = @{ Authorization = "Bearer $($guest.token)" }
        $state = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/game/state/$($created.sessionId)" `
            -Headers $readHeaders -TimeoutSec 20
        if ($state.sessionId -ne $created.sessionId -or $null -eq $state.stateVersion) {
            throw 'The session read-back did not match the created session.'
        }

        $deleteHeaders = @{
            Authorization = "Bearer $($guest.token)"
            'X-Expected-State-Version' = [string]$state.stateVersion
            'X-Idempotency-Key' = [guid]::NewGuid().ToString()
        }
        $removed = Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/game/sessions/$($created.sessionId)" `
            -Headers $deleteHeaders -TimeoutSec 20
        if (-not $removed.success) {
            throw 'The smoke-test session could not be cleaned up.'
        }
        $deleted = $true
    } finally {
        if ($null -ne $created -and -not $deleted) {
            try {
                $latest = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/game/state/$($created.sessionId)" `
                    -Headers @{ Authorization = "Bearer $($guest.token)" } -TimeoutSec 10
                Invoke-RestMethod -Method Delete -Uri "$BaseUrl/api/game/sessions/$($created.sessionId)" -Headers @{
                    Authorization = "Bearer $($guest.token)"
                    'X-Expected-State-Version' = [string]$latest.stateVersion
                    'X-Idempotency-Key' = [guid]::NewGuid().ToString()
                } -TimeoutSec 10 | Out-Null
            } catch {
                Write-Warning "Smoke-test cleanup failed: $($_.Exception.Message)"
            }
        }
    }

    Write-Host 'PASS: health + frontend + guest JWT + create/read/delete game session.' -ForegroundColor Green
}

Ensure-DemoEnv

try {
    Ensure-DockerEngine

    switch ($Action) {
        'up' {
            Assert-PortAvailable
            Write-Step 'Validating the demo configuration'
            Invoke-Compose config --quiet

            $useCachedImage = $NoBuild -and (Test-DemoImageExists)
            if ($NoBuild -and -not $useCachedImage) {
                Write-Host 'No local demo image was found. This is the first launch, so a full build is required.' -ForegroundColor Yellow
            }

            if ($useCachedImage) {
                Write-Step 'Starting MySQL, Redis and the application from the cached image'
                Invoke-Compose up -d --no-build --wait --wait-timeout 600
            } else {
                Write-Step 'Building and starting MySQL, Redis and the application (first launch may take several minutes)'
                Invoke-Compose up -d --build --wait --wait-timeout 600
            }
            Invoke-DemoSmoke
            Invoke-Compose ps
            Write-Host "`nDemo is ready: $BaseUrl" -ForegroundColor Green
            Write-Host "Swagger: $BaseUrl/swagger-ui/index.html" -ForegroundColor Green
            if (-not $NoBrowser) {
                Open-DemoBrowser
            }
        }
        'smoke' {
            Invoke-DemoSmoke
        }
        'status' {
            Invoke-Compose ps
        }
        'logs' {
            & docker compose --project-name $ProjectName --env-file $EnvFile --file $ComposeFile logs --tail 150 -f app
            exit $LASTEXITCODE
        }
        'down' {
            Write-Step 'Stopping the demo (database volumes are preserved)'
            Invoke-Compose down
            Write-Host 'Demo stopped. No database volume was deleted.' -ForegroundColor Green
        }
    }
} catch {
    Write-Host "`nDEMO FAILED: $($_.Exception.Message)" -ForegroundColor Red
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        try {
            & docker compose --project-name $ProjectName --env-file $EnvFile --file $ComposeFile ps
            & docker compose --project-name $ProjectName --env-file $EnvFile --file $ComposeFile logs --tail 100 app
        } catch {}
    }
    exit 1
}
