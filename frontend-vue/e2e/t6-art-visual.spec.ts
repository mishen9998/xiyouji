import { test, expect, type Page } from '@playwright/test'
import { readFileSync } from 'node:fs'
const mapping = JSON.parse(readFileSync(new URL('../src/constants/scene-images.json', import.meta.url), 'utf8')) as Record<'journey' | 'camp' | 'blackwind' | 'firemountain' | 'lionridge' | 'completion' | 'blackbear' | 'bullking' | 'roc', string>
const manifest = JSON.parse(readFileSync(new URL('../src/constants/image-manifest.json', import.meta.url), 'utf8'))

// Rendering fixtures only: real production components and image HTTP responses.
// Actual service progression is exercised separately by t6-real-integration.spec.ts.
const widths = [[360, 800], [390, 844], [768, 1024], [1366, 768], [1440, 900], [844, 390], [390, 844, 200]]
const scenes = ['blackwind', 'firemountain', 'lionridge'] as const
const bosses = ['黑熊精', '牛魔王', '大鹏']
const bossKeys = ['blackbear', 'bullking', 'roc'] as const
const entries = manifest.entries as Record<string, { variants: Array<{ webp: { url: string }; avif: { url: string } }> }>
function expectedPaths(key: keyof typeof mapping) { return entries[mapping[key]].variants.flatMap(v => [v.webp.url, v.avif.url]) }

async function artFixture(page: Page) {
  const state = { layer: 1, complete: false }
  const cards = ['挥棒', '格挡'].map((name, index) => ({ id: index + 1, index, name, cost: 1, damage: 6, type: 'ATTACK', description: '造成6点伤害', drawCards: 0, upgraded: false, exhaust: false }))
  const player = { characterClass: 'SUN_WUKONG', displayName: '孙悟空', hp: 60, maxHp: 80, energy: 3, maxEnergy: 3, block: 0, gold: 120, deck: cards, hand: cards, relics: [], buffs: [] }
  const players = ['SUN_WUKONG', 'ZHU_BAJIE', 'SHA_SENG', 'BAI_LONGMA', 'TANG_SANZANG'].map((characterClass, index) => ({ ...player, characterClass, userId: index ? `friend${index}` : 'pilgrim', username: index ? `同行${index}` : '孙悟空', alive: true, endedTurn: false, ready: true, buffs: {} }))
  const storyEvent = { scenes: [{ id: 'art-complete', trigger: 'COMPLETE', title: '取经归来', text: '灵山钟声回荡，师徒终于走到经卷之前。回望来路，有黑风山的灯火、火焰山的清雨，也有狮驼岭获救的人们。真经记下道理，这一路的相扶相助则让道理有了温度。', skippable: true }] }
  await page.addInitScript(() => {
    localStorage.setItem('xiyouji_jwt_token', `eyJhbGciOiJub25lIn0.${btoa(JSON.stringify({ sub: 'pilgrim', exp: 9999999999 }))}.fixture`)
    localStorage.setItem('xiyouji_auth_profile', JSON.stringify({ account: 'pilgrim', username: 'pilgrim', role: 'PLAYER' }))
    localStorage.setItem('xiyouji_session_id', 't6-art')
    sessionStorage.setItem('xiyouji-story-seen', JSON.stringify(['art-complete']))
  })
  await page.route('http://*/api/**', route => {
    const path = new URL(route.request().url()).pathname
    const enemy = { name: bosses[state.layer - 1], hp: 130, maxHp: 160, block: 6, isBoss: true, intent: 'ATTACK', intentValue: 8, intentHits: 1, intentTargetUserIds: players.map(p => p.userId), intentEffects: { actionType: 'ATTACK_ALL', block: 0, strength: 0, statuses: {}, targetDamage: Object.fromEntries(players.map(p => [p.userId, { damagePerHit: 8, hits: 1, blockAbsorbed: 0, hpLoss: 8 }])) }, buffs: {} }
    const body = path.includes('/battle/') || path.includes('/multiplayer/')
      ? { roomCode: 'T6ART001', stateVersion: 1, inBattle: true, turnNumber: 1, playerTurn: true, battleOver: false, player, players, enemy, alivePlayerCount: 5, playersEndedTurn: 0, combatLog: [] }
      : path.startsWith('/api/room/')
        ? { code: 'T6ART001', hostUserId: 'pilgrim', players, playerCount: 5, status: state.complete ? 'FINISHED' : 'IN_BATTLE', floor: state.layer, stateVersion: 1, storyEvent: state.complete ? storyEvent : undefined }
        : { sessionId: 't6-art', stateVersion: 1, player, map: [], currentNode: { type: 'BOSS' }, currentLayer: state.layer, maxLayer: 3, inBattle: !state.complete, storyEvent: state.complete ? storyEvent : undefined }
    return route.fulfill({ json: body })
  })
  await page.routeWebSocket('**/ws?*', socket => socket.close())
  return state
}

for (const [width, height, scale = 100] of widths) test(`final art ${width}x${height} text${scale}: three bosses, solo/coop and completion`, async ({ page }, info) => {
  test.setTimeout(60000)
  await page.setViewportSize({ width, height })
  if (scale === 200) await page.addInitScript(() => document.addEventListener('DOMContentLoaded', () => { document.documentElement.style.fontSize = '200%' }))
  const state = await artFixture(page)
  for (const multiplayer of [false, true]) for (let layer = 1; layer <= 3; layer++) {
    state.layer = layer
    await page.goto(multiplayer ? '/room/T6ART001/battle' : '/battle')
    await expect(page.locator('.enemy-intent')).toBeVisible()
    const background = page.locator('.battle-backdrop img')
    const portrait = page.locator(multiplayer ? '.enemy-avatar img' : '.enemy-portrait img')
    for (const [locator, key] of [[background, scenes[layer - 1]], [portrait, bossKeys[layer - 1]]] as const) {
      await expect.poll(() => locator.evaluate(img => (img as HTMLImageElement).complete && (img as HTMLImageElement).naturalWidth > 0)).toBe(true)
      const loaded = await locator.evaluate(img => new URL((img as HTMLImageElement).currentSrc).pathname)
      expect(expectedPaths(key)).toContain(loaded)
    }
    expect(await portrait.evaluate(img => getComputedStyle(img).objectFit)).toBe('contain')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
    await expect(page.locator('.intent-damage li')).toHaveCount(5)
    const confirm = page.getByRole('button', { name: '打出此牌', exact: true })
    await confirm.scrollIntoViewIfNeeded()
    await expect(confirm).toBeInViewport({ ratio: 1 })
    await page.screenshot({ path: info.outputPath(`${multiplayer ? 'coop' : 'solo'}-${scenes[layer - 1]}.png`), fullPage: true })
  }
  state.complete = true
  for (const multiplayer of [false, true]) {
    await page.goto(multiplayer ? '/room/T6ART001/complete' : '/complete')
    await expect(page.getByRole('heading', { name: '取经归来' })).toBeVisible()
    await expect.poll(() => page.locator('.completion-art img').evaluate(img => (img as HTMLImageElement).complete && (img as HTMLImageElement).naturalWidth > 0)).toBe(true)
    expect(expectedPaths('completion')).toContain(await page.locator('.completion-art img').evaluate(img => new URL((img as HTMLImageElement).currentSrc).pathname))
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
    const button = page.getByRole('button', { name: '返回营地', exact: true })
    await button.scrollIntoViewIfNeeded(); await expect(button).toBeInViewport({ ratio: 1 })
    const box = await button.boundingBox(); expect(box!.height).toBeGreaterThanOrEqual(48)
    if (scale === 200) expect(await page.locator('.completion-story').evaluate(p => parseFloat(getComputedStyle(p).fontSize))).toBeGreaterThanOrEqual(32)
    await page.screenshot({ path: info.outputPath(`${multiplayer ? 'coop' : 'solo'}-completion.png`), fullPage: true })
  }
})

test('final completion art has accessible network-error fallback', async ({ page }) => {
  const state = await artFixture(page); state.complete = true
  await page.route('**/illustrations/**', route => route.abort())
  await page.goto('/complete')
  await expect(page.getByRole('heading', { name: '取经归来' })).toBeVisible()
  await expect(page.locator('.completion-art .responsive-image__fallback')).toBeVisible()
  await expect(page.getByRole('button', { name: '返回营地', exact: true })).toBeEnabled()
})
