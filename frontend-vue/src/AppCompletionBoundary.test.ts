import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { mount, type VueWrapper } from '@vue/test-utils'
import App from './App.vue'
import { useGameStore } from '@/stores/game'
import { useRoomStore } from '@/stores/room'
import type { RoomDTO, StoryEvent } from '@/types'

const routing = vi.hoisted(() => ({ currentRoute: null as any }))
vi.mock('vue-router', () => ({ useRouter: () => routing }))
const wrappers: VueWrapper[] = []
beforeEach(() => {
  setActivePinia(createPinia()); localStorage.clear(); sessionStorage.clear()
  routing.currentRoute = ref({ name: 'map' })
  useGameStore().sessionId = 'solo-story'
})

it('discards A queue across completion/new-room navigation and still shows/skips B chapter', async () => {
  const event = (id: string, trigger: string): StoryEvent => ({ scenes: [{ id, trigger, title: id, text: id, skippable: true }] })
  const room = useRoomStore()
  room.room = { code: 'ROOMA001', storyEvent: event('A chapter', 'CHAPTER_START') } as RoomDTO
  routing.currentRoute.value = { name: 'mp-map', params: { code: 'ROOMA001' } }
  const wrapper = mount(App, { global: { stubs: { RouterView: true, Toast: true, ConfirmModal: true } } })
  wrappers.push(wrapper)
  expect(wrapper.get('.story-panel').text()).toContain('A chapter')
  room.room.storyEvent = event('A ending', 'COMPLETE')
  routing.currentRoute.value = { name: 'mp-complete', params: { code: 'ROOMA001' } }; await wrapper.vm.$nextTick()
  expect(wrapper.find('.story-panel').exists()).toBe(false)
  routing.currentRoute.value = { name: 'mp-map', params: { code: 'ROOMB002' } }; await wrapper.vm.$nextTick()
  expect(wrapper.find('.story-panel').exists()).toBe(false) // A's old snapshot still lives in the store.
  room.room = { code: 'ROOMB002', storyEvent: event('B chapter', 'CHAPTER_START') } as RoomDTO
  await wrapper.vm.$nextTick()
  expect(wrapper.get('.story-panel').text()).toContain('B chapter')
  expect(wrapper.text()).not.toContain('A ending')
  await wrapper.get('.story-panel button:last-child').trigger('click')
  expect(wrapper.find('.story-panel').exists()).toBe(false)
  useGameStore().storyEvent = event('old solo ending', 'COMPLETE')
  routing.currentRoute.value = { name: 'map', params: {} }; await wrapper.vm.$nextTick()
  expect(wrapper.find('.story-panel').exists()).toBe(false)
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()) })

it.each(['complete', 'mp-complete'])('leaves %s stories to its authoritative view, not the global queue', async name => {
  const wrapper = mount(App, { global: { stubs: {
    RouterView: true, Toast: true, ConfirmModal: true, StoryPanel: true,
  } } })
  wrappers.push(wrapper)
  expect(wrapper.find('story-panel-stub').exists()).toBe(true)
  routing.currentRoute.value = { name }; await wrapper.vm.$nextTick()
  expect(wrapper.find('story-panel-stub').exists()).toBe(false)
  routing.currentRoute.value = { name: 'mp-map', params: { code: 'ROOMB002' } }; await wrapper.vm.$nextTick()
  expect(wrapper.find('story-panel-stub').exists()).toBe(true)
})
