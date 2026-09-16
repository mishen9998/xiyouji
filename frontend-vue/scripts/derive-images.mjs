import sharp from 'sharp'
import { createHash } from 'node:crypto'
import { promises as fs } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// Reproducible offline runtime assets. Originals are read only and never rewritten.
// Run: npm --prefix frontend-vue/scripts ci && npm --prefix frontend-vue/scripts run images
const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const root = path.resolve(frontend, '..', 'assets', 'images')
const output = path.join(frontend, 'public', 'illustrations')
const widths = [320, 640, 960]
const maxBytes = 200 * 1024
sharp.concurrency(2)
await fs.mkdir(output, { recursive: true })
async function discover(directory) {
  const entries = await fs.readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map(entry => entry.isDirectory()
    ? discover(path.join(directory, entry.name))
    : /\.(png|jpe?g|webp|avif)$/i.test(entry.name) ? [path.join(directory, entry.name)] : []))
  return nested.flat().sort()
}
const entries = {}
for (const source of await discover(root)) {
  const relative = path.relative(root, source).split(path.sep).join('/')
  const original = await fs.readFile(source)
  const sourceHash = createHash('sha256').update(original).digest('hex')
  const key = createHash('sha256').update(relative).digest('hex').slice(0, 12)
  const metadata = await sharp(original).metadata()
  const variants = []
  for (const requestedWidth of widths) {
    const width = Math.min(requestedWidth, metadata.autoOrient?.width || metadata.width || requestedWidth)
    if (variants.some(variant => variant.width === width)) continue
    const files = {}
    let actualHeight = 0
    for (const format of ['webp', 'avif']) {
      let encoded
      for (const quality of format === 'webp' ? [76, 66, 56, 44] : [48, 40, 32, 25]) {
        encoded = await sharp(original).rotate().resize({ width, withoutEnlargement: true })
          .toFormat(format, { quality, effort: format === 'avif' ? 3 : 4 }).toBuffer({ resolveWithObject: true })
        if (encoded.data.length <= maxBytes) break
      }
      if (encoded.data.length > maxBytes) throw new Error(`${relative} ${width} ${format} exceeds 200 KiB`)
      actualHeight = encoded.info.height
      const filename = `${key}-${width}.${format}`
      await fs.writeFile(path.join(output, filename), encoded.data)
      files[format] = { url: `/illustrations/${filename}`, bytes: encoded.data.length }
    }
    variants.push({ width, height: actualHeight, ...files })
  }
  // A source hash is deliberately retained to prove originals were unchanged and make audits easy.
  entries[`/images/${relative}`] = { sourceHash, sourceBytes: original.length, width: metadata.width, height: metadata.height, variants }
  if (Object.keys(entries).length % 25 === 0) console.log(`Derived ${Object.keys(entries).length} source images`)
}
const manifest = { version: 1, widths, entries }
await fs.writeFile(path.join(frontend, 'src', 'constants', 'image-manifest.json'), JSON.stringify(manifest))
await fs.writeFile(path.join(output, 'manifest.json'), JSON.stringify(manifest, null, 2))
const all = Object.values(entries)
console.log(JSON.stringify({ sources: all.length, sourceBytes: all.reduce((sum, entry) => sum + entry.sourceBytes, 0),
  mobileWebpBytes: all.reduce((sum, entry) => sum + entry.variants[0].webp.bytes, 0),
  maxVariantBytes: Math.max(...all.flatMap(entry => entry.variants.flatMap(v => [v.webp.bytes, v.avif.bytes]))) }))
