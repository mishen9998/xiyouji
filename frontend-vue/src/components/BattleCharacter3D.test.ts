import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
const attempted = vi.hoisted(() => vi.fn())
vi.mock('three', async importOriginal => {
  const original = await importOriginal<typeof import('three')>()
  return { ...original, WebGLRenderer: class { constructor() { attempted(); throw new Error('WebGL unavailable in probe') } } }
})
import BattleCharacter3D from './BattleCharacter3D.vue'

afterEach(() => { vi.restoreAllMocks(); attempted.mockClear() })
describe('optional 3D fallback', () => {
  it('does not attempt WebGL on a low-core device', async () => {
    vi.spyOn(navigator, 'hardwareConcurrency', 'get').mockReturnValue(2)
    const wrapper = mount(BattleCharacter3D, { props: { label: '悟空' }, global: { stubs: { BattleCharacter: true } } })
    await flushPromises()
    expect(attempted).not.toHaveBeenCalled()
    expect(wrapper.attributes('data-renderer')).toBe('illustration')
    expect(wrapper.find('battle-character-stub').exists()).toBe(true)
    wrapper.unmount()
  })
  it('keeps the same action interface as an illustration when WebGL creation fails', async () => {
    vi.spyOn(navigator, 'hardwareConcurrency', 'get').mockReturnValue(8)
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    const wrapper = mount(BattleCharacter3D, { props: { label: '悟空', action: 'attack', actionToken: 5 }, global: { stubs: { BattleCharacter: true } } })
    await flushPromises()
    expect(attempted).toHaveBeenCalledOnce()
    expect(wrapper.attributes('data-renderer')).toBe('illustration')
    expect(wrapper.find('battle-character-stub').attributes()).toMatchObject({ action: 'attack', actiontoken: '5' })
    wrapper.unmount()
  })
})
