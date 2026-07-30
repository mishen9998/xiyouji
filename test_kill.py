import urllib.request, json

req = urllib.request.Request('http://localhost:8080/api/auth/guest', method='POST')
resp = urllib.request.urlopen(req)
token = json.loads(resp.read())['token']
print('1. token OK')

req = urllib.request.Request('http://localhost:8080/api/game/new',
    data=json.dumps({'characterClass':'SUN_WUKONG'}).encode(),
    headers={'Content-Type':'application/json','Authorization':'Bearer '+token},
    method='POST')
resp = urllib.request.urlopen(req)
data = json.loads(resp.read())
sid = data['sessionId']
print('2. sessionId:', sid)

emperor = [n for n in data['map'] if n['type'] == 'EMPEROR'][0]

# 移到 EMPEROR
req = urllib.request.Request('http://localhost:8080/api/game/move/'+sid,
    data=json.dumps({'nodeId':emperor['id']}).encode(),
    headers={'Content-Type':'application/json','Authorization':'Bearer '+token},
    method='POST')
urllib.request.urlopen(req)

# 查可达 BATTLE 节点
req = urllib.request.Request('http://localhost:8080/api/game/state/'+sid,
    headers={'Authorization':'Bearer '+token})
resp = urllib.request.urlopen(req)
state = json.loads(resp.read())
target = [n for n in state['map'] if n.get('accessible') and n['type'] == 'BATTLE'][0]

# 移到 BATTLE
req = urllib.request.Request('http://localhost:8080/api/game/move/'+sid,
    data=json.dumps({'nodeId':target['id']}).encode(),
    headers={'Content-Type':'application/json','Authorization':'Bearer '+token},
    method='POST')
urllib.request.urlopen(req)

# 开始战斗
req = urllib.request.Request('http://localhost:8080/api/game/battle/start/'+sid,
    headers={'Authorization':'Bearer '+token}, method='POST')
resp = urllib.request.urlopen(req)
battle = json.loads(resp.read())
print('3. 战斗开始:')
print('   敌人:', battle['enemy']['name'], 'HP=', battle['enemy']['hp'], '/', battle['enemy']['maxHp'])
print('   手牌:')
for i, c in enumerate(battle['player']['hand']):
    print('     [' + str(i) + '] ' + c['name'] + ' cost=' + str(c['cost']) +
          ' dmg=' + str(c.get('damage',0)))

# 连续出牌杀敌
turn_count = 0
max_iter = 30
while not battle.get('battleOver') and turn_count < max_iter:
    turn_count += 1
    hand = battle['player']['hand']
    # 优先找攻击牌
    attack_idx = None
    for i, c in enumerate(hand):
        if c.get('damage', 0) > 0 and c['cost'] <= battle['player']['energy']:
            attack_idx = i
            break
    if attack_idx is None:
        # 结束回合
        print('   回合' + str(turn_count) + ': 无可出牌, 结束回合')
        req = urllib.request.Request('http://localhost:8080/api/game/battle/endturn/'+sid,
            headers={'Authorization':'Bearer '+token}, method='POST')
        try:
            resp = urllib.request.urlopen(req)
            battle = json.loads(resp.read())
            print('   敌人HP:', battle['enemy'].get('hp'), '玩家HP:', battle['player'].get('hp'))
            if battle.get('rewards'):
                print('   ★ 战斗结束（endturn触发）rewards存在!')
                r = battle['rewards']
                print('     victory:', r.get('victory'))
                print('     cardRewards:', len(r.get('cardRewards', [])))
                print('     relicReward:', r.get('relicReward'))
        except urllib.error.HTTPError as e:
            print('   endturn失败:', e.code, e.read().decode('utf-8')[:200])
            break
    else:
        c = hand[attack_idx]
        print('   回合' + str(turn_count) + ': 出牌[' + str(attack_idx) + '] ' + c['name'] +
              ' 敌HP=' + str(battle['enemy']['hp']) + ' 玩家能量=' + str(battle['player']['energy']))
        req = urllib.request.Request('http://localhost:8080/api/game/battle/play/'+sid,
            data=json.dumps({'handIndex': attack_idx}).encode(),
            headers={'Content-Type':'application/json','Authorization':'Bearer '+token},
            method='POST')
        try:
            resp = urllib.request.urlopen(req)
            battle = json.loads(resp.read())
            print('     -> 敌HP=' + str(battle['enemy'].get('hp')) +
                  ' battleOver=' + str(battle.get('battleOver')) +
                  ' victory=' + str(battle.get('victory')))
            if battle.get('rewards'):
                print('   ★ 战斗结束（playCard触发）rewards存在!')
                r = battle['rewards']
                print('     victory:', r.get('victory'))
                print('     goldReward:', r.get('goldReward'))
                print('     cardRewards:', len(r.get('cardRewards', [])))
                if r.get('cardRewards'):
                    for cc in r['cardRewards']:
                        print('       -', cc.get('name'))
                print('     relicReward:', r.get('relicReward'))
                break
        except urllib.error.HTTPError as e:
            print('   playCard失败:', e.code, e.read().decode('utf-8')[:300])
            break

print('4. 测试结束, battleOver=' + str(battle.get('battleOver')))
