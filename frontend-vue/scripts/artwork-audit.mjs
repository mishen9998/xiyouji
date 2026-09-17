import sharp from 'sharp'
import { promises as fs } from 'node:fs'
import { createHash } from 'node:crypto'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// Offline QA only: assemble inspection sheets; never alter illustration masters.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const output = path.join(root, 'output/artwork-v2-qa')
const plan = JSON.parse(await fs.readFile(path.join(root, 'docs/artwork-v2-prompts.json'), 'utf8'))
const manifest = JSON.parse(await fs.readFile(path.join(root, 'frontend-vue/src/constants/image-manifest.json'), 'utf8'))
await fs.mkdir(output, { recursive: true })
const rows = []
const hashes = new Set()
for (const job of plan.jobs) {
  const relative = `artwork-v2/${job.kind}/${job.id}-v2.png`
  const master = await fs.readFile(path.join(root, 'assets/images', relative))
  const hash = createHash('sha256').update(master).digest('hex')
  if (hashes.has(hash)) throw new Error(`Duplicated artwork: ${job.name}`)
  hashes.add(hash)
  const meta = await sharp(master).metadata()
  if (meta.width < 1024 || meta.height < 768) throw new Error(`Undersized artwork: ${job.name}`)
  const entry = manifest.entries[`/images/${relative}`]
  if (entry?.sourceHash !== hash) throw new Error(`Missing or stale image derivatives: ${job.name}`)
  rows.push({ name: job.name, id: job.id, kind: job.kind, relative, hash, masterBytes: master.length,
    width: meta.width, height: meta.height, derived: entry?.sourceHash === hash,
    mobileWebpBytes: entry?.variants[0].webp.bytes, mobileAvifBytes: entry?.variants[0].avif.bytes })
}
for (let start = 0; start < rows.length; start += 30) {
  const page = rows.slice(start, start + 30)
  const layers = []
  for (const [index, item] of page.entries()) {
    const left = index % 5 * 300, top = Math.floor(index / 5) * 226
    const thumb = await sharp(path.join(root, 'assets/images', item.relative)).resize(296, 196, { fit: 'contain', background: '#f8f0df' }).png().toBuffer()
    layers.push({ input: thumb, left: left + 2, top: top + 2 })
    const label = `${start + index + 1}. ${item.name}`.replace(/[&<>]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' })[c])
    layers.push({ input: Buffer.from(`<svg width="296" height="26"><text x="8" y="20" font-family="Microsoft YaHei" font-size="17" fill="#3d3124">${label}</text></svg>`), left: left + 2, top: top + 199 })
  }
  await sharp({ create: { width: 1500, height: Math.ceil(page.length / 5) * 226, channels: 3, background: '#f8f0df' } }).composite(layers).jpeg({ quality: 86 }).toFile(path.join(output, `sheet-${start / 30 + 1}.jpg`))
}
await fs.writeFile(path.join(output, 'audit.json'), JSON.stringify({ assets: rows.length, unique: hashes.size, derived: rows.filter(row => row.derived).length, rows }, null, 2))
console.log(JSON.stringify({ assets: rows.length, unique: hashes.size, derived: rows.filter(row => row.derived).length, output }))
