import { test, expect, type Browser, type CDPSession, type Page, type TestInfo } from '@playwright/test'
import { writeFile } from 'node:fs/promises'
import os from 'node:os'

// Fixed, disclosed simulation. Run against `vite preview`, never Vite development mode.
const MOBILE = { width: 390, height: 844 }
const DESKTOP = { width: 1366, height: 768 }
const NETWORK = { offline: false, latency: 150, downloadThroughput: 4_000_000 / 8, uploadThroughput: 4_000_000 / 8, connectionType: 'cellular4g' as const }
const FRAMES = 240
// One worker keeps the machine uncontended, but a failed budget must not skip later scenarios.
test.describe.configure({ mode: 'default' })
// Trace/video recording changes render cost; explicit screenshots are captured after each sample.
test.use({ trace: 'off', video: 'off', screenshot: 'off' })

function percentile(values: number[], fraction: number) {
  if (!values.length) return null
  return [...values].sort((a, b) => a - b)[Math.max(0, Math.ceil(values.length * fraction) - 1)]
}

async function save(info: TestInfo, name: string, report: unknown) {
  const path = info.outputPath(`${name}.json`)
  await writeFile(path, JSON.stringify(report, null, 2))
  await info.attach(name, { path, contentType: 'application/json' })
}

async function environment(browser: Browser, page: Page, viewport: { width: number; height: number }, cpuRate: number) {
  return {
    measuredAt: new Date().toISOString(), browser: browser.version(), node: process.version,
    os: { platform: os.platform(), release: os.release(), architecture: os.arch(), cpu: os.cpus()[0]?.model, logicalCpus: os.cpus().length, memoryGiB: Math.round(os.totalmem() / 1024 ** 3) },
    viewport, cpuThrottleRate: cpuRate, deviceScaleFactor: 1,
    navigator: await page.evaluate(() => ({ userAgent: navigator.userAgent, hardwareConcurrency: navigator.hardwareConcurrency, devicePixelRatio, visibility: document.visibilityState })),
    scope: 'Local headless Chromium/CDP simulation; not physical mobile hardware, production SLA or backend latency.',
  }
}

interface NetworkRow {
  requestId: string; url: string; resourceType: string; mimeType: string; status: number
  encodedDataLength?: number; fromDiskCache: boolean; fromServiceWorker: boolean; error?: string
}
function collectNetwork(cdp: CDPSession) {
  const rows = new Map<string, NetworkRow>()
  cdp.on('Network.responseReceived', event => {
    rows.set(event.requestId, { requestId: event.requestId, url: event.response.url, resourceType: event.type,
      mimeType: event.response.mimeType, status: event.response.status,
      fromDiskCache: !!event.response.fromDiskCache, fromServiceWorker: !!event.response.fromServiceWorker })
  })
  cdp.on('Network.loadingFinished', event => {
    const row = rows.get(event.requestId)
    if (row) row.encodedDataLength = event.encodedDataLength
  })
  cdp.on('Network.loadingFailed', event => {
    const row = rows.get(event.requestId)
    if (row) row.error = event.errorText
  })
  return rows
}

async function installObservers(page: Page) {
  await page.addInitScript(() => {
    const metrics = { lcp: [] as any[], longTasks: [] as any[], longAnimationFrames: [] as any[] }
    ;(window as any).__t5Metrics = metrics
    const supported = PerformanceObserver.supportedEntryTypes
    if (supported.includes('largest-contentful-paint')) {
      new PerformanceObserver(list => {
        for (const entry of list.getEntries() as any[]) metrics.lcp.push({ startTime: entry.startTime,
          renderTime: entry.renderTime, loadTime: entry.loadTime, size: entry.size, url: entry.url,
          element: entry.element ? `${entry.element.tagName.toLowerCase()}${entry.element.id ? '#' + entry.element.id : ''}.${String(entry.element.className).replace(/\s+/g, '.')}` : null })
      }).observe({ type: 'largest-contentful-paint', buffered: true })
    }
    for (const type of ['longtask', 'long-animation-frame']) if (supported.includes(type)) {
      new PerformanceObserver(list => {
        const target = type === 'longtask' ? metrics.longTasks : metrics.longAnimationFrames
        for (const entry of list.getEntries() as any[]) target.push({ startTime: entry.startTime, duration: entry.duration, blockingDuration: entry.blockingDuration })
      }).observe({ type, buffered: true })
    }
  })
}

async function settleVisibleImages(page: Page) {
  await page.evaluate(async () => {
    await document.fonts.ready
    const visible = [...document.images].filter(img => {
      const rect = img.getBoundingClientRect()
      return rect.width > 0 && rect.height > 0 && rect.top < innerHeight && rect.bottom > 0 && rect.left < innerWidth && rect.right > 0
    })
    await Promise.all(visible.map(img => img.decode().catch(() => undefined)))
    await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())))
  })
}

/** Only combat JSON is fixed, so rendering/input measurements don't include backend variance. */
async function battleFixture(page: Page) {
  const hand = ['挥棒', '格挡', '筋斗云', '火眼金睛', '七十二变'].map((name, index) => ({
    id: index + 1, name, type: index === 1 ? 'DEFENSE' : 'ATTACK', cost: 1, damage: index === 1 ? 0 : 6,
    block: index === 1 ? 5 : 0, drawCards: 0, upgraded: false, description: index === 1 ? '获得 5 点格挡。' : '造成 6 点伤害。',
  }))
  const player = { characterClass: 'SUN_WUKONG', displayName: '孙悟空', name: '孙悟空', hp: 60, maxHp: 80,
    block: 0, energy: 3, maxEnergy: 3, gold: 120, floor: 1, deck: hand, hand, relics: [], buffs: [], drawPileSize: 5, discardPileSize: 0 }
  const node = { id: 'L1-R1-C0', type: 'BATTLE', row: 1, col: 0, name: '黑风山山道', enemyId: '1', visited: true, accessible: false, connections: [] }
  const battle = { stateVersion: 1, inBattle: true, turnNumber: 1, playerTurn: true, battleOver: false, victory: false, player,
    enemy: { name: '黑熊精', hp: 130, maxHp: 160, block: 6, strength: 0, intent: 'ATTACK', intentValue: 8, intentHits: 3,
      intentTargetUserIds: ['performance-player'], intentEffects: { actionType: 'MULTI_HIT', block: 0, strength: 0, statuses: {},
        targetDamage: { 'performance-player': { damagePerHit: 8, hits: 3, blockAbsorbed: 0, hpLoss: 24 } } }, isBoss: true, buffs: [] },
    combatLog: ['黑熊精举起长枪，预告连续三次攻击。'], storyEvent: { scenes: [] } }
  await page.addInitScript(() => {
    localStorage.setItem('xiyouji_jwt_token', 'performance-fixture-token')
    localStorage.setItem('xiyouji_auth_profile', JSON.stringify({ account: 'performance-player', username: 'performance-player', role: 'PLAYER' }))
    localStorage.setItem('xiyouji_session_id', 'performance-session')
  })
  await page.route('**/api/game/**', async route => {
    const isBattle = new URL(route.request().url()).pathname.includes('/battle/')
    await route.fulfill({ json: isBattle ? battle : { sessionId: 'performance-session', stateVersion: 1, player,
      map: [node], currentNode: node, currentLayer: 1, maxLayer: 3, inBattle: true, storyEvent: { scenes: [] } } })
  })
}

async function collectSteadyFrames(page: Page) {
  return page.evaluate(async count => {
    if (document.hidden) throw new Error('Frame measurement requires a visible document')
    const intervals: number[] = []
    let previous: number | undefined
    const start = performance.now()
    await new Promise<void>(resolve => {
      function frame(now: number) {
        if (previous !== undefined) intervals.push(now - previous)
        previous = now
        if (intervals.length >= count) resolve()
        else requestAnimationFrame(frame)
      }
      requestAnimationFrame(frame)
    })
    return { start, end: performance.now(), intervals, visibility: document.visibilityState,
      longAnimationFrames: ((window as any).__t5Metrics?.longAnimationFrames || []).filter((entry: any) => entry.startTime >= start),
      longTasks: ((window as any).__t5Metrics?.longTasks || []).filter((entry: any) => entry.startTime >= start) }
  }, FRAMES)
}

async function feedbackSamples(page: Page) {
  const samples: any[] = []
  for (let index = 0; index < 6; index++) {
    const cardIndex = index % 2
    const card = page.locator('.hand-zone .game-card').nth(cardIndex)
    await card.scrollIntoViewIfNeeded()
    await page.evaluate(selectedIndex => {
      const card = document.querySelectorAll<HTMLElement>('.hand-zone .game-card')[selectedIndex]
      ;(window as any).__t5Feedback = new Promise(resolve => {
        let start = 0
        const timeout = window.setTimeout(() => { observer.disconnect(); card.removeEventListener('pointerdown', begin, true); resolve({ error: 'No visible selection feedback in 2 seconds' }) }, 2000)
        const begin = () => { start = performance.now() }
        card.addEventListener('pointerdown', begin, { capture: true, once: true })
        const observer = new MutationObserver(() => {
          if (!start || card.getAttribute('aria-pressed') !== 'true') return
          observer.disconnect()
          const domCommitted = performance.now()
          requestAnimationFrame(() => requestAnimationFrame(() => {
            clearTimeout(timeout)
            resolve({ pointerDown: start, domCommitted, paintOpportunity: performance.now(),
              latencyMs: performance.now() - start, domLatencyMs: domCommitted - start,
              selected: card.classList.contains('selected'), outlineStyle: getComputedStyle(card).outlineStyle,
              previewText: document.querySelector('.selection-preview')?.textContent?.trim() })
          }))
        })
        observer.observe(card, { attributes: true, attributeFilter: ['aria-pressed', 'class'] })
      })
    }, cardIndex)
    await card.click()
    samples.push(await page.evaluate(() => (window as any).__t5Feedback))
    await expect(card).toHaveAttribute('aria-pressed', 'true')
  }
  return samples
}

for (const mode of [
  { name: 'desktop', viewport: DESKTOP, cpuRate: 1, budget: 20, mobile: false },
  { name: 'mobile-cpu4', viewport: MOBILE, cpuRate: 4, budget: 33, mobile: true },
]) {
  test(`performance: ${mode.name} steady battle and visual input`, async ({ browser, browserName, baseURL }, info) => {
    test.skip(browserName !== 'chromium', 'This benchmark requires Chromium CDP')
    test.setTimeout(90_000)
    const context = await browser.newContext({ viewport: mode.viewport, deviceScaleFactor: 1, isMobile: mode.mobile,
      hasTouch: mode.mobile, serviceWorkers: 'block', baseURL })
    try {
      const page = await context.newPage()
      const cdp = await context.newCDPSession(page)
      await cdp.send('Emulation.setCPUThrottlingRate', { rate: mode.cpuRate })
      await installObservers(page)
      await battleFixture(page)
      await page.goto('/battle', { waitUntil: 'networkidle' })
      await expect(page.locator('.enemy-intent')).toBeVisible()
      await expect(page.locator('.hand-zone .game-card')).toHaveCount(5)
      await settleVisibleImages(page)
      const loading = await page.evaluate(() => ({ readyAt: performance.now(), navigation: performance.getEntriesByType('navigation')[0]?.toJSON(), lcp: (window as any).__t5Metrics.lcp }))
      // Warm-up is explicitly excluded from the steady-state sample.
      await page.waitForTimeout(1000)
      const frames = await collectSteadyFrames(page)
      const feedback = await feedbackSamples(page)
      const p95 = percentile(frames.intervals, 0.95)
      const report = { environment: await environment(browser, page, mode.viewport, mode.cpuRate),
        fixture: 'Only /api/game JSON is fixed for reproducible rendering. Real built UI, image HTTP requests and browser paint remain enabled. No backend latency claim.',
        renderingMode: 'Default lightweight illustration; 3D is opt-in and is not silently enabled by this benchmark.',
        network: 'Unthrottled local preview for this steady-state test; cold-start network is tested separately.',
        definition: { frame: 'Consecutive requestAnimationFrame timestamp intervals (scheduling/paint cadence proxy, not GPU instrumented render cost).',
          feedback: 'Real Playwright click: DOM pointerdown to selected aria state / class and two requestAnimationFrame callbacks, allowing one intervening paint. Not physical display or backend latency.' },
        budgets: { frameP95Ms: mode.budget, feedbackMaxMs: 100 }, loadingExcluded: loading,
        steady: { ...frames, p50Ms: percentile(frames.intervals, 0.5), p95Ms: p95, maxMs: Math.max(...frames.intervals) }, feedback }
      await save(info, `steady-${mode.name}`, report)
      await page.screenshot({ path: info.outputPath(`steady-${mode.name}.png`), fullPage: true })
      expect(p95).not.toBeNull()
      expect(p95!, 'Steady frame scheduling p95').toBeLessThanOrEqual(mode.budget)
      expect(feedback).toHaveLength(6)
      for (const sample of feedback) {
        expect(sample.error).toBeUndefined()
        expect(sample.selected).toBe(true)
        expect(sample.outlineStyle).not.toBe('none')
        expect(sample.latencyMs, 'Pointerdown → visible selection paint opportunity').toBeLessThanOrEqual(100)
      }
    } finally { await context.close() }
  })
}

test('performance: five cold mobile identity loads at 4Mbps / 150ms / CPU4x', async ({ browser, browserName, baseURL }, info) => {
  test.skip(browserName !== 'chromium', 'This benchmark requires Chromium CDP')
  test.setTimeout(180_000)
  const runs: any[] = []
  for (let run = 1; run <= 5; run++) {
    // Fresh context, blocked SW and disabled/cleared HTTP cache on every run. No API or asset routes.
    const context = await browser.newContext({ viewport: MOBILE, deviceScaleFactor: 1, isMobile: true,
      hasTouch: true, serviceWorkers: 'block', baseURL })
    const page = await context.newPage()
    try {
      const cdp = await context.newCDPSession(page)
      await cdp.send('Network.enable')
      await cdp.send('Network.setCacheDisabled', { cacheDisabled: true })
      await cdp.send('Network.clearBrowserCache')
      await cdp.send('Network.emulateNetworkConditions', NETWORK)
      await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 })
      const network = collectNetwork(cdp)
      await installObservers(page)
      await page.goto('/', { waitUntil: 'networkidle', timeout: 30_000 })
      await expect(page.getByLabel('登录账号')).toBeVisible()
      await settleVisibleImages(page)
      await page.waitForTimeout(600)
      const measured = await page.evaluate(() => {
        const criticalImages = new Set<string>()
        for (const element of document.querySelectorAll<HTMLElement>('*')) {
          const rect = element.getBoundingClientRect()
          const style = getComputedStyle(element)
          if (rect.width <= 0 || rect.height <= 0 || rect.top >= innerHeight || rect.bottom <= 0 || rect.left >= innerWidth || rect.right <= 0 || style.display === 'none' || style.visibility === 'hidden') continue
          if (element instanceof HTMLImageElement && element.currentSrc) criticalImages.add(element.currentSrc)
          for (const match of style.backgroundImage.matchAll(/url\(["']?(.*?)["']?\)/g)) criticalImages.add(new URL(match[1], location.href).href)
        }
        const lcpEntries = (window as any).__t5Metrics.lcp
        return { measuredAt: performance.now(), lcpMs: lcpEntries.at(-1)?.startTime ?? null, lcpEntries,
          criticalImageUrls: [...criticalImages], resources: performance.getEntriesByType('resource').map(entry => entry.toJSON()),
          navigation: performance.getEntriesByType('navigation')[0]?.toJSON(),
          visibleImageStatus: [...document.images].filter(img => criticalImages.has(img.currentSrc)).map(img => ({ url: img.currentSrc, complete: img.complete, naturalWidth: img.naturalWidth })) }
      })
      const responses = [...network.values()]
      const critical = responses.filter(row => measured.criticalImageUrls.includes(row.url))
      const imageTransfers = responses.filter(row => row.resourceType === 'Image' || row.mimeType.startsWith('image/'))
      const result = { run, environment: await environment(browser, page, MOBILE, 4), ...measured,
        criticalImageResponses: critical, allImageResponses: imageTransfers, allNetworkResponses: responses,
        criticalImageTransferBytes: critical.reduce((sum, row) => sum + (row.encodedDataLength || 0), 0),
        largestCriticalImageTransferBytes: Math.max(0, ...critical.map(row => row.encodedDataLength || 0)),
        totalImageTransferBytes: imageTransfers.reduce((sum, row) => sum + (row.encodedDataLength || 0), 0) }
      runs.push(result)
      await save(info, `cold-run-${run}`, result)
      if (run === 1) await page.screenshot({ path: info.outputPath('cold-mobile-identity.png'), fullPage: true })
    } catch (error) {
      const failure = { run, error: String(error) }
      runs.push(failure)
      await save(info, `cold-run-${run}-error`, failure)
    } finally { await context.close() }
  }
  const successfulLcp = runs.filter(run => typeof run.lcpMs === 'number').map(run => run.lcpMs)
  const medianLcpMs = percentile(successfulLcp, 0.5)
  await save(info, 'cold-summary', { simulation: 'Real built identity page, no mocked routes, five fresh contexts; local CDP simulation only, not phone hardware or production SLA.',
    network: { ...NETWORK, description: '4,000,000 bits/sec each direction (500,000 bytes/sec), 150ms added latency' },
    cpuThrottleRate: 4, viewport: MOBILE, deviceScaleFactor: 1, cacheDisabled: true, serviceWorkers: 'block',
    loadingOnly: true, medianLcpMs, budgets: { medianLcpMs: 3000, criticalImageSumBytes: 600 * 1024, criticalImageSingleBytes: 200 * 1024 },
    transferDefinition: 'CDP Network.loadingFinished.encodedDataLength: real received encoded bytes including protocol/header overhead where Chromium reports it; also includes raw resource timing and response MIME/cache metadata.', runs })
  expect(runs.filter(run => run.error), 'Every cold run must complete').toHaveLength(0)
  expect(successfulLcp, 'All five runs must produce an LCP entry').toHaveLength(5)
  expect(medianLcpMs!, 'Five-run cold LCP median').toBeLessThanOrEqual(3000)
  for (const run of runs) {
    expect(run.criticalImageResponses.length, 'Critical imagery must be measured, not vacuously empty').toBeGreaterThan(0)
    expect(run.criticalImageTransferBytes).toBeLessThanOrEqual(600 * 1024)
    expect(run.largestCriticalImageTransferBytes).toBeLessThanOrEqual(200 * 1024)
    for (const url of run.criticalImageUrls) expect(run.criticalImageResponses.some((response: NetworkRow) => response.url === url), `Missing actual transfer for critical image ${url}`).toBe(true)
    for (const image of run.visibleImageStatus) { expect(image.complete).toBe(true); expect(image.naturalWidth).toBeGreaterThan(0) }
    for (const response of run.criticalImageResponses) {
      expect(response.status).toBe(200); expect(response.encodedDataLength).toBeGreaterThan(0)
      expect(response.fromDiskCache).toBe(false); expect(response.fromServiceWorker).toBe(false)
    }
  }
})
