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
const AUTH_PROFILE_KEY = 'xiyouji_auth_profile'
const GUEST_TOKEN_KEY = 'xiyouji_guest_jwt_token'
const GUEST_PROFILE_KEY = 'xiyouji_guest_auth_profile'

export interface AuthProfile {
  account: string
  username: string
  role: string
}

export interface AuthResult extends AuthProfile {
  token: string
}

function getToken(): string | null {
  try { return localStorage.getItem(TOKEN_KEY) } catch { return null }
}

function getProfile(): AuthProfile | null {
  try {
    const raw = localStorage.getItem(AUTH_PROFILE_KEY)
    return raw ? JSON.parse(raw) as AuthProfile : null
  } catch { return null }
}

function setAuth(result: AuthResult, rememberGuest = false) {
  const profile: AuthProfile = {
    account: result.account || result.username,
    username: result.username,
    role: result.role,
  }
  try {
    localStorage.setItem(TOKEN_KEY, result.token)
    localStorage.setItem(AUTH_PROFILE_KEY, JSON.stringify(profile))
    if (rememberGuest) {
      localStorage.setItem(GUEST_TOKEN_KEY, result.token)
      localStorage.setItem(GUEST_PROFILE_KEY, JSON.stringify(profile))
    }
  } catch {}
}

function clearActiveAuth(purgeRememberedGuest = false) {
  try {
    const activeToken = localStorage.getItem(TOKEN_KEY)
    const guestToken = localStorage.getItem(GUEST_TOKEN_KEY)
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(AUTH_PROFILE_KEY)
    if (purgeRememberedGuest && activeToken && activeToken === guestToken) {
      localStorage.removeItem(GUEST_TOKEN_KEY)
      localStorage.removeItem(GUEST_PROFILE_KEY)
    }
  } catch {}
}

function isAuthFailure(status: number): boolean {
  // Invalid/missing JWTs are 401. A real 403 is a permission denial and must
  // never log out a valid user or silently replace their guest identity.
  return status === 401
}

function expireAuth(requestToken: string | null) {
  // A late response from the previous identity must not log out a new login.
  if (!requestToken || getToken() !== requestToken) return
  clearActiveAuth(true)
  window.dispatchEvent(new Event('xiyouji-auth-expired'))
}

/** 获取用户主动选择登录/注册/游客模式后保存的 Token。 */
async function ensureToken(): Promise<string> {
  const cached = getToken()
  if (cached) return cached
  throw new Error('请先选择登录、注册或游客模式')
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
interface PendingCommand { key: string; url: string; body?: unknown; createdAt: number }
const inFlight = new Map<string, Promise<any>>()
const uncertain = new Map<string, PendingCommand>()
const retiredKeys = new Set<string>()
const COMMAND_STORAGE = 'xiyouji_pending_commands'
try {
  for (const [id, command] of JSON.parse(sessionStorage.getItem(COMMAND_STORAGE) || '[]')) uncertain.set(id, command)
} catch { /* storage may be unavailable */ }
function persistCommands() {
  try { sessionStorage.setItem(COMMAND_STORAGE, JSON.stringify([...uncertain])) } catch {}
}
function unknown(command: PendingCommand): ApiError {
  const error = new ApiError(409, { code: 'RESULT_UNKNOWN', message: '结果未确认。请同步状态并查询原命令回执；不要重复操作。幂等保护有时限。' })
  window.dispatchEvent(new CustomEvent('xiyouji-command-unknown', { detail: command }))
  return error
}
/** Explicit user acknowledgement only, after reading current authoritative state.
 * Expiry itself never releases a command or generates a new key. */
export function acknowledgeUnknownCommands() {
  for (const command of uncertain.values()) retiredKeys.add(command.key)
  uncertain.clear()
  persistCommands()
}
export function hasUnknownCommands() { return uncertain.size > 0 }
export function unknownCommandsPastTtl() {
  return [...uncertain.values()].every(command => Date.now() - command.createdAt >= 10 * 60 * 1000)
}
export async function reconcileUnknownCommands(apply?: (command: PendingCommand, response: any) => Promise<void>) {
  for (const [identity, command] of uncertain) {
    const response = await readCommandReceipt(command)
    await apply?.(command, response)
    uncertain.delete(identity)
  }
  persistCommands()
}
async function readCommandReceipt(command: PendingCommand) {
  const resource = (command.body as any)?.code || ''
  let receipt
  try {
    receipt = await getJson(`/api/commands/receipt?path=${encodeURIComponent(command.url)}&commandId=${encodeURIComponent(command.key)}&resource=${encodeURIComponent(resource)}`)
  } catch { throw unknown(command) }
  if (receipt.commandId !== command.key || receipt.status !== 'COMPLETED' || !receipt.response) throw unknown(command)
  return receipt.response
}
export function postJson(url: string, body?: unknown, options: CommandOptions = {}): Promise<any> {
  return commandJson(url, body, options, 'POST')
}
function commandJson(url: string, body: unknown, options: CommandOptions, method: 'POST' | 'DELETE'): Promise<any> {
  const identity = `${getToken()}:${method}:${url}:${JSON.stringify(body)}`
  const existing = inFlight.get(identity)
  if (existing) return existing
  const execute = async () => {
    const previous = uncertain.get(identity)
    if (previous) {
      const response = await readCommandReceipt(previous)
      uncertain.delete(identity)
      persistCommands()
      return response
    }
    const resolved = resolveCommandOptions({ ...options, idempotencyKey: options.idempotencyKey && !retiredKeys.has(options.idempotencyKey) ? options.idempotencyKey : undefined })
    const command = { key: resolved.idempotencyKey, url, body, createdAt: Date.now() }
    uncertain.set(identity, command)
    persistCommands()
    try {
      const response = await sendPostJson(url, body, resolved, method)
      uncertain.delete(identity)
      persistCommands()
      return response
    } catch (error: any) {
      if (error?.status >= 400 && error.status < 500 && !['RESULT_UNKNOWN', 'IDEMPOTENCY_IN_PROGRESS'].includes(error.code)) {
        uncertain.delete(identity)
        persistCommands()
        throw error
      }
      throw unknown(command)
    }
  }
  const promise = execute().finally(() => inFlight.delete(identity))
  inFlight.set(identity, promise)
  return promise
}
async function sendPostJson(url: string, body?: unknown, options: CommandOptions = {}, method = 'POST'): Promise<any> {
  // A retry after refreshing an expired token is still the same logical
  // command. Resolve the key once so the server can replay the first result
  // instead of executing the mutation twice.
  const commandOptions = resolveCommandOptions(options)
  const headers = commandHeaders(await authHeaders(), commandOptions)
  const res = await fetch(url, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })

  // 登录已过期时返回认证错误，让界面回到登录页；不再静默创建新游客。
  if (isAuthFailure(res.status)) {
    expireAuth(headers.get('Authorization')?.slice(7) ?? null)
    return parseFailure(res)
  }

  if (!res.ok) {
    return parseFailure(res)
  }
  return res.status === 204 ? undefined : res.json()
}

export async function getJson(url: string): Promise<any> {
  const headers = new Headers(await authHeaders())
  const res = await fetch(url, { headers })

  if (isAuthFailure(res.status)) {
    expireAuth(headers.get('Authorization')?.slice(7) ?? null)
    return parseFailure(res)
  }

  if (!res.ok) {
    return parseFailure(res)
  }
  return res.status === 204 ? undefined : res.json()
}

async function deleteJson(url: string, options: CommandOptions = {}): Promise<any> {
  return commandJson(url, undefined, options, 'DELETE')
}

// ====== 认证 API ======
export const authApi = {
  async guestLogin(): Promise<AuthResult> {
    let remembered: AuthResult | null = null
    try {
      const savedToken = localStorage.getItem(GUEST_TOKEN_KEY)
      const savedProfileRaw = localStorage.getItem(GUEST_PROFILE_KEY)
      if (savedToken && savedProfileRaw) {
        const savedProfile = JSON.parse(savedProfileRaw) as AuthProfile
        remembered = { ...savedProfile, token: savedToken }
      }
    } catch {}
    if (remembered) {
      const check = await fetch(`${AUTH_API}/session`, {
        headers: { Authorization: `Bearer ${remembered.token}` },
      })
      if (check.ok) {
        setAuth(remembered, true)
        return remembered
      }
      // Only confirmed invalid authentication permits a fresh guest after
      // the player explicitly clicks guest mode. Network/5xx errors do not.
      if (check.status !== 401) return parseFailure(check)
      try {
        if (localStorage.getItem(GUEST_TOKEN_KEY) === remembered.token) {
          localStorage.removeItem(GUEST_TOKEN_KEY)
          localStorage.removeItem(GUEST_PROFILE_KEY)
        }
      } catch {}
      expireAuth(remembered.token)
    }
    const res = await fetch(`${AUTH_API}/guest`, {
      method: 'POST', headers: { 'X-Idempotency-Key': createIdempotencyKey() }
    })
    if (!res.ok) throw new Error('游客登录失败')
    const data = await res.json() as AuthResult
    if (data.token) setAuth(data, true)
    return data
  },

  async login(account: string, password: string): Promise<AuthResult> {
    const res = await fetch(`${AUTH_API}/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Idempotency-Key': createIdempotencyKey() },
      body: JSON.stringify({ account, password })
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || err.error || '登录失败')
    }
    const data = await res.json() as AuthResult
    if (data.token) setAuth(data)
    return data
  },

  async register(account: string, username: string, password: string): Promise<AuthResult> {
    const res = await fetch(`${AUTH_API}/register`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        // Registration is a resource-creation command. Reusing this key after
        // a network retry returns the original token instead of creating a
        // second account or surfacing a duplicate-user error.
        'X-Idempotency-Key': createIdempotencyKey(),
      },
      body: JSON.stringify({ account, username, password })
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || err.error || '注册失败')
    }
    const data = await res.json() as AuthResult
    if (data.token) setAuth(data)
    return data
  },

  getToken,
  getProfile,
  validateSession: () => getJson(`${AUTH_API}/session`),
  // Switching identity keeps the remembered guest identity so its three save
  // slots remain reachable. Authentication failures purge an invalid guest.
  logout: () => clearActiveAuth(false),
  clearToken: () => clearActiveAuth(true),
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
