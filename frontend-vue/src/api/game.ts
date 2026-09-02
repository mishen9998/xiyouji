// ====== 游戏 API 封装 ======
import type {
  NewGameResponse, GameState, MoveResponse, EventResponse,
  BattleInfo, CardRewardChooseResponse, CharacterClass
} from '@/types'

const API = '/api/game'
const AUTH_API = '/api/auth'

export interface CommandOptions {
  expectedStateVersion?: number
  idempotencyKey?: string
}

interface ResolvedCommandOptions extends CommandOptions {
  idempotencyKey: string
}

export class ApiError extends Error {
  readonly status: number
  readonly code?: string
  readonly details?: Record<string, unknown>

  constructor(status: number, payload: any) {
    super(payload?.message || payload?.error || `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    // Spring's ErrorResponse serializes the business code as `error`, while
    // a few gateways use `code`; accept both so conflict/idempotency handling
    // remains stable across deployments.
    this.code = payload?.code || payload?.error
    this.details = payload?.details
  }
}

function createIdempotencyKey(): string {
  try { return crypto.randomUUID() } catch {
    return `${Date.now()}-${Math.random().toString(36).slice(2)}`
  }
}

function resolveCommandOptions(options: CommandOptions): ResolvedCommandOptions {
  return {
    ...options,
    idempotencyKey: options.idempotencyKey || createIdempotencyKey(),
  }
}

function commandHeaders(base: HeadersInit, options: ResolvedCommandOptions): Headers {
  const headers = new Headers(base)
  headers.set('X-Idempotency-Key', options.idempotencyKey)
  if (options.expectedStateVersion !== undefined && options.expectedStateVersion !== null) {
    headers.set('X-Expected-State-Version', String(options.expectedStateVersion))
  }
  return headers
}

async function parseFailure(res: Response): Promise<never> {
  const payload = await res.json().catch(() => ({}))
  throw new ApiError(res.status, payload)
}

// ====== JWT Token 管理 ======
const TOKEN_KEY = 'xiyouji_jwt_token'

function getToken(): string | null {
  try { return localStorage.getItem(TOKEN_KEY) } catch { return null }
}

function setToken(token: string) {
  try { localStorage.setItem(TOKEN_KEY, token) } catch {}
}

function clearToken() {
  try { localStorage.removeItem(TOKEN_KEY) } catch {}
}

function isAuthFailure(status: number): boolean {
  // Spring Security may return 403 when a cached JWT cannot be parsed or has
  // been signed with a previous deployment secret. Treat it like 401 once so
  // the browser can obtain a fresh guest token instead of getting stuck.
  return status === 401 || status === 403
}

/** 获取游客 Token（如果没有则自动登录） */
async function ensureToken(): Promise<string> {
  const cached = getToken()
  if (cached) return cached

  // 自动游客登录
  const res = await fetch(`${AUTH_API}/guest`, {
    method: 'POST', headers: { 'X-Idempotency-Key': createIdempotencyKey() }
  })
  if (!res.ok) throw new Error('游客登录失败')
  const data = await res.json()
  const token: string = data.token
  if (token) setToken(token)
  return token
}

/** 带 Token 的请求头 */
export async function authHeaders(): Promise<HeadersInit> {
  const token = await ensureToken()
  return {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`
  }
}

// ====== 核心 HTTP 方法 ======
export async function postJson(url: string, body?: unknown, options: CommandOptions = {}): Promise<any> {
  // A retry after refreshing an expired token is still the same logical
  // command. Resolve the key once so the server can replay the first result
  // instead of executing the mutation twice.
  const commandOptions = resolveCommandOptions(options)
  const headers = commandHeaders(await authHeaders(), commandOptions)
  const res = await fetch(url, {
    method: 'POST',
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })

  // 401/403 时清除缓存 Token 并重试一次
  if (isAuthFailure(res.status)) {
    clearToken()
    const retryHeaders = commandHeaders(await authHeaders(), commandOptions)
    const retryRes = await fetch(url, {
      method: 'POST',
      headers: retryHeaders,
      body: body ? JSON.stringify(body) : undefined,
    })
    if (!retryRes.ok) {
      return parseFailure(retryRes)
    }
    return retryRes.json()
  }

  if (!res.ok) {
    return parseFailure(res)
  }
  return res.json()
}

export async function getJson(url: string): Promise<any> {
  const headers = await authHeaders()
  const res = await fetch(url, { headers })

  if (isAuthFailure(res.status)) {
    clearToken()
    const retryHeaders = await authHeaders()
    const retryRes = await fetch(url, { headers: retryHeaders })
    if (!retryRes.ok) {
      const err = await retryRes.json().catch(() => ({}))
      throw new Error(err.message || err.error || `HTTP ${retryRes.status}`)
    }
    return retryRes.json()
  }

  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || err.error || `HTTP ${res.status}`)
  }
  return res.json()
}

async function deleteJson(url: string, options: CommandOptions = {}): Promise<any> {
  const commandOptions = resolveCommandOptions(options)
  const headers = commandHeaders(await authHeaders(), commandOptions)
  const res = await fetch(url, { method: 'DELETE', headers })

  if (isAuthFailure(res.status)) {
    clearToken()
    const retryHeaders = commandHeaders(await authHeaders(), commandOptions)
    const retryRes = await fetch(url, { method: 'DELETE', headers: retryHeaders })
    if (!retryRes.ok) {
      return parseFailure(retryRes)
    }
    return
  }

  if (!res.ok) {
    return parseFailure(res)
  }
}

// ====== 认证 API ======
export const authApi = {
  async guestLogin(): Promise<{ token: string; username: string; role: string }> {
    const res = await fetch(`${AUTH_API}/guest`, {
      method: 'POST', headers: { 'X-Idempotency-Key': createIdempotencyKey() }
    })
    if (!res.ok) throw new Error('游客登录失败')
    const data = await res.json()
    if (data.token) setToken(data.token)
    return data
  },

  async login(username: string, password: string): Promise<{ token: string; username: string; role: string }> {
    const res = await fetch(`${AUTH_API}/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Idempotency-Key': createIdempotencyKey() },
      body: JSON.stringify({ username, password })
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || err.error || '登录失败')
    }
    const data = await res.json()
    if (data.token) setToken(data.token)
    return data
  },

  async register(username: string, password: string): Promise<{ token: string; username: string; role: string }> {
    const res = await fetch(`${AUTH_API}/register`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        // Registration is a resource-creation command. Reusing this key after
        // a network retry returns the original token instead of creating a
        // second account or surfacing a duplicate-user error.
        'X-Idempotency-Key': createIdempotencyKey(),
      },
      body: JSON.stringify({ username, password })
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || err.error || '注册失败')
    }
    const data = await res.json()
    if (data.token) setToken(data.token)
    return data
  },

  getToken,
  clearToken,
}

// ====== 游戏 API ======
export const gameApi = {
  newGame(characterClass: CharacterClass): Promise<NewGameResponse> {
    return postJson(`${API}/new`, { characterClass })
  },

  getState(sessionId: string): Promise<GameState> {
    return getJson(`${API}/state/${sessionId}`)
  },

  deleteSession(sessionId: string, expectedStateVersion: number, idempotencyKey?: string): Promise<void> {
    return deleteJson(`${API}/sessions/${sessionId}`, { expectedStateVersion, idempotencyKey })
  },

  move(sessionId: string, nodeId: string, expectedStateVersion: number, idempotencyKey?: string): Promise<MoveResponse> {
    return postJson(`${API}/move/${sessionId}`, { nodeId }, { expectedStateVersion, idempotencyKey })
  },

  nextLayer(sessionId: string, expectedStateVersion: number, idempotencyKey?: string): Promise<EventResponse> {
    return postJson(`${API}/next-layer/${sessionId}`, undefined, { expectedStateVersion, idempotencyKey })
  },

  handleEvent(sessionId: string, action: string, params?: {
    cardIndex?: number; cardId?: number; price?: number; relicName?: string
  }, expectedStateVersion = 0, idempotencyKey?: string): Promise<EventResponse> {
    return postJson(`${API}/event/${sessionId}`, { action, ...params }, { expectedStateVersion, idempotencyKey })
  },

  removeCard(sessionId: string, index: number, expectedStateVersion: number, idempotencyKey?: string): Promise<any> {
    return postJson(`${API}/deck/remove/${sessionId}`, { index }, { expectedStateVersion, idempotencyKey })
  },

  startBattle(sessionId: string, expectedStateVersion: number, idempotencyKey?: string): Promise<BattleInfo> {
    return postJson(`${API}/battle/start/${sessionId}`, undefined, { expectedStateVersion, idempotencyKey })
  },

  playCard(sessionId: string, handIndex: number, expectedStateVersion: number, idempotencyKey?: string): Promise<BattleInfo> {
    return postJson(`${API}/battle/play/${sessionId}`, { handIndex }, { expectedStateVersion, idempotencyKey })
  },

  endTurn(sessionId: string, expectedStateVersion: number, idempotencyKey?: string): Promise<BattleInfo> {
    return postJson(`${API}/battle/endturn/${sessionId}`, undefined, { expectedStateVersion, idempotencyKey })
  },

  getBattleState(sessionId: string): Promise<BattleInfo> {
    return getJson(`${API}/battle/state/${sessionId}`)
  },

  chooseCardReward(sessionId: string, cardIndex: number, expectedStateVersion: number, idempotencyKey?: string): Promise<CardRewardChooseResponse> {
    return postJson(`${API}/reward/choose/${sessionId}`, { cardIndex }, { expectedStateVersion, idempotencyKey })
  },

  skipReward(sessionId: string, expectedStateVersion: number, idempotencyKey?: string): Promise<any> {
    return postJson(`${API}/reward/skip/${sessionId}`, undefined, { expectedStateVersion, idempotencyKey })
  },
}
