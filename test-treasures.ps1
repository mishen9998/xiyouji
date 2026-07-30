$ErrorActionPreference = 'Stop'
$BASE = 'http://localhost:8080'

function Get-GuestToken {
    $resp = Invoke-RestMethod -Uri "$BASE/api/auth/guest" -Method Post -ContentType 'application/json'
    return $resp.token
}

function Api-Post($token, $url, $body = $null) {
    $headers = @{ Authorization = "Bearer $token" }
    if ($body) {
        $json = $body | ConvertTo-Json -Depth 10 -Compress
        return Invoke-RestMethod -Uri "$BASE$url" -Method Post -Headers $headers -ContentType 'application/json' -Body $json
    }
    return Invoke-RestMethod -Uri "$BASE$url" -Method Post -Headers $headers -ContentType 'application/json'
}

function Api-Get($token, $url) {
    $headers = @{ Authorization = "Bearer $token" }
    return Invoke-RestMethod -Uri "$BASE$url" -Method Get -Headers $headers
}

Write-Host "==== 1. 创建两个游客账号 ===="
$t1 = Get-GuestToken
$t2 = Get-GuestToken

# 从 JWT 解析 username
function Parse-User($token) {
    $parts = $token.Split('.')
    $payload = $parts[1].Replace('-', '+').Replace('_', '/')
    while ($payload.Length % 4) { $payload += '=' }
    $json = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload))
    $obj = $json | ConvertFrom-Json
    return $obj.sub
}
$u1 = Parse-User $t1
$u2 = Parse-User $t2
Write-Host "玩家1: $u1"
Write-Host "玩家2: $u2"

Write-Host "`n==== 2. 玩家1创建房间 ===="
$room = Api-Post $t1 '/api/room/create'
$code = $room.code
Write-Host "房间码: $code"
Write-Host "玩家数: $($room.playerCount)"

Write-Host "`n==== 3. 玩家2加入房间 ===="
$room = Api-Post $t2 '/api/room/join' @{ code = $code }
Write-Host "玩家数: $($room.playerCount)"

Write-Host "`n==== 4. 选择角色 ===="
Api-Post $t1 "/api/room/$code/character" @{ characterClass = 'SUN_WUKONG' } | Out-Null
Api-Post $t2 "/api/room/$code/character" @{ characterClass = 'ZHU_BAJIE' } | Out-Null
Write-Host "已选择角色"

Write-Host "`n==== 5. 准备 ===="
Api-Post $t1 "/api/room/$code/ready" | Out-Null
Api-Post $t2 "/api/room/$code/ready" | Out-Null
$room = Api-Get $t1 "/api/room/$code"
Write-Host "全员准备状态: $(($room.players | ForEach-Object { $_.ready }) -join ',')"

Write-Host "`n==== 6. 房主开始战斗 ===="
$battle = Api-Post $t1 "/api/multiplayer/battle/$code/start"
Write-Host "回合数: $($battle.turnNumber)"
Write-Host "敌HP: $($battle.enemy.hp)/$($battle.enemy.maxHp)"
Write-Host "玩家数: $($battle.players.Count)"

Write-Host "`n==== 7. 查询玩家手牌 ===="
$p1 = $battle.players | Where-Object { $_.userId -eq $u1 }
$p2 = $battle.players | Where-Object { $_.userId -eq $u2 }
Write-Host "玩家1 手牌数: $($p1.hand.Count) 能量: $($p1.energy)"
Write-Host "玩家2 手牌数: $($p2.hand.Count) 能量: $($p2.energy)"

Write-Host "`n==== 8. 出牌击败敌人 ===="
$turn = 0
$maxTurns = 30
while (-not $battle.battleOver -and $turn -lt $maxTurns) {
    $turn++
    Write-Host "--- 回合 $($battle.turnNumber) (iteration $turn) ---"
    Write-Host "敌HP: $($battle.enemy.hp)/$($battle.enemy.maxHp), 意图: $($battle.enemy.intent)"

    # 找到能量足够的攻击牌
    function Play-AttackCards($token, $battle, $user) {
        $me = $battle.players | Where-Object { $_.userId -eq $user }
        if (-not $me -or -not $me.alive -or $me.endedTurn -or -not $battle.playerTurn) { return }
        # 按 damage 降序
        $sorted = $me.hand | Sort-Object -Property damage -Descending
        foreach ($card in $sorted) {
            if ($card.cost -le $me.energy -and $card.damage -gt 0) {
                Write-Host "  $user 出牌: $($card.name) (cost $($card.cost), dmg $($card.damage))"
                $battle = Api-Post $token "/api/multiplayer/battle/$code/play" @{ handIndex = $card.index }
                if ($battle.battleOver) { return $battle }
                # 更新自己的状态
                $me = $battle.players | Where-Object { $_.userId -eq $user }
                if (-not $me -or $me.endedTurn -or -not $me.alive) { break }
            }
        }
        return $battle
    }

    # 玩家1出牌
    $result = Play-AttackCards $t1 $battle $u1
    if ($result) { $battle = $result }
    if ($battle.battleOver) { break }

    # 玩家1结束回合
    if ($battle.playerTurn) {
        $me = $battle.players | Where-Object { $_.userId -eq $u1 }
        if ($me -and $me.alive -and -not $me.endedTurn) {
            Write-Host "  $u1 结束回合"
            $battle = Api-Post $t1 "/api/multiplayer/battle/$code/endturn"
        }
    }
    if ($battle.battleOver) { break }

    # 玩家2出牌
    $result = Play-AttackCards $t2 $battle $u2
    if ($result) { $battle = $result }
    if ($battle.battleOver) { break }

    # 玩家2结束回合
    if ($battle.playerTurn) {
        $me = $battle.players | Where-Object { $_.userId -eq $u2 }
        if ($me -and $me.alive -and -not $me.endedTurn) {
            Write-Host "  $u2 结束回合"
            $battle = Api-Post $t2 "/api/multiplayer/battle/$code/endturn"
        }
    }
}

Write-Host "`n==== 9. 战斗结果 ===="
Write-Host "战斗结束: $($battle.battleOver)"
Write-Host "胜利: $($battle.victory)"

if ($battle.victory) {
    Write-Host "`n==== 10. 验证宝物数量 ===="
    Write-Host "奖励阶段: $($battle.rewardsPhase)"
    Write-Host "已发放奖励的玩家数: $($battle.rewards.PSObject.Properties.Count)"

    # 列出每个玩家的奖励选项
    foreach ($prop in $battle.rewards.PSObject.Properties) {
        $userId = $prop.Name
        $cards = $prop.Value
        $alivePlayer = $battle.players | Where-Object { $_.userId -eq $userId }
        Write-Host "  玩家 $userId (alive=$($alivePlayer.alive)): $($cards.Count) 张可选"
        foreach ($c in $cards) {
            Write-Host "    - $($c.name) (cost $($c.cost), dmg $($c.damage))"
        }
    }

    Write-Host "`n==== 11. 领取奖励 ===="
    # 玩家1选第一张
    $p1Cards = $battle.rewards.$u1
    if ($p1Cards) {
        $chosen = $p1Cards[0].name
        Write-Host "玩家1 领取: $chosen"
        $battle = Api-Post $t1 "/api/multiplayer/battle/$code/claim-reward" @{ cardName = $chosen }
    }
    # 玩家2选第一张
    $p2Cards = $battle.rewards.$u2
    if ($p2Cards) {
        $chosen = $p2Cards[0].name
        Write-Host "玩家2 领取: $chosen"
        $battle = Api-Post $t2 "/api/multiplayer/battle/$code/claim-reward" @{ cardName = $chosen }
    }

    Write-Host "`n==== 12. 进入下一层 ===="
    $battle = Api-Post $t1 "/api/multiplayer/battle/$code/next-floor"
    Write-Host "新回合: $($battle.turnNumber)"
    Write-Host "新敌HP: $($battle.enemy.hp)/$($battle.enemy.maxHp)"

    Write-Host "`n==== 测试完成 ===="
    Write-Host "玩家1 deckSize: $($battle.players[0].deckSize)"
    Write-Host "玩家2 deckSize: $($battle.players[1].deckSize)"
} else {
    Write-Host "战斗失败，无法验证宝物机制"
}
