$ErrorActionPreference='Continue'
$base='http://localhost:8080'

function Get-UserId($t){ $p=$t.Split('.')[1]; $d=[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($p.PadRight($p.Length+(4-$p.Length%4)%4,'='))); ($d|ConvertFrom-Json).sub }

$tA=(Invoke-RestMethod -Uri "$base/api/auth/guest" -Method POST).token
$hA=@{Authorization="Bearer $tA";'Content-Type'='application/json'}
$uid=Get-UserId $tA

# 重试直到弱敌人
for($a=0;$a -lt 10;$a++){
  $room=Invoke-RestMethod -Uri "$base/api/room/create" -Method POST -Headers $hA
  Invoke-RestMethod -Uri "$base/api/room/$($room.code)/character" -Method POST -Headers $hA -Body '{"characterClass":"SUN_WUKONG"}'|Out-Null
  Invoke-RestMethod -Uri "$base/api/room/$($room.code)/ready" -Method POST -Headers $hA|Out-Null
  $b=Invoke-RestMethod -Uri "$base/api/multiplayer/battle/$($room.code)/start" -Method POST -Headers $hA
  if([int]$b.enemy.maxHp -le 60){ break }
}

"room=$($room.code) enemyHP=$($b.enemy.maxHp) atk=$($b.enemy.intentValue) myHP=$($b.players[0].maxHp)"

for($r=0;$r -lt 20 -and -not $b.battleOver;$r++){
  $p=$b.players[0]
  if(-not $p.alive){ "R${r}: DEAD"; break }
  $handStr=($p.hand | ForEach-Object{"$($_.name)($($_.cost),$($_.damage),$($_.block))"}) -join ' '
  "R${r}: HP=$($p.hp) EN=$($p.energy) enemyHP=$($b.enemy.hp) enemyIntent=$($b.enemy.intent) hand: $handStr"

  # 出攻击牌
  $played=$true
  while($played -and -not $b.battleOver){
    $played=$false
    $p=$b.players[0]
    if(-not $p.alive -or $p.endedTurn){ break }
    for($i=0;$i -lt $p.hand.Count;$i++){
      $c=$p.hand[$i]
      if($c.damage -gt 0 -and $c.cost -le $p.energy){
        try{
          $b=Invoke-RestMethod -Uri "$base/api/multiplayer/battle/$($room.code)/play" -Method POST -Headers $hA -Body("{`"handIndex`":$i}")
          $played=$true
          "  played $($c.name) dmg=$($c.damage) -> enemyHP=$($b.enemy.hp) myEN=$($b.players[0].energy)"
          if($b.battleOver){ break }
          break
        }catch{
          $msg=$_.Exception.Message
          "  play FAILED: $msg"
          break
        }
      }
    }
  }
  if(-not $b.battleOver){
    $p=$b.players[0]
    if($p.alive -and -not $p.endedTurn){
      try{
        $b=Invoke-RestMethod -Uri "$base/api/multiplayer/battle/$($room.code)/endturn" -Method POST -Headers $hA
        "  endturn -> myHP=$($b.players[0].hp) block=$($b.players[0].block) enemyHP=$($b.enemy.hp)"
      }catch{ "  endturn FAILED: $($_.Exception.Message)"; break }
    }
  }
}

"`n=== FINAL ==="
"over=$($b.battleOver) win=$($b.victory) rewardsPhase=$($b.rewardsPhase)"
"myHP=$($b.players[0].hp) enemyHP=$($b.enemy.hp)"
$b.combatLog | Select-Object -Last 8 | ForEach-Object{ "  $_" }

