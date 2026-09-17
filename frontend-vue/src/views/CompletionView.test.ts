import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import CompletionView from './CompletionView.vue'
import ResponsiveImage from '@/components/ResponsiveImage.vue'
import { useGameStore } from '@/stores/game'
import { useRoomStore } from '@/stores/room'
import { roomApi } from '@/api/room'
import { gameApi } from '@/api/game'
import type { GameState, RoomDTO, StoryEvent } from '@/types'

const { route, push } = vi.hoisted(() => ({
  route: { params: {} as { code?: string } },
  push: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push }) }))

const completedStory: StoryEvent = {
  scenes: [{ id: 'completed', trigger: 'COMPLETE', title: '灵山归来', text: '服务端确认的通关故事。', skippable: true }],
}
const wrappers: VueWrapper[] = []

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(done => { resolve = done })
  return { promise, resolve }
}

function savedState(storyEvent?: StoryEvent): GameState {
  return { sessionId: 'saved-journey', storyEvent } as GameState
}

function roomState(status: RoomDTO['status'] = 'FINISHED'): RoomDTO {
  return {
    code: 'TEAM42', status, stateVersion: 21, hostUserId: 'host',
    players: [
      { userId: 'host', username: 'host', characterClass: 'SUN_WUKONG', ready: true, host: true },
      { userId: 'friend', username: 'friend', characterClass: null, ready: true, host: false },
    ],
    playerCount: 2, createdAt: '2026-09-17T00:00:00Z', floor: 3, storyEvent: completedStory,
  }
}

function render(errorHandler = vi.fn()) {
  const wrapper = mount(CompletionView, {
    global: { stubs: { ResponsiveImage: true }, config: { errorHandler } },
  })
  wrappers.push(wrapper)
  return wrapper
}

function button(wrapper: VueWrapper, label: string) {
  const match = wrapper.findAll('button').find(candidate => candidate.text() === label)
  if (!match) throw new Error(`Missing button: ${label}`)
  return match
}

function mockCompletedSolo() {
  const game = useGameStore()
  game.sessionId = 'saved-journey'
  return vi.spyOn(gameApi, 'getState').mockResolvedValue(savedState(completedStory))
}

function mockFinishedRoom() {
  route.params = { code: 'TEAM42' }
  const room = useRoomStore()
  room.room = roomState()
  const open = vi.spyOn(roomApi, 'getRoom').mockResolvedValue(roomState())
  return { room, open }
}

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  route.params = {}
  push.mockClear()
  setActivePinia(createPinia())
  // Only fresh read-only APIs are mocked; unexpected transport/writes must fail.
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Unexpected network request in completion view')))
})

afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  vi.unstubAllGlobals()
})

describe('CompletionView authoritative completion and safe navigation', () => {
  it('waits for the solo server COMPLETE scene before showing the art, title and ending', async () => {
    const game = useGameStore()
    game.sessionId = 'saved-journey'
    game.storyEvent = completedStory // Cached local data alone is not confirmation.
    const read = deferred<GameState>()
    const load = vi.spyOn(gameApi, 'getState').mockReturnValue(read.promise)
    const wrapper = render()

    expect(load).toHaveBeenCalledExactlyOnceWith('saved-journey')
    expect(wrapper.get('[role="status"]').text()).toContain('正在确认')
    expect(wrapper.find('#completion-title').exists()).toBe(false)
    expect(wrapper.findComponent(ResponsiveImage).exists()).toBe(false)

    read.resolve(savedState(completedStory))
    await flushPromises()

    expect(wrapper.get('#completion-title').text()).toBe('取经归来')
    expect(wrapper.get('.completion-story').text()).toBe('服务端确认的通关故事。')
    expect(wrapper.get('.completion-summary').text()).toBe('三章圆满，西行之志已成')
    expect(wrapper.findComponent(ResponsiveImage).exists()).toBe(true)
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })

  it.each([
    { label: 'no saved record', state: null },
    { label: 'a saved journey without completion', state: savedState({ scenes: [{ id: 'chapter', trigger: 'CHAPTER_START', title: '初入黑风山', text: '旅程仍在继续。', skippable: true }] }) },
  ])('does not invent a solo victory for $label', async ({ state }) => {
    const game = useGameStore()
    game.storyEvent = completedStory // A stale ending in the store must not override the response.
    if (state) game.sessionId = 'saved-journey'
    const load = vi.spyOn(gameApi, 'getState').mockResolvedValue(state as GameState)
    const wrapper = render()
    await flushPromises()

    if (state) expect(load).toHaveBeenCalledExactlyOnceWith('saved-journey')
    else expect(load).not.toHaveBeenCalled()
    expect(wrapper.get('[role="status"]').text()).toContain('未找到本局通关记录')
    expect(wrapper.find('#completion-title').exists()).toBe(false)
    expect(wrapper.findComponent(ResponsiveImage).exists()).toBe(false)
    expect(wrapper.find('.completion-actions').exists()).toBe(false)
  })

  it('opens the requested multiplayer room and confirms only its returned FINISHED state', async () => {
    route.params = { code: 'TEAM42' }
    const room = useRoomStore()
    room.room = { ...roomState(), code: 'OLD-ROOM' }
    const read = deferred<RoomDTO>()
    const open = vi.spyOn(roomApi, 'getRoom').mockReturnValue(read.promise)
    const load = vi.spyOn(gameApi, 'getState')
    const wrapper = render()

    expect(open).toHaveBeenCalledExactlyOnceWith('TEAM42')
    expect(wrapper.find('#completion-title').exists()).toBe(false)
    expect(wrapper.findComponent(ResponsiveImage).exists()).toBe(false)
    read.resolve(roomState())
    await flushPromises()

    expect(load).not.toHaveBeenCalled()
    expect(wrapper.get('#completion-title').text()).toBe('取经归来')
    expect(wrapper.get('.completion-summary').text()).toBe('2 人同行，三章圆满')
    expect(wrapper.findComponent(ResponsiveImage).exists()).toBe(true)
  })

  it('does not treat an unfinished multiplayer room as completed even when its cached story has an ending', async () => {
    route.params = { code: 'TEAM42' }
    const room = useRoomStore()
    vi.spyOn(roomApi, 'getRoom').mockResolvedValue(roomState('IN_MAP'))
    const wrapper = render()
    await flushPromises()

    expect(wrapper.get('[role="status"]').text()).toContain('未找到本局通关记录')
    expect(wrapper.find('#completion-title').exists()).toBe(false)
    expect(wrapper.findComponent(ResponsiveImage).exists()).toBe(false)
  })

  it('handles a rejected multiplayer read as unconfirmed without an unhandled rejection or stale victory', async () => {
    route.params = { code: 'TEAM42' }
    const room = useRoomStore()
    room.room = roomState()
    vi.spyOn(roomApi, 'getRoom').mockRejectedValue(new Error('Network unavailable'))
    const errorHandler = vi.fn()
    const unhandled = vi.fn()
    window.addEventListener('unhandledrejection', unhandled)
    try {
      const wrapper = render(errorHandler)
      await flushPromises()

      expect(wrapper.get('[role="status"]').text()).toMatch(/未确认|无法确认/)
      expect(wrapper.get('[role="status"]').text()).not.toContain('未找到本局通关记录')
      expect(wrapper.find('#completion-title').exists()).toBe(false)
      expect(wrapper.findComponent(ResponsiveImage).exists()).toBe(false)
      expect(errorHandler).not.toHaveBeenCalled()
      expect(unhandled).not.toHaveBeenCalled()
      expect(push).not.toHaveBeenCalled()
    } finally {
      window.removeEventListener('unhandledrejection', unhandled)
    }
  })

  it('reports an unreadable known solo save as unconfirmed instead of asserting no completion record exists', async () => {
    const game = useGameStore()
    game.sessionId = 'saved-journey'
    game.storyEvent = completedStory
    vi.spyOn(gameApi, 'getState').mockRejectedValue(new Error('Network unavailable'))
    const wrapper = render()
    await flushPromises()

    expect(wrapper.get('[role="status"]').text()).toContain('结果未确认')
    expect(wrapper.get('[role="status"]').text()).not.toContain('未找到本局通关记录')
    expect(wrapper.find('#completion-title').exists()).toBe(false)
    expect(wrapper.findComponent(ResponsiveImage).exists()).toBe(false)
  })

  it('finding new companions resets only local room state and never leaves or deletes the completed room', async () => {
    const { room } = mockFinishedRoom()
    const reset = vi.spyOn(room, 'reset')
    const leave = vi.spyOn(room, 'leaveRoom')
    const remoteLeave = vi.spyOn(roomApi, 'leaveRoom')
    const wrapper = render()
    await flushPromises()
    const completedRoom = room.room

    await button(wrapper, '寻找新的同行').trigger('click')
    await flushPromises()

    expect(reset).toHaveBeenCalledTimes(1)
    expect(room.room).toBeNull()
    expect(completedRoom?.status).toBe('FINISHED')
    expect(completedRoom?.players).toHaveLength(2)
    expect(leave).not.toHaveBeenCalled()
    expect(remoteLeave).not.toHaveBeenCalled()
    expect(fetch).not.toHaveBeenCalled() // Includes any accidental DELETE or write request.
    expect(push).toHaveBeenCalledExactlyOnceWith('/room')
  })

  it('starts a new solo character selection without resetting a multiplayer room', async () => {
    mockCompletedSolo()
    const reset = vi.spyOn(useRoomStore(), 'reset')
    const wrapper = render()
    await flushPromises()
    await button(wrapper, '再启西行').trigger('click')

    expect(push).toHaveBeenCalledExactlyOnceWith('/char-select')
    expect(reset).not.toHaveBeenCalled()
    expect(fetch).not.toHaveBeenCalled()
  })

  it.each(['solo', 'multiplayer', 'unconfirmed'] as const)('returns to camp from the %s screen without changing the server room', async mode => {
    if (mode === 'multiplayer') mockFinishedRoom()
    else if (mode === 'solo') mockCompletedSolo()
    else vi.spyOn(gameApi, 'getState').mockRejectedValue(new Error('Network unavailable'))
    const room = useRoomStore()
    const reset = vi.spyOn(room, 'reset')
    const leave = vi.spyOn(room, 'leaveRoom')
    const wrapper = render()
    await flushPromises()
    await button(wrapper, '返回营地').trigger('click')

    expect(push).toHaveBeenCalledExactlyOnceWith('/menu')
    expect(reset).not.toHaveBeenCalled()
    expect(leave).not.toHaveBeenCalled()
    expect(fetch).not.toHaveBeenCalled()
  })
})
