import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createVisibleFrameLoop, useMotionVisibility } from './useMotionVisibility'

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })

describe('motion scheduling', () => {
  it('keeps one RAF owner, cancels immediately and resumes idempotently', () => {
    let callback: FrameRequestCallback = () => {}
    const request = vi.fn((fn: FrameRequestCallback) => { callback = fn; return 7 })
    const cancel = vi.fn()
    vi.stubGlobal('requestAnimationFrame', request); vi.stubGlobal('cancelAnimationFrame', cancel)
    const render = vi.fn(); const loop = createVisibleFrameLoop(render, 30)
    loop.start(); loop.start(); expect(request).toHaveBeenCalledTimes(1)
    callback(0); callback(16); callback(34)
    expect(render).toHaveBeenCalledTimes(2)
    loop.stop(); expect(cancel).toHaveBeenCalledWith(7)
    callback(100); expect(render).toHaveBeenCalledTimes(2)
    loop.start(); callback(200); expect(render).toHaveBeenCalledTimes(3)
    loop.stop()
  })
  it('observes viewport, tab visibility and dynamic reduced-motion preferences; cleans up', async () => {
    let intersection: IntersectionObserverCallback = () => {}
    let preferenceChange = () => {}
    const disconnect = vi.fn(); const remove = vi.fn()
    const media = { matches: false, addEventListener: vi.fn((_type, callback) => { preferenceChange = callback }), removeEventListener: remove }
    vi.stubGlobal('matchMedia', vi.fn(() => media))
    vi.stubGlobal('IntersectionObserver', class { constructor(callback: IntersectionObserverCallback) { intersection = callback } observe() {} disconnect = disconnect })
    let hidden = false
    vi.spyOn(document, 'hidden', 'get').mockImplementation(() => hidden)
    let motion!: ReturnType<typeof useMotionVisibility>
    const component = defineComponent({ setup() { const target = ref<HTMLElement | null>(null); motion = useMotionVisibility(target); return () => h('div', { ref: target }) } })
    const wrapper = mount(component)
    expect(motion.motionAllowed.value).toBe(true)
    intersection([{ isIntersecting: false }] as IntersectionObserverEntry[], {} as IntersectionObserver)
    await nextTick(); expect(motion.motionAllowed.value).toBe(false)
    intersection([{ isIntersecting: true }] as IntersectionObserverEntry[], {} as IntersectionObserver)
    hidden = true; document.dispatchEvent(new Event('visibilitychange'))
    expect(motion.visible.value).toBe(false)
    hidden = false; document.dispatchEvent(new Event('visibilitychange'))
    media.matches = true; preferenceChange()
    expect(motion.visible.value).toBe(true); expect(motion.motionAllowed.value).toBe(false)
    media.matches = false; preferenceChange(); expect(motion.motionAllowed.value).toBe(true)
    wrapper.unmount(); expect(disconnect).toHaveBeenCalled(); expect(remove).toHaveBeenCalled()
  })
})
