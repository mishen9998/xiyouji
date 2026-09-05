import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount, flushPromises } from '@vue/test-utils'
import { useRoomStore } from '@/stores/room'
import MultiplayerBattleView from './MultiplayerBattleView.vue'
import type { MultiplayerBattleInfo, RoomDTO } from '@/types'

const { push } = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { code: 'TEST' } }), useRouter: () => ({ push }) }))
beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.setItem('xiyouji_jwt_token', 'x.' + btoa(JSON.stringify({ sub: 'me' })) + '.x')
})
function setup() {
  const store = useRoomStore()
  store.room = { code: 'TEST', hostUserId: 'me', stateVersion: 1, players: [], status: 'IN_BATTLE' } as unknown as RoomDTO
  store.battleInfo = {
    roomCode: 'TEST', stateVersion: 1, enemy: { name: 'test', hp: 0, maxHp: 1, buffs: {} },
    players: [], combatLog: [], rewardsPhase: true, victory: true, battleOver: true,
    rewards: { me: ['A', 'B', 'C', 'D', 'E'].map(name => ({ name, cost: 1, description: '' })) },
    claimedRewards: {}, rewardsHandled: false,
  } as unknown as MultiplayerBattleInfo
  vi.spyOn(store, 'refreshBattleState').mockResolvedValue(undefined)
  return store
}
it('changes selection locally, then waits for everyone after confirmation', async () => {
  const store = setup()
  const claim = vi.spyOn(store, 'claimReward').mockImplementation(async name => { store.battleInfo!.claimedRewards!.me = name })
  const wrapper = mount(MultiplayerBattleView)
  await flushPromises()
  expect(wrapper.findAll('.reward-card')).toHaveLength(5)
  await wrapper.findAll('.reward-card')[0].trigger('click')
  await wrapper.findAll('.reward-card')[1].trigger('click')
  expect(claim).not.toHaveBeenCalled()
  await wrapper.get('.reward-actions .btn-primary').trigger('click'); await flushPromises()
  expect(claim).toHaveBeenCalledExactlyOnceWith('B')
  expect(wrapper.get('.claimed-status').text()).toContain('B')
  expect(wrapper.find('.btn-next-floor').exists()).toBe(false)
})
it('explicit skip is displayed as handled and enables host after everyone finishes', async () => {
  const store = setup()
  const skip = vi.spyOn(store, 'skipReward').mockImplementation(async () => {
    store.battleInfo!.claimedRewards!.me = '__SKIPPED__'
    store.battleInfo!.rewardsHandled = true
  })
  const wrapper = mount(MultiplayerBattleView); await flushPromises()
  await wrapper.get('.reward-actions .btn-small').trigger('click'); await flushPromises()
  expect(skip).toHaveBeenCalledTimes(1)
  expect(wrapper.get('.claimed-status').text()).toContain('已跳过奖励')
  expect(wrapper.find('.btn-next-floor').exists()).toBe(true)
  vi.spyOn(store, 'nextFloor').mockResolvedValue({ completed: true })
  await wrapper.get('.btn-next-floor').trigger('click'); await flushPromises()
  expect(push).toHaveBeenCalledWith('/menu')
})
it('failed confirmation keeps selection and allows retry', async () => {
  const store = setup()
  vi.spyOn(store, 'claimReward').mockRejectedValue(new Error('offline'))
  const wrapper = mount(MultiplayerBattleView); await flushPromises()
  await wrapper.findAll('.reward-card')[1].trigger('click')
  await wrapper.get('.reward-actions .btn-primary').trigger('click'); await flushPromises()
  expect(wrapper.findAll('.reward-card')[1].classes()).toContain('selected')
  expect(wrapper.find('.claimed-status').exists()).toBe(false)
  expect(wrapper.get('.reward-actions .btn-primary').attributes('disabled')).toBeUndefined()
})
