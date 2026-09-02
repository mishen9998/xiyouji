import { computed, onBeforeUnmount, ref } from 'vue'

/** 战斗角色可播放的动作。idle 表示没有出牌时的静立/呼吸状态。 */
export type BattleAnimationAction = 'idle' | 'attack' | 'defense' | 'ability' | 'hit'

export const BATTLE_ACTION_DURATION: Record<BattleAnimationAction, number> = {
  idle: 0,
  attack: 620,
  defense: 760,
  ability: 860,
  hit: 560,
}

/** 只声明推断动作所需字段，避免和单人/多人 BattleInfo 绑死。 */
export interface BattleSnapshotLike {
  player?: { hp?: number | null; block?: number | null } | null
  enemy?: { intent?: string | null } | null
  turnNumber?: number | null
  playerTurn?: boolean | null
  battleOver?: boolean | null
  combatLog?: readonly string[] | null
}

/** 将后端 CardType（或前端卡牌 type）映射为角色动作。 */
export function actionForCardType(cardType?: string | null): BattleAnimationAction {
  switch (String(cardType || '').toUpperCase()) {
    case 'ATTACK':
      return 'attack'
    case 'DEFENSE':
      return 'defense'
    // SKILL 也视为能力施放；POWER 是后端明确的能力牌类型。
    case 'POWER':
    case 'SKILL':
      return 'ability'
    default:
      return 'idle'
  }
}

/**
 * 判断一次状态刷新是否包含“敌人攻击导致的受击”。
 *
 * 敌人行动发生在 endTurn 请求内部，因此 API 没有独立事件时可以将前后
 * 快照传入这里。优先使用回合序号或新增战斗日志确认敌人回合，避免把
 * “狂暴”等玩家自损能力牌误判成受击；即使格挡完全吸收伤害，也会触发。
 */
export function isIncomingEnemyAttack(
  previous: BattleSnapshotLike | null | undefined,
  next: BattleSnapshotLike | null | undefined,
): boolean {
  if (!previous || !next) return false
  const intent = String(previous.enemy?.intent || '').toUpperCase()
  if (intent !== 'ATTACK') return false

  if (previous.playerTurn !== true) return false

  const beforeTurn = Number(previous.turnNumber)
  const afterTurn = Number(next.turnNumber)
  const turnAdvanced = Number.isFinite(beforeTurn)
    && Number.isFinite(afterTurn)
    && afterTurn > beforeTurn

  // 敌人攻击日志在单人/多人后端都会追加；用增量日志覆盖击杀时回合号
  // 不递增以及旧客户端未返回 turnNumber 的情况。
  const beforeLogs = previous.combatLog ?? []
  const afterLogs = next.combatLog ?? []
  const newLogs = afterLogs.length >= beforeLogs.length
    ? afterLogs.slice(beforeLogs.length)
    : afterLogs
  const attackLogged = newLogs.some((line) => /攻击|造成\s*\d+\s*点伤害|被打败|阵亡/.test(String(line)))

  // 兼容没有回合号/日志的轻量快照：只有在 HP 或格挡确实发生变化时才
  // 认为敌人行动，仍然不会把同回合的玩家自损误判为受击。
  const beforeHp = Number(previous.player?.hp)
  const afterHp = Number(next.player?.hp)
  const beforeBlock = Number(previous.player?.block)
  const afterBlock = Number(next.player?.block)
  const hpChanged = Number.isFinite(beforeHp) && Number.isFinite(afterHp) && afterHp < beforeHp
  const blockChanged = Number.isFinite(beforeBlock) && Number.isFinite(afterBlock) && afterBlock < beforeBlock
  const stateChanged = hpChanged || blockChanged

  if (!turnAdvanced && !attackLogged && !stateChanged) return false

  // 结束回合后后端通常会将 playerTurn 置回 true；若这次攻击击杀玩家，
  // battleOver=true 时 playerTurn 会保持 false，也应保留受击反馈。
  return next.playerTurn === true || next.battleOver === true
}

/**
 * 可复用的动作状态机。
 *
 * 典型用法：
 * ```ts
 * const animation = useBattleAnimation()
 * animation.playCard(card.type)
 * // <BattleCharacter3D :action="animation.action" :action-token="animation.actionToken" />
 * const before = battle.value
 * await endTurn()
 * animation.sync(before, battle.value)
 * ```
 */
export function useBattleAnimation() {
  const action = ref<BattleAnimationAction>('idle')
  const actionToken = ref(0)
  const isAnimating = computed(() => action.value !== 'idle')
  let timer: ReturnType<typeof setTimeout> | undefined

  function clearTimer() {
    if (timer !== undefined) {
      clearTimeout(timer)
      timer = undefined
    }
  }

  function setAction(next: BattleAnimationAction) {
    action.value = next
    // token 变化用于强制重播连续的相同动作。
    actionToken.value += 1
  }

  function play(next: BattleAnimationAction, duration = BATTLE_ACTION_DURATION[next]) {
    clearTimer()
    setAction(next)
    if (next === 'idle' || duration <= 0) return
    timer = setTimeout(() => {
      timer = undefined
      setAction('idle')
    }, duration)
  }

  function idle() {
    play('idle', 0)
  }

  function playCard(cardType?: string | null) {
    play(actionForCardType(cardType))
  }

  // 语义化快捷方法，便于模板按钮或多人战斗事件直接调用。
  const playAttack = () => play('attack')
  const playDefense = () => play('defense')
  const playAbility = () => play('ability')
  const playHit = () => play('hit')

  /**
   * 根据一张卡和前后战斗快照触发动作。
   * cardType 存在时优先播放出牌动作；不传 cardType 时仅检查受击。
   */
  function sync(
    previous: BattleSnapshotLike | null | undefined,
    next: BattleSnapshotLike | null | undefined,
    cardType?: string | null,
  ) {
    if (cardType !== undefined && cardType !== null) {
      playCard(cardType)
      return action.value
    }
    if (isIncomingEnemyAttack(previous, next)) play('hit')
    return action.value
  }

  onBeforeUnmount(clearTimer)

  return {
    action,
    actionToken,
    isAnimating,
    play,
    playCard,
    playAttack,
    playDefense,
    playAbility,
    playHit,
    sync,
    idle,
    clear: clearTimer,
  }
}
