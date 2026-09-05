import { expect, test } from '@playwright/test'

for (const width of [1280, 390]) {
  test(`reward confirmation and reload at ${width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 844 })
    const cards = ['挥棒', '格挡', '重击', '金钟罩', '回春术'].map((name, i) => ({
      id: i + 1, name, type: 'ATTACK', cost: 1, damage: 6, block: 0, drawCards: 0, upgraded: false, description: '奖励测试卡牌',
    }))
    const player = { characterClass: 'SUN_WUKONG', displayName: '孙悟空', hp: 60, maxHp: 80, block: 0, energy: 3,
      maxEnergy: 3, gold: 50, deckSize: 0, floor: 1, deck: [], relics: [], hand: [], buffs: [] }
    const node = { id: 'test-battle', type: 'BATTLE', row: 1, col: 1, visited: true, accessible: false, connections: [] }
    let resolved = false
    let claimed: number[] = []
    await page.addInitScript(() => {
      localStorage.setItem('xiyouji_jwt_token', 'test-token')
      localStorage.setItem('xiyouji_session_id', 'reward-test')
    })
    await page.route('**/api/game/**', async route => {
      const path = new URL(route.request().url()).pathname
      let body: unknown
      if (path.includes('/reward/choose/')) {
        claimed.push(route.request().postDataJSON().cardIndex)
        resolved = true
        body = { success: true, stateVersion: 2, player }
      } else if (path.includes('/battle/state/')) {
        body = { stateVersion: 1, inBattle: true, playerTurn: false, battleOver: true, victory: true, player,
          enemy: { name: '测试妖怪', hp: 0, maxHp: 30, block: 0, intent: 'ATTACK', isBoss: false, buffs: [] },
          rewards: { goldReward: 50, cardRewards: resolved ? [] : cards, resolved } }
      } else {
        body = { sessionId: 'reward-test', stateVersion: 1, player, map: [], currentNode: node,
          currentLayer: 1, maxLayer: 3, inBattle: !resolved }
      }
      await route.fulfill({ json: body })
    })
    await page.goto('/battle')
    const modal = page.locator('.result-modal')
    await expect(modal.locator('.card-mini')).toHaveCount(5)
    await modal.locator('.card-mini').nth(0).click()
    await modal.locator('.card-mini').nth(1).click()
    expect(claimed).toEqual([])
    await expect(modal.locator('.card-mini').nth(1)).toHaveClass(/selected/)
    const fits = await modal.evaluate(el => el.scrollWidth <= el.clientWidth + 1)
    expect(fits).toBe(true)
    await modal.screenshot({ path: testInfo.outputPath(`rewards-${width}.png`) })
    await page.reload()
    await expect(modal.locator('.card-mini')).toHaveCount(5)
    await expect(modal.locator('.continue-btn')).toBeDisabled()
    await modal.locator('.card-mini').nth(4).click()
    await modal.locator('.continue-btn').click()
    await expect(page).toHaveURL(/\/map$/)
    expect(claimed).toEqual([4])
  })
}
