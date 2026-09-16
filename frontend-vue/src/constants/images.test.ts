import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fullImgUrl, imageSources, imageUrl, preloadImages, sceneImageUrl } from './images'

describe('scene-scoped image hints', () => {
  beforeEach(() => { document.querySelectorAll('[data-game-image-preload]').forEach(node => node.remove()); vi.spyOn(document, 'hidden', 'get').mockReturnValue(false) })
  it('resolves original and derived URLs to the same responsive catalog', () => {
    const source = '/images/孙悟空/建模/full_sunwukong.jpg'
    expect(imageSources(source)).toEqual(imageSources(fullImgUrl('SUN_WUKONG')))
    expect(imageUrl(source, 960)).not.toBe(imageUrl(source, 320))
    expect(sceneImageUrl('journey', 640)).toMatch(/^\/illustrations\//)
  })
  it('preloads only requested next images, honors limit and deduplicates', () => {
    preloadImages([fullImgUrl('SUN_WUKONG'), fullImgUrl('ZHU_BAJIE'), fullImgUrl('SHA_SENG')], { limit: 1 })
    expect(document.querySelectorAll('[data-game-image-preload]')).toHaveLength(1)
    const link = document.querySelector('link[data-game-image-preload]')!
    expect(link.getAttribute('type')).toBe('image/avif')
    expect(link.getAttribute('fetchpriority')).toBe('low')
    preloadImages([fullImgUrl('SUN_WUKONG')], { limit: 1 })
    expect(document.querySelectorAll('[data-game-image-preload]')).toHaveLength(1)
  })
  it('does not download speculative images while tab is hidden', () => {
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(true)
    preloadImages([fullImgUrl('BAI_LONGMA')])
    expect(document.querySelectorAll('[data-game-image-preload]')).toHaveLength(0)
  })
})
