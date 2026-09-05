// ====== 多人游戏 API 封装 ======
import { postJson, getJson, authHeaders } from './game'
import type { RoomDTO, CharacterClass, MultiplayerBattleInfo } from '@/types'

const ROOM_API = '/api/room'
const BATTLE_API = '/api/multiplayer/battle'

// ====== 房间 API ======
export const roomApi = {
  createRoom(): Promise<RoomDTO> {
    return postJson(`${ROOM_API}/create`)
  },

  async joinRoom(code: string, expectedStateVersion?: number, idempotencyKey?: string): Promise<RoomDTO> {
    const version = expectedStateVersion ?? (await this.getRoom(code)).stateVersion
    return postJson(`${ROOM_API}/join`, { code }, { expectedStateVersion: version, idempotencyKey })
  },

  leaveRoom(code: string, expectedStateVersion: number, idempotencyKey?: string): Promise<{ dissolved: boolean; room?: RoomDTO }> {
    return postJson(`${ROOM_API}/${code}/leave`, undefined, { expectedStateVersion, idempotencyKey })
  },

  toggleReady(code: string, expectedStateVersion: number, idempotencyKey?: string): Promise<RoomDTO> {
    return postJson(`${ROOM_API}/${code}/ready`, undefined, { expectedStateVersion, idempotencyKey })
  },

  selectCharacter(code: string, characterClass: CharacterClass, expectedStateVersion: number, idempotencyKey?: string): Promise<RoomDTO> {
    return postJson(`${ROOM_API}/${code}/character`, { characterClass }, { expectedStateVersion, idempotencyKey })
  },

  getRoom(code: string): Promise<RoomDTO> {
    return getJson(`${ROOM_API}/${code}`)
  },

  listCharacters(): Promise<CharacterClass[]> {
    return getJson(`${ROOM_API}/characters`)
  },

  canStart(code: string): Promise<boolean> {
    return getJson(`${ROOM_API}/${code}/canStart`)
  },

  // ===== 地图探索 API =====

  startGame(code: string, expectedStateVersion: number, idempotencyKey?: string): Promise<RoomDTO> {
    return postJson(`${ROOM_API}/${code}/start-game`, undefined, { expectedStateVersion, idempotencyKey })
  },

  moveToNode(code: string, nodeId: string, expectedStateVersion: number, idempotencyKey?: string): Promise<{ node: any; eventType: string; room: RoomDTO }> {
    return postJson(`${ROOM_API}/${code}/move`, { nodeId }, { expectedStateVersion, idempotencyKey })
  },

  handleEvent(code: string, action: string, params: { cardId?: number; cardIndex?: number } | undefined,
              expectedStateVersion: number, idempotencyKey?: string): Promise<any> {
    return postJson(`${ROOM_API}/${code}/event`, { action, ...params }, { expectedStateVersion, idempotencyKey })
  },

  nextLayer(code: string, expectedStateVersion: number, idempotencyKey?: string): Promise<any> {
    return postJson(`${ROOM_API}/${code}/next-layer`, undefined, { expectedStateVersion, idempotencyKey })
  },
}

// ====== 多人战斗 API ======
export const multiplayerBattleApi = {
  startBattle(roomCode: string, expectedStateVersion: number, idempotencyKey?: string): Promise<MultiplayerBattleInfo> {
    return postJson(`${BATTLE_API}/${roomCode}/start`, undefined, { expectedStateVersion, idempotencyKey })
  },

  playCard(roomCode: string, handIndex: number, expectedStateVersion: number, idempotencyKey?: string): Promise<MultiplayerBattleInfo> {
    return postJson(`${BATTLE_API}/${roomCode}/play`, { handIndex }, { expectedStateVersion, idempotencyKey })
  },

  endTurn(roomCode: string, expectedStateVersion: number, idempotencyKey?: string): Promise<MultiplayerBattleInfo> {
    return postJson(`${BATTLE_API}/${roomCode}/endturn`, undefined, { expectedStateVersion, idempotencyKey })
  },

  getBattleState(roomCode: string): Promise<MultiplayerBattleInfo> {
    return getJson(`${BATTLE_API}/${roomCode}/state`)
  },

  battleExists(roomCode: string): Promise<boolean> {
    return getJson(`${BATTLE_API}/${roomCode}/exists`)
  },

  claimReward(roomCode: string, cardName: string, expectedStateVersion: number, idempotencyKey?: string): Promise<MultiplayerBattleInfo> {
    return postJson(`${BATTLE_API}/${roomCode}/claim-reward`, { cardName }, { expectedStateVersion, idempotencyKey })
  },

  skipReward(roomCode: string, expectedStateVersion: number, idempotencyKey?: string): Promise<MultiplayerBattleInfo> {
    return postJson(`${BATTLE_API}/${roomCode}/skip-reward`, undefined, { expectedStateVersion, idempotencyKey })
  },

  nextFloor(roomCode: string, expectedStateVersion: number, idempotencyKey?: string): Promise<{ completed?: boolean; message?: string }> {
    return postJson(`${BATTLE_API}/${roomCode}/next-floor`, undefined, { expectedStateVersion, idempotencyKey })
  },
}

/** 获取当前 JWT token（供 WebSocket 握手使用） */
export async function getTokenForWs(): Promise<string> {
  const headers = await authHeaders() as Record<string, string>
  return headers['Authorization'].replace('Bearer ', '')
}

/** 从 JWT 中解析当前用户名（无需后端请求） */
export function getCurrentUsername(): string | null {
  try {
    const token = localStorage.getItem('xiyouji_jwt_token')
    if (!token) return null
    const payload = token.split('.')[1]
    const bytes = Uint8Array.from(atob(payload.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0))
    const decoded = JSON.parse(new TextDecoder().decode(bytes))
    return decoded.sub || decoded.username || null
  } catch {
    return null
  }
}
