import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import BattleResultModal from './BattleResultModal.vue'
import EventModal from './EventModal.vue'
import { useGameStore } from '@/stores/game'
import type { BattleInfo, Card, MapNode, Player } from '@/types'

const cards = ['A', 'B', 'C', 'D', 'E'].map((name, i) => ({ id: i + 1, name, type: 'ATTACK', cost: 1, description: '', damage: 5, block: 0 })) as Card[]
function setup() {
  const store = useGameStore()
  store.battleInfo = { victory: true, battleOver: true } as BattleInfo
  store.rewards = { cardRewards: cards, resolved: false }
  store.currentNode = { type: 'BATTLE' } as MapNode
  return store
}
beforeEach(() => { setActivePinia(createPinia()); localStorage.clear() })

describe('battle reward confirmation', () => {
  it('allows changing selection and commits only the final card on continue', async () => {
    const store = setup()
    const claim = vi.spyOn(store, 'chooseCardReward').mockImplementation(async () => {
      store.rewards!.resolved = true
      return { success: true }
    })
    const wrapper = mount(BattleResultModal, { props: { visible: true } })
    expect(wrapper.get('.continue-btn').attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('.card-mini')).toHaveLength(5)
    await wrapper.findAll('.card-mini')[0].trigger('click')
    await wrapper.findAll('.card-mini')[1].trigger('click')
    expect(claim).not.toHaveBeenCalled()
    expect(wrapper.findAll('.card-mini')[1].classes()).toContain('selected')
    await wrapper.get('.continue-btn').trigger('click')
    await flushPromises()
    expect(claim).toHaveBeenCalledExactlyOnceWith(1)
    expect(wrapper.emitted('return-to-map')).toHaveLength(1)
  })
  it('keeps selection after failure and blocks duplicate submissions', async () => {
    const store = setup()
    let reject!: (error: Error) => void
    const claim = vi.spyOn(store, 'chooseCardReward').mockImplementation(() => new Promise((_, fail) => { reject = fail }))
    const wrapper = mount(BattleResultModal, { props: { visible: true } })
    await wrapper.findAll('.card-mini')[1].trigger('click')
    await wrapper.get('.continue-btn').trigger('click')
    await wrapper.get('.continue-btn').trigger('click')
    expect(claim).toHaveBeenCalledTimes(1)
    reject(new Error('offline'))
    await flushPromises()
    expect(wrapper.emitted('return-to-map')).toBeUndefined()
    expect(wrapper.findAll('.card-mini')[1].classes()).toContain('selected')
    expect(wrapper.get('.continue-btn').attributes('disabled')).toBeUndefined()
  })
  it('retries boss progression without claiming twice', async () => {
    const store = setup()
    store.currentNode = { type: 'BOSS' } as MapNode
    const claim = vi.spyOn(store, 'chooseCardReward').mockImplementation(async () => {
      store.rewards!.resolved = true
      return { success: true }
    })
    const advance = vi.spyOn(store, 'nextLayer').mockRejectedValueOnce(new Error('offline')).mockResolvedValue({ success: true, currentLayer: 2 })
    const wrapper = mount(BattleResultModal, { props: { visible: true } })
    await wrapper.findAll('.card-mini')[0].trigger('click')
    await wrapper.get('.continue-btn').trigger('click'); await flushPromises()
    expect(wrapper.emitted('next-layer')).toBeUndefined()
    await wrapper.get('.continue-btn').trigger('click'); await flushPromises()
    expect(claim).toHaveBeenCalledTimes(1)
    expect(advance).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('next-layer')).toHaveLength(1)
  })
  it('explicit skip never claims a card', async () => {
    const store = setup()
    const claim = vi.spyOn(store, 'chooseCardReward')
    const skip = vi.spyOn(store, 'skipReward').mockResolvedValue({ success: true })
    const wrapper = mount(BattleResultModal, { props: { visible: true } })
    await wrapper.get('.btn-small').trigger('click'); await flushPromises()
    expect(skip).toHaveBeenCalledTimes(1)
    expect(claim).not.toHaveBeenCalled()
    expect(wrapper.emitted('return-to-map')).toHaveLength(1)
  })
})

describe('event confirmation', () => {
  it('emperor choice can change before confirmation and backdrop cannot discard it', async () => {
    const store = setup()
    const event = vi.spyOn(store, 'handleEvent').mockImplementation(async (action, params) => action === 'view'
      ? { choices: [{ name: 'A', description: '' }, { name: 'B', description: '' }] } : { relic: { name: params!.relicName!, description: '' } })
    const wrapper = mount(EventModal, { props: { visible: false, eventType: 'emperor' } })
    await wrapper.setProps({ visible: true }); await flushPromises()
    expect(wrapper.get('.btn-primary').attributes('disabled')).toBeDefined()
    await wrapper.findAll('.emperor-relic-card')[0].trigger('click')
    await wrapper.findAll('.emperor-relic-card')[1].trigger('click')
    await wrapper.get('.modal-overlay').trigger('click')
    expect(event).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('close')).toBeUndefined()
    await wrapper.get('.btn-primary').trigger('click'); await flushPromises()
    expect(event).toHaveBeenLastCalledWith('choose', { relicName: 'B' })
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
  it('upgrades only after confirmation and allows another upgrade', async () => {
    const store = setup()
    store.player = { deck: cards } as Player
    store.bonfireUpgradesLeft = 2
    vi.spyOn(store, 'refreshState').mockResolvedValue(undefined)
    const upgrade = vi.spyOn(store, 'upgradeCard').mockImplementation(async () => { store.bonfireUpgradesLeft--; return { success: true } })
    const wrapper = mount(EventModal, { props: { visible: false, eventType: 'bonfire' } })
    await wrapper.setProps({ visible: true }); await flushPromises()
    await wrapper.findAll('.card-mini')[0].trigger('click')
    await wrapper.findAll('.card-mini')[1].trigger('click')
    expect(upgrade).not.toHaveBeenCalled()
    await wrapper.get('.btn-primary').trigger('click'); await flushPromises()
    expect(upgrade).toHaveBeenCalledExactlyOnceWith(1)
    expect(wrapper.emitted('close')).toBeUndefined()
    await wrapper.findAll('.card-mini')[2].trigger('click')
    await wrapper.get('.btn-primary').trigger('click'); await flushPromises()
    expect(upgrade).toHaveBeenLastCalledWith(2)
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
