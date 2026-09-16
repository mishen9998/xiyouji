import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { useMapLayout } from './useMapLayout'
import MapNodeComponent from '@/components/MapNodeComponent.vue'
import type { MapNode } from '@/types'

afterEach(() => vi.unstubAllGlobals())
const node: MapNode = { id: 'a', row: 1, col: 0, type: 'BATTLE', name: '山间遭遇', visited: false, accessible: true, connections: ['b'] }
describe('responsive route map', () => {
  it('observes actual container width, recalculates nodes and lines, disconnects on unmount', async () => {
    let resize = () => {}
    const disconnect = vi.fn()
    vi.stubGlobal('ResizeObserver', class { constructor(fn: () => void) { resize = fn } observe() {} disconnect = disconnect })
    let layout: ReturnType<typeof useMapLayout>
    const wrapper = mount(defineComponent({ template: '<div ref="element" style="padding: 6px"></div>', setup() {
      const element = ref<HTMLElement | null>(null)
      layout = useMapLayout(ref([node, { ...node, id: 'b', col: 3, row: 2 }]), ref(node), element)
      return { element }
    } }))
    Object.defineProperty(wrapper.element, 'clientWidth', { value: 390, configurable: true })
    resize(); await nextTick()
    expect(layout!.MAP_WIDTH.value).toBe(378)
    expect(layout!.nodePositions.value.a.x).toBe(47.25)
    expect(layout!.connectionLines.value[0].x2).toBe(330.75)
    Object.defineProperty(wrapper.element, 'clientWidth', { value: 844 })
    resize(); await nextTick()
    expect(layout!.MAP_WIDTH.value).toBe(832)
    expect(layout!.nodePositions.value.a.x).toBe(104)
    expect(layout!.connectionLines.value[0].x2).toBe(728)
    wrapper.unmount(); expect(disconnect).toHaveBeenCalledOnce()
  })
  it('uses native keyboard-capable buttons and prevents unavailable or pending movement', async () => {
    const wrapper = mount(MapNodeComponent, { props: { node, isCurrent: false, x: 40, y: 80 } })
    expect(wrapper.element.tagName).toBe('BUTTON')
    expect(wrapper.attributes('aria-label')).toContain('可前往')
    await wrapper.trigger('click'); expect(wrapper.emitted('move')).toHaveLength(1)
    await wrapper.setProps({ busy: true }); expect(wrapper.attributes('disabled')).toBeDefined()
    await wrapper.trigger('click'); expect(wrapper.emitted('move')).toHaveLength(1)
  })
  it('retains an accessible icon placeholder if node artwork fails', async () => {
    const wrapper = mount(MapNodeComponent, { props: { node, isCurrent: true, x: 40, y: 80 } })
    await wrapper.get('img').trigger('error')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('.node-icon').exists()).toBe(true)
    expect(wrapper.attributes('aria-current')).toBe('step')
  })
})
