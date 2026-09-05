// @vitest-environment node
import { existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import {
  CARD_IMG, CHARACTER_DIR, ENEMY_IMG, RELIC_IMG, NODE_IMG,
  cardImgUrl, characterAvatarUrl, fullImgUrl, enemyImgUrl, relicImgUrl, nodeImgUrl,
} from '../src/constants/images'

describe('game asset references', () => {
  it('resolves every dynamically mapped image to a repository asset', () => {
    const urls = [
      ...Object.keys(CARD_IMG).map(name => cardImgUrl(name)),
      ...Object.keys(CHARACTER_DIR).flatMap(name => [characterAvatarUrl(name), fullImgUrl(name)]),
      ...Object.keys(ENEMY_IMG).map(name => enemyImgUrl(name)),
      ...Object.keys(RELIC_IMG).map(name => relicImgUrl(name)),
      ...Object.keys(NODE_IMG).map(name => nodeImgUrl(name)),
    ]
    const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../assets/images')
    for (const url of urls) {
      expect(url).toMatch(/^\/images\//)
      const path = resolve(root, url.slice('/images/'.length))
      expect(existsSync(path), `Missing image: ${url}`).toBe(true)
    }
  })
})
