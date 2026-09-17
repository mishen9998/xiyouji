import sharp from 'sharp'
import { promises as fs } from 'node:fs'
import { createHash } from 'node:crypto'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

// Read-only audit: preserve source pixels/alpha, verify actual derived bytes, not just labels.
const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const repository = path.resolve(frontend, '..')
const mapping = JSON.parse(await fs.readFile(path.join(frontend, 'src/constants/scene-images.json'), 'utf8'))
const manifest = JSON.parse(await fs.readFile(path.join(frontend, 'src/constants/image-manifest.json'), 'utf8'))
assert.deepEqual(Object.keys(mapping).sort(), ['journey', 'camp', 'blackwind', 'firemountain', 'lionridge', 'completion', 'blackbear', 'bullking', 'roc'].sort())
const assets = []
for (const [key, source] of Object.entries(mapping)) {
  assert.ok(source.startsWith('/images/旅程/'))
  const originalPath = path.join(repository, 'assets', source.slice(1))
  const original = await fs.readFile(originalPath)
  const sourceHash = createHash('sha256').update(original).digest('hex')
  const metadata = await sharp(original).metadata()
  const entry = manifest.entries[source]
  assert.equal(sourceHash, entry.sourceHash)
  const stats = await sharp(original).stats()
  const { data, info } = await sharp(original).ensureAlpha().raw().toBuffer({ resolveWithObject: true })
  let transparent = 0
  for (let i = 3; i < data.length; i += info.channels) if (data[i] === 0) transparent++
  const alpha = { hasAlpha: !!metadata.hasAlpha, min: stats.channels[3]?.min ?? 255, max: stats.channels[3]?.max ?? 255, transparentRatio: transparent / (info.width * info.height) }
  if (['blackbear', 'bullking', 'roc'].includes(key)) {
    assert.ok(alpha.hasAlpha && alpha.min === 0 && alpha.max === 255 && alpha.transparentRatio > .2, `${key}: real transparent alpha required`)
  }
  const variants = []
  for (const variant of entry.variants) for (const format of ['avif', 'webp']) {
    const file = path.join(frontend, 'public', variant[format].url.slice(1))
    const bytes = (await fs.stat(file)).size
    const decoded = await sharp(file).metadata()
    assert.equal(bytes, variant[format].bytes)
    assert.ok(bytes <= 200 * 1024)
    assert.equal(decoded.width, variant.width)
    assert.equal(decoded.height, variant.height)
    if (alpha.hasAlpha) assert.ok(decoded.hasAlpha, `${key}: ${format} must preserve alpha`)
    variants.push({ format, width: variant.width, height: variant.height, bytes, url: variant[format].url, hasAlpha: !!decoded.hasAlpha })
  }
  assets.push({ key, source, originalPath, sourceHash, sourceBytes: original.length, width: metadata.width, height: metadata.height, alpha, variants })
}
assert.equal(new Set(assets.map(asset => asset.sourceHash)).size, 9, 'nine genuinely distinct source files')
console.log(JSON.stringify({ generatedBy: 'built-in imagegen', audit: 'pass', generatedAt: new Date().toISOString(), sourceBytes: assets.reduce((sum, asset) => sum + asset.sourceBytes, 0), derivedBytes: assets.flatMap(asset => asset.variants).reduce((sum, variant) => sum + variant.bytes, 0), assets }, null, 2))
