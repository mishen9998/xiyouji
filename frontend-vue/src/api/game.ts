// ====== 游戏 API 封装 ======
import type {
  NewGameResponse, GameState, MoveResponse, EventResponse,
  BattleInfo, CardRewardChooseResponse, CharacterClass
} from '@/types'

const API = '/api/game'
const AUTH_API = '/api/auth'

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

/** 获取游客 Token（如果没有则自动登录） */
async function ensureToken(): Promise<string> {
  const cached = getToken()
  if (cached) return cached

  // 自动游客登录
  const res = await fetch(`${AUTH_API}/guest`, { method: 'POST' })
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
export async function postJson(url: string, body?: unknown): Promise<any> {
  const headers = await authHeaders()
  const res = await fetch(url, {
    method: 'POST',
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })

  // 401 时清除 Token 并重试一次
  if (res.status === 401) {
    clearToken()
    const retryHeaders = await authHeaders()
    const retryRes = await fetch(url, {
      method: 'POST',
      headers: retryHeaders,
      body: body ? JSON.stringify(body) : undefined,
    })
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

export async function getJson(url: string): Promise<any> {
  const headers = await authHeaders()
  const res = await fetch(url, { headers })

  if (res.status === 401) {
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

async function deleteJson(url: string): Promise<void> {
  const headers = await authHeaders()
  const res = await fetch(url, { method: 'DELETE', headers })

  if (res.status === 401) {
    clearToken()
    const retryHeaders = await authHeaders()
    const retryRes = await fetch(url, { method: 'DELETE', headers: retryHeaders })
    if (!retryRes.ok) {
      const err = await retryRes.json().catch(() => ({}))
      throw new Error(err.message || err.error || `HTTP ${retryRes.status}`)
    }
    return
  }

  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || err.error || `HTTP ${res.status}`)
  }
}

// ====== 认证 API ======
export const authApi = {
  async guestLogin(): Promise<{ token: string; username: string; role: string }> {
    const res = await fetch(`${AUTH_API}/guest`, { method: 'POST' })
    if (!res.ok) throw new Error('游客登录失败')
    const data = await res.json()
    if (data.token) setToken(data.token)
    return data
  },

  async login(username: string, password: string): Promise<{ token: string; username: string; role: string }> {
    const res = await fetch(`${AUTH_API}/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
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
      headers: { 'Content-Type': 'application/json' },
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

  deleteSession(sessionId: string): Promise<void> {
    return deleteJson(`${API}/sessions/${sessionId}`)
  },

  move(sessionId: string, nodeId: string): Promise<MoveResponse> {
    return postJson(`${API}/move/${sessionId}`, { nodeId })
  },

  nextLayer(sessionId: string): Promise<EventResponse> {
    return postJson(`${API}/next-layer/${sessionId}`)
  },

  handleEvent(sessionId: string, action: string, params?: {
    cardIndex?: number; cardId?: number; price?: number; relicName?: string; relicId?: number
  }): Promise<EventResponse> {
    return postJson(`${API}/event/${sessionId}`, { action, ...params })
  },

  removeCard(sessionId: string, index: number): Promise<any> {
    return postJson(`${API}/deck/remove/${sessionId}`, { index })
  },

  startBattle(sessionId: string): Promise<BattleInfo> {
    return postJson(`${API}/battle/start/${sessionId}`)
  },

  playCard(sessionId: string, handIndex: number): Promise<BattleInfo> {
    return postJson(`${API}/battle/play/${sessionId}`, { handIndex })
  },

  endTurn(sessionId: string): Promise<BattleInfo> {
    return postJson(`${API}/battle/endturn/${sessionId}`)
  },

  getBattleState(sessionId: string): Promise<BattleInfo> {
    return getJson(`${API}/battle/state/${sessionId}`)
  },

  chooseCardReward(sessionId: string, cardIndex: number): Promise<CardRewardChooseResponse> {
    return postJson(`${API}/reward/choose/${sessionId}`, { cardIndex })
  },

  skipReward(sessionId: string): Promise<any> {
    return postJson(`${API}/reward/skip/${sessionId}`)
  },
}
