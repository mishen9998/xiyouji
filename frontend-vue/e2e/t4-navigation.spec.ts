import { test, expect } from '@playwright/test'

test.skip(process.env.T4_BATTLE_GENERATION_E2E !== '1', 'Requires the isolated T4 two-instance stack')
const apps = ['http://127.0.0.1:18088', 'http://127.0.0.1:18089']
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
function gate() {
  let resolve!: () => void
  const promise = new Promise<void>(done => { resolve = done })
  return { promise, resolve }
}

for (const delayedStatus of [200, 403]) {
  test(`same-code leave/rejoin survives the previous navigation real GET ${delayedStatus}`, async ({ browser }, info) => {
    test.setTimeout(60000)
    const host = await api(0, '/api/auth/guest', undefined, {})
    const guest = await api(1, '/api/auth/guest', undefined, {})
    const created = await api(0, '/api/room/create', host.token, {})
    const code = created.code
    await api(1, '/api/room/join', guest.token, { code })
    await api(0, `/api/room/${code}/character`, host.token, { characterClass: 'SUN_WUKONG' }, (await api(0, `/api/room/${code}`, host.token)).stateVersion)
    const context = await browser.newContext()
    await context.addInitScript(({ identity, code }) => {
      localStorage.setItem('xiyouji_jwt_token', identity.token)
      localStorage.setItem('xiyouji_auth_profile', JSON.stringify(identity))
      sessionStorage.setItem(`xiyouji_room:${identity.username}`, code)
    }, { identity: guest, code })
    const page = await context.newPage()
    const leaving = gate(); const allowLeave = gate(); const captured = gate(); const releaseRead = gate(); const delivered = gate()
    let armed = false; let held = false; let reads = 0; let oldVersion = 0; let joinWrites = 0
    const subscriptions: number[] = []
    await page.routeWebSocket('**/ws?*', socket => {
      const server = socket.connectToServer(); const index = subscriptions.push(0) - 1
      socket.onMessage(message => { if (message.toString().startsWith('SUBSCRIBE')) subscriptions[index]++; server.send(message) })
    })
    page.on('request', request => { if (request.method() === 'POST' && request.url().endsWith('/api/room/join')) joinWrites++ })
    await page.route(`**/api/room/${code}`, async route => {
      reads++
      if (!armed || held) return route.continue()
      held = true
      const response = await route.fetch() // A real pre-leave 200 or post-leave 403, not a fabricated API response.
      expect(response.status()).toBe(delayedStatus)
      oldVersion = (await response.json()).stateVersion ?? 0
      captured.resolve(); await releaseRead.promise
      await route.fulfill({ response }); delivered.resolve()
    })
    await page.route(`**/api/room/${code}/leave`, async route => {
      // The UI's preflight GET has already completed before this POST is dispatched.
      if (delayedStatus === 200) { leaving.resolve(); await allowLeave.promise }
      const response = await route.fetch()
      expect(response.status()).toBe(200)
      if (delayedStatus === 403) { leaving.resolve(); await allowLeave.promise }
      await route.fulfill({ response })
    })
    try {
      await page.goto(`${apps[1]}/room`)
      await expect(page.locator('.code-value')).toHaveText(code)
      await expect(page.locator('.player-slot').first()).toContainText('孙悟空')
      await expect.poll(() => subscriptions[0]).toBe(2)
      await page.getByRole('button', { name: '退出房间', exact: true }).click()
      await leaving.promise
      armed = true
      await page.evaluate(() => window.dispatchEvent(new Event('focus')))
      await captured.promise
      allowLeave.resolve()
      await expect(page.locator('.lobby')).toBeVisible() // Actual leaveRoom reset, never a test-side store clear.
      const afterLeave = await api(0, `/api/room/${code}`, host.token)
      expect(afterLeave.players.map((p: any) => p.userId)).not.toContain(guest.username)
      await api(0, `/api/room/${code}/character`, host.token, { characterClass: 'SHA_SENG' }, afterLeave.stateVersion)
      let readsBeforeReopen = reads
      if (delayedStatus === 200) {
        // Simulate the same identity rejoining from its other device, then reopening this tab's room URL.
        await api(0, '/api/room/join', guest.token, { code })
        await page.evaluate(code => {
          const router = (document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router
          void router.push(`/room/${code}/map`)
        }, code)
        await expect.poll(() => reads).toBeGreaterThan(readsBeforeReopen)
        await expect(page).toHaveURL(new RegExp(`/room/${code}/map$`))
        await expect(page.locator('.player-chip .player-emoji').first()).toHaveText('🟤')
      } else {
        await page.getByPlaceholder('输入8位房间码').fill(code)
        await page.locator('.lobby-btn.join').click()
        await expect(page.locator('.code-value')).toHaveText(code)
        await expect(page.locator('.player-slot').first()).toContainText('沙僧')
      }
      await expect.poll(() => subscriptions[1]).toBe(2)
      const responseSeen = page.waitForResponse(response => response.url().endsWith(`/api/room/${code}`) && response.status() === delayedStatus)
      releaseRead.resolve(); await delivered.promise; await responseSeen
      // Flush the browser's fetch/microtask chain, then assert actual state, membership and connection survived.
      await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
      if (delayedStatus === 200) {
        await expect(page.locator('.player-chip .player-emoji').first()).toHaveText('🟤')
        await expect(page).toHaveURL(new RegExp(`/room/${code}/map$`))
      } else {
        await expect(page.locator('.code-value')).toHaveText(code)
        await expect(page.locator('.player-slot').first()).toContainText('沙僧')
        await expect(page.locator('.connection-status')).toContainText('实时连接正常')
        await expect(page.locator('.lobby')).toHaveCount(0)
        expect(joinWrites).toBe(1)
      }
      const authoritative = await api(1, `/api/room/${code}`, guest.token)
      expect(authoritative.players.map((p: any) => p.userId)).toContain(guest.username)
      expect(authoritative.players[0].characterClass).toBe('SHA_SENG')
      expect(authoritative.stateVersion).toBeGreaterThan(oldVersion)
      expect(await page.evaluate(user => sessionStorage.getItem(`xiyouji_room:${user}`), guest.username)).toBe(code)
      expect(subscriptions).toEqual([2, 2])
      await page.screenshot({ path: info.outputPath(`late-${delayedStatus}-new-room-preserved.png`) })
      console.log(JSON.stringify({ code, delayedStatus, oldVersion, newVersion: authoritative.stateVersion,
        character: authoritative.players[0].characterClass, reads, readsBeforeReopen, subscriptions, joinWrites }))
    } finally { allowLeave.resolve(); releaseRead.resolve(); await context.close() }
  })
}
