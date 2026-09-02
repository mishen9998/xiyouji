import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { gameApi, postJson } from './game'

const TOKEN_KEY = 'xiyouji_jwt_token'

function jsonResponse(status: number, payload: unknown): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function requestHeaders(fetchMock: ReturnType<typeof vi.fn>, callIndex: number): Headers {
  const init = fetchMock.mock.calls[callIndex][1] as RequestInit
  return new Headers(init.headers)
}

describe('authenticated command retries', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem(TOKEN_KEY, 'expired-token')
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('reuses the generated idempotency key when POST retries after 401', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { error: 'UNAUTHORIZED' }))
      .mockResolvedValueOnce(jsonResponse(200, {
        token: 'fresh-token', username: 'guest', role: 'USER',
      }))
      .mockResolvedValueOnce(jsonResponse(200, { ok: true }))

    await expect(postJson('/api/game/test-command', { action: 'move' }))
      .resolves.toEqual({ ok: true })

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[1][0]).toBe('/api/auth/guest')

    const firstHeaders = requestHeaders(fetchMock, 0)
    const retryHeaders = requestHeaders(fetchMock, 2)
    const firstKey = firstHeaders.get('X-Idempotency-Key')

    expect(firstKey).toBeTruthy()
    expect(retryHeaders.get('X-Idempotency-Key')).toBe(firstKey)
    expect(firstHeaders.get('Authorization')).toBe('Bearer expired-token')
    expect(retryHeaders.get('Authorization')).toBe('Bearer fresh-token')
  })

  it('reuses the generated idempotency key and version when DELETE retries after 403', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(403, { error: 'FORBIDDEN' }))
      .mockResolvedValueOnce(jsonResponse(200, {
        token: 'fresh-token', username: 'guest', role: 'USER',
      }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))

    await expect(gameApi.deleteSession('session-1', 12)).resolves.toBeUndefined()

    expect(fetchMock).toHaveBeenCalledTimes(3)
    const firstHeaders = requestHeaders(fetchMock, 0)
    const retryHeaders = requestHeaders(fetchMock, 2)

    expect(retryHeaders.get('X-Idempotency-Key'))
      .toBe(firstHeaders.get('X-Idempotency-Key'))
    expect(retryHeaders.get('X-Expected-State-Version')).toBe('12')
  })
})
