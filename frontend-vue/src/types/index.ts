// ====== 西游记 Roguelike - TypeScript 类型定义 ======

export type CharacterClass = 'SUN_WUKONG' | 'ZHU_BAJIE' | 'SHA_SENG' | 'BAI_LONGMA' | 'TANG_SANZANG'

export type NodeType = 'BATTLE' | 'BOSS' | 'REST' | 'TREASURE' | 'SHOP' | 'RANDOM' | 'BONFIRE' | 'EMPEROR'

export type CardType = 'ATTACK' | 'SKILL' | 'DEFENSE' | 'POWER' | 'STATUS'

export type IntentType = 'ATTACK' | 'DEFEND' | 'BUFF' | 'DEBUFF' | 'SPECIAL'

export interface Card {
  id: number
  name: string
  type: CardType
  cost: number
  damage: number
  block: number
  drawCards: number
  description: string
  emoji?: string
  upgraded: boolean
}

export interface Relic {
  name: string
  description: string
  emoji?: string
}

export interface MapNode {
  id: string
  row: number
  col: number
  type: NodeType
  name?: string
  visited: boolean
  accessible: boolean
  connections: string[]
}

export interface Player {
  characterClass: CharacterClass
  displayName: string
  emoji?: string
  hp: number
  maxHp: number
  block: number
  energy: number
  maxEnergy: number
  gold: number
  deckSize: number
  floor: number
  deck: Card[]
  relics: Relic[]
  drawPile?: Card[]
  discardPile?: Card[]
  exhaustPile?: Card[]
  drawPileSize?: number
  discardPileSize?: number
  buffs?: BuffEntry[]
  hand?: Card[]
}

export interface BuffEntry {
  name: string
  value: number
  permanent: boolean
}

export interface Enemy {
  name: string
  emoji?: string
  hp: number
  maxHp: number
  block: number
  intent: IntentType
  intentValue?: number | string
  isBoss: boolean
  buffs?: BuffEntry[]
}

export interface BattleInfo {
  stateVersion: number
  inBattle: boolean
  /** 当前回合序号；用于在敌人行动后精准触发受击动画 */
  turnNumber?: number
  playerTurn: boolean
  battleOver: boolean
  victory: boolean
  player: Player
  enemy: Enemy
  /** 后端返回的最近战斗日志；动画同步时可用于识别致命攻击 */
  combatLog?: string[]
  rewards?: Rewards | null
}

export interface Rewards {
  goldReward?: number
  cardRewards?: Card[]
  relicReward?: Relic | null
  message?: string
}

export interface GameState {
  sessionId: string
  stateVersion: number
  player: Player
  map: MapNode[]
  currentNode: MapNode | null
  inBattle: boolean
  currentLayer: number
  maxLayer: number
}

export interface NewGameResponse {
  sessionId: string
  stateVersion: number
  player: Player
  map: MapNode[]
  currentNode: MapNode
}

export interface MoveResponse {
  node: MapNode
  eventType: string
  stateVersion: number
}

export interface EventResponse {
  stateVersion?: number
  relic?: Relic
  choices?: Relic[]
  shopCards?: Card[]
  bought?: boolean
  player?: Player
  bonfireUpgradesLeft?: number
  error?: string
  message?: string
  success?: boolean
  currentLayer?: number
  chosenCard?: string
}

export interface CardRewardChooseResponse {
  stateVersion?: number
  player?: Player
  chosenCard?: string
}

// ====== 多人游戏类型 ======

export type RoomStatus = 'WAITING' | 'IN_MAP' | 'IN_BATTLE' | 'FINISHED'

export interface RoomPlayer {
  userId: string
  username: string
  characterClass: CharacterClass | null
  ready: boolean
  host: boolean
  // 地图探索期间持久化的玩家状态
  hp?: number
  maxHp?: number
  gold?: number
  deck?: Card[]
  relics?: Relic[]
}

export interface RoomDTO {
  eventId?: string
  code: string
  hostUserId: string
  players: RoomPlayer[]
  playerCount: number
  status: RoomStatus
  createdAt: string
  floor: number
  // 地图探索相关字段
  maxLayer?: number
  map?: MapNode[]
  currentNode?: MapNode | null
  bonfireUpgradesLeft?: number
  stateVersion: number
}

/** 多人战斗中的玩家信息 */
export interface MultiplayerPlayerInfo {
  index: number
  userId: string
  username: string
  characterClass: CharacterClass | null
  hp: number
  maxHp: number
  block: number
  energy: number
  maxEnergy: number
  strength: number
  dexterity: number
  endedTurn: boolean
  alive: boolean
  deckSize: number
  drawPileSize: number
  discardPileSize: number
  buffs: Record<string, number>
  hand: MultiplayerCardInfo[]
}

export interface MultiplayerCardInfo {
  index: number
  name: string
  type: CardType | null
  cost: number
  damage: number
  block: number
  emoji?: string
  exhaust: boolean
  description: string
}

export interface MultiplayerEnemyInfo {
  name: string
  hp: number
  maxHp: number
  block: number
  strength: number
  emoji?: string
  intent: string
  intentValue: number
  isBoss: boolean
  buffs: Record<string, number>
  targetPlayerIndex: number
}

export interface MultiplayerBattleInfo {
  eventId?: string
  roomCode: string
  stateVersion: number
  turnNumber: number
  playerTurn: boolean
  battleOver: boolean
  victory: boolean
  enemy: MultiplayerEnemyInfo
  players: MultiplayerPlayerInfo[]
  alivePlayerCount: number
  playersEndedTurn: number
  combatLog: string[]
  rewardsPhase?: boolean
  rewardsHandled?: boolean
  rewards?: Record<string, MultiplayerCardInfo[]>
  claimedRewards?: Record<string, string>
}
