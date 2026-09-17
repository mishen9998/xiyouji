// @vitest-environment node
import { existsSync, readFileSync, statSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import {
  CARD_IMG, CHARACTER_DIR, ENEMY_IMG, RELIC_IMG, NODE_IMG,
  cardImgUrl, characterAvatarUrl, fullImgUrl, enemyImgUrl, relicImgUrl, nodeImgUrl,
  imageSources, sceneImageUrl,
} from '../src/constants/images'

describe('game asset references', () => {
  it('ships distinct versioned artwork for every card and relic without changing old source paths', () => {
    const frontend = resolve(dirname(fileURLToPath(import.meta.url)), '..')
    const manifest = JSON.parse(readFileSync(resolve(frontend, 'src/constants/image-manifest.json'), 'utf8'))
    const newHashes = new Set()
    for (const [kind, mapping] of [['cards', CARD_IMG], ['relics', RELIC_IMG]]) {
      for (const id of new Set(Object.values(mapping))) {
        const entry = manifest.entries[`/images/artwork-v2/${kind}/${id}-v2.png`]
        expect(entry, id).toBeDefined()
        newHashes.add(entry.sourceHash)
        expect(entry.variants[0].avif.bytes).toBeLessThanOrEqual(60 * 1024)
      }
    }
    expect(newHashes.size).toBe(120)
  })
  it('resolves every dynamically mapped image to a repository asset', () => {
    const urls = [
      ...Object.keys(CARD_IMG).map(name => cardImgUrl(name)),
      ...Object.keys(CHARACTER_DIR).flatMap(name => [characterAvatarUrl(name), fullImgUrl(name)]),
      ...Object.keys(ENEMY_IMG).map(name => enemyImgUrl(name)),
      ...Object.keys(RELIC_IMG).map(name => relicImgUrl(name)),
      ...Object.keys(NODE_IMG).map(name => nodeImgUrl(name)),
    ]
    const root = resolve(dirname(fileURLToPath(import.meta.url)), '../public')
    for (const url of urls) {
      expect(url).toMatch(/^\/illustrations\//)
      const path = resolve(root, url.slice(1))
      expect(existsSync(path), `Missing image: ${url}`).toBe(true)
      expect(imageSources(url).avifSrcset).toContain('.avif ')
      expect(imageSources(url).webpSrcset).toContain('.webp ')
    }
  })
  it('preserves all source images and ships valid, budgeted multi-size modern formats', () => {
    const frontend = resolve(dirname(fileURLToPath(import.meta.url)), '..')
    const manifest = JSON.parse(readFileSync(resolve(frontend, 'src/constants/image-manifest.json'), 'utf8'))
    expect(Object.keys(manifest.entries).length).toBeGreaterThanOrEqual(203)
    for (const [source, entry] of Object.entries(manifest.entries)) {
      const original = readFileSync(resolve(frontend, '../assets/images', source.slice('/images/'.length)))
      expect(createHash('sha256').update(original).digest('hex')).toBe(entry.sourceHash)
      expect(original.length).toBe(entry.sourceBytes)
      expect(entry.variants.length).toBeGreaterThanOrEqual(1)
      for (const variant of entry.variants) {
        for (const format of ['webp', 'avif']) {
          const path = resolve(frontend, 'public', variant[format].url.slice(1))
          expect(statSync(path).size).toBe(variant[format].bytes)
          expect(variant[format].bytes).toBeLessThanOrEqual(200 * 1024)
          const header = readFileSync(path).subarray(0, 16).toString('ascii')
          expect(header).toContain(format === 'webp' ? 'WEBP' : 'avif')
        }
      }
    }
    for (const key of ['journey', 'camp', 'blackwind', 'firemountain', 'lionridge', 'completion', 'blackbear', 'bullking', 'roc']) {
      expect(sceneImageUrl(key, 640)).toMatch(/^\/illustrations\//)
    }
  })
})
