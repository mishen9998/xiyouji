import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
const probe = vi.hoisted(() => ({ fail: false, attempt: vi.fn(), dispose: vi.fn(), render: vi.fn() }))
vi.mock('three', async importOriginal => {
  const original = await importOriginal<typeof import('three')>()
  return { ...original, WebGLRenderer: class {
    domElement = document.createElement('canvas')
    shadowMap = {}; ratio = 1
    constructor() { probe.attempt(); if (probe.fail) throw new Error('WebGL unavailable in probe') }
    setPixelRatio(value: number) { this.ratio = value }
    getPixelRatio() { return this.ratio }
    setSize() {}
    render = probe.render
    dispose = probe.dispose
  } }
})
import BattleCharacter3D from './BattleCharacter3D.vue'

beforeEach(() => {
  probe.fail = false
  vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} })
  vi.spyOn(console, 'warn').mockImplementation(() => {})
})
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.clearAllMocks() })
describe('3D-only battle character', () => {
  it('renders 3D even on low-core devices and releases it on unmount', async () => {
    vi.spyOn(navigator, 'hardwareConcurrency', 'get').mockReturnValue(2)
    const wrapper = mount(BattleCharacter3D, { props: { label: '八戒', characterClass: 'ZHU_BAJIE' } })
    await flushPromises()
    expect(probe.attempt).toHaveBeenCalledOnce()
    expect(wrapper.attributes('data-renderer')).toBe('webgl')
    expect(wrapper.find('canvas').exists()).toBe(true)
    expect(wrapper.find('img').exists()).toBe(false)
    wrapper.unmount()
    expect(probe.dispose).toHaveBeenCalledOnce()
  })
  it('shows retry instead of a 2D fallback; retry recovers the 3D renderer', async () => {
    probe.fail = true
    const wrapper = mount(BattleCharacter3D, { props: { label: '八戒', characterClass: 'ZHU_BAJIE', action: 'attack', actionToken: 5 } })
    await flushPromises()
    expect(wrapper.attributes('data-renderer')).toBe('unavailable')
    expect(wrapper.text()).toContain('出牌不受影响')
    expect(wrapper.find('img').exists()).toBe(false)
    probe.fail = false
    await wrapper.get('button').trigger('click'); await flushPromises()
    expect(wrapper.attributes('data-renderer')).toBe('webgl')
    expect(wrapper.find('.character-3d__action').text()).toBe('攻击')
    wrapper.unmount()
  })
  it('keeps a static 3D scene for reduced-motion users, and handles context loss', async () => {
    vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: true, addEventListener() {}, removeEventListener() {} })))
    const wrapper = mount(BattleCharacter3D, { props: { label: '八戒', characterClass: 'ZHU_BAJIE' } })
    await flushPromises()
    expect(wrapper.attributes('data-renderer')).toBe('webgl')
    expect(wrapper.attributes('data-motion-active')).toBe('false')
    expect(probe.render).toHaveBeenCalled()
    await wrapper.get('canvas').trigger('webglcontextlost')
    expect(wrapper.attributes('data-renderer')).toBe('unavailable')
    expect(probe.dispose).toHaveBeenCalledOnce()
    wrapper.unmount()
    expect(probe.dispose).toHaveBeenCalledOnce()
  })
})
