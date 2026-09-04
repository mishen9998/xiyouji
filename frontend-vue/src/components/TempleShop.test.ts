import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TempleShop from './TempleShop.vue'
import type { Card } from '@/types'

const cards: Card[] = [
  {
    id: 101,
    name: '定海神针',
    type: 'ATTACK',
    cost: 2,
    damage: 12,
    block: 0,
    drawCards: 0,
    description: '造成12点伤害，获得2点力量。',
    upgraded: false,
  },
  {
    id: 102,
    name: '筋斗云',
    type: 'DEFENSE',
    cost: 1,
    damage: 0,
    block: 10,
    drawCards: 1,
    description: '获得10点格挡，下回合多抽1张牌。',
    upgraded: false,
  },
]

describe('TempleShop', () => {
  it('展示卡面数值，并支持查看、购买、返回商店和继续前进', async () => {
    const wrapper = mount(TempleShop, {
      props: {
        cards,
        gold: 120,
        boughtIndices: new Set<number>(),
        price: 50,
      },
    })

    expect(wrapper.get('[data-testid="temple-shop"]').text()).toContain('土地庙')
    expect(wrapper.get('[data-testid="temple-shop-grid"]').text()).toContain('费用 2')
    expect(wrapper.get('[data-testid="temple-shop-grid"]').text()).toContain('伤害 12')

    await wrapper.get('[aria-label="查看卡牌 定海神针"]').trigger('click')
    const detail = wrapper.get('[data-testid="temple-card-detail"]')
    expect(detail.text()).toContain('定海神针')
    expect(detail.text()).toContain('造成12点伤害，获得2点力量。')

    await detail.get('.purchase-button').trigger('click')
    expect(wrapper.emitted('buy')).toEqual([[cards[0], 0]])

    await wrapper.setProps({ boughtIndices: new Set([0]) })
    expect(detail.get('.purchase-button').text()).toBe('已收入牌组')
    expect(detail.get('.purchase-button').attributes('disabled')).toBeDefined()

    await detail.get('.back-link').trigger('click')
    expect(wrapper.find('[data-testid="temple-card-detail"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="temple-shop-grid"]').text()).toContain('筋斗云')

    await wrapper.get('[data-testid="temple-forward"]').trigger('click')
    expect(wrapper.emitted('forward')).toHaveLength(1)
  })

  it('金币不足时禁止购买', async () => {
    const wrapper = mount(TempleShop, {
      props: {
        cards: [cards[0]],
        gold: 40,
        boughtIndices: new Set<number>(),
        price: 50,
      },
    })

    await wrapper.get('[aria-label="查看卡牌 定海神针"]').trigger('click')
    const button = wrapper.get('.purchase-button')
    expect(button.text()).toBe('香火钱不足')
    expect(button.attributes('disabled')).toBeDefined()
    await button.trigger('click')
    expect(wrapper.emitted('buy')).toBeUndefined()
  })
})
