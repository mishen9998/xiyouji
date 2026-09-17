import { test, expect, type Browser, type BrowserContext, type Page, type TestInfo } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
test.use({ actionTimeout: 15000 })

/** Real integration, never a JSON API mock. Only the disposable Compose Redis is
 * seeded, after creation by the real service. Every row is visited in the UI.
 * Enemy definitions/loops, services, JWT, STOMP and databases remain unmodified.
 * HP/gold/card damage and node assignments are explicit deterministic fixtures,
 * not evidence of difficulty balance, randomness or normal-duration gameplay. */
const ROOT = resolve(import.meta.dirname, '../..')
const PROJECT = process.env.T6_COMPOSE_PROJECT || 'xiyouji-t6-20260917'
const APP = ['http://127.0.0.1:18086', 'http://127.0.0.1:18087']
const CHARS = ['SUN_WUKONG', 'ZHU_BAJIE', 'SHA_SENG', 'TANG_SANZANG', 'BAI_LONGMA']
const ENEMIES = [
  ['寅将军', '熊山君', '白衣秀士', '黑熊精'],
  ['红孩儿', '黄风怪', '如意真仙', '牛魔王'],
  ['黄狮精', '七狮', '铁背苍狼怪', '大鹏'],
]
const EVENTS = [
  ['双叉岭迷途', '禅院护袈裟', '山神密径'],
  ['火云洞童谣', '芭蕉扇借风', '落胎泉取水'],
  ['狮驼岭难民', '小钻风巡山', '灵山前问心'],
]
type Identity = { token: string; username: string; account: string; role: string }
type Actor = { identity: Identity; app: string; page: Page; context: BrowserContext }

function compose(args: string[], input?: string) {
  if (!PROJECT.startsWith('xiyouji-t6-')) throw new Error('Refuse non-T6 Compose namespace')
  return execFileSync('docker', ['compose', '-p', PROJECT, '-f', 'docker-compose.t6.yml', 'exec', '-T', ...args], {
    cwd: ROOT, encoding: 'utf8', input, maxBuffer: 8 * 1024 * 1024,
  }).trim()
}
function list(value: any): any[] {
  return Array.isArray(value) && typeof value[0] === 'string' && Array.isArray(value[1]) ? value[1] : value
}
function redis(key: string) {
  const value = compose(['redis', 'redis-cli', '--raw', 'GET', key])
  if (!value) throw new Error(`Fixture target must already exist: ${key}`)
  return JSON.parse(value)
}
function seed(key: string, snapshot: any) {
  compose(['redis', 'redis-cli', '-x', 'SET', key], JSON.stringify(snapshot))
  compose(['redis', 'redis-cli', 'EXPIRE', key, '7200'])
}
function enemyIds(): Record<string, string> {
  return Object.fromEntries(compose(['mysql', 'mysql', '--default-character-set=utf8mb4', '-uroot', '-pt6-local-disposable-only', 'xiyouji_t6', '-N', '-e', 'SELECT name,id FROM enemies;'])
    .split('\n').map(line => line.trim().split('\t')))
}
function deterministicLayer(key: string, layer: number, multiplayer: boolean, ids: Record<string, string>) {
  const snapshot = redis(key)
  expect(multiplayer ? snapshot.floor : snapshot.currentLayer).toBe(layer)
  const nodes = list(snapshot.map)
  expect(new Set(nodes.map(n => n.row)).size).toBe(27)
  const path = Array.from({ length: 27 }, (_, row) => nodes.find(n => n.row === row))
  for (const node of nodes) node.accessible = false
  for (let row = 0; row < path.length; row++) {
    const node = path[row]
    if (row >= 1 && row <= 3) {
      // Keep all nodes, but swap occupied columns: do not manufacture overlap.
      const displaced = nodes.find(other => other !== node && other.row === row && other.col === 2)
      if (displaced) displaced.col = node.col
      node.col = 2 // (row + col) % 3 => 0,1,2
    }
    node.connections = ['java.util.ArrayList', row < 26 ? [path[row + 1].id] : []]
    node.visited = false; node.accessible = row === 0
    node.type = 'REST'; node.name = `验收休息站 ${row}`; node.enemyId = null
    node.eventState = null; node.shopStock = { '@class': 'java.util.LinkedHashMap' }
    if (row >= 1 && row <= 3) { node.type = 'RANDOM'; node.name = EVENTS[layer - 1][row - 1] }
    if (row === 4) { node.type = 'SHOP'; node.name = '土地庙' }
    if ((row >= 5 && row <= 7) || row === 26) {
      const name = ENEMIES[layer - 1][row === 26 ? 3 : row - 5]
      node.type = row === 26 ? 'BOSS' : 'BATTLE'; node.name = name; node.enemyId = ids[name]
      expect(node.enemyId, `seeded enemy ${name}`).toBeTruthy()
    }
  }
  const players = multiplayer ? list(snapshot.players) : [snapshot.player]
  // Fixture acceleration is applied once, then normal service settlement carries
  // HP, gold, deck and relic changes through all three chapters.
  if (layer === 1) for (const player of players) {
    player.maxHp = 10000; player.hp = 9000; player.gold = 1000
    const deck = list(player.deck)
    for (const card of deck) {
      card.cost = 0; card.upgradeable = true
      if (card.damage > 0) { card.damage = 10000; card.description = '隔离验收夹具：造成10000点伤害。' }
    }
    // One legitimate non-basic card target for 小钻风; preserve real card schema.
    const extra = structuredClone(deck.find(card => card.damage > 0))
    extra.rarity = 'COMMON'; extra.upgraded = false; deck.push(extra)
  }
  snapshot.stateVersion++
  seed(key, snapshot)
  return path.map(n => ({ id: n.id, row: n.row, type: n.type, name: n.name }))
}
async function request(app: string, path: string, identity?: Identity, body?: unknown, version?: number) {
  const response = await fetch(app + path, { method: body === undefined ? 'GET' : 'POST', headers: {
    'Content-Type': 'application/json', 'X-Idempotency-Key': crypto.randomUUID(),
    ...(identity ? { Authorization: `Bearer ${identity.token}` } : {}),
    ...(version === undefined ? {} : { 'X-Expected-State-Version': String(version) }),
  }, body: body === undefined ? undefined : JSON.stringify(body) })
  const result = await response.json()
  expect(response.status, `${path}: ${JSON.stringify(result)}`).toBeLessThan(300)
  return result
}
async function actor(browser: Browser, index: number, info: TestInfo): Promise<Actor> {
  const app = APP[index % 2]
  const identity = await request(app, '/api/auth/guest', undefined, {}) as Identity
  const desktop = index === 0 && process.env.T6_HOST_MOBILE !== '1'
  const context = await browser.newContext({ viewport: { width: desktop ? 1366 : 390, height: desktop ? 768 : 844 } })
  await context.addInitScript(({ identity, app }) => {
    localStorage.setItem('xiyouji_jwt_token', identity.token)
    localStorage.setItem('xiyouji_auth_profile', JSON.stringify(identity))
    const Native = window.WebSocket
    window.WebSocket = class extends Native {
      constructor(url: string | URL, protocols?: string | string[]) {
        const target = new URL(String(url), location.href)
        if (target.pathname === '/ws') { const backend = new URL(app); target.host = backend.host; target.protocol = 'ws:' }
        super(target.href, protocols)
      }
    }
  }, { identity, app })
  // Transport rewrite only: no fulfill(), static responses or synthetic JWT.
  await context.route('http://*/api/**', route => {
    const url = new URL(route.request().url())
    return route.continue({ url: app + url.pathname + url.search })
  })
  const page = await context.newPage()
  page.on('pageerror', error => console.log(`PAGEERROR actor${index}: ${error.message}`))
  page.on('response', response => {
    if (response.url().includes('/api/') && response.status() >= 400) console.log(`API ${response.status()} ${response.url()}`)
  })
  await page.goto('/menu')
  await expect(page).toHaveURL(/\/menu$/)
  return { identity, app, context, page }
}
async function skipStory(page: Page) {
  const button = page.getByRole('button', { name: '跳过剧情', exact: true })
  if (await button.isVisible()) await button.click()
}
async function state(a: Actor, id: string, multiplayer: boolean) {
  return request(a.app, multiplayer ? `/api/room/${id}` : `/api/game/state/${id}`, a.identity)
}
async function battleState(a: Actor, id: string, multiplayer: boolean) {
  return request(a.app, multiplayer ? `/api/multiplayer/battle/${id}/state` : `/api/game/battle/state/${id}`, a.identity)
}
async function snap(page: Page, info: TestInfo, name: string) {
  await skipStory(page)
  await page.screenshot({ path: info.outputPath(`${name}.png`) })
}
async function saveEvidence(info: TestInfo, name: string, report: unknown) {
  // A list reporter does not persist body-only attachments. Keep raw JSON beside
  // screenshots regardless of reporter, then link it into any HTML report.
  const path = info.outputPath(name)
  await writeFile(path, JSON.stringify(report, null, 2))
  await info.attach(name, { path, contentType: 'application/json' })
}
async function handleRest(page: Page, multiplayer: boolean) {
  await expect(page.locator('.modal-box')).toBeVisible()
  await page.locator('.modal-box .btn-primary').last().click()
  await expect(page.locator('.modal-box')).toHaveCount(0)
}
async function handleShop(a: Actor, id: string, multiplayer: boolean, info: TestInfo, layer: number) {
  const page = a.page
  await expect(page.locator('.shop-card').first()).toBeVisible()
  const before = await state(a, id, multiplayer)
  const player = multiplayer ? before.players.find((p: any) => p.userId === a.identity.username) : before.player
  const price = player.relics?.some((relic: any) => relic.name === '通关文牒') ? 40 : 50
  await page.locator('.shop-card').first().click()
  await expect(page.locator('.purchase-button')).toContainText(`供奉 ${price} 金币购买`)
  await page.locator('.purchase-button').click()
  await expect(page.locator('.purchase-button')).toContainText('已收入牌组')
  const after = await state(a, id, multiplayer)
  const updated = multiplayer ? after.players.find((p: any) => p.userId === a.identity.username) : after.player
  expect(updated.gold).toBe(player.gold - price)
  expect(updated.deck.length).toBe(player.deck.length + 1)
  if (layer === 1) await snap(page, info, 'real-shop-purchased')
  await page.locator('.back-link').click(); await page.getByTestId('temple-forward').click()
  await expect(page.getByTestId('temple-shop')).toHaveCount(0)
}
async function fight(actors: Actor[], id: string, multiplayer: boolean, info: TestInfo, layer: number, row: number, name: string) {
  const host = actors[0]; const page = host.page
  await expect(page).toHaveURL(multiplayer ? /\/room\/[^/]+\/battle$/ : /\/battle$/)
  await expect(page.locator('.enemy-intent')).toBeVisible()
  for (const a of actors) await skipStory(a.page)
  let battle = await battleState(host, id, multiplayer)
  expect(battle.enemy.name).toBe(name)
  // One complete enemy action every encounter, including all three bosses.
  // Existing Maven EnemyTurnResolver/loop tests cover all seven action variants.
  const firstTurn = battle.turnNumber
  for (const a of actors) {
    await expect(a.page).toHaveURL(multiplayer ? /\/room\/[^/]+\/battle$/ : /\/battle$/)
    await a.page.getByRole('button', { name: '结束回合', exact: true }).click()
    await expect.poll(async () => {
      const current = await battleState(host, id, multiplayer)
      return multiplayer ? current.players.find((p: any) => p.userId === a.identity.username).endedTurn || current.turnNumber > firstTurn : current.turnNumber > firstTurn
    }).toBeTruthy()
  }
  await expect.poll(async () => (await battleState(host, id, multiplayer)).turnNumber).toBe(firstTurn + 1)
  battle = await battleState(host, id, multiplayer)
  if (row === 26) await snap(page, info, `layer-${layer}-boss-real-turn`)
  for (let attempts = 0; attempts < 8 && !battle.battleOver; attempts++) {
    const hand = multiplayer ? battle.players.find((p: any) => p.userId === host.identity.username).hand : battle.player.hand
    const index = hand.findIndex((card: any) => card.damage >= 10000)
    if (index < 0) {
      for (const a of actors) await a.page.getByRole('button', { name: '结束回合', exact: true }).click()
    } else {
      await page.locator(multiplayer ? '.hand-cards .game-card' : '.hand-zone .game-card').nth(index).click()
      await page.getByRole('button', { name: '打出此牌', exact: true }).click()
    }
    await expect.poll(async () => (await battleState(host, id, multiplayer)).stateVersion).toBeGreaterThan(battle.stateVersion)
    battle = await battleState(host, id, multiplayer)
  }
  expect(battle.victory).toBe(true)
  if (row === 26) await snap(page, info, `layer-${layer}-boss-reward`)
  for (const a of actors) {
    await skipStory(a.page)
    if (row === 5) {
      await a.page.locator(multiplayer ? '.reward-card' : '.reward-cards .card-mini').first().click()
      await a.page.getByRole('button', { name: multiplayer ? '确认领取' : '继续前进', exact: true }).click()
    } else await a.page.getByRole('button', { name: '跳过奖励', exact: true }).click()
    if (multiplayer) await expect(a.page.locator('.claimed-status')).toBeVisible()
  }
  if (multiplayer) await page.getByRole('button', { name: '返回地图', exact: true }).click()
  const final = layer === 3 && row === 26
  for (const a of actors) await expect(a.page).toHaveURL(final ? /\/complete$/ : multiplayer ? /\/room\/[^/]+\/map$/ : /\/map$/)
}

test('real cross-instance disconnect, missed broadcast, exactly two reconnect subscriptions and REST convergence', async ({ browser }, info) => {
  test.setTimeout(90000)
  const actors = [await actor(browser, 0, info), await actor(browser, 1, info)]
  const [host, guest] = actors
  const subscriptions: number[] = []; let closeLink: (() => void) | undefined; let blocked = false; let reads = 0
  guest.page.on('request', req => { if (/\/api\/room\/[A-Z0-9]{8}$/.test(req.url()) && req.method() === 'GET') reads++ })
  await guest.page.routeWebSocket('**/ws?*', socket => {
    if (blocked) { socket.close(); return }
    const server = socket.connectToServer(); const index = subscriptions.push(0) - 1
    socket.onMessage(message => { if (message.toString().startsWith('SUBSCRIBE')) subscriptions[index]++; server.send(message) })
    closeLink = () => { server.close(); socket.close() }
  })
  try {
    await host.page.goto('/room'); await guest.page.goto('/room')
    await host.page.locator('.lobby-btn.create').click()
    const code = await host.page.locator('.code-value').innerText()
    await guest.page.getByPlaceholder('输入8位房间码').fill(code); await guest.page.locator('.lobby-btn.join').click()
    await expect(guest.page.locator('.connection-status')).toContainText('实时连接正常')
    await expect.poll(() => subscriptions[0]).toBe(2)
    blocked = true; closeLink!()
    await expect(guest.page.locator('.connection-status')).toContainText('恢复中')
    await host.page.locator('.char-card-mini').first().click()
    await expect(host.page.locator('.player-slot.is-me .slot-char')).toHaveText('孙悟空')
    await host.page.getByRole('button', { name: '准备', exact: true }).click()
    await expect(host.page.locator('.player-slot.is-me .slot-ready')).toHaveText('✓ 已准备')
    const before = reads; blocked = false
    await expect(guest.page.locator('.connection-status')).toContainText('实时连接正常', { timeout: 15000 })
    await expect(guest.page.locator('.player-slot').first()).toContainText('✓ 已准备')
    await expect.poll(() => reads).toBeGreaterThan(before)
    expect(subscriptions).toEqual([2, 2])
    const one = await state(host, code, true); const two = await state(guest, code, true)
    expect(two.stateVersion).toBe(one.stateVersion); expect(two.players).toEqual(one.players)
    await snap(guest.page, info, 'real-cross-instance-reconnected')
    await saveEvidence(info, 'real-recovery.json', { code, instances: APP, subscriptions, reads, stateVersion: one.stateVersion })
  } finally { for (const a of actors) await a.context.close() }
})

for (const count of [1, 2, 5]) test(`real ${count}-player UI journey: 81 rows, 9 events, shops, 12 enemies, three bosses and completion`, async ({ browser }, info) => {
  test.setTimeout(20 * 60 * 1000)
  const actors: Actor[] = []
  const multiplayer = count > 1
  const progress: unknown[] = []
  try {
    for (let i = 0; i < count; i++) actors.push(await actor(browser, i, info))
    const host = actors[0]; const page = host.page; let id: string
    if (!multiplayer) {
      const game = await request(host.app, '/api/game/new', host.identity, { characterClass: CHARS[0] })
      id = game.sessionId
      await page.evaluate(id => localStorage.setItem('xiyouji_session_id', id), id)
    } else {
      const room = await request(host.app, '/api/room/create', host.identity, {})
      id = room.code
      for (const a of actors.slice(1)) await request(a.app, '/api/room/join', a.identity, { code: id })
      for (let i = 0; i < actors.length; i++) {
        const a = actors[i]
        let current = await state(a, id, true)
        await request(a.app, `/api/room/${id}/character`, a.identity, { characterClass: CHARS[i] }, current.stateVersion)
        current = await state(a, id, true)
        await request(a.app, `/api/room/${id}/ready`, a.identity, {}, current.stateVersion)
      }
      const current = await state(host, id, true)
      await request(host.app, `/api/room/${id}/start-game`, host.identity, {}, current.stateVersion)
    }
    const key = multiplayer ? `room:${id}` : `xiyouji:session:v2:${id}`
    const ids = enemyIds()
    for (let layer = 1; layer <= 3; layer++) {
      const path = deterministicLayer(key, layer, multiplayer, ids)
      for (const a of actors) { await a.page.goto(multiplayer ? `/room/${id}/map` : '/map'); await skipStory(a.page) }
      await snap(page, info, `layer-${layer}-map`)
      for (const node of path) {
        for (const a of actors) await skipStory(a.page)
        const index = (await state(host, id, multiplayer)).map.findIndex((n: any) => n.id === node.id)
        const button = page.locator('.map-node').nth(index)
        await expect(button).toBeEnabled(); await button.click()
        if (node.type === 'RANDOM') {
          await expect(page.locator('.branch-option button').first()).toBeEnabled()
          await expect(page.locator('.modal-box h3')).toContainText(node.name)
          if (layer === 1) await snap(page, info, `event-${node.row}`)
          // Solo/5-player select option 1, dual selects option 2: both paths are
          // exercised for every event, with all living members settled together.
          await page.locator('.branch-option button').nth(count === 2 ? 1 : 0).click()
          await expect(page.locator('.branch-choices')).toHaveCount(0)
        } else if (node.type === 'SHOP') await handleShop(host, id, multiplayer, info, layer)
        else if (node.type === 'BATTLE' || node.type === 'BOSS') await fight(actors, id, multiplayer, info, layer, node.row, node.name)
        else await handleRest(page, multiplayer)
        progress.push({ layer, row: node.row, type: node.type, name: node.name })
        console.log(`${count}P layer=${layer} row=${node.row} ${node.type} ${node.name}`)
      }
    }
    const complete = await state(host, id, multiplayer)
    if (multiplayer) expect(complete.status).toBe('FINISHED')
    else { expect(redis(key).completed).toBe(true); expect(complete.currentLayer).toBe(3); expect(complete.inBattle).toBe(false) }
    expect(progress).toHaveLength(81)
    for (const a of actors) {
      await skipStory(a.page)
      await expect(a.page.getByRole('heading', { name: '取经归来', exact: true })).toBeVisible()
      await a.page.reload()
      await skipStory(a.page)
      await expect(a.page.getByRole('heading', { name: '取经归来', exact: true })).toBeVisible()
      await expect.poll(() => a.page.locator('.completion-art img').evaluate(img => (img as HTMLImageElement).complete && (img as HTMLImageElement).naturalWidth > 0)).toBe(true)
    }
    await snap(page, info, `${count}-player-complete`)
    await saveEvidence(info, 'real-journey.json', { count, id, instances: APP, viewports: actors.map(a => a.page.viewportSize()), fixture: 'isolated Redis: 27-row connected path, encounter/event assignments, HP 10000/9000, gold 1000, zero-cost 10000-damage attack cards; no API mocks', progress, final: complete })
  } finally {
    await saveEvidence(info, 'partial-progress.json', progress)
    for (const a of actors) await a.context.close()
  }
})
