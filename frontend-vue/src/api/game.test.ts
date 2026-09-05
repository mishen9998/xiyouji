import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi, gameApi, postJson } from './game'

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

describe('explicit authentication flow', () => {
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

  it('clears an expired token without silently creating a guest', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(401, { error: 'UNAUTHORIZED' }))

    await expect(postJson('/api/game/test-command', { action: 'move' }))
      .rejects.toMatchObject({ status: 401 })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const firstHeaders = requestHeaders(fetchMock, 0)
    expect(firstHeaders.get('X-Idempotency-Key')).toBeTruthy()
    expect(firstHeaders.get('Authorization')).toBe('Bearer expired-token')
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
  })

  it('keeps command metadata on an authenticated DELETE', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await expect(gameApi.deleteSession('session-1', 12)).resolves.toBeUndefined()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const firstHeaders = requestHeaders(fetchMock, 0)
    expect(firstHeaders.get('X-Idempotency-Key')).toBeTruthy()
    expect(firstHeaders.get('X-Expected-State-Version')).toBe('12')
  })

  it('register sends account, display username and password and stores the profile', async () => {
    localStorage.clear()
    fetchMock.mockResolvedValueOnce(jsonResponse(200, {
      token: 'player-token', account: 'pilgrim01', username: '取经人', role: 'PLAYER',
    }))

    await expect(authApi.register('pilgrim01', '取经人', 'secret123'))
      .resolves.toMatchObject({ account: 'pilgrim01', username: '取经人' })

    const request = fetchMock.mock.calls[0][1] as RequestInit
    expect(JSON.parse(String(request.body))).toEqual({
      account: 'pilgrim01', username: '取经人', password: 'secret123',
    })
    expect(localStorage.getItem(TOKEN_KEY)).toBe('player-token')
    expect(authApi.getProfile()).toEqual({
      account: 'pilgrim01', username: '取经人', role: 'PLAYER',
    })
  })
})
