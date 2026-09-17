import { test, expect, type Page } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { resolve } from 'node:path'

// Opt in: docker compose -p xiyouji-t4-round2 -f docker-compose.t4-recovery.yml up -d
test.skip(process.env.T4_BATTLE_GENERATION_E2E !== '1', 'Requires the isolated T4 two-instance stack')
const apps = ['http://127.0.0.1:18088', 'http://127.0.0.1:18089']
function compose(args: string[], input?: string) {
  return execFileSync('docker', ['compose', '-p', 'xiyouji-t4-round2', '-f', 'docker-compose.t4-recovery.yml', 'exec', '-T', ...args],
    { cwd: resolve(import.meta.dirname, '../..'), encoding: 'utf8', input }).trim()
}
const list = (value: any): any[] => typeof value?.[0] === 'string' && Array.isArray(value?.[1]) ? value[1] : value
async function api(index: number, path: string, token?: string, body?: unknown, version?: number) {
  const response = await fetch(apps[index] + path, { method: body === undefined ? 'GET' : 'POST', headers: {
    'Content-Type': 'application/json', 'X-Idempotency-Key': crypto.randomUUID(),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(version === undefined ? {} : { 'X-Expected-State-Version': String(version) }),
  }, body: body === undefined ? undefined : JSON.stringify(body) })
  const result = await response.json()
  expect(response.status, `${path}: ${JSON.stringify(result)}`).toBeLessThan(300)
  return result
}
function seedTwoEncounters(code: string) {
  const ids = Object.fromEntries(compose(['mysql', 'mysql', '--default-character-set=utf8mb4', '-uroot', '-pt4-round2-disposable-only',
    'xiyouji_t4_round2', '-N', '-e', 'SELECT name,id FROM enemies;']).split('\n').map(line => line.trim().split('\t')))
  const room = JSON.parse(compose(['redis', 'redis-cli', '--raw', 'GET', `room:${code}`]))
  const nodes = list(room.map)
  const path = [nodes.find(n => n.row === 0), nodes.find(n => n.row === 1)]
  for (const node of nodes) node.accessible = false
  for (let index = 0; index < path.length; index++) {
    const node = path[index]; const name = ['寅将军', '熊山君'][index]
    node.type = 'BATTLE'; node.name = name; node.enemyId = ids[name]
    expect(node.enemyId).toBeTruthy()
    node.visited = false; node.accessible = index === 0
    node.connections = ['java.util.ArrayList', index === 0 ? [path[1].id] : []]
  }
  // Fixture acceleration only: real definitions, game commands, enemy turns and rewards remain intact.
  for (const player of list(room.players)) {
    player.maxHp = 10000; player.hp = 9000
    for (const card of list(player.deck)) { card.cost = 0; if (card.damage > 0) card.damage = 10000 }
  }
  room.stateVersion++
  compose(['redis', 'redis-cli', '-x', 'SET', `room:${code}`], JSON.stringify(room))
  compose(['redis', 'redis-cli', 'EXPIRE', `room:${code}`, '7200'])
  return path.map(node => nodes.indexOf(node))
}
async function dismissStory(page: Page) {
  const button = page.getByRole('button', { name: '跳过剧情', exact: true })
  if (await button.isVisible()) await button.click()
}

test('real two-instance second battle accepts v1 after reward v6, rejects delayed first battle and reconnects', async ({ browser }, info) => {
  test.setTimeout(180000)
  const identities = [await api(0, '/api/auth/guest', undefined, {}), await api(1, '/api/auth/guest', undefined, {})]
  const contexts = await Promise.all(identities.map(async identity => {
    const context = await browser.newContext()
    await context.addInitScript(identity => {
      localStorage.setItem('xiyouji_jwt_token', identity.token)
      localStorage.setItem('xiyouji_auth_profile', JSON.stringify(identity))
    }, identity)
    return context
  }))
  const pages = await Promise.all(contexts.map(context => context.newPage()))
  const subscriptions: number[] = []
  let closeLink: (() => void) | undefined; let sendOld: ((data: string) => void) | undefined
  let blocked = false; let oldBattleFrame = ''; let oldMapFrame = ''; let battleReads = 0
  pages[1].on('request', request => { if (/\/battle\/.+\/state$/.test(request.url())) battleReads++ })
  await pages[1].routeWebSocket('**/ws?*', socket => {
    if (blocked) { socket.close(); return }
    const server = socket.connectToServer(); const index = subscriptions.push(0) - 1
    const destinations = new Map<string, string>()
    socket.onMessage(message => {
      const frame = message.toString()
      if (frame.startsWith('SUBSCRIBE')) {
        subscriptions[index]++
        destinations.set(frame.match(/^destination:(.+)$/m)![1], frame.match(/^id:(.+)$/m)![1])
      }
      server.send(message)
    })
    server.onMessage(message => {
      const frame = message.toString()
      if (frame.startsWith('MESSAGE')) {
        try {
          const body = JSON.parse(frame.slice(frame.indexOf('\n\n') + 2).replace(/\0$/, ''))
          // Hold this real frame until the next encounter: its eventId has never reached the store.
          if (body.enemy?.name === '寅将军' && body.rewardsHandled) { oldBattleFrame = frame; return }
          if (body.status === 'IN_MAP' && body.battleId) oldMapFrame = frame
        } catch { /* heartbeats and framing are forwarded unchanged */ }
      }
      socket.send(message)
    })
    closeLink = () => { server.close(); socket.close() }
    sendOld = data => {
      const destination = data.match(/^destination:(.+)$/m)![1]
      const subscription = destinations.get(destination)
      expect(subscription).toBeTruthy()
      // Reconnect replaces subscription IDs; retain the recorded server payload byte-for-byte.
      socket.send(data.replace(/^subscription:.*$/m, `subscription:${subscription}`))
    }
  })
  try {
    const created = await api(0, '/api/room/create', identities[0].token, {})
    const code = created.code
    await api(1, '/api/room/join', identities[1].token, { code })
    const room = (index = 0) => api(index, `/api/room/${code}`, identities[index].token)
    const battle = (index = 0) => api(index, `/api/multiplayer/battle/${code}/state`, identities[index].token)
    for (let index = 0; index < 2; index++) {
      await api(index, `/api/room/${code}/character`, identities[index].token, { characterClass: ['SUN_WUKONG', 'ZHU_BAJIE'][index] }, (await room(index)).stateVersion)
      await api(index, `/api/room/${code}/ready`, identities[index].token, {}, (await room(index)).stateVersion)
    }
    await api(0, `/api/room/${code}/start-game`, identities[0].token, {}, (await room()).stateVersion)
    const path = seedTwoEncounters(code)
    for (let index = 0; index < 2; index++) { await pages[index].goto(`${apps[index]}/room/${code}/map`); await dismissStory(pages[index]) }
    await expect.poll(() => subscriptions[0]).toBe(2)
    await pages[0].locator('.map-node').nth(path[0]).click()
    for (const page of pages) { await expect(page.locator('.enemy-intent')).toBeVisible({ timeout: 15000 }); await dismissStory(page) }
    for (let index = 0; index < 2; index++) {
      await pages[index].getByRole('button', { name: '结束回合', exact: true }).click()
      await expect.poll(async () => (await battle()).players[index].endedTurn || (await battle()).turnNumber > 1).toBeTruthy()
    }
    await expect.poll(async () => (await battle()).turnNumber).toBe(2)
    let state = await battle()
    // If the shuffled second hand is all defense, advance a real round until an attack is drawn.
    while (!state.players[0].hand.some((card: any) => card.damage >= 10000)) {
      for (const page of pages) await page.getByRole('button', { name: '结束回合', exact: true }).click()
      await expect.poll(async () => (await battle()).turnNumber).toBeGreaterThan(state.turnNumber)
      state = await battle()
    }
    const attack = state.players[0].hand.findIndex((card: any) => card.damage >= 10000)
    await pages[0].locator('.hand-cards .game-card').nth(attack).click()
    await pages[0].getByRole('button', { name: '打出此牌', exact: true }).click()
    for (const page of pages) {
      await page.locator('.reward-card').first().click()
      await page.getByRole('button', { name: '确认领取', exact: true }).click()
      await expect(page.locator('.claimed-status')).toBeVisible()
    }
    const first = await battle()
    expect(first.stateVersion).toBeGreaterThanOrEqual(6); expect(first.rewardsHandled).toBe(true)
    await expect.poll(() => oldBattleFrame.length).toBeGreaterThan(0)
    await pages[0].getByRole('button', { name: '返回地图', exact: true }).click()
    for (const page of pages) await expect(page).toHaveURL(/\/map$/)
    const readsBefore = battleReads
    blocked = true; closeLink!()
    await pages[0].locator('.map-node').nth(path[1]).click()
    await expect(pages[0].locator('.enemy-intent')).toBeVisible()
    const second = await battle()
    expect(second.enemy.name).toBe('熊山君'); expect(second.enemy.hp).toBe(48)
    expect(second.battleOver).toBe(false); expect(second.stateVersion).toBe(1)
    expect(second.battleId).not.toBe(first.battleId)
    expect(second.battleGeneration).toBeGreaterThan(first.battleGeneration)
    blocked = false
    await expect.poll(() => subscriptions.length, { timeout: 15000 }).toBe(2)
    await expect(pages[1].locator('.enemy-intent')).toBeVisible({ timeout: 15000 })
    expect(subscriptions).toEqual([2, 2]); expect(battleReads).toBeGreaterThan(readsBefore)
    sendOld!(oldBattleFrame); if (oldMapFrame) sendOld!(oldMapFrame)
    for (const page of pages) {
      await expect(page.locator('.enemy-name')).toContainText('熊山君')
      await expect(page.locator('.rewards-overlay')).toHaveCount(0)
      await expect(page).toHaveURL(/\/battle$/)
    }
    for (const page of pages) await page.getByRole('button', { name: '结束回合', exact: true }).click()
    await expect.poll(async () => (await battle(1)).turnNumber).toBe(2)
    const persisted = JSON.parse(compose(['redis', 'redis-cli', '--raw', 'GET', `multiplayer-battle:${code}`]))
    expect(persisted.battleId).toBe(second.battleId)
    await pages[1].screenshot({ path: info.outputPath('second-battle-reconnected.png') })
    const evidence = { room: code, first: { id: first.battleId, generation: first.battleGeneration, version: first.stateVersion, enemy: first.enemy.name },
      second: { id: second.battleId, generation: second.battleGeneration, version: second.stateVersion, enemy: second.enemy.name, hp: second.enemy.hp },
      subscriptions, battleReads, oldFrameReplayed: true, redisBattleId: persisted.battleId }
    console.log(JSON.stringify(evidence))
    await info.attach('battle-generations.json', { body: JSON.stringify(evidence, null, 2), contentType: 'application/json' })
  } finally { for (const context of contexts) await context.close() }
})
