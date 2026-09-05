import { expect, it } from 'vitest'
import { createCommandRetry } from './retryCommand'
it('reuses key after lost response and creates a new key after success', async () => {
  const retry = createCommandRetry()
  const keys: string[] = []
  await expect(retry('upgrade', async key => { keys.push(key); throw new TypeError('network') })).rejects.toThrow()
  await retry('upgrade', async key => { keys.push(key); return true })
  await retry('upgrade', async key => { keys.push(key); return true })
  expect(keys[0]).toBe(keys[1])
  expect(keys[2]).not.toBe(keys[1])
})
it('starts a new attempt after a version rejection', async () => {
  const retry = createCommandRetry()
  const keys: string[] = []
  await expect(retry('upgrade', async key => { keys.push(key); throw { status: 409 } })).rejects.toEqual({ status: 409 })
  await retry('upgrade', async key => { keys.push(key); return true })
  expect(keys[0]).not.toBe(keys[1])
})
