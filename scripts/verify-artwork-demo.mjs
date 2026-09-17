// Read-only integration check against the packaged local demo, not Vite.
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'

const base = new URL(process.argv[2] || 'http://localhost:8080')
assert.ok(['localhost', '127.0.0.1'].includes(base.hostname), 'Local demo verification only')
const manifest = JSON.parse(await readFile(new URL('../frontend-vue/src/constants/image-manifest.json', import.meta.url), 'utf8'))
const entries = Object.entries(manifest.entries).filter(([source]) => source.startsWith('/images/artwork-v2/'))
assert.equal(entries.length, 120)
const jobs = entries.flatMap(([, entry]) => entry.variants.flatMap(variant => ['webp', 'avif'].map(format => ({ ...variant[format], format }))))
assert.equal(jobs.length, 720)
let next = 0
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
await Promise.all(Array.from({ length: 6 }, async () => {
  while (next < jobs.length) {
    const job = jobs[next++]
    const response = await fetch(new URL(job.url, base), { signal: AbortSignal.timeout(20_000) })
    assert.equal(response.status, 200, job.url)
    assert.equal(response.headers.get('content-type')?.split(';')[0], `image/${job.format}`, job.url)
    const actual = Buffer.from(await response.arrayBuffer())
    assert.equal(actual.length, job.bytes, job.url)
    const expected = await readFile(new URL(`../frontend-vue/public${job.url}`, import.meta.url))
    assert.equal(hash(actual), hash(expected), job.url)
  }
}))
console.log('PASS: all 120 new illustrations / 720 packaged WebP and AVIF variants match local assets byte-for-byte')
