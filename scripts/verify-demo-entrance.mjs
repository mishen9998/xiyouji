// Exercise the packaged Spring Boot entrance, not a Vite-only asset server.
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'

const base = new URL(process.argv[2] || 'http://localhost:8080')
assert.ok(['localhost', '127.0.0.1'].includes(base.hostname), 'Local demo verification only')
const request = (path, options = {}) => fetch(new URL(path, base), { ...options, signal: AbortSignal.timeout(20_000) })
const manifest = JSON.parse(await readFile(new URL('../frontend-vue/src/constants/image-manifest.json', import.meta.url), 'utf8'))
const images = [
  '/images/宝物/场景/login_screen.jpg', '/images/孙悟空/建模/full_sunwukong.jpg',
  '/images/猪八戒/建模/full_zhubajie.jpg', '/images/沙僧/建模/full_shaseng.jpg',
  '/images/白龙马/头像/bailongma.png', '/images/唐三藏/头像/avatar_tangsanzang.jpg',
]
for (const source of images) {
  for (const format of ['webp', 'avif']) {
    const variant = manifest.entries[source].variants[0][format]
    const response = await request(variant.url)
    assert.equal(response.status, 200, variant.url)
    assert.match(response.headers.get('content-type'), /^image\//)
    assert.equal((await response.arrayBuffer()).byteLength, variant.bytes)
  }
}
for (const path of ['/', '/menu', '/char-select', '/complete']) {
  const response = await request(path)
  assert.equal(response.status, 200, path)
  assert.match(response.headers.get('content-type'), /text\/html/)
}
for (const token of ['', 'invalid-demo-verification']) {
  const headers = { 'Content-Type': 'application/json', 'X-Idempotency-Key': randomUUID() }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await request('/api/game/new', { method: 'POST', headers, body: JSON.stringify({ characterClass: 'SUN_WUKONG' }) })
  assert.equal(response.status, 401)
  assert.equal((await response.json()).error, 'UNAUTHORIZED')
}
const guestResponse = await request('/api/auth/guest', { method: 'POST', headers: { 'X-Idempotency-Key': randomUUID() } })
assert.equal(guestResponse.status, 200)
const guest = await guestResponse.json()
const headers = { Authorization: `Bearer ${guest.token}`, 'Content-Type': 'application/json' }
assert.equal((await request('/api/auth/session', { headers })).status, 204)
let created
try {
  const response = await request('/api/game/new', {
    method: 'POST', headers: { ...headers, 'X-Idempotency-Key': randomUUID() },
    body: JSON.stringify({ characterClass: 'ZHU_BAJIE' }),
  })
  assert.equal(response.status, 200)
  created = await response.json()
  assert.ok(created.success && created.sessionId)
  assert.equal(created.player.characterClass, 'ZHU_BAJIE')
  assert.ok(created.player.deck.length > 0)
  assert.ok(created.player.deck.every(card => typeof card.description === 'string' && card.description.trim()), 'Deck forwards real effect descriptions')
  const state = await request(`/api/game/state/${created.sessionId}`, { headers })
  assert.equal(state.status, 200)
  assert.equal((await state.json()).sessionId, created.sessionId)
} finally {
  // Only the disposable journey created above is removed; no user data is touched.
  if (created?.sessionId) {
    const state = await (await request(`/api/game/state/${created.sessionId}`, { headers })).json()
    const response = await request(`/api/game/sessions/${created.sessionId}`, {
      method: 'DELETE', headers: { ...headers, 'X-Idempotency-Key': randomUUID(), 'X-Expected-State-Version': String(state.stateVersion) },
    })
    assert.ok(response.ok, 'Disposable test session cleanup')
  }
}
console.log('PASS: 12 packaged illustration responses, 4 SPA routes, invalid JWT 401, valid guest reuse probe, create/read/clean up a game')
