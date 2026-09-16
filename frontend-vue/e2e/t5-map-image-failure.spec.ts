import { test, expect, type Page } from '@playwright/test'

async function mapFixture(page: Page) {
  const player = { characterClass: 'SUN_WUKONG', displayName: '孙悟空', hp: 60, maxHp: 80, block: 0, energy: 3, maxEnergy: 3, gold: 80, floor: 1, deckSize: 1, deck: [], hand: [], buffs: [], relics: [{ name: '玉净瓶', description: '随身宝物', emoji: '💧' }] }
  const nodes = [
    { id: 'current', row: 0, col: 1, type: 'BATTLE', name: '山间遭遇', visited: true, accessible: false, connections: ['next'] },
    { id: 'next', row: 1, col: 1, type: 'SHOP', name: '土地庙', visited: false, accessible: true, connections: [] },
  ]
  let moves = 0
  await page.addInitScript(() => {
    localStorage.setItem('xiyouji_jwt_token', `eyJhbGciOiJub25lIn0.${btoa(JSON.stringify({ sub: 'pilgrim', exp: 9999999999 }))}.fixture`)
    localStorage.setItem('xiyouji_auth_profile', JSON.stringify({ account: 'pilgrim', username: 'pilgrim', role: 'PLAYER' }))
    localStorage.setItem('xiyouji_session_id', 'map-image-failure')
  })
  await page.route('http://*/api/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.includes('/move/')) { moves++; return route.fulfill({ json: { node: nodes[1], eventType: 'shop', stateVersion: 2 } }) }
    if (path.includes('/event/')) return route.fulfill({ json: { player, stateVersion: 2, shopCards: [], message: '土地庙' } })
    return route.fulfill({ json: { sessionId: 'map-image-failure', stateVersion: 1, player, map: nodes, currentNode: nodes[0], currentLayer: 1, maxLayer: 3, inBattle: false } })
  })
  return () => moves
}

for (const viewport of [{ width: 390, height: 844 }, { width: 844, height: 390 }, { width: 1366, height: 768 }]) {
  test(`map image network failures retain avatars, relic and keyboard navigation at ${viewport.width}x${viewport.height}`, async ({ page }, info) => {
    await page.setViewportSize(viewport)
    const moveCount = await mapFixture(page)
    const selectors = ['.player-avatar-full', '.map-avatar-img', '.map-relic-icon']
    const labels = ['角色头像', '当前位置玩家', '玉净瓶']
    await page.goto('/map')
    const before: Array<{ width: number; height: number }> = []
    for (let index = 0; index < selectors.length; index++) {
      const frame = page.locator(selectors[index])
      await expect(frame).toBeVisible()
      await expect.poll(() => frame.locator('img').evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)).toBe(true)
      const box = await frame.boundingBox()
      before.push({ width: box!.width, height: box!.height })
      await expect(frame.locator('img')).toHaveAttribute('decoding', 'async')
      await expect(frame.locator('img')).toHaveAttribute('loading', index === 2 ? 'lazy' : 'eager')
    }
    await page.screenshot({ path: info.outputPath('map-images-success.png'), fullPage: true })

    // A real browser resource failure, not a synthetic component error event.
    let failedRequests = 0
    await page.route('**/illustrations/**', route => { failedRequests++; return route.abort('failed') })
    await page.reload()
    for (let index = 0; index < selectors.length; index++) {
      const frame = page.locator(selectors[index])
      const fallback = frame.getByRole('img', { name: labels[index], exact: true })
      await expect(fallback).toBeVisible()
      await expect(fallback).toHaveText(index === 2 ? '💧' : '🐵')
      await expect(frame.locator('img, source')).toHaveCount(0)
      const box = await frame.boundingBox()
      expect({ width: box!.width, height: box!.height }).toEqual(before[index])
      const contentFits = await fallback.evaluate(element => {
        const parent = element.parentElement!.getBoundingClientRect(), child = element.getBoundingClientRect()
        return child.width <= parent.width && child.height <= parent.height
      })
      expect(contentFits, 'Fallback fits inside the preserved icon frame').toBe(true)
    }
    expect(failedRequests).toBeGreaterThan(0)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
    await page.screenshot({ path: info.outputPath('map-images-failed.png'), fullPage: true })

    const next = page.getByRole('button', { name: '土地庙，可前往', exact: true })
    await next.scrollIntoViewIfNeeded(); await next.focus()
    await expect(next).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(page.locator('.temple-shop')).toBeVisible()
    expect(moveCount()).toBe(1)
  })
}
