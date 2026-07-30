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
print('   初始relics:', [r.get('name') for r in data['player'].get('relics', [])])

emperor = [n for n in data['map'] if n['type'] == 'EMPEROR'][0]
print('3. EMPEROR:', emperor['id'])

# 移到 EMPEROR
req = urllib.request.Request('http://localhost:8080/api/game/move/'+sid,
    data=json.dumps({'nodeId':emperor['id']}).encode(),
    headers={'Content-Type':'application/json','Authorization':'Bearer '+token},
    method='POST')
urllib.request.urlopen(req)

# 获取三选一候选
req = urllib.request.Request('http://localhost:8080/api/game/event/'+sid,
    data=json.dumps({'action':'view'}).encode(),
    headers={'Content-Type':'application/json','Authorization':'Bearer '+token},
    method='POST')
resp = urllib.request.urlopen(req)
event_data = json.loads(resp.read())
print('4. 候选宝物:')
for c in event_data.get('choices', []):
    print('   -', c.get('name'), '|', c.get('description'))

# 选第一件
chosen_name = event_data['choices'][0]['name']
print('5. 选择:', chosen_name)
req = urllib.request.Request('http://localhost:8080/api/game/event/'+sid,
    data=json.dumps({'action':'choose', 'relicName': chosen_name}).encode(),
    headers={'Content-Type':'application/json','Authorization':'Bearer '+token},
    method='POST')
resp = urllib.request.urlopen(req)
choose_data = json.loads(resp.read())
print('   choose result:', choose_data.get('message'))
print('   player relics after choose:', [r.get('name') for r in choose_data.get('player', {}).get('relics', [])])

# 查找 BATTLE 节点开始战斗
req = urllib.request.Request('http://localhost:8080/api/game/state/'+sid,
    headers={'Authorization':'Bearer '+token})
resp = urllib.request.urlopen(req)
state = json.loads(resp.read())
target = [n for n in state['map'] if n.get('accessible') and n['type'] == 'BATTLE'][0]

req = urllib.request.Request('http://localhost:8080/api/game/move/'+sid,
    data=json.dumps({'nodeId':target['id']}).encode(),
    headers={'Content-Type':'application/json','Authorization':'Bearer '+token},
    method='POST')
urllib.request.urlopen(req)

req = urllib.request.Request('http://localhost:8080/api/game/battle/start/'+sid,
    headers={'Authorization':'Bearer '+token}, method='POST')
resp = urllib.request.urlopen(req)
battle = json.loads(resp.read())
print('6. 战斗开始')
print('   battle.player.relics:', [r.get('name') for r in battle['player'].get('relics', [])])
print('   relic详情:')
for r in battle['player'].get('relics', []):
    print('     -', r)
