import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi, gameApi, getJson, postJson } from './game'

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

  it('preserves a valid identity on a real permission denial', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(403, { error: 'ACCESS_DENIED' }))
    await expect(postJson('/api/game/test-command', { action: 'move' })).rejects.toMatchObject({ status: 403 })
    expect(localStorage.getItem(TOKEN_KEY)).toBe('expired-token')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('announces expired authentication without removing saves or other remembered data', async () => {
    const onExpired = vi.fn()
    window.addEventListener('xiyouji-auth-expired', onExpired)
    localStorage.setItem('xiyouji_guest_jwt_token', 'expired-token')
    localStorage.setItem('xiyouji_guest_auth_profile', '{"username":"old-guest"}')
    localStorage.setItem('xiyouji_session_id:GUEST:old-guest', 'saved-journey')
    fetchMock.mockResolvedValueOnce(jsonResponse(401, { error: 'UNAUTHORIZED' }))
    await expect(getJson('/api/game/state/saved-journey')).rejects.toMatchObject({ status: 401 })
    expect(onExpired).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem('xiyouji_guest_jwt_token')).toBeNull()
    expect(localStorage.getItem('xiyouji_session_id:GUEST:old-guest')).toBe('saved-journey')
    window.removeEventListener('xiyouji-auth-expired', onExpired)
  })

  it('does not let a late unauthorized response clear a newer identity', async () => {
    let resolve!: (response: Response) => void
    fetchMock.mockImplementationOnce(() => new Promise<Response>(done => { resolve = done }))
    const request = getJson('/api/game/state/previous-journey')
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1))
    localStorage.setItem(TOKEN_KEY, 'new-login-token')
    resolve(jsonResponse(401, { error: 'UNAUTHORIZED' }))
    await expect(request).rejects.toMatchObject({ status: 401 })
    expect(localStorage.getItem(TOKEN_KEY)).toBe('new-login-token')
  })

  function rememberGuest() {
    localStorage.setItem('xiyouji_guest_jwt_token', 'remembered-token')
    localStorage.setItem('xiyouji_guest_auth_profile', JSON.stringify({ account: 'guest1', username: 'guest1', role: 'GUEST' }))
  }

  it('validates and reuses a remembered guest instead of creating another identity', async () => {
    rememberGuest()
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))
    await expect(authApi.guestLogin()).resolves.toMatchObject({ token: 'remembered-token', username: 'guest1' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/session')
    expect(requestHeaders(fetchMock, 0).get('Authorization')).toBe('Bearer remembered-token')
  })

  it('replaces an invalid remembered guest only on an explicit guest login', async () => {
    rememberGuest()
    fetchMock.mockResolvedValueOnce(jsonResponse(401, { error: 'UNAUTHORIZED' }))
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { token: 'new-guest-token', account: 'guest2', username: 'guest2', role: 'GUEST' }))
    await expect(authApi.guestLogin()).resolves.toMatchObject({ token: 'new-guest-token' })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[1][0]).toBe('/api/auth/guest')
    expect(localStorage.getItem('xiyouji_guest_jwt_token')).toBe('new-guest-token')
  })

  it('does not replace a remembered guest on network/server failure', async () => {
    rememberGuest()
    fetchMock.mockResolvedValueOnce(jsonResponse(503, { message: '服务暂不可用' }))
    await expect(authApi.guestLogin()).rejects.toMatchObject({ status: 503 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem('xiyouji_guest_jwt_token')).toBe('remembered-token')
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
