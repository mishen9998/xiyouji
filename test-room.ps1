#Requires -Version 5.1
<# Room system API test - multi-player room phase 1 #>
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$pass = 0; $fail = 0

function Get-GuestToken {
    param([string]$Label)
    $resp = Invoke-RestMethod -Uri "$base/api/auth/guest" -Method POST
    Write-Host "  [Guest] $Label -> $($resp.username)" -ForegroundColor DarkGray
    return @{ Authorization = "Bearer $($resp.token)"; 'Content-Type' = 'application/json' }
}

function Test-Step {
    param([string]$Name, [scriptblock]$Action)
    Write-Host ""
    Write-Host "=== $Name ===" -ForegroundColor Cyan
    try {
        & $Action
        Write-Host "PASS: $Name" -ForegroundColor Green
        $script:pass++
    } catch {
        Write-Host "FAIL: $Name - $($_.Exception.Message)" -ForegroundColor Red
        $script:fail++
    }
}

# 0. Get 2 guest tokens
Write-Host "=== 0. Get 2 guest tokens ===" -ForegroundColor Cyan
$h1 = Get-GuestToken -Label "Player1(host)"
$h2 = Get-GuestToken -Label "Player2"

# 1. Create room
$room = $null
Test-Step "1. Player1 create room" {
    $resp = Invoke-RestMethod -Uri "$base/api/room/create" -Method POST -Headers $h1
    $script:room = $resp
    if ($resp.code.Length -ne 8) { throw "code length != 8: $($resp.code)" }
    if ($resp.playerCount -ne 1) { throw "playerCount != 1: $($resp.playerCount)" }
    if (-not $resp.players[0].host) { throw "host flag wrong" }
    Write-Host "  Room code: $($resp.code)"
}
$code = $room.code

# 2. Player2 join
Test-Step "2. Player2 join room" {
    $body = @{ code = $code } | ConvertTo-Json
    $resp = Invoke-RestMethod -Uri "$base/api/room/join" -Method POST -Headers $h2 -Body $body
    if ($resp.playerCount -ne 2) { throw "playerCount != 2: $($resp.playerCount)" }
    Write-Host "  Players: $($resp.playerCount)"
}

# 3. Duplicate join should fail
Test-Step "3. Duplicate join should fail" {
    $body = @{ code = $code } | ConvertTo-Json
    try {
        Invoke-RestMethod -Uri "$base/api/room/join" -Method POST -Headers $h2 -Body $body
        throw "should throw"
    } catch { $script:pass++ }
}

# 4. Get room info
Test-Step "4. Get room info" {
    $resp = Invoke-RestMethod -Uri "$base/api/room/$code" -Method GET -Headers $h1
    if ($resp.code -ne $code) { throw "code mismatch" }
    if ($resp.playerCount -ne 2) { throw "playerCount mismatch" }
}

# 5. List characters
Test-Step "5. List characters" {
    $resp = Invoke-RestMethod -Uri "$base/api/room/characters" -Method GET -Headers $h1
    if ($resp.Count -lt 1) { throw "empty" }
    Write-Host "  Characters: $($resp -join ', ')"
}

# 6. Player1 select character
Test-Step "6. Player1 select SUN_WUKONG" {
    $body = @{ characterClass = 'SUN_WUKONG' } | ConvertTo-Json
    $resp = Invoke-RestMethod -Uri "$base/api/room/$code/character" -Method POST -Headers $h1 -Body $body
    if ($resp.players[0].characterClass -ne 'SUN_WUKONG') { throw "not set" }
}

# 7. Player2 select character
Test-Step "7. Player2 select TANG_SANZANG" {
    $body = @{ characterClass = 'TANG_SANZANG' } | ConvertTo-Json
    $resp = Invoke-RestMethod -Uri "$base/api/room/$code/character" -Method POST -Headers $h2 -Body $body
    if ($resp.players[1].characterClass -ne 'TANG_SANZANG') { throw "not set" }
}

# 8. canStart false (not ready)
Test-Step "8. canStart=false before ready" {
    $resp = Invoke-RestMethod -Uri "$base/api/room/$code/canStart" -Method GET -Headers $h1
    if ($resp -ne $false) { throw "canStart should be false, got $resp" }
}

# 9. Player1 ready
Test-Step "9. Player1 ready" {
    $resp = Invoke-RestMethod -Uri "$base/api/room/$code/ready" -Method POST -Headers $h1
    if (-not $resp.players[0].ready) { throw "ready not set" }
}

# 10. Player2 ready
Test-Step "10. Player2 ready" {
    $resp = Invoke-RestMethod -Uri "$base/api/room/$code/ready" -Method POST -Headers $h2
    if (-not $resp.players[1].ready) { throw "ready not set" }
}

# 11. canStart true
Test-Step "11. canStart=true after all ready" {
    $resp = Invoke-RestMethod -Uri "$base/api/room/$code/canStart" -Method GET -Headers $h1
    if ($resp -ne $true) { throw "canStart should be true, got $resp" }
}

# 12. Player2 leave (non-host)
Test-Step "12. Player2 leave (non-host)" {
    $resp = Invoke-RestMethod -Uri "$base/api/room/$code/leave" -Method POST -Headers $h2
    if ($resp.dissolved -ne $false) { throw "should not dissolve" }
    if ($resp.room.playerCount -ne 1) { throw "playerCount should be 1" }
}

# 13. Host leave dissolves room
Test-Step "13. Host leave dissolves room" {
    $resp = Invoke-RestMethod -Uri "$base/api/room/$code/leave" -Method POST -Headers $h1
    if ($resp.dissolved -ne $true) { throw "should dissolve" }
}

# 14. Get dissolved room should 404
Test-Step "14. Get dissolved room should fail" {
    try {
        Invoke-RestMethod -Uri "$base/api/room/$code" -Method GET -Headers $h1
        throw "should fail"
    } catch { $script:pass++ }
}

# 15. Join non-existent room should fail
Test-Step "15. Join non-existent room should fail" {
    $body = @{ code = 'ZZZZZZZZ' } | ConvertTo-Json
    try {
        Invoke-RestMethod -Uri "$base/api/room/join" -Method POST -Headers $h2 -Body $body
        throw "should fail"
    } catch { $script:pass++ }
}

# Summary
Write-Host ""
Write-Host "========================================" -ForegroundColor Yellow
Write-Host "Room system test result: $pass passed / $fail failed" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Yellow
if ($fail -gt 0) { exit 1 } else { exit 0 }
