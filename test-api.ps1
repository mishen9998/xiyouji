# ====== Xiyouji Game - Runtime & Gameplay Test Script ======
# Coverage: Health -> Guest Login -> New Game -> Move -> Battle -> Play Card -> End Turn -> Rate Limit
# Usage: powershell -ExecutionPolicy Bypass -File test-api.ps1

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$h = @{ 'Content-Type' = 'application/json' }
$passCount = 0
$failCount = 0

# ====== 1. Server Health Check ======
Write-Host "=== 1. Server Health Check ===" -ForegroundColor Cyan
try {
    $health = Invoke-RestMethod -Uri "$base/actuator/health" -Method GET
    if ($health.status -eq 'UP') {
        Write-Host "PASS: Server UP (MySQL: $($health.components.db.status), Redis: $($health.components.redis.status))" -ForegroundColor Green
        $passCount++
    } else {
        Write-Host "FAIL: Server status $($health.status)" -ForegroundColor Red
        $failCount++
    }
} catch {
    Write-Host "FAIL: Cannot connect to server - $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Start backend first: cd backend; mvn spring-boot:run" -ForegroundColor Yellow
    $failCount++
    exit 1
}

Start-Sleep -Milliseconds 150

# ====== 2. Guest Login ======
Write-Host ""
Write-Host "=== 2. Guest Login (POST /api/auth/guest) ===" -ForegroundColor Cyan
try {
    $r = Invoke-RestMethod -Uri "$base/api/auth/guest" -Method POST -Headers $h
    $h['Authorization'] = "Bearer $($r.token)"
    Write-Host "PASS: Token obtained, user=$($r.username), role=$($r.role)" -ForegroundColor Green
    $passCount++
} catch {
    Write-Host "FAIL: Guest login failed - $($_.Exception.Message)" -ForegroundColor Red
    $failCount++
    exit 1
}

Start-Sleep -Milliseconds 150

# ====== 3. New Game (Start Westward Journey) ======
Write-Host ""
Write-Host "=== 3. New Game (POST /api/game/new) ===" -ForegroundColor Cyan
try {
    $ng = Invoke-RestMethod -Uri "$base/api/game/new" -Method POST -Headers $h -Body '{"characterClass":"SUN_WUKONG"}'
    $sid = $ng.sessionId
    Write-Host "PASS: sessionId=$sid, success=$($ng.success)" -ForegroundColor Green
    Write-Host "  Player: $($ng.player.displayName) HP=$($ng.player.hp)/$($ng.player.maxHp) gold=$($ng.player.gold) deck=$($ng.player.deckSize)"
    $passCount++

    $accessibleNodes = $ng.map | Where-Object { $_.accessible -eq $true }
    $battleNodes = $accessibleNodes | Where-Object { $_.type -eq 'BATTLE' }
    if ($accessibleNodes.Count -ge 2 -and $battleNodes.Count -ge 1) {
        Write-Host "PASS: Map OK, $($ng.map.Count) nodes, $($accessibleNodes.Count) accessible, $($battleNodes.Count) battle nodes" -ForegroundColor Green
        $passCount++
    } else {
        Write-Host "FAIL: Map nodes abnormal - accessible=$($accessibleNodes.Count) battle=$($battleNodes.Count)" -ForegroundColor Red
        $failCount++
    }
} catch {
    Write-Host "FAIL: New game failed - $($_.Exception.Message)" -ForegroundColor Red
    $failCount++
    exit 1
}

Start-Sleep -Milliseconds 150

# ====== 4. Move to Battle Node ======
Write-Host ""
Write-Host "=== 4. Move to Battle Node (POST /api/game/move) ===" -ForegroundColor Cyan
$targetNode = $accessibleNodes | Where-Object { $_.type -eq 'BATTLE' } | Select-Object -First 1
try {
    $body = @{ nodeId = $targetNode.id } | ConvertTo-Json
    $mv = Invoke-RestMethod -Uri "$base/api/game/move/$sid" -Method POST -Headers $h -Body $body
    if ($mv.eventType -eq 'battle') {
        Write-Host "PASS: Moved to $($targetNode.id), eventType=battle" -ForegroundColor Green
        $passCount++
    } else {
        Write-Host "FAIL: eventType=$($mv.eventType), expected=battle" -ForegroundColor Red
        $failCount++
    }
} catch {
    Write-Host "FAIL: Move failed - $($_.Exception.Message)" -ForegroundColor Red
    $failCount++
}

Start-Sleep -Milliseconds 150

# ====== 5. Start Battle ======
Write-Host ""
Write-Host "=== 5. Start Battle (POST /api/game/battle/start) ===" -ForegroundColor Cyan
try {
    $bt = Invoke-RestMethod -Uri "$base/api/game/battle/start/$sid" -Method POST -Headers $h
    if ($bt.inBattle -eq $true -and $bt.enemy -and $bt.player.hand.Count -gt 0) {
        Write-Host "PASS: Battle started, turn=$($bt.turnNumber), playerTurn=$($bt.playerTurn)" -ForegroundColor Green
        Write-Host "  Enemy: $($bt.enemy.name) HP=$($bt.enemy.hp)/$($bt.enemy.maxHp) intent=$($bt.enemy.intent)($($bt.enemy.intentValue))"
        Write-Host "  Hand: $($bt.player.hand.Count) cards"
        $passCount++
    } else {
        Write-Host "FAIL: Battle state abnormal inBattle=$($bt.inBattle)" -ForegroundColor Red
        $failCount++
    }
} catch {
    Write-Host "FAIL: Start battle failed - $($_.Exception.Message)" -ForegroundColor Red
    $failCount++
}

Start-Sleep -Milliseconds 150

# ====== 6. Play Card (find first ATTACK card in hand) ======
Write-Host ""
Write-Host "=== 6. Play Card (POST /api/game/battle/play) ===" -ForegroundColor Cyan
$enemyHpBefore = $bt.enemy.hp
$attackIdx = -1
for ($ci = 0; $ci -lt $bt.player.hand.Count; $ci++) {
    if ($bt.player.hand[$ci].type -eq 'ATTACK') {
        $attackIdx = $ci
        break
    }
}
if ($attackIdx -lt 0) { $attackIdx = 0 }
$cardName = $bt.player.hand[$attackIdx].name
try {
    $playBody = "{""handIndex"":$attackIdx}"
    $pc = Invoke-RestMethod -Uri "$base/api/game/battle/play/$sid" -Method POST -Headers $h -Body $playBody
    $dmg = $enemyHpBefore - $pc.enemy.hp
    if ($pc.player.energy -lt $bt.player.energy -or $dmg -gt 0 -or $pc.player.block -gt 0) {
        Write-Host "PASS: Card '$cardName' played (idx=$attackIdx), enemy HP $enemyHpBefore to $($pc.enemy.hp), player block=$($pc.player.block)" -ForegroundColor Green
        $passCount++
    } else {
        Write-Host "FAIL: No effect after playing card (idx=$attackIdx)" -ForegroundColor Red
        $failCount++
    }
} catch {
    Write-Host "FAIL: Play card failed - $($_.Exception.Message)" -ForegroundColor Red
    $failCount++
}

Start-Sleep -Milliseconds 150

# ====== 7. End Turn ======
Write-Host ""
Write-Host "=== 7. End Turn (POST /api/game/battle/endturn) ===" -ForegroundColor Cyan
try {
    $et = Invoke-RestMethod -Uri "$base/api/game/battle/endturn/$sid" -Method POST -Headers $h
    if ($et.turnNumber -gt 1) {
        Write-Host "PASS: Turn ended, new turn=$($et.turnNumber), player HP=$($et.player.hp)/$($et.player.maxHp)" -ForegroundColor Green
        $passCount++
    } else {
        Write-Host "FAIL: Turn number not increased turnNumber=$($et.turnNumber)" -ForegroundColor Red
        $failCount++
    }
} catch {
    Write-Host "FAIL: End turn failed - $($_.Exception.Message)" -ForegroundColor Red
    $failCount++
}

Start-Sleep -Milliseconds 150

# ====== 8. Rate Limit Stress Test ======
Write-Host ""
Write-Host "=== 8. Rate Limit Stress Test (rapid /api/game/state) ===" -ForegroundColor Cyan
$rateLimited = $false
$successCount = 0
$blockedCount = 0
for ($i = 1; $i -le 5; $i++) {
    try {
        $st = Invoke-RestMethod -Uri "$base/api/game/state/$sid" -Method GET -Headers $h
        $successCount++
        Write-Host "  Req $i : OK" -ForegroundColor DarkGray
    } catch {
        $blockedCount++
        $rateLimited = $true
        Write-Host "  Req $i : BLOCKED (429)" -ForegroundColor Yellow
    }
}
if ($rateLimited) {
    Write-Host "FAIL: Rate limiter too aggressive - $successCount ok / $blockedCount blocked" -ForegroundColor Red
    Write-Host "  Root cause: RateLimitFilter uses min-interval(100ms) not sliding window" -ForegroundColor Yellow
    Write-Host "  Impact: frontend move to startBattle calls get 429, cannot enter battle" -ForegroundColor Yellow
    $failCount++
} else {
    Write-Host "PASS: 5 rapid requests all succeeded" -ForegroundColor Green
    $passCount++
}

# ====== 9. Simulate Frontend Back-to-Back Requests (move + startBattle no delay) ======
Write-Host ""
Write-Host "=== 9. Simulate Frontend (move then immediate startBattle, no delay) ===" -ForegroundColor Cyan
try {
    Start-Sleep -Milliseconds 200
    $ng2 = Invoke-RestMethod -Uri "$base/api/game/new" -Method POST -Headers $h -Body '{"characterClass":"ZHU_BAJIE"}'
    $sid2 = $ng2.sessionId
    Start-Sleep -Milliseconds 200

    $target2 = ($ng2.map | Where-Object { $_.accessible -eq $true -and $_.type -eq 'BATTLE' } | Select-Object -First 1)
    $body2 = @{ nodeId = $target2.id } | ConvertTo-Json

    $mv2 = Invoke-RestMethod -Uri "$base/api/game/move/$sid2" -Method POST -Headers $h -Body $body2
    Write-Host "  move OK: eventType=$($mv2.eventType)" -ForegroundColor DarkGray

    try {
        $bt2 = Invoke-RestMethod -Uri "$base/api/game/battle/start/$sid2" -Method POST -Headers $h
        if ($bt2.inBattle) {
            Write-Host "PASS: Back-to-back request OK (move then startBattle)" -ForegroundColor Green
            $passCount++
        } else {
            Write-Host "FAIL: startBattle returned inBattle=false" -ForegroundColor Red
            $failCount++
        }
    } catch {
        Write-Host "FAIL: startBattle rate-limited - $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "  THIS IS THE BUG: clicking battle node cannot start battle" -ForegroundColor Yellow
        $failCount++
    }
} catch {
    Write-Host "FAIL: Back-to-back test failed - $($_.Exception.Message)" -ForegroundColor Red
    $failCount++
}

# ====== Summary ======
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Result: $passCount passed / $failCount failed" -ForegroundColor $(if ($failCount -eq 0) { 'Green' } else { 'Red' })
Write-Host "========================================" -ForegroundColor Cyan
if ($failCount -gt 0) {
    Write-Host ""
    Write-Host "SOME TESTS FAILED - check output above" -ForegroundColor Red
    exit 1
} else {
    Write-Host ""
    Write-Host "All tests passed! Rate limit fix verified." -ForegroundColor Green
    exit 0
}
