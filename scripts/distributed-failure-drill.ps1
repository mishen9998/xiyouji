[CmdletBinding()]
param(
    [string]$ComposeFile = "docker-compose.yml",
    [string]$BaseUrl = "http://localhost:8080",
    # A cold Spring Boot start can take roughly two minutes on a developer
    # laptop while JPA, Redisson and Flyway initialize together.
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

function Write-Pass([string]$Message) {
    Write-Host "[PASS] $Message" -ForegroundColor Green
}

function Write-Step([string]$Message) {
    Write-Host "[STEP] $Message" -ForegroundColor Cyan
}

function Invoke-HealthRequest {
    param([string]$Uri)

    $httpClient = [System.Net.Http.HttpClient]::new()
    try {
        $response = $httpClient.GetAsync($Uri).GetAwaiter().GetResult()
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        return [pscustomobject]@{
            StatusCode = [int]$response.StatusCode
            Body       = $body
        }
    }
    finally {
        $httpClient.Dispose()
    }
}

function Wait-ContainerHealthy([string]$ContainerName, [int]$Timeout = $TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($Timeout)
    do {
        $status = docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $ContainerName 2>$null
        if ($status -eq "healthy") {
            return
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    throw "$ContainerName did not become healthy within $Timeout seconds (last status: $status)"
}

function Wait-HttpHealth([int]$ExpectedStatus = 200, [string]$ExpectedComponentStatus = "UP",
                          [int]$Timeout = $TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($Timeout)
    do {
        $response = Invoke-HealthRequest "$BaseUrl/health"
        if ($response.StatusCode -eq $ExpectedStatus) {
            try {
                $json = $response.Body | ConvertFrom-Json
                if ($ExpectedComponentStatus -eq "ANY" -or $json.status -eq $ExpectedComponentStatus) {
                    return $response
                }
            }
            catch {
                # Keep polling until the endpoint returns valid JSON.
            }
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    throw "Expected $ExpectedStatus/$ExpectedComponentStatus from $BaseUrl/health, got $($response.StatusCode): $($response.Body)"
}

function Assert-RunningInstance([string]$ExpectedInstance, [int]$Attempts = 8) {
    $observed = @()
    for ($index = 0; $index -lt $Attempts; $index++) {
        $response = Invoke-HealthRequest "$BaseUrl/api/instance/info"
        if ($response.StatusCode -eq 200) {
            $instance = ($response.Body | ConvertFrom-Json).instanceId
            $observed += $instance
            if ($instance -eq $ExpectedInstance) {
                return
            }
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Expected traffic to reach $ExpectedInstance; observed: $($observed -join ', ')"
}

function Assert-PrometheusTargets {
    $payload = docker compose -f $ComposeFile exec -T prometheus wget -qO- 'http://localhost:9090/api/v1/query?query=up' | ConvertFrom-Json
    if ($payload.status -ne "success") {
        throw "Prometheus query failed"
    }
    $targets = @($payload.data.result | Where-Object {
        $_.metric.job -eq "xiyouji-app-1" -or $_.metric.job -eq "xiyouji-app-2"
    })
    if ($targets.Count -ne 2 -or @($targets | Where-Object { $_.value[1] -ne "1" }).Count -ne 0) {
        throw "Expected both application Prometheus targets to be up"
    }
}

Write-Step "Checking Docker Engine and Compose configuration"
docker info --format '{{.ServerVersion}}' | Out-Null
docker compose -f $ComposeFile config --quiet
Write-Pass "Docker Engine and Compose configuration are available"

$app1Stopped = $false
$redisStopped = $false
try {
    Write-Step "Checking baseline readiness and load-balancing"
    Wait-ContainerHealthy "xiyouji-app-1"
    Wait-ContainerHealthy "xiyouji-app-2"
    Wait-ContainerHealthy "xiyouji-redis"
    $null = Wait-HttpHealth
    Assert-RunningInstance "instance-1"
    Assert-RunningInstance "instance-2"
    Assert-PrometheusTargets
    Write-Pass "Both instances, Redis and Prometheus targets are healthy"

    Write-Step "Stopping app-1 and verifying Nginx failover to app-2"
    docker compose -f $ComposeFile stop app-1 | Out-Null
    $app1Stopped = $true
    $null = Wait-HttpHealth
    Assert-RunningInstance "instance-2"
    Write-Pass "Nginx served healthy traffic from app-2 while app-1 was stopped"

    Write-Step "Starting app-1 and verifying recovery"
    docker compose -f $ComposeFile start app-1 | Out-Null
    Wait-ContainerHealthy "xiyouji-app-1"
    $app1Stopped = $false
    $null = Wait-HttpHealth
    Write-Pass "app-1 recovered and the cluster is ready again"

    Write-Step "Stopping Redis and verifying fail-closed readiness"
    docker compose -f $ComposeFile stop redis | Out-Null
    $redisStopped = $true
    $down = Wait-HttpHealth -ExpectedStatus 503 -ExpectedComponentStatus "DOWN"
    $downJson = $down.Body | ConvertFrom-Json
    if ($downJson.components.redis.status -ne "DOWN") {
        throw "Health response did not report Redis DOWN"
    }
    Write-Pass "Both app instances reported non-ready while Redis was unavailable"

    Write-Step "Starting Redis and verifying readiness recovery"
    docker compose -f $ComposeFile start redis | Out-Null
    Wait-ContainerHealthy "xiyouji-redis"
    $redisStopped = $false
    $null = Wait-HttpHealth
    Assert-PrometheusTargets
    Write-Pass "Redis recovered and both Prometheus application targets are UP"
}
finally {
    # Leave the developer's stack in the original healthy state even if an
    # assertion fails halfway through the drill.
    if ($redisStopped) {
        docker compose -f $ComposeFile start redis | Out-Null
        Wait-ContainerHealthy "xiyouji-redis"
    }
    if ($app1Stopped) {
        docker compose -f $ComposeFile start app-1 | Out-Null
        Wait-ContainerHealthy "xiyouji-app-1"
    }
}

Write-Host "Distributed failure drill completed successfully." -ForegroundColor Green
