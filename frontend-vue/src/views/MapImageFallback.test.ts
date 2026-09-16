import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import MapView from './MapView.vue'
import ResponsiveImage from '@/components/ResponsiveImage.vue'
import { useGameStore } from '@/stores/game'
import type { MapNode, Player } from '@/types'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }))

const cases = [
  { selector: '.player-avatar-full', alt: '角色头像', emoji: '🐵', sizes: '(max-width: 600px) 28px, 36px', loading: 'eager', priority: 'high' },
  { selector: '.map-avatar-img', alt: '当前位置玩家', emoji: '🐵', sizes: '(max-width: 600px) 32px, 40px', loading: 'eager', priority: 'high' },
  { selector: '.map-relic-icon', alt: '蟠桃', emoji: '🍑', sizes: '(max-width: 600px) 24px, 32px', loading: 'lazy', priority: 'auto' },
]

beforeEach(() => {
  localStorage.clear()
  setActivePinia(createPinia())
  // Layout geometry belongs to the browser regression; retain the real layout composable here.
  vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} })
})
afterEach(() => vi.unstubAllGlobals())

function setup() {
  const store = useGameStore()
  const node: MapNode = { id: 'map-position', row: 1, col: 0, type: 'BATTLE', name: '黑风山道', visited: true, accessible: false, connections: [] }
  store.sessionId = 'map-fallback-test'
  store.selectedCharacter = 'SUN_WUKONG'
  store.player = { characterClass: 'SUN_WUKONG', displayName: '孙悟空', hp: 60, maxHp: 80, block: 0, energy: 3, maxEnergy: 3,
    gold: 50, deckSize: 0, floor: 1, deck: [], relics: [{ name: '蟠桃', description: '携带的宝物', emoji: '🍑' }] } as Player
  store.currentNode = node
  store.mapNodes = [node]
  return mount(MapView, { global: { stubs: { MapNodeComponent: true, EventModal: true, DeckModal: true, RelicsModal: true } } })
}

describe('MapView real responsive-image failure boundaries', () => {
  it.each(cases)('$selector keeps its frame and readable fallback after a network image error', async scenario => {
    const wrapper = setup()
    await flushPromises()
    expect(wrapper.findAllComponents(ResponsiveImage)).toHaveLength(3)
    const frame = wrapper.get(scenario.selector)
    const originalElement = frame.element
    const frameStyle = frame.attributes('style')
    const markerPosition = wrapper.get('.map-player-marker').attributes('style')
    const image = frame.get('img')

    expect(originalElement.tagName).toBe('PICTURE')
    expect(frameStyle).toContain('aspect-ratio:')
    expect(frame.findAll('source').map(source => source.attributes('type'))).toEqual(['image/avif', 'image/webp'])
    for (const source of frame.findAll('source')) expect(source.attributes('sizes')).toBe(scenario.sizes)
    expect(image.attributes()).toMatchObject({ alt: scenario.alt, loading: scenario.loading, fetchpriority: scenario.priority, decoding: 'async' })
    expect(Number(image.attributes('width'))).toBeGreaterThan(0)
    expect(Number(image.attributes('height'))).toBeGreaterThan(0)
    expect(image.attributes('style')).toContain('object-fit: cover')

    await image.trigger('error')

    expect(wrapper.get(scenario.selector).element).toBe(originalElement)
    expect(frame.classes()).toContain(scenario.selector.slice(1))
    expect(frame.classes()).toContain('responsive-image--failed')
    expect(frame.attributes('style')).toBe(frameStyle)
    expect(wrapper.get('.map-player-marker').attributes('style')).toBe(markerPosition)
    expect(frame.find('img').exists()).toBe(false)
    expect(frame.find('source').exists()).toBe(false)
    expect(frame.get('[role="img"]').attributes('aria-label')).toBe(scenario.alt)
    expect(frame.get('[role="img"]').text()).toBe(scenario.emoji)
    // A failed header image must not remove the separate location image, or vice versa.
    for (const other of cases.filter(item => item.selector !== scenario.selector)) expect(wrapper.get(other.selector).find('img').exists()).toBe(true)
    wrapper.unmount()
  })

  it('supports all three failed image requests together without dropping location or relic meaning', async () => {
    const wrapper = setup()
    await flushPromises()
    const frames = cases.map(scenario => wrapper.get(scenario.selector).element)
    for (const scenario of cases) await wrapper.get(`${scenario.selector} img`).trigger('error')
    expect(wrapper.findAll('img')).toHaveLength(0)
    for (const [index, scenario] of cases.entries()) {
      const frame = wrapper.get(scenario.selector)
      expect(frame.element).toBe(frames[index])
      expect(frame.get('[role="img"]').attributes('aria-label')).toBe(scenario.alt)
      expect(frame.text()).toBe(scenario.emoji)
    }
    expect(wrapper.find('.map-player-marker').exists()).toBe(true)
    expect(wrapper.get('.map-relic-icon').attributes('title')).toContain('蟠桃 — 携带的宝物')
    expect(wrapper.findAll('.top-actions button')).toHaveLength(3)
    wrapper.unmount()
  })
})
