import { test, expect, type Page } from '@playwright/test'

async function login(page: Page) {
  await page.goto('/')
  await page.getByRole('button', { name: /游客模式/ }).click()
  await expect(page).toHaveURL(/\/menu$/)
  await page.getByRole('button', { name: /多人游戏/ }).click()
}
async function api(page: Page, path: string, body?: unknown) {
  return page.evaluate(async ({ path, body }) => {
    const response = await fetch(path, { method: body === undefined ? 'GET' : 'POST', headers: {
      Authorization: `Bearer ${localStorage.getItem('xiyouji_jwt_token')}`,
      'Content-Type': 'application/json', 'X-Idempotency-Key': crypto.randomUUID(),
    }, body: body === undefined ? undefined : JSON.stringify(body) })
    return { status: response.status, body: await response.json() }
  }, { path, body })
}

test('real socket disconnection resubscribes once and reconciles missed updates via REST', async ({ browser }, info) => {
  test.setTimeout(60000)
  const hostContext = await browser.newContext(); const guestContext = await browser.newContext()
  const host = await hostContext.newPage(); const guest = await guestContext.newPage()
  const subscriptions: number[] = []; let closeLink: (() => void) | undefined; let block = false
  let reads = 0
  guest.on('request', request => { if (/\/api\/room\/[A-Z0-9]{8}$/.test(request.url()) && request.method() === 'GET') reads++ })
  await guest.routeWebSocket('**/ws?*', socket => {
    if (block) { socket.close(); return }
    const server = socket.connectToServer(); const index = subscriptions.push(0) - 1
    socket.onMessage(message => { if (message.toString().startsWith('SUBSCRIBE')) subscriptions[index]++; server.send(message) })
    closeLink = () => { server.close(); socket.close() }
  })
  try {
    await login(host); await login(guest)
    await host.locator('.lobby-btn.create').click()
    const code = await host.locator('.code-value').innerText()
    await guest.getByPlaceholder('输入8位房间码').fill(code); await guest.locator('.lobby-btn.join').click()
    await expect(guest.locator('.connection-status')).toContainText('实时连接正常')
    await expect.poll(() => subscriptions[0]).toBe(2)
    block = true; closeLink!()
    await expect(guest.locator('.connection-status')).toContainText('恢复中')
    await host.locator('.char-card-mini').first().click()
    await expect(host.locator('.player-slot.is-me .slot-char')).toHaveText('孙悟空')
    await host.getByRole('button', { name: '准备', exact: true }).click()
    await expect(host.locator('.player-slot.is-me .slot-ready')).toHaveText('✓ 已准备')
    const beforeReconnectReads = reads
    block = false
    await expect(guest.locator('.connection-status')).toContainText('实时连接正常', { timeout: 15000 })
    await expect(guest.locator('.player-slot').first()).toContainText('✓ 已准备')
    await expect.poll(() => reads).toBeGreaterThan(beforeReconnectReads)
    expect(subscriptions).toEqual([2, 2])
    await guest.screenshot({ path: info.outputPath('reconnected.png') })
    await info.attach('subscriptions-and-rest', { body: JSON.stringify({ subscriptions, reads, room: code }), contentType: 'application/json' })
  } finally { await hostContext.close(); await guestContext.close() }
})

test('outsider receives REST 403 and actual STOMP ERROR; URL change drops former room', async ({ browser }, info) => {
  const hostContext = await browser.newContext(); const outsiderContext = await browser.newContext()
  const host = await hostContext.newPage(); const outsider = await outsiderContext.newPage()
  try {
    await login(host); await login(outsider)
    await host.locator('.lobby-btn.create').click()
    const code = await host.locator('.code-value').innerText()
    expect((await api(outsider, `/api/room/${code}`)).status).toBe(403)
    expect((await api(outsider, `/api/multiplayer/battle/${code}/state`)).status).toBe(403)
    const errorFrame = await outsider.evaluate(code => new Promise<string>((resolve, reject) => {
      const socket = new WebSocket(`${location.origin.replace('http', 'ws')}/ws?token=${encodeURIComponent(localStorage.getItem('xiyouji_jwt_token')!)}`)
      const timeout = setTimeout(() => { socket.close(); reject(new Error('No STOMP ERROR received')) }, 8000)
      socket.onopen = () => socket.send('CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\0')
      socket.onmessage = event => {
        const frame = String(event.data)
        if (frame.startsWith('CONNECTED')) socket.send(`SUBSCRIBE\nid:private\ndestination:/topic/room/${code}\n\n\0`)
        if (frame.startsWith('ERROR')) { clearTimeout(timeout); socket.close(); resolve(frame) }
      }
    }), code)
    expect(errorFrame).toContain('ERROR')
    const another = (await api(host, '/api/room/create', {})).body.code
    await host.goto(`/room/${another}/map`)
    await expect(host).toHaveURL(new RegExp(`/room/${another}/map$`))
    await host.goto(`/room/${code}/map`)
    await expect(host).toHaveURL(new RegExp(`/room/${code}/map$`))
    await host.goto('/room/MISSING1/map')
    await expect(host).toHaveURL(/\/room$/)
    await expect(host.locator('.lobby')).toBeVisible()
    await expect(host.locator('.code-value')).toHaveCount(0)
    await host.screenshot({ path: info.outputPath('ghost-cleared.png') })
    await info.attach('subscription-error', { body: errorFrame, contentType: 'text/plain' })
  } finally { await hostContext.close(); await outsiderContext.close() }
})

test('lost command response stays unknown after version advances and never auto repeats', async ({ page }, info) => {
  await login(page); await page.locator('.lobby-btn.create').click()
  await page.locator('.char-card-mini').first().click()
  await expect(page.locator('.player-slot.is-me .slot-char')).toHaveText('孙悟空')
  let writes = 0
  await page.route('**/api/room/*/ready', async route => {
    writes++
    await route.fetch() // The real server commits; only its HTTP response is lost.
    await route.abort('failed')
  })
  await page.route('**/api/commands/receipt?*', route => route.fulfill({ status: 409, json: { code: 'RESULT_UNKNOWN' } }))
  await page.getByRole('button', { name: '准备', exact: true }).click()
  await expect(page.locator('.command-recovery')).toBeVisible()
  await page.getByRole('button', { name: '同步状态并查询原命令回执' }).click()
  await expect(page.locator('.player-slot.is-me .slot-ready')).toHaveText('✓ 已准备')
  await expect(page.locator('.command-recovery')).toContainText('原命令仍无完成回执')
  expect(writes).toBe(1)
  await page.screenshot({ path: info.outputPath('result-still-unknown.png') })
  await page.unroute('**/api/commands/receipt?*')
  await page.getByRole('button', { name: '同步状态并查询原命令回执' }).click()
  await expect(page.locator('.command-recovery')).toHaveCount(0)
  expect(writes).toBe(1)
})

test('lost room creation response restores the atomically associated original room', async ({ page }) => {
  await login(page)
  let writes = 0; let originalCode = ''
  await page.route('**/api/room/create', async route => {
    writes++
    const response = await route.fetch(); originalCode = (await response.json()).code
    await route.abort('failed')
  })
  await page.locator('.lobby-btn.create').click()
  await expect(page.locator('.command-recovery')).toBeVisible()
  await page.getByRole('button', { name: '同步状态并查询原命令回执' }).click()
  await expect(page.locator('.code-value')).toHaveText(originalCode)
  expect(writes).toBe(1)
})
