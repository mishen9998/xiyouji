import { test, expect, type Page } from '@playwright/test'

test('three independent players remain usable after idle and a late join', async ({ browser }) => {
  test.setTimeout(210_000)
  const contexts = await Promise.all([browser.newContext(), browser.newContext(), browser.newContext()])
  const pages = await Promise.all(contexts.map(c => c.newPage()))
  let testRoomCode = ''
  const errors: string[] = []
  pages.forEach((p, i) => p.on('pageerror', e => { errors.push(`${i}: ${e.message}`); console.log('PAGE ERROR', i, e.message) }))
  try {
    for (const p of pages) {
      await p.goto('/')
      await p.getByRole('button', { name: /游客模式/ }).click()
      await expect(p).toHaveURL(/\/menu$/)
      await p.getByRole('button', { name: /多人游戏/ }).click()
    }
    await pages[0].locator('.lobby-btn.create').click()
    const code = await pages[0].locator('.code-value').innerText()
    testRoomCode = code
    const join = async (p: Page) => {
      await p.getByPlaceholder('输入8位房间码').fill(code)
      await p.locator('.lobby-btn.join').click()
      await expect(p.locator('.waiting-room')).toBeVisible()
    }
    await join(pages[1])
    for (const p of pages.slice(0, 2)) await expect(p.locator('.player-slot.occupied')).toHaveCount(2)
    console.log('Two players joined; waiting 90 seconds before third player joins.')
    for (let i = 0; i < 3; i++) {
      await pages[0].waitForTimeout(30_000)
      console.log(`Idle elapsed: ${(i + 1) * 30}s`)
    }
    await join(pages[2])
    for (const p of pages) await expect(p.locator('.player-slot.occupied')).toHaveCount(3)
    for (let i = 0; i < 3; i++) {
      await pages[i].locator('.char-card-mini').nth(i).click()
      await expect(pages[i].locator('.player-slot.is-me .slot-char')).not.toHaveText('未选择')
      await pages[i].getByRole('button', { name: '准备', exact: true }).click()
      await expect(pages[i].locator('.player-slot.is-me .slot-ready')).toHaveText('✓ 已准备')
    }
    await pages[0].getByRole('button', { name: /开始游戏/ }).click()
    for (const p of pages) await expect(p).toHaveURL(/\/room\/.+\/map$/)
    expect(errors).toEqual([])
  } finally {
    await cleanup(pages[0], testRoomCode)
    await Promise.all(contexts.map(c => c.close()))
  }
})

async function cleanup(page: Page, code: string) {
  if (!code) return
  await page.evaluate(async code => {
    const token = localStorage.getItem('xiyouji_jwt_token')
    const headers = { Authorization: `Bearer ${token}` }
    const response = await fetch(`/api/room/${code}`, { headers })
    if (!response.ok) return
    const room = await response.json()
    await fetch(`/api/room/${code}/leave`, { method: 'POST', headers: { ...headers,
      'X-Expected-State-Version': String(room.stateVersion), 'X-Idempotency-Key': crypto.randomUUID() } })
  }, code).catch(() => {})
}

test('REST fallback, stale-ready recovery and room restoration work without WebSocket', async ({ browser }) => {
  test.setTimeout(90_000)
  const contexts = await Promise.all([browser.newContext(), browser.newContext(), browser.newContext()])
  const pages = await Promise.all(contexts.map(c => c.newPage()))
  let code = ''
  try {
    for (const p of pages) {
      // Deliberately remove all realtime updates. REST must keep the lobby usable.
      await p.routeWebSocket('**/ws*', ws => ws.close())
      await p.goto('/')
      await p.getByRole('button', { name: /游客模式/ }).click()
      await expect(p).toHaveURL(/\/menu$/)
      await p.getByRole('button', { name: /多人游戏/ }).click()
    }
    await pages[0].locator('.lobby-btn.create').click()
    code = await pages[0].locator('.code-value').innerText()
    for (const p of pages.slice(1)) {
      await p.getByPlaceholder('输入8位房间码').fill(code)
      await p.locator('.lobby-btn.join').click()
      await expect(p.locator('.waiting-room')).toBeVisible()
    }
    for (const p of pages) await expect(p.locator('.player-slot.occupied')).toHaveCount(3)
    await pages[0].reload()
    await expect(pages[0].locator('.code-value')).toHaveText(code)
    await expect(pages[0].locator('.player-slot.occupied')).toHaveCount(3)
    let rejectedOnce = false
    await pages[1].route('**/api/room/*/ready', async route => {
      if (!rejectedOnce) {
        rejectedOnce = true
        await route.fulfill({ status: 409, json: { error: 'STATE_VERSION_CONFLICT', message: 'stale state' } })
      } else await route.continue()
    })
    for (let i = 0; i < 3; i++) {
      await pages[i].locator('.char-card-mini').nth(i).click()
      await expect(pages[i].locator('.player-slot.is-me .slot-char')).not.toHaveText('未选择')
      await pages[i].getByRole('button', { name: '准备', exact: true }).click()
      await expect(pages[i].locator('.player-slot.is-me .slot-ready')).toHaveText('✓ 已准备')
    }
    expect(rejectedOnce).toBe(true)
    await expect(pages[0].getByRole('button', { name: /开始游戏/ })).toBeVisible()
    await pages[0].getByRole('button', { name: /开始游戏/ }).click()
    for (const p of pages) await expect(p).toHaveURL(/\/room\/.+\/map$/)
  } finally {
    await cleanup(pages[0], code)
    await Promise.all(contexts.map(c => c.close()))
  }
})

test('Chinese username retains character selection and ready controls after refresh', async ({ page }) => {
  const name = '联机测试甲'
  const token = 'x.' + Buffer.from(JSON.stringify({ sub: name })).toString('base64url') + '.x'
  let room = { code: 'TESTCN01', hostUserId: name, players: [{ userId: name, username: name, ready: false, host: true, characterClass: null as string | null }],
    playerCount: 1, status: 'WAITING', floor: 1, stateVersion: 1 }
  await page.addInitScript(({ token, name }) => {
    localStorage.setItem('xiyouji_jwt_token', token)
    sessionStorage.setItem(`xiyouji_room:${name}`, 'TESTCN01')
  }, { token, name })
  await page.routeWebSocket('**/ws*', ws => ws.close())
  await page.route('**/api/room/**', async route => {
    const url = route.request().url()
    if (url.endsWith('/character')) { room.players[0].characterClass = 'SUN_WUKONG'; room.stateVersion++ }
    if (url.endsWith('/ready')) { room.players[0].ready = true; room.stateVersion++ }
    await route.fulfill({ json: room })
  })
  await page.goto('/room')
  await expect(page.locator('.player-slot.is-me')).toContainText(name)
  await page.locator('.char-card-mini').first().click()
  await page.getByRole('button', { name: '准备', exact: true }).click()
  await expect(page.getByRole('button', { name: /开始游戏/ })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('button', { name: '取消准备', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /开始游戏/ })).toBeVisible()
})
