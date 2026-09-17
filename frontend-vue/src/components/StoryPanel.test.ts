import { mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const stores = vi.hoisted(() => ({ game: {} as any, room: {} as any }))
vi.mock('@/stores/game', () => ({ useGameStore: () => stores.game }))
vi.mock('@/stores/room', () => ({ useRoomStore: () => stores.room }))
import StoryPanel from './StoryPanel.vue'
describe('personal story skip', () => {
  beforeEach(() => {
    sessionStorage.clear()
    stores.game = reactive({ storyEvent: { scenes: [{ id: 'run:start', trigger: 'DEPARTURE', title: '长安', text: '出发', skippable: true }] }, handleEvent: vi.fn(), nextLayer: vi.fn() })
    stores.room = reactive({ room: { stateVersion: 7 }, handleEvent: vi.fn(), nextLayer: vi.fn() })
  })
  it('skip changes only local display and survives remount without sending a game command', async () => {
    let wrapper = mount(StoryPanel, { props: { stories: [stores.game.storyEvent] } })
    expect(wrapper.text()).toContain('长安')
    await wrapper.findAll('button')[1].trigger('click')
    expect(wrapper.find('aside').exists()).toBe(false)
    expect(stores.room.room.stateVersion).toBe(7)
    expect(stores.game.handleEvent).not.toHaveBeenCalled()
    expect(stores.room.handleEvent).not.toHaveBeenCalled()
    expect(stores.room.nextLayer).not.toHaveBeenCalled()
    wrapper.unmount()
    wrapper = mount(StoryPanel, { props: { stories: [stores.game.storyEvent] } })
    expect(wrapper.find('aside').exists()).toBe(false)
    stores.game.storyEvent = { scenes: [{ id: 'run:chapter2', trigger: 'CHAPTER_START', title: '借风', text: '新章', skippable: true }] }
    await wrapper.setProps({ stories: [stores.game.storyEvent] })
    expect(wrapper.text()).toContain('借风')
    wrapper.unmount()
  })
})
