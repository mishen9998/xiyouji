import { expect, test } from '@playwright/test'

test('首页可以进入单人角色选择', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: '西行之路' })).toBeVisible()
  await page.getByRole('button', { name: /单人游戏/ }).click()

  await expect(page).toHaveURL(/\/char-select$/)
  await expect(page.getByRole('heading', { name: '选择你的角色' })).toBeVisible()
  await expect(page.getByRole('button', { name: /孙悟空/ })).toBeVisible()
})

test('游客选择角色后可以创建会话并进入地图', async ({ page }) => {
  await page.goto('/char-select')

  const character = page.getByRole('button', { name: /孙悟空/ })
  await character.click()
  await expect(character).toHaveAttribute('aria-pressed', 'true')

  const guestResponse = page.waitForResponse((response) =>
    response.url().includes('/api/auth/guest') && response.ok(),
  )
  const newGameResponse = page.waitForResponse((response) =>
    response.url().includes('/api/game/new') && response.ok(),
  )

  await page.getByRole('button', { name: '开始西行' }).click()
  await Promise.all([guestResponse, newGameResponse])

  await expect(page).toHaveURL(/\/map$/)
  await expect(page.getByRole('button', { name: /牌组/ })).toBeVisible()
  await expect.poll(() => page.evaluate(() => ({
    token: localStorage.getItem('xiyouji_jwt_token'),
    sessionId: localStorage.getItem('xiyouji_session_id'),
  }))).toEqual({
    token: expect.any(String),
    sessionId: expect.any(String),
  })
})
