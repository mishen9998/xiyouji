$ErrorActionPreference = 'Stop'
$BASE = 'http://localhost:8080'

function Call-Api($token, $url, $body = $null) {
    $h = @{ Authorization = "Bearer $token" }
    if ($body -ne $null) {
        $j = $body | ConvertTo-Json -Compress -Depth 5
        return Invoke-RestMethod -Uri "$BASE$url" -Method Post -Headers $h -ContentType 'application/json' -Body $j
    }
    return Invoke-RestMethod -Uri "$BASE$url" -Method Post -Headers $h -ContentType 'application/json'
}

function Get-User($token) {
    $p = $token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    while ($p.Length % 4) { $p += '=' }
    ([System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p)) | ConvertFrom-Json).sub
}

Write-Host "=== 1. Create two guests ==="
$t1 = (Invoke-RestMethod -Uri "$BASE/api/auth/guest" -Method Post -ContentType 'application/json').token
$t2 = (Invoke-RestMethod -Uri "$BASE/api/auth/guest" -Method Post -ContentType 'application/json').token
$u1 = Get-User $t1
$u2 = Get-User $t2
Write-Host "u1=$u1 u2=$u2"

Write-Host "=== 2. Create + Join room ==="
$room = Call-Api $t1 '/api/room/create'
$code = $room.code
Write-Host "code=$code"
Call-Api $t2 '/api/room/join' @{ code = $code } | Out-Null

Write-Host "=== 3. Select character + Ready ==="
Call-Api $t1 "/api/room/$code/character" @{ characterClass = 'SUN_WUKONG' } | Out-Null
Call-Api $t2 "/api/room/$code/character" @{ characterClass = 'ZHU_BAJIE' } | Out-Null
Call-Api $t1 "/api/room/$code/ready" | Out-Null
Call-Api $t2 "/api/room/$code/ready" | Out-Null

Write-Host "=== 4. Start battle ==="
$b = Call-Api $t1 "/api/multiplayer/battle/$code/start"
Write-Host "Enemy HP=$($b.enemy.hp)/$($b.enemy.maxHp) PlayerCount=$($b.players.Count)"

Write-Host "=== 5. Battle loop (max 30) ==="
for ($i = 1; $i -le 30 -and -not $b.battleOver; $i++) {
    Write-Host "iter ${i} turn=$($b.turnNumber) phase=$($b.playerTurn) enemyHP=$($b.enemy.hp)"

    foreach ($tu in @(@($t1, $u1), @($t2, $u2))) {
        $tk = $tu[0]; $un = $tu[1]
        $me = $b.players | Where-Object { $_.userId -eq $un }
        if (-not $me -or -not $me.alive -or $me.endedTurn -or -not $b.playerTurn -or $b.battleOver) { continue }

        foreach ($card in ($me.hand | Sort-Object -Property damage -Descending)) {
            if ($b.battleOver) { break }
            if ($card.damage -gt 0 -and $card.cost -le $me.energy) {
                try {
                    $b = Call-Api $tk "/api/multiplayer/battle/$code/play" @{ handIndex = $card.index }
                    $me = $b.players | Where-Object { $_.userId -eq $un }
                    Write-Host "  $un play $($card.name) dmg=$($card.damage) enemyHP=$($b.enemy.hp)"
                } catch { Write-Host "  err: $_" }
            }
        }
        if ($b.battleOver) { break }

        if ($b.playerTurn -and $me -and $me.alive -and -not $me.endedTurn) {
            try {
                $b = Call-Api $tk "/api/multiplayer/battle/$code/endturn"
                Write-Host "  $un end turn"
            } catch { Write-Host "  endturn err: $_" }
        }
    }
}

Write-Host "=== 6. Battle result ==="
Write-Host "battleOver=$($b.battleOver) victory=$($b.victory)"

if ($b.victory) {
    Write-Host "=== 7. Verify rewards ==="
    $rewardCount = ($b.rewards.PSObject.Properties | Measure-Object).Count
    $aliveCount = ($b.players | Where-Object { $_.alive }).Count
    Write-Host "Alive players: $aliveCount"
    Write-Host "Reward groups (per player): $rewardCount"
    if ($rewardCount -eq $aliveCount) {
        Write-Host "PASS - Rewards count matches alive player count"
    } else {
        Write-Host "FAIL - Mismatch"
    }

    Write-Host "=== 8. Cards per player ==="
    foreach ($p in $b.rewards.PSObject.Properties) {
        Write-Host "  $($p.Name): $($p.Value.Count) cards"
    }

    Write-Host "=== 9. Claim rewards ==="
    foreach ($tu in @(@($t1, $u1), @($t2, $u2))) {
        $tk = $tu[0]; $un = $tu[1]
        if ($b.rewards.$un) {
            $chosen = $b.rewards.$un[0].name
            try {
                $b = Call-Api $tk "/api/multiplayer/battle/$code/claim-reward" @{ cardName = $chosen }
                Write-Host "  $un claimed: $chosen"
            } catch { Write-Host "  $un claim failed: $_" }
        }
    }
    Write-Host "rewardsHandled=$($b.rewardsHandled)"

    Write-Host "=== 10. Next floor ==="
    try {
        $b = Call-Api $t1 "/api/multiplayer/battle/$code/next-floor"
        Write-Host "New turn=$($b.turnNumber) New enemy HP=$($b.enemy.hp)/$($b.enemy.maxHp)"
        Write-Host "Player1 deckSize=$($b.players[0].deckSize)"
        Write-Host "Player2 deckSize=$($b.players[1].deckSize)"
        Write-Host "PASS - Full flow"
    } catch { Write-Host "Next floor failed: $_" }
} else {
    Write-Host "Battle failed - cannot verify rewards"
}
