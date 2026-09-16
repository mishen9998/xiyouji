import { test, expect, type Page, type Locator } from '@playwright/test'

/** Rendering-only fixture. Real built/dev UI; no backend correctness or real-device claim. */
async function battleFixture(page: Page, multiplayer: boolean, allTargets = false) {
  const hand = ['挥棒', '格挡', '筋斗云', '火眼金睛', '七十二变'].map((name, index) => ({ id: index + 1, index, name, type: 'ATTACK', cost: 1, damage: 6, block: 0, drawCards: 0, upgraded: false, exhaust: false, description: '造成 6 点伤害。出牌前确认效果与敌人攻击预告。' }))
  const player = { characterClass: 'SUN_WUKONG', displayName: '孙悟空', hp: 60, maxHp: 80, block: 0, energy: 3, maxEnergy: 3, gold: 120, floor: 1, deck: hand, hand, relics: [], buffs: [] }
  const players = ['孙悟空', '猪八戒', '沙僧', '白龙马', '唐三藏'].map((username, index) => ({ ...player, userId: index ? `friend${index}` : 'pilgrim', username, index, ready: true, alive: true, endedTurn: false, strength: 0, buffs: {}, characterClass: ['SUN_WUKONG', 'ZHU_BAJIE', 'SHA_SENG', 'BAI_LONGMA', 'TANG_SANZANG'][index] }))
  const targets = allTargets ? players : [players[0]]
  const enemy = { name: '黑熊精', hp: 130, maxHp: 160, block: 6, intent: 'ATTACK', intentValue: 8, intentHits: allTargets ? 1 : 3, intentTargetUserIds: targets.map(p => p.userId), intentEffects: { actionType: allTargets ? 'ATTACK_ALL' : 'MULTI_HIT', block: 0, strength: 0, statuses: {}, targetDamage: Object.fromEntries(targets.map(p => [p.userId, { damagePerHit: 8, hits: allTargets ? 1 : 3, blockAbsorbed: 0, hpLoss: allTargets ? 8 : 24 }])) }, isBoss: true, buffs: multiplayer ? {} : [] }
  const room = { code: 'T5ROOM01', hostUserId: 'pilgrim', players, playerCount: 5, status: 'IN_BATTLE', createdAt: '2026-09-17T00:00:00Z', floor: 1, stateVersion: 1 }
  const battle = { roomCode: room.code, stateVersion: 1, inBattle: true, turnNumber: 1, playerTurn: true, battleOver: false, victory: false, player, players, enemy, alivePlayerCount: 5, playersEndedTurn: 0, combatLog: ['黑熊精举起长枪，预告连续三次攻击。'] }
  await page.addInitScript(() => {
    localStorage.setItem('xiyouji_jwt_token', `eyJhbGciOiJub25lIn0.${btoa(JSON.stringify({ sub: 'pilgrim', exp: 9999999999 }))}.fixture`)
    localStorage.setItem('xiyouji_auth_profile', JSON.stringify({ account: 'pilgrim', username: 'pilgrim', role: 'PLAYER' }))
    localStorage.setItem('xiyouji_session_id', 't5-layout-session')
  })
  await page.route('http://*/api/**', route => {
    const path = new URL(route.request().url()).pathname
    const body = path.includes('/battle/') || path.includes('/multiplayer/') ? battle
      : path.startsWith('/api/room/') ? room
      : { sessionId: 't5-layout-session', stateVersion: 1, player, map: [], currentNode: { type: 'BATTLE' }, currentLayer: 1, maxLayer: 3, inBattle: true }
    return route.fulfill({ json: body })
  })
  await page.routeWebSocket('**/ws?*', socket => socket.close())
}

/** Unlike DOM visibility, these checks reject clipping by the dock or scroll ancestors. */
async function fullyReadable(locator: Locator) {
  await expect(locator).toBeInViewport({ ratio: 1 })
  const result = await locator.evaluate(element => {
    const r = element.getBoundingClientRect()
    let left = 0, top = 0, right = innerWidth, bottom = innerHeight
    for (let parent = element.parentElement; parent; parent = parent.parentElement) {
      const style = getComputedStyle(parent), p = parent.getBoundingClientRect()
      if (/(auto|scroll|hidden|clip)/.test(style.overflowX)) { left = Math.max(left, p.left); right = Math.min(right, p.right) }
      if (/(auto|scroll|hidden|clip)/.test(style.overflowY)) { top = Math.max(top, p.top); bottom = Math.min(bottom, p.bottom) }
    }
    const hit = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2)
    return { inside: r.left >= left - 1 && r.right <= right + 1 && r.top >= top - 1 && r.bottom <= bottom + 1,
      uncovered: !!hit && (hit === element || element.contains(hit)), rect: { x: r.x, y: r.y, width: r.width, height: r.height }, clip: { left, top, right, bottom } }
  })
  expect(result.inside, JSON.stringify(result)).toBe(true)
  expect(result.uncovered, JSON.stringify(result)).toBe(true)
}

for (const viewport of [{ width: 1366, height: 768 }, { width: 844, height: 390 }, { width: 360, height: 800 }]) {
  test(`five-target forecast preserves the first-screen self status: ${viewport.width}x${viewport.height}`, async ({ page }, info) => {
    await page.setViewportSize(viewport)
    await battleFixture(page, true, true)
    await page.goto('/room/T5ROOM01/battle')
    for (const selector of ['.intent-heading', '.intent-effect', '.intent-target', '.player-panel.is-me .hp-bar-container', '.player-panel.is-me .player-stats']) await fullyReadable(page.locator(selector))
    await expect(page.locator('.intent-damage li')).toHaveCount(5)
    await page.screenshot({ path: info.outputPath('five-targets.png'), fullPage: true })
  })
  for (const multiplayer of [false, true]) {
    test(`critical battle content is genuinely on-screen: ${multiplayer ? 'coop' : 'solo'} ${viewport.width}x${viewport.height}`, async ({ page }, info) => {
      await page.setViewportSize(viewport)
      await battleFixture(page, multiplayer)
      await page.goto(multiplayer ? '/room/T5ROOM01/battle' : '/battle')
      await expect(page.locator('.enemy-intent')).toBeVisible()
      // No scrollIntoView: all of these must be readable immediately on entry.
      for (const selector of ['.intent-heading', '.intent-effect', '.intent-target', '.confirm-card-btn', multiplayer ? '.btn-end-turn' : '.end-turn-btn']) await fullyReadable(page.locator(selector))
      await fullyReadable(page.locator(multiplayer ? '.player-panel.is-me .hp-bar-container' : '.arena-info .hp-bar-container'))
      await fullyReadable(page.locator(multiplayer ? '.player-panel.is-me .player-stats' : '.arena-enemy > .hp-bar-container'))
      if (viewport.height > 500) await fullyReadable(page.locator('.intent-damage'))
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
      await page.screenshot({ path: info.outputPath('critical-content.png'), fullPage: true })
    })
  }
}

test('200% text actually doubles key battle fonts and remains reachable', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await battleFixture(page, false)
  await page.goto('/battle')
  const text = page.locator('.intent-effect')
  await expect(text).toBeVisible()
  const before = await text.evaluate(element => parseFloat(getComputedStyle(element).fontSize))
  await page.evaluate(() => { document.documentElement.style.fontSize = '200%' })
  const after = await text.evaluate(element => parseFloat(getComputedStyle(element).fontSize))
  expect(after).toBeCloseTo(before * 2)
  await text.scrollIntoViewIfNeeded(); await fullyReadable(text)
  await page.locator('.game-card').first().click()
  await page.locator('.selection-preview').scrollIntoViewIfNeeded()
  await expect(page.locator('.selection-preview')).toContainText('出牌前确认效果与敌人攻击预告')
  await page.locator('.confirm-card-btn').scrollIntoViewIfNeeded(); await fullyReadable(page.locator('.confirm-card-btn'))
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
  await page.screenshot({ path: info.outputPath('actual-text-200.png'), fullPage: true })
})
