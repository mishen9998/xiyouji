import { test, expect, type Page } from '@playwright/test'

const cards = ['挥棒', '格挡', '筋斗云', '火眼金睛', '七十二变'].map((name, index) => ({ id: index + 1, name, type: 'ATTACK', cost: 1, damage: 6, block: 0, drawCards: 0, upgraded: false, description: '造成 6 点伤害。出牌前确认效果与敌人攻击预告。' }))
const player = { characterClass: 'SUN_WUKONG', displayName: '孙悟空', hp: 60, maxHp: 80, block: 0, energy: 3, maxEnergy: 3, gold: 120, deckSize: 10, floor: 1, deck: cards, relics: [], hand: cards, buffs: [] }
const nodes = Array.from({ length: 27 }, (_, row) => Array.from({ length: row === 26 ? 1 : 4 }, (_, col) => ({ id: `L1-R${row}-C${col}`, row, col, type: row === 26 ? 'BOSS' : row === 0 ? 'RANDOM' : row === 1 ? 'SHOP' : 'BATTLE', name: row === 26 ? '黑熊精' : row === 0 ? '双叉岭迷途' : row === 1 ? '土地庙' : '山间遭遇', visited: row === 0, accessible: row === 1, connections: row < 26 ? [`L1-R${row + 1}-C0`] : [] }))).flat()
const event = { eventInstanceId: 't5-event', definitionId: 'fork', title: '双叉岭迷途', text: '雾气遮住前路，山神指点两条小径。你可以付出些许体力探路，也可以请山民引路，或暂且离开。', resolved: false, options: [
  { id: 'option1', label: '消耗最大生命 8%，获得 20 金币', enabled: true, members: { pilgrim: { hpCost: 7, goldCost: 0, reward: 'GOLD', amount: 20, goldReward: 20 } } },
  { id: 'option2', label: '消耗 10 金币，恢复最大生命 10%', enabled: true, members: { pilgrim: { hpCost: 0, goldCost: 10, reward: 'HEAL', amount: 8, goldReward: 0 } } },
  { id: 'leave', label: '离开（无副作用）', enabled: true, members: {} },
] }

export async function fixture(page: Page, options: { battle?: boolean; rewards?: boolean; multiplayer?: boolean; lobby?: boolean; map?: boolean } = {}) {
  let currentNode = options.map ? { ...nodes[0], type: 'BATTLE' } : nodes[0], resolved = false, version = 1
  const members = ['孙悟空', '猪八戒', '沙僧', '白龙马', '唐三藏'].map((username, i) => ({ ...player, userId: i ? `friend${i}` : 'pilgrim', username, characterClass: ['SUN_WUKONG', 'ZHU_BAJIE', 'SHA_SENG', 'BAI_LONGMA', 'TANG_SANZANG'][i], index: i, ready: true, host: i === 0, alive: true, endedTurn: false, strength: 0, dexterity: 0, drawPileSize: 5, discardPileSize: 0, buffs: {}, hand: cards.map((card, index) => ({ ...card, index, exhaust: false })) }))
  const enemy = { name: '黑熊精', hp: options.rewards ? 0 : 130, maxHp: 160, block: 6, intent: 'ATTACK', intentValue: 8, intentHits: 3, intentTargetUserIds: ['pilgrim'], intentEffects: { actionType: 'MULTI_HIT', block: 0, strength: 0, statuses: {}, targetDamage: { pilgrim: { damagePerHit: 8, hits: 3, blockAbsorbed: 0, hpLoss: 24 } } }, isBoss: false, buffs: options.multiplayer ? {} : [] }
  const room = () => ({ code: 'T5ROOM01', hostUserId: 'pilgrim', players: members, playerCount: 5, status: options.lobby ? 'WAITING' : options.battle ? 'IN_BATTLE' : 'IN_MAP', createdAt: new Date().toISOString(), floor: 1, maxLayer: 3, map: nodes, currentNode, stateVersion: version })
  const battle = () => ({ roomCode: 'T5ROOM01', stateVersion: version, inBattle: true, turnNumber: 1, playerTurn: true, battleOver: !!options.rewards, victory: !!options.rewards, player, players: members, enemy, alivePlayerCount: 5, playersEndedTurn: 0, combatLog: ['黑熊精举起长枪，预告连续三次攻击。'], rewardsPhase: !!options.rewards, rewards: options.multiplayer ? Object.fromEntries(members.map(member => [member.userId, cards])) : { goldReward: 50, cardRewards: resolved ? [] : cards, resolved } })
  await page.addInitScript(() => {
    localStorage.setItem('xiyouji_jwt_token', `eyJhbGciOiJub25lIn0.${btoa(JSON.stringify({ sub: 'pilgrim', exp: 9999999999 }))}.fixture`)
    localStorage.setItem('xiyouji_auth_profile', JSON.stringify({ account: 'pilgrim', username: 'pilgrim', role: 'PLAYER' }))
    localStorage.setItem('xiyouji_session_id', 't5-session')
  })
  if (options.lobby) await page.addInitScript(() => sessionStorage.setItem('xiyouji_room:pilgrim', 'T5ROOM01'))
  await page.route('http://*/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    let body: unknown
    if (path.includes('/battle/') || path.includes('/multiplayer/')) body = battle()
    else if (path.includes('/move/')) { const id = route.request().postDataJSON().nodeId; currentNode = nodes.find(node => node.id === id)!; version++; body = { node: currentNode, eventType: currentNode.type.toLowerCase(), stateVersion: version } }
    else if (path.includes('/event/')) { const action = route.request().postDataJSON().action; if (action.startsWith('option') || action === 'leave') resolved = true; body = { stateVersion: version, player, shopCards: cards, message: event.text, storyEvent: { scenes: [], event: { ...event, resolved } } } }
    else if (path.startsWith('/api/room/')) body = room()
    else if (path.includes('/reward/choose/')) { resolved = true; body = { success: true, stateVersion: ++version, player } }
    else body = { sessionId: 't5-session', stateVersion: version, player, map: nodes, currentNode, currentLayer: 1, maxLayer: 3, inBattle: !!options.battle }
    await route.fulfill({ json: body })
  })
  await page.routeWebSocket('**/ws?*', socket => socket.close())
}

async function noOverflow(page: Page) {
  expect(await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, width: innerWidth }))).toEqual(expect.objectContaining({ width: expect.any(Number) }))
  const dims = await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, width: innerWidth }))
  expect(dims.scroll, 'No whole-page horizontal scroll').toBeLessThanOrEqual(dims.width + 1)
}
async function capture(page: Page, info: import('@playwright/test').TestInfo, name: string) {
  await noOverflow(page)
  await expect.poll(() => page.locator('img').evaluateAll(images => images.filter(image => {
    const rect = image.getBoundingClientRect()
    return rect.bottom > 0 && rect.top < innerHeight && rect.right > 0 && rect.left < innerWidth
  }).every(image => image.complete && image.naturalWidth > 0)), { message: 'Visible illustrations have decoded successfully' }).toBe(true)
  const undersized = await page.locator('button, [role=button], input, summary').evaluateAll(elements => elements.flatMap(element => {
    const r = element.getBoundingClientRect(), css = getComputedStyle(element)
    if (!r.width || !r.height || css.visibility === 'hidden' || r.bottom <= 0 || r.top >= innerHeight) return []
    return r.width < 43.9 || r.height < 43.9 ? [{ label: element.textContent?.trim().slice(0, 30), width: r.width, height: r.height }] : []
  }))
  expect(undersized, 'Touch targets are at least 44 by 44 CSS pixels').toEqual([])
  if ((name === 'battle' || name === 'five-player-battle') && !info.title.includes('text200')) {
    await expect(page.locator('.enemy-intent .intent-heading')).toBeInViewport({ ratio: 1 })
    await expect(page.locator('.enemy-intent .intent-effect')).toBeInViewport({ ratio: 1 })
    await expect(page.locator('.enemy-intent .intent-target')).toBeInViewport({ ratio: 1 })
    await expect(page.getByRole('button', { name: '打出此牌', exact: true })).toBeInViewport({ ratio: 1 })
    await expect(page.getByRole('button', { name: '结束回合', exact: true })).toBeInViewport({ ratio: 1 })
  }
  await page.screenshot({ path: info.outputPath(`${name}.png`), fullPage: true })
}

for (const [width, height, textScale = 100] of [[360, 800], [390, 844], [768, 1024], [1366, 768], [1440, 900], [844, 390], [390, 844, 200]]) {
  test(`viewport ${width}x${height} text${textScale}: identity, home, selection, map, event, shop, battle, rewards`, async ({ page }, info) => {
    test.setTimeout(60000)
    await page.setViewportSize({ width, height })
    if (textScale === 200) await page.addInitScript(() => document.addEventListener('DOMContentLoaded', () => { document.documentElement.style.fontSize = '200%' }))
    await fixture(page)
    await page.goto('/'); await expect(page.getByLabel('登录账号')).toBeVisible(); await capture(page, info, 'identity')
    await page.goto('/menu'); await page.getByRole('button', { name: /单人游戏/ }).focus(); await capture(page, info, 'home')
    await page.getByRole('button', { name: /单人游戏/ }).click(); await expect(page.locator('.char-card')).toHaveCount(5); await capture(page, info, 'selection')
    await page.goto('/map'); await expect(page.locator('.branch-choices')).toBeVisible(); await capture(page, info, 'event')
    await page.getByRole('button', { name: '离开（无副作用）' }).click(); await expect(page.locator('.branch-choices')).toHaveCount(0); await capture(page, info, 'map')
    const node = page.locator('.map-node.accessible').first(); await node.scrollIntoViewIfNeeded(); await node.focus(); await page.keyboard.press('Enter')
    await expect(page.locator('.temple-shop')).toBeVisible(); await capture(page, info, 'shop')
    await page.locator('.shop-card').first().click(); await expect(page.locator('.purchase-button')).toBeVisible(); await capture(page, info, 'shop-detail')
    await page.unroute('http://*/api/**'); await fixture(page, { battle: true }); await page.goto('/battle'); await expect(page.locator('.enemy-intent')).toBeVisible(); await capture(page, info, 'battle')
    await page.unroute('http://*/api/**'); await fixture(page, { battle: true, rewards: true }); await page.goto('/battle'); await expect(page.locator('.result-modal')).toBeVisible(); await capture(page, info, 'rewards')
    await page.unroute('http://*/api/**'); await fixture(page, { battle: true, multiplayer: true }); await page.goto('/room/T5ROOM01/battle'); await expect(page.locator('.enemy-intent')).toBeVisible(); await capture(page, info, 'five-player-battle')
    await page.unroute('http://*/api/**'); await fixture(page, { map: true }); await page.goto('/room/T5ROOM01/map'); await expect(page.locator('.map-node.current')).toBeVisible(); await capture(page, info, 'five-player-map')
    await page.unroute('http://*/api/**'); await fixture(page, { lobby: true }); await page.goto('/room'); await expect(page.locator('.waiting-room')).toBeVisible(); await capture(page, info, 'five-player-room')
  })
}

test('map ResizeObserver reflows after rotation; focus and 200% root text remain reachable', async ({ page }, info) => {
  await fixture(page)
  await page.setViewportSize({ width: 390, height: 844 }); await page.goto('/map')
  await page.getByRole('button', { name: '离开（无副作用）' }).click()
  const before = await page.locator('.map-svg').boundingBox()
  await page.setViewportSize({ width: 844, height: 390 })
  await expect.poll(async () => (await page.locator('.map-svg').boundingBox())?.width).toBeGreaterThan(before!.width)
  const current = page.locator('.map-node.current').first(); await expect(current).toBeInViewport()
  await page.goto('/char-select'); await page.evaluate(() => { document.documentElement.style.fontSize = '200%' })
  await page.getByRole('button', { name: /唐三藏/ }).focus(); await page.keyboard.press('Enter')
  await expect(page.getByRole('button', { name: /唐三藏/ })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByRole('button', { name: '开始西行' })).toBeInViewport(); await capture(page, info, 'text-200')
})

test('no WebGL / reduced motion still supports card preview and one in-flight command', async ({ page }, info) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.addInitScript(() => { HTMLCanvasElement.prototype.getContext = (() => null) as typeof HTMLCanvasElement.prototype.getContext })
  await fixture(page, { battle: true })
  let requests = 0
  await page.route('**/api/game/battle/play/**', async route => {
    requests++
    await new Promise(resolve => setTimeout(resolve, 300))
    await route.fulfill({ json: { stateVersion: 2, inBattle: true, turnNumber: 1, playerTurn: true, battleOver: false, victory: false, player, enemy: { name: '黑熊精', hp: 124, maxHp: 160, block: 0, intent: 'ATTACK', intentValue: 8, isBoss: false, buffs: [] } } })
  })
  await page.goto('/battle')
  await page.getByRole('button', { name: '体验 3D' }).click()
  await expect(page.locator('[data-renderer=illustration]').first()).toBeVisible()
  await page.locator('.game-card').first().click()
  expect(requests).toBe(0)
  const confirm = page.getByRole('button', { name: '打出此牌', exact: true })
  await confirm.evaluate(button => { (button as HTMLButtonElement).click(); (button as HTMLButtonElement).click() })
  await expect(page.getByText(/正在同步本次操作|正在出牌|同步中/).first()).toBeVisible()
  await expect.poll(() => requests).toBe(1)
  await expect(page.getByRole('button', { name: '结束回合', exact: true })).toBeEnabled()
  await capture(page, info, 'reduced-motion-no-webgl')
})

test('real local service: guest creates game and coop room using the new UI', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 }); await page.goto('/')
  await page.getByRole('button', { name: /游客模式/ }).click(); await expect(page).toHaveURL(/\/menu$/)
  await page.getByRole('button', { name: /单人游戏/ }).click(); await page.getByRole('button', { name: /孙悟空/ }).click()
  await page.getByRole('button', { name: '开始西行' }).click(); await expect(page).toHaveURL(/\/map$/)
  const skip = page.getByRole('button', { name: '跳过剧情' }); if (await skip.isVisible()) await skip.click()
  await expect.poll(() => page.locator('.map-node').count()).toBeGreaterThan(27); await capture(page, info, 'real-map')
  await page.getByRole('button', { name: /主菜单/ }).click(); await page.getByRole('button', { name: /多人游戏/ }).click()
  await page.locator('.lobby-btn.create').click(); await expect(page.locator('.code-value')).toBeVisible()
  await page.locator('.char-card-mini').first().click(); await page.getByRole('button', { name: '准备', exact: true }).click()
  await expect(page.locator('.slot-ready.ready')).toBeVisible(); await capture(page, info, 'real-room')
})
