import { expect, test } from '@playwright/test'

async function enterGuest(page: import('@playwright/test').Page) {
  await page.goto('/')
  const guestResponse = page.waitForResponse((response) =>
    response.url().includes('/api/auth/guest') && response.ok(),
  )
  await page.getByRole('button', { name: /游客模式/ }).click()
  await guestResponse
  await expect(page).toHaveURL(/\/menu$/)
}

test('身份页提供登录、注册三个字段并提交注册信息', async ({ page }) => {
  await page.route('**/api/auth/register', async route => {
    expect(route.request().postDataJSON()).toEqual({
      account: 'pilgrim01',
      username: '取经人',
      password: 'secret123',
    })
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        token: 'register-token', account: 'pilgrim01', username: '取经人', role: 'PLAYER',
      }),
    })
  })

  await page.goto('/')
  const account = page.getByLabel('登录账号')
  const username = page.getByLabel('显示用户名')
  const password = page.getByLabel('密码')
  await expect(account).toBeVisible()
  await expect(username).toBeDisabled()
  await expect(password).toBeVisible()

  await page.getByRole('tab', { name: '注册' }).click()
  await expect(username).toBeEnabled()
  await account.fill('pilgrim01')
  await username.fill('取经人')
  await password.fill('secret123')
  await page.getByRole('button', { name: '创建账号并进入' }).click()

  await expect(page).toHaveURL(/\/menu$/)
  await expect(page.getByText('取经人', { exact: true })).toBeVisible()
})

test('首页可以进入单人角色选择', async ({ page }) => {
  await enterGuest(page)

  await expect(page.getByRole('heading', { name: '西行之路' })).toBeVisible()
  await page.getByRole('button', { name: /单人游戏/ }).click()

  await expect(page).toHaveURL(/\/char-select$/)
  await expect(page.getByRole('heading', { name: '选择你的角色' })).toBeVisible()
  await expect(page.getByRole('button', { name: /孙悟空/ })).toBeVisible()
})

test('游客选择角色后可以创建会话并进入地图', async ({ page }) => {
  await enterGuest(page)
  await page.getByRole('button', { name: /单人游戏/ }).click()

  const character = page.getByRole('button', { name: /孙悟空/ })
  await character.click()
  await expect(character).toHaveAttribute('aria-pressed', 'true')

  const newGameResponse = page.waitForResponse((response) =>
    response.url().includes('/api/game/new') && response.ok(),
  )

  await page.getByRole('button', { name: '开始西行' }).click()
  await newGameResponse

  await expect(page).toHaveURL(/\/map$/)
  await expect(page.getByRole('button', { name: /牌组/ })).toBeVisible()
  await expect.poll(() => page.evaluate(() => ({
    token: localStorage.getItem('xiyouji_jwt_token'),
    sessionId: Object.keys(localStorage)
      .filter(key => key.startsWith('xiyouji_session_id:GUEST:'))
      .map(key => localStorage.getItem(key))[0] || null,
  }))).toEqual({
    token: expect.any(String),
    sessionId: expect.any(String),
  })
})

test('土地庙支持查看卡牌、返回商店、购买并继续前进', async ({ page }) => {
  const card = {
    id: 101,
    name: '定海神针',
    type: 'ATTACK',
    cost: 2,
    damage: 12,
    block: 0,
    drawCards: 0,
    description: '造成12点伤害，获得2点力量。',
    upgraded: false,
  }
  const shopCards = [
    card,
    { ...card, id: 102, name: '筋斗云', type: 'DEFENSE', cost: 1, damage: 0, block: 10, drawCards: 1, description: '获得10点格挡，下回合多抽1张牌。' },
    { ...card, id: 103, name: '火眼金睛', cost: 2, damage: 3, description: '造成3点伤害，施加1层脆弱。' },
    { ...card, id: 104, name: '大闹天宫', cost: 2, damage: 15, description: '造成15点伤害。消耗。' },
    { ...card, id: 105, name: '七十二变', type: 'SKILL', cost: 1, damage: 0, drawCards: 1, description: '施加2层虚弱，抽1张牌。' },
  ]
  const player = {
    characterClass: 'SUN_WUKONG',
    displayName: '孙悟空',
    hp: 80,
    maxHp: 80,
    block: 0,
    energy: 3,
    maxEnergy: 3,
    gold: 120,
    deckSize: 10,
    floor: 1,
    deck: [],
    relics: [],
  }
  const startNode = {
    id: 'L1-R0-C1', row: 0, col: 1, type: 'EMPEROR', name: '大唐皇宫',
    visited: true, accessible: false, connections: ['L1-R1-C1'],
  }
  const shopNode = {
    id: 'L1-R1-C1', row: 1, col: 1, type: 'SHOP', name: '土地庙',
    visited: false, accessible: true, connections: [],
  }

  await page.addInitScript(() => {
    localStorage.setItem('xiyouji_jwt_token', 'e2e-token')
    localStorage.setItem('xiyouji_session_id', 'e2e-shop')
  })
  await page.route('**/api/game/state/e2e-shop', async route => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        sessionId: 'e2e-shop', stateVersion: 1, player,
        map: [startNode, shopNode], currentNode: startNode,
        inBattle: false, currentLayer: 1, maxLayer: 3,
      }),
    })
  })
  await page.route('**/api/game/move/e2e-shop', async route => {
    await route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ node: shopNode, eventType: 'shop', stateVersion: 2 }),
    })
  })
  await page.route('**/api/game/event/e2e-shop', async route => {
    const request = route.request().postDataJSON()
    const response = request.action === 'buy'
      ? { stateVersion: 4, bought: true, player: { ...player, gold: 70, deck: [card], deckSize: 11 } }
      : { stateVersion: 3, shopCards }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify(response) })
  })

  await page.goto('/map')
  await page.getByText('土地庙', { exact: true }).click()
  await expect(page.getByTestId('temple-shop')).toBeVisible()
  await expect(page.locator('.gold-pouch')).toContainText('120')
  if (process.env.CAPTURE_SHOWCASE) {
    await expect(page.locator('#server-loading')).toBeHidden()
    await page.screenshot({ path: '../docs/assets/demo-temple-shop.png' })
  }

  await page.getByRole('button', { name: '查看卡牌 定海神针' }).click()
  await expect(page.getByTestId('temple-card-detail')).toContainText('伤害 12')
  await page.getByRole('button', { name: '返回商店' }).click()
  await expect(page.getByTestId('temple-shop-grid')).toBeVisible()

  await page.getByRole('button', { name: '查看卡牌 定海神针' }).click()
  await page.getByRole('button', { name: '供奉 50 金币购买' }).click()
  await expect(page.getByRole('button', { name: '已收入牌组' })).toBeDisabled()
  await page.getByRole('button', { name: '返回商店' }).click()

  await page.getByTestId('temple-forward').click()
  await expect(page.getByTestId('temple-shop')).toBeHidden()
})
