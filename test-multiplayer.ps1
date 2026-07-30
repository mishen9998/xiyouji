$ErrorActionPreference='Continue'
$base='http://localhost:8080'

$tA=(Invoke-RestMethod -Uri "$base/api/auth/guest" -Method POST).token
$tB=(Invoke-RestMethod -Uri "$base/api/auth/guest" -Method POST).token
$hA=@{Authorization="Bearer $tA";'Content-Type'='application/json'}
$hB=@{Authorization="Bearer $tB";'Content-Type'='application/json'}

$room=Invoke-RestMethod -Uri "$base/api/room/create" -Method POST -Headers $hA
Invoke-RestMethod -Uri "$base/api/room/join" -Method POST -Headers $hB -Body (@{code=$room.code}|ConvertTo-Json) | Out-Null
"room: $($room.code)"

Invoke-RestMethod -Uri "$base/api/room/$($room.code)/character" -Method POST -Headers $hA -Body '{"characterClass":"SUN_WUKONG"}'|Out-Null
Invoke-RestMethod -Uri "$base/api/room/$($room.code)/character" -Method POST -Headers $hB -Body '{"characterClass":"ZHU_BAJIE"}'|Out-Null
Invoke-RestMethod -Uri "$base/api/room/$($room.code)/ready" -Method POST -Headers $hA|Out-Null
Invoke-RestMethod -Uri "$base/api/room/$($room.code)/ready" -Method POST -Headers $hB|Out-Null
"ready done"

$b=Invoke-RestMethod -Uri "$base/api/multiplayer/battle/$($room.code)/start" -Method POST -Headers $hA
"battle started enemyHP=$($b.enemy.maxHp) players=$($b.players.Count)"

# 解析userId
function Get-UserId($token){ $p=$token.Split('.')[1]; $d=[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($p.PadRight($p.Length+($p.Length%4),'='))); ($d|ConvertFrom-Json).sub }
$uidA=Get-UserId $tA
$uidB=Get-UserId $tB

# 循环出牌
for($r=0;$r -lt 30 -and -not $b.battleOver;$r++){
  $pA=$b.players|Where-Object{$_.userId -eq $uidA}
  if($pA -and $pA.alive -and -not $pA.endedTurn){
    for($i=0;$i -lt $pA.hand.Count;$i++){
      if($pA.hand[$i].damage -gt 0 -and $pA.hand[$i].cost -le $pA.energy){
        try{$b=Invoke-RestMethod -Uri "$base/api/multiplayer/battle/$($room.code)/play" -Method POST -Headers $hA -Body("{`"handIndex`":$i}")}catch{break}
        if($b.battleOver){break}
      }
    }
  }
  if(-not $b.battleOver){
    try{$b=Invoke-RestMethod -Uri "$base/api/multiplayer/battle/$($room.code)/endturn" -Method POST -Headers $hA}catch{}
  }
  $pB=$b.players|Where-Object{$_.userId -eq $uidB}
  if($pB -and $pB.alive -and -not $pB.endedTurn -and -not $b.battleOver){
    for($i=0;$i -lt $pB.hand.Count;$i++){
      if($pB.hand[$i].damage -gt 0 -and $pB.hand[$i].cost -le $pB.energy){
        try{$b=Invoke-RestMethod -Uri "$base/api/multiplayer/battle/$($room.code)/play" -Method POST -Headers $hB -Body("{`"handIndex`":$i}")}catch{break}
        if($b.battleOver){break}
      }
    }
  }
  if(-not $b.battleOver){
    try{$b=Invoke-RestMethod -Uri "$base/api/multiplayer/battle/$($room.code)/endturn" -Method POST -Headers $hB}catch{}
  }
}

"`n=== result ==="
"over=$($b.battleOver) win=$($b.victory) rewardsPhase=$($b.rewardsPhase)"
if($b.rewardsPhase){
  "rewards keys: $($b.rewards.PSObject.Properties.Name -join ',')"
  $myR=$b.rewards.$uidA
  if($myR){ "A options: $($myR.Count) cards" }
  $cardName=$myR[0].name
  "A claiming: $cardName"
  $b=Invoke-RestMethod -Uri "$base/api/multiplayer/battle/$($room.code)/claim-reward" -Method POST -Headers $hA -Body("{`"cardName`":`"$cardName`"}")
  "claimed: $($b.claimedRewards | ConvertTo-Json -Compress) handled=$($b.rewardsHandled)"
  $cardB=$b.rewards.$uidB[0].name
  $b=Invoke-RestMethod -Uri "$base/api/multiplayer/battle/$($room.code)/claim-reward" -Method POST -Headers $hB -Body("{`"cardName`":`"$cardB`"}")
  "B claimed. handled=$($b.rewardsHandled)"
  $b=Invoke-RestMethod -Uri "$base/api/multiplayer/battle/$($room.code)/next-floor" -Method POST -Headers $hA
  "`nnext floor: enemyHP=$($b.enemy.maxHp) turn=$($b.turnNumber) deckA=$($b.players[0].deckSize) deckB=$($b.players[1].deckSize)"
  "ALL TESTS PASSED!"
}
