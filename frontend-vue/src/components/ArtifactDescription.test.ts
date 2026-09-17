import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ArtifactDescription from './ArtifactDescription.vue'
import MiniCard from './MiniCard.vue'
import GameCard from './GameCard.vue'
import { artworkLore } from '@/constants/artwork'
import { CARD_IMG, RELIC_IMG } from '@/constants/images'
import copy from '@/constants/artwork-copy.json'
import type { Card } from '@/types'

const card: Card = { id: 1, name: '挥棒', type: 'ATTACK', cost: 1, damage: 6, block: 0, drawCards: 0, description: '造成6点伤害。', upgraded: false }

describe('新版卡牌与宝物介绍', () => {
  it('covers all 86 cards and 34 distinct relics, including the legacy staff alias', () => {
    expect(Object.keys(copy.cards)).toHaveLength(86)
    expect(Object.keys(copy.relics)).toHaveLength(34)
    for (const name of Object.keys(CARD_IMG)) expect(artworkLore('card', name), name).not.toBe('')
    for (const name of Object.keys(RELIC_IMG)) expect(artworkLore('relic', name), name).not.toBe('')
    expect(artworkLore('relic', '降魔宝杖')).toBe(artworkLore('relic', '降妖宝杖'))
  })

  it('keeps lore separate from authoritative effects, including upgraded/save-specific values', async () => {
    const wrapper = mount(ArtifactDescription, { props: { kind: 'card', name: card.name, effect: card.description } })
    expect(wrapper.get('.artifact-effect').text()).toBe('实战效果造成6点伤害。')
    expect(wrapper.get('.artifact-lore').text()).toContain(artworkLore('card', card.name))
    await wrapper.setProps({ effect: '造成9点伤害。' })
    expect(wrapper.get('.artifact-effect').text()).toBe('实战效果造成9点伤害。')
    expect(wrapper.text()).not.toContain('6点')
  })

  it('does not invent unknown lore or interpret text as HTML', () => {
    const wrapper = mount(ArtifactDescription, { props: { kind: 'relic', name: '旧存档的未知宝物', effect: '<script>不执行</script>' } })
    expect(wrapper.find('.artifact-lore').exists()).toBe(false)
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.text()).toContain('<script>不执行</script>')
    expect(artworkLore('card', '不存在')).toBe('')
  })

  it('shows complete effects in deck/reward cards and preserves keyboard selection guards', async () => {
    const wrapper = mount(MiniCard, { props: { card, clickable: true } })
    expect(wrapper.text()).toContain(card.description)
    expect(wrapper.text()).toContain(artworkLore('card', card.name))
    await wrapper.trigger('keydown.enter')
    expect(wrapper.emitted('click')).toHaveLength(1)
    await wrapper.setProps({ disabled: true })
    await wrapper.trigger('click')
    await wrapper.trigger('keydown.space')
    expect(wrapper.emitted('click')).toHaveLength(1)
  })

  it('does not insert another step into single-click combat or hide the effect behind lore', async () => {
    const wrapper = mount(GameCard, { props: { card, canPlay: true, index: 2 } })
    expect(wrapper.get('.card-desc').text()).toBe(card.description)
    expect(wrapper.find('.artifact-lore').exists()).toBe(false)
    await wrapper.trigger('click')
    expect(wrapper.emitted('play')).toEqual([[2]])
    await wrapper.setProps({ canPlay: false })
    await wrapper.trigger('click')
    expect(wrapper.emitted('play')).toHaveLength(1)
  })
})
