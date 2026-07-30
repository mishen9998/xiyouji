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

# 找到 EMPEROR 节点
emperor = [n for n in data['map'] if n['type'] == 'EMPEROR'][0]
print('   EMPEROR:', emperor['id'], 'accessible=', emperor.get('accessible'))

# 先 move 到 EMPEROR
req = urllib.request.Request('http://localhost:8080/api/game/move/'+sid,
    data=json.dumps({'nodeId':emperor['id']}).encode(),
    headers={'Content-Type':'application/json','Authorization':'Bearer '+token},
    method='POST')
resp = urllib.request.urlopen(req)
print('3. moved to EMPEROR')

# 关闭 emperor 弹窗（不需要event，直接看下一个可达节点）
emperor_node = None
for n in data['map']:
    if n['id'] == emperor['id']:
        emperor_node = n
        break
print('   EMPEROR connections:', emperor_node.get('connections'))

# 获取游戏状态查看可达节点
req = urllib.request.Request('http://localhost:8080/api/game/state/'+sid,
    headers={'Authorization':'Bearer '+token})
resp = urllib.request.urlopen(req)
state = json.loads(resp.read())

accessible_battle = [n for n in state['map'] if n.get('accessible') and n['type'] == 'BATTLE']
print('4. 可达的BATTLE节点数:', len(accessible_battle))
if not accessible_battle:
    print('   错误：没有可达的BATTLE节点！')
    exit()
target = accessible_battle[0]
print('   选:', target['id'], 'enemyId=', target.get('enemyId'))

# 移动到 BATTLE 节点
req = urllib.request.Request('http://localhost:8080/api/game/move/'+sid,
    data=json.dumps({'nodeId':target['id']}).encode(),
    headers={'Content-Type':'application/json','Authorization':'Bearer '+token},
    method='POST')
resp = urllib.request.urlopen(req)
move_data = json.loads(resp.read())
print('5. moved to BATTLE, eventType=', move_data.get('eventType'))

# 开始战斗
req = urllib.request.Request('http://localhost:8080/api/game/battle/start/'+sid,
    headers={'Authorization':'Bearer '+token},
    method='POST')
resp = urllib.request.urlopen(req)
battle = json.loads(resp.read())
enemy = battle['enemy']
print('6. 战斗开始:')
print('   敌人:', enemy['name'], 'HP=', enemy['hp'], '/', enemy['maxHp'], 'block=', enemy.get('block'))
print('   敌人buff:', enemy.get('buffs'))
print('   手牌:')
for i, c in enumerate(battle['player']['hand']):
    print('     [' + str(i) + '] ' + c['name'] + ' cost=' + str(c['cost']) +
          ' dmg=' + str(c.get('damage',0)) + ' block=' + str(c.get('block',0)))

# 找到一张攻击牌杀敌
hand = battle['player']['hand']
attack_idx = None
for i, c in enumerate(hand):
    if c.get('damage', 0) > 0:
        attack_idx = i
        break

if attack_idx is None:
    print('7. 没有攻击牌！')
    exit()

print('7. 选择出牌 [' + str(attack_idx) + '] ' + hand[attack_idx]['name'])

# 出牌
req = urllib.request.Request('http://localhost:8080/api/game/battle/play/'+sid,
    data=json.dumps({'handIndex': attack_idx}).encode(),
    headers={'Content-Type':'application/json','Authorization':'Bearer '+token},
    method='POST')
try:
    resp = urllib.request.urlopen(req)
    result = json.loads(resp.read())
    print('8. 出牌成功')
    print('   battleOver:', result.get('battleOver'))
    print('   victory:', result.get('victory'))
    print('   敌人HP:', result['enemy'].get('hp'))
    if result.get('rewards'):
        r = result['rewards']
        print('   rewards.victory:', r.get('victory'))
        print('   rewards.goldReward:', r.get('goldReward'))
        print('   rewards.cardRewards数量:', len(r.get('cardRewards', [])))
        if r.get('cardRewards'):
            for c in r['cardRewards']:
                print('     -', c.get('name'))
        print('   rewards.relicReward:', r.get('relicReward'))
    else:
        print('   ⚠️ 没有rewards字段！')
except urllib.error.HTTPError as e:
    print('8. 出牌失败 HTTP', e.code)
    body = e.read().decode('utf-8')
    print('   body:', body[:500])
