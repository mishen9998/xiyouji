import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import CompletionView from './CompletionView.vue'
import { useGameStore } from '@/stores/game'
import { useRoomStore } from '@/stores/room'
import { gameApi } from '@/api/game'
import { roomApi } from '@/api/room'
import type { GameState, RoomDTO } from '@/types'

const routing = vi.hoisted(() => ({ current: null as any }))
vi.mock('vue-router', () => ({ useRoute: () => routing.current, useRouter: () => ({ push: vi.fn() }) }))
const wrappers: VueWrapper[] = []
const story = (text: string) => ({ scenes: [{ id: text, trigger: 'COMPLETE', title: text, text, skippable: true }] })
const room = (code: string, finished = true) => ({ code, status: finished ? 'FINISHED' : 'WAITING',
  players: [{ userId: 'host' }], storyEvent: story(code) } as RoomDTO)
const solo = (id: string, finished = true) => ({ sessionId: id, storyEvent: finished ? story(id) : { scenes: [] } } as GameState)
function deferred<T>() {
  let resolve!: (value: T) => void; let reject!: (reason: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function render() {
  const wrapper = mount(CompletionView, { global: { stubs: { ResponsiveImage: true } } })
  wrappers.push(wrapper); return wrapper
}
function noVictory(wrapper: VueWrapper) {
  expect(wrapper.find('#completion-title').exists()).toBe(false)
  expect(wrapper.find('.completion-art').exists()).toBe(false)
}
beforeEach(() => {
  setActivePinia(createPinia()); localStorage.clear(); sessionStorage.clear()
  localStorage.setItem('xiyouji_jwt_token', 'completion-boundary-test-token')
  routing.current = reactive({ params: {} as { code?: string } })
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Injected read failure')))
  // Original component's store boundary remains functional for red reproduction;
  // the corrected component may use fresh read-only API results directly.
  vi.spyOn(useRoomStore(), 'openRoom').mockImplementation(async code => {
    const dto = await roomApi.getRoom(code); useRoomStore().room = dto; return dto
  })
})
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); vi.restoreAllMocks(); vi.unstubAllGlobals() })

it('cold Pinia preserves a real persisted pointer and reports a failed GET as unknown', async () => {
  localStorage.setItem('xiyouji_session_id', 'cold-save')
  expect(useGameStore().sessionId).toBeNull()
  const wrapper = render(); await flushPromises()
  expect(wrapper.get('[role="status"]').text()).toContain('结果未确认')
  expect(wrapper.get('[role="status"]').text()).not.toContain('未找到本局通关记录')
  expect(localStorage.getItem('xiyouji_session_id')).toBe('cold-save')
  expect(fetch).toHaveBeenCalledOnce(); noVictory(wrapper)
})

it('revokes A immediately during SPA A→B confirmation and can later confirm A again', async () => {
  routing.current.params.code = 'ROOMA001'
  const pending = deferred<RoomDTO>()
  const read = vi.spyOn(roomApi, 'getRoom').mockImplementation(code => code === 'ROOMA001' ? Promise.resolve(room(code)) : pending.promise)
  const wrapper = render(); await flushPromises()
  expect(wrapper.get('.completion-story').text()).toBe('ROOMA001')
  routing.current.params.code = 'ROOMB002'; await flushPromises()
  noVictory(wrapper); expect(wrapper.get('[role="status"]').text()).toContain('正在确认')
  pending.resolve(room('ROOMB002', false)); await flushPromises()
  noVictory(wrapper); expect(wrapper.get('[role="status"]').text()).toContain('旅程尚未圆满')
  routing.current.params.code = 'ROOMA001'; await flushPromises()
  expect(wrapper.get('.completion-story').text()).toBe('ROOMA001')
  expect(read.mock.calls.map(args => args[0])).toEqual(['ROOMA001', 'ROOMB002', 'ROOMA001'])
})

it('does not trust an openRoom cached FINISHED snapshot as fresh confirmation', async () => {
  routing.current.params.code = 'ROOMA001'
  useRoomStore().room = room('ROOMA001')
  vi.mocked(useRoomStore().openRoom).mockResolvedValue(room('ROOMA001'))
  const pending = deferred<RoomDTO>(); vi.spyOn(roomApi, 'getRoom').mockReturnValue(pending.promise)
  const wrapper = render(); await flushPromises(); noVictory(wrapper)
  pending.resolve(room('ROOMA001', false)); await flushPromises(); noVictory(wrapper)
})

it('shows unknown after switching to a failed read and retries once with a fresh GET', async () => {
  routing.current.params.code = 'ROOMA001'
  const read = vi.spyOn(roomApi, 'getRoom').mockResolvedValueOnce(room('ROOMA001')).mockRejectedValueOnce(new Error('offline'))
  const wrapper = render(); await flushPromises()
  routing.current.params.code = 'ROOMB002'; await flushPromises(); noVictory(wrapper)
  expect(wrapper.get('[role="status"]').text()).toContain('结果未确认')
  const retry = deferred<RoomDTO>(); read.mockReturnValueOnce(retry.promise)
  const button = wrapper.get('[data-testid="retry-completion"]')
  await button.trigger('click'); await button.trigger('click')
  expect(read).toHaveBeenCalledTimes(3); noVictory(wrapper)
  retry.resolve(room('ROOMB002')); await flushPromises()
  expect(wrapper.get('.completion-story').text()).toBe('ROOMB002')
})

it.each(['resolve', 'reject'] as const)('ignores late A %s after B was authoritatively confirmed', async outcome => {
  routing.current.params.code = 'ROOMA001'
  const pending = deferred<RoomDTO>()
  vi.spyOn(roomApi, 'getRoom').mockImplementation(code => code === 'ROOMA001' ? pending.promise : Promise.resolve(room(code)))
  const wrapper = render()
  routing.current.params.code = 'ROOMB002'; await flushPromises()
  expect(wrapper.get('.completion-story').text()).toBe('ROOMB002')
  if (outcome === 'resolve') pending.resolve(room('ROOMA001'))
  else pending.reject(new Error('late failure'))
  await flushPromises()
  expect(wrapper.get('.completion-story').text()).toBe('ROOMB002')
  expect(wrapper.find('[role="status"]').exists()).toBe(false)
})

it('ignores late A success after B was authoritatively found unfinished', async () => {
  routing.current.params.code = 'ROOMA001'
  const pending = deferred<RoomDTO>()
  vi.spyOn(roomApi, 'getRoom').mockImplementation(code => code === 'ROOMA001' ? pending.promise : Promise.resolve(room(code, false)))
  const wrapper = render(); routing.current.params.code = 'ROOMB002'; await flushPromises()
  pending.resolve(room('ROOMA001')); await flushPromises(); noVictory(wrapper)
  expect(wrapper.get('[role="status"]').text()).toContain('旅程尚未圆满')
})

it('session changes revoke the old ending and stale solo success cannot replace the new pointer', async () => {
  const game = useGameStore(); game.sessionId = 'save-a'
  const pending = deferred<GameState>()
  vi.spyOn(gameApi, 'getState').mockImplementation(id => id === 'save-a' ? pending.promise : Promise.resolve(solo(id, false)))
  const wrapper = render(); game.sessionId = 'save-b'; game.saveSessionLocal(); await flushPromises()
  pending.resolve(solo('save-a')); await flushPromises(); noVictory(wrapper)
  expect(game.sessionId).toBe('save-b')
  expect(localStorage.getItem('xiyouji_session_id')).toBe('save-b')
})

it('a cold saved pointer can recover through retry without any save write', async () => {
  localStorage.setItem('xiyouji_session_id', 'cold-save')
  const read = vi.spyOn(gameApi, 'getState').mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(solo('cold-save'))
  const wrapper = render(); await flushPromises()
  await wrapper.get('[data-testid="retry-completion"]').trigger('click'); await flushPromises()
  expect(wrapper.get('.completion-story').text()).toBe('cold-save')
  expect(read.mock.calls).toEqual([['cold-save'], ['cold-save']])
  expect(useGameStore().sessionId).toBeNull() // Confirmation must not mutate global gameplay state.
  expect(localStorage.getItem('xiyouji_session_id')).toBe('cold-save')
})

it('rejects a response whose resource identity does not match the requested room', async () => {
  routing.current.params.code = 'ROOMB002'
  vi.spyOn(roomApi, 'getRoom').mockResolvedValue(room('ROOMA001'))
  const wrapper = render(); await flushPromises(); noVictory(wrapper)
  expect(wrapper.get('[role="status"]').text()).toContain('结果未确认')
})
