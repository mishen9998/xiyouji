package com.xiyouji.service;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.exception.BusinessException;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.MapNode;
import com.xiyouji.service.battle.CardPlayHandler;
import com.xiyouji.service.battle.MultiplayerBattleInfoAssembler;
import com.xiyouji.service.battle.MultiplayerBattleStarter;
import com.xiyouji.service.battle.MultiplayerTurnCoordinator;
import com.xiyouji.service.battle.RewardService;
import com.xiyouji.service.room.DistributedLockService;
import com.xiyouji.service.room.MultiplayerBattleState;
import com.xiyouji.service.room.MultiplayerBattleStore;
import com.xiyouji.service.room.MultiplayerPlayer;
import com.xiyouji.service.room.Room;
import com.xiyouji.service.room.RoomEventPublisher;
import com.xiyouji.service.room.RoomLockKeys;
import com.xiyouji.service.room.RoomPlayer;
import com.xiyouji.service.room.RoomService;
import com.xiyouji.service.room.RoomStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 多人战斗服务（门面）
 *
 * 统一持有锁 / 幂等 / 状态读取模板，并委托给内聚组件：
 *   - MultiplayerBattleStarter   开场校验与战斗构建
 *   - CardPlayHandler            抢出牌结算
 *   - MultiplayerTurnCoordinator 回合流转（结束回合/敌人回合/新一轮）
 *   - RewardService              胜利结算与奖励领取
 *   - MultiplayerBattleInfoAssembler 状态转 Map（广播与响应）
 *
 * 实现5人PvE协作战斗（唐僧师徒五人），参考杀戮尖塔2的"抢出牌"机制：
 *   - 所有存活玩家在同一回合内可自由出牌，谁先点击谁先出（FIFO抢占）
 *   - 使用按房间粒度的分布式锁保证线程安全，同时只有一张卡牌被结算
 *   - 玩家可等待队友先施加debuff再出牌以打出更高伤害
 *   - 每个玩家各自结束回合，所有存活玩家都结束后敌人执行回合
 *
 * 剩余公开 API 契约不变（Controller 依赖），前端零改动。
 */
@Service
public class MultiplayerBattleService {

    private static final Logger log = LoggerFactory.getLogger(MultiplayerBattleService.class);

    private final RoomService roomService;
    private final MultiplayerBattleStore battleStore;
    private final RoomEventPublisher broadcaster;
    private final DistributedLockService lockService;
    private final IdempotencyStore idempotencyStore;

    private final MultiplayerBattleStarter starter;
    private final CardPlayHandler cardPlayHandler;
    private final MultiplayerTurnCoordinator turnCoordinator;
    private final RewardService rewardService;
    private final MultiplayerBattleInfoAssembler infoAssembler;

    public MultiplayerBattleService(RoomService roomService,
                                    MultiplayerBattleStore battleStore,
                                    RoomEventPublisher broadcaster,
                                    DistributedLockService lockService,
                                    IdempotencyStore idempotencyStore,
                                    MultiplayerBattleStarter starter,
                                    CardPlayHandler cardPlayHandler,
                                    MultiplayerTurnCoordinator turnCoordinator,
                                    RewardService rewardService,
                                    MultiplayerBattleInfoAssembler infoAssembler) {
        this.roomService = roomService;
        this.battleStore = battleStore;
        this.broadcaster = broadcaster;
        this.lockService = lockService;
        this.idempotencyStore = idempotencyStore;
        this.starter = starter;
        this.cardPlayHandler = cardPlayHandler;
        this.turnCoordinator = turnCoordinator;
        this.rewardService = rewardService;
        this.infoAssembler = infoAssembler;
    }

    // ===== 公开API =====

    /**
     * 开始多人战斗
     * 从地图战斗节点触发，为每个玩家从持久化状态初始化角色。
     *
     * @param roomCode   房间码
     * @param requesterId 发起者用户ID（必须是房主）
     * @return 初始战斗状态
     */
    public MultiplayerBattleState startBattle(String roomCode, String requesterId) {
        return startBattle(roomCode, requesterId, -1, null);
    }

    /** Idempotent variant used by HTTP clients that send X-Idempotency-Key. */
    public MultiplayerBattleState startBattle(String roomCode, String requesterId, String idempotencyKey) {
        return startBattle(roomCode, requesterId, -1, idempotencyKey);
    }

    public MultiplayerBattleState startBattle(String roomCode, String requesterId,
                                              long expectedVersion, String idempotencyKey) {
        String key = commandKey("start", roomCode, requesterId, idempotencyKey);
        String fingerprint = CommandGuard.fingerprint("POST", "/multiplayer/battle/" + roomCode + "/start", "");
        IdempotencyStore.Entry entry = begin(key, fingerprint);
        if (entry != null && entry.completed()) {
            MultiplayerBattleState existing = battleStore.get(roomCode);
            if (existing != null) return existing;
        }
        try {
            MultiplayerBattleState result = withRoomLock(roomCode, () -> {
                MultiplayerBattleStarter.StartResult started =
                        starter.start(roomCode, requesterId, expectedVersion);
                // 重试短路：战斗已存在，无需再次保存与广播
                if (!started.created()) {
                    return started.state();
                }
                MultiplayerBattleState state = started.state();
                battleStore.save(state);
                broadcaster.broadcastBattleUpdate(roomCode, infoAssembler.toBattleInfo(state));
                broadcaster.broadcastSystemMessage(roomCode, "战斗开始！");
                return state;
            });
            complete(key, fingerprint, "");
            return result;
        } catch (RuntimeException error) {
            abort(key);
            throw error;
        }
    }

    /**
     * 出牌（抢出牌机制：谁先请求谁先出）
     * 使用房间级分布式锁保证同一房间同时只有一张卡牌被结算。
     */
    public MultiplayerBattleState playCard(String roomCode, String userId, int handIndex) {
        return playCard(roomCode, userId, handIndex, -1, null);
    }

    public MultiplayerBattleState playCard(String roomCode, String userId, int handIndex,
                                           long expectedVersion, String idempotencyKey) {
        String key = commandKey("play", roomCode, userId, idempotencyKey);
        String fingerprint = CommandGuard.fingerprint("POST", "/multiplayer/battle/" + roomCode + "/play",
                String.valueOf(handIndex));
        IdempotencyStore.Entry entry = begin(key, fingerprint);
        if (entry != null && entry.completed()) {
            MultiplayerBattleState existing = battleStore.get(roomCode);
            if (existing != null) return existing;
        }
        try {
            MultiplayerBattleState result = withRoomLock(roomCode, () -> {
                MultiplayerBattleState state = getBattleOrThrow(roomCode);
                if (expectedVersion >= 0) {
                    CommandGuard.checkVersion("battle:" + roomCode, expectedVersion, state.getStateVersion());
                }
                cardPlayHandler.play(state, userId, handIndex);
                return saveAndBroadcast(roomCode, state);
            });
            complete(key, fingerprint, "");
            return result;
        } catch (RuntimeException error) {
            abort(key);
            throw error;
        }
    }

    /**
     * 结束自己的回合
     * 当所有存活玩家都结束回合后，触发敌人回合。
     */
    public MultiplayerBattleState endTurn(String roomCode, String userId) {
        return endTurn(roomCode, userId, -1, null);
    }

    public MultiplayerBattleState endTurn(String roomCode, String userId,
                                          long expectedVersion, String idempotencyKey) {
        String key = commandKey("endturn", roomCode, userId, idempotencyKey);
        String fingerprint = CommandGuard.fingerprint("POST", "/multiplayer/battle/" + roomCode + "/endturn", "");
        IdempotencyStore.Entry entry = begin(key, fingerprint);
        if (entry != null && entry.completed()) {
            MultiplayerBattleState existing = battleStore.get(roomCode);
            if (existing != null) return existing;
        }
        try {
            MultiplayerBattleState result = withRoomLock(roomCode, () -> {
                MultiplayerBattleState state = getBattleOrThrow(roomCode);
                if (expectedVersion >= 0) {
                    CommandGuard.checkVersion("battle:" + roomCode, expectedVersion, state.getStateVersion());
                }
                turnCoordinator.endTurn(state, userId);
                return saveAndBroadcast(roomCode, state);
            });
            complete(key, fingerprint, "");
            return result;
        } catch (RuntimeException error) {
            abort(key);
            throw error;
        }
    }

    /** 获取战斗状态信息 */
    public Map<String, Object> getBattleInfo(String roomCode) {
        MultiplayerBattleState state = getBattleOrThrow(roomCode);
        return infoAssembler.toBattleInfo(state);
    }

    /** 获取战斗状态实体（供内部使用） */
    public MultiplayerBattleState getBattleState(String roomCode) {
        return getBattleOrThrow(roomCode);
    }

    /**
     * 玩家领取奖励（从5张中选1张加入牌组）
     */
    public MultiplayerBattleState claimReward(String roomCode, String userId, String cardName) {
        return claimReward(roomCode, userId, cardName, null);
    }

    /** Idempotent variant used by HTTP clients that send X-Idempotency-Key. */
    public MultiplayerBattleState claimReward(String roomCode, String userId, String cardName,
                                              String idempotencyKey) {
        return claimReward(roomCode, userId, cardName, -1, idempotencyKey);
    }

    public MultiplayerBattleState claimReward(String roomCode, String userId, String cardName,
                                              long expectedVersion, String idempotencyKey) {
        return resolveReward(roomCode, userId, cardName, false, expectedVersion, idempotencyKey);
    }

    public MultiplayerBattleState skipReward(String roomCode, String userId,
                                             long expectedVersion, String idempotencyKey) {
        return resolveReward(roomCode, userId, null, true, expectedVersion, idempotencyKey);
    }

    private MultiplayerBattleState resolveReward(String roomCode, String userId, String cardName,
                                                 boolean skip, long expectedVersion, String idempotencyKey) {
        String key = commandKey(skip ? "skip-reward" : "claim-reward", roomCode, userId, idempotencyKey);
        String fingerprint = CommandGuard.fingerprint("POST", "/multiplayer/battle/" + roomCode + (skip ? "/skip-reward" : "/claim-reward"), cardName);
        IdempotencyStore.Entry entry = begin(key, fingerprint);
        if (entry != null && entry.completed()) {
            MultiplayerBattleState existing = battleStore.get(roomCode);
            if (existing != null) return existing;
        }
        try {
            MultiplayerBattleState result = withRoomLock(roomCode, () -> {
                MultiplayerBattleState state = getBattleOrThrow(roomCode);
                if (expectedVersion >= 0) {
                    CommandGuard.checkVersion("battle:" + roomCode, expectedVersion, state.getStateVersion());
                }
                if (skip) {
                    rewardService.skip(state, userId);
                } else {
                    rewardService.claim(state, userId, cardName);
                }
                return saveAndBroadcast(roomCode, state);
            });
            complete(key, fingerprint, "");
            return result;
        } catch (RuntimeException error) {
            abort(key);
            throw error;
        }
    }

    /**
     * 战斗结束、领完奖励后，房主返回地图探索
     * 同步GameCharacter状态到RoomPlayer，恢复房间为IN_MAP状态。
     * 如果是Boss节点，进入下一层地图。
     */
    public Map<String, Object> returnToMap(String roomCode, String requesterId) {
        return returnToMap(roomCode, requesterId, -1, null);
    }

    public Map<String, Object> returnToMap(String roomCode, String requesterId,
                                           long expectedVersion, String idempotencyKey) {
        String key = commandKey("next-floor", roomCode, requesterId, idempotencyKey);
        String fingerprint = CommandGuard.fingerprint("POST", "/multiplayer/battle/" + roomCode + "/next-floor", "");
        IdempotencyStore.Entry entry = begin(key, fingerprint);
        if (entry != null && entry.completed()) {
            return Map.of("message", "操作已完成", "completed",
                    roomService.getRoomEntity(roomCode).getStatus() == RoomStatus.FINISHED);
        }
        try {
            Map<String, Object> result = withRoomLock(roomCode, () -> {
                MultiplayerBattleState state = getBattleOrThrow(roomCode);
                if (expectedVersion >= 0) {
                    CommandGuard.checkVersion("battle:" + roomCode, expectedVersion, state.getStateVersion());
                }
                Room room = roomService.getRoomEntity(roomCode);

                if (!room.getHostUserId().equals(requesterId)) {
                    throw new InvalidActionException("只有房主才能继续");
                }
                if (!state.isVictory()) {
                    throw new InvalidActionException("战斗未胜利");
                }
                if (!state.isRewardsHandled() && state.isRewardsPhase()) {
                    throw new InvalidActionException("请等待所有玩家领取奖励");
                }

                // 同步GameCharacter状态到RoomPlayer（HP、金币、牌组、遗物）
                for (MultiplayerPlayer mp : state.getPlayers()) {
                    RoomPlayer rp = room.getPlayers().stream()
                            .filter(p -> p.getUserId().equals(mp.getUserId()))
                            .findFirst()
                            .orElse(null);
                    if (rp != null) {
                        rp.syncFromCharacter(mp.getCharacter());
                    }
                }

                Map<String, Object> outcome = new HashMap<>();
                MapNode currentNode = room.getCurrentNode();

                // 如果是Boss节点，进入下一层
                if (currentNode != null && GameConstants.NODE_BOSS.equals(currentNode.getType())) {
                    boolean success = roomService.nextLayer(roomCode, requesterId).get("message") != null;
                    // nextLayer内部已设置room状态和floor
                    if (room.getStatus() == RoomStatus.FINISHED) {
                        outcome.put("completed", true);
                        outcome.put("message", "恭喜通关！西天取经圆满！");
                    } else {
                        outcome.put("nextLayer", true);
                        outcome.put("floor", room.getFloor());
                        outcome.put("message", "进入第 " + room.getFloor() + " 层");
                    }
                } else {
                    // 普通战斗，恢复地图探索
                    roomService.markInMap(roomCode);
                    outcome.put("nextLayer", false);
                    outcome.put("message", "返回地图探索");
                }

                // 清理战斗状态
                battleStore.remove(roomCode);

                // 广播
                broadcaster.broadcastRoomUpdate(roomCode, roomService.getRoom(roomCode));
                broadcaster.broadcastSystemMessage(roomCode, outcome.get("message").toString());

                log.info("Return to map: room={}, nextLayer={}", roomCode, outcome.get("nextLayer"));
                return outcome;
            });
            complete(key, fingerprint, "");
            return result;
        } catch (RuntimeException error) {
            abort(key);
            throw error;
        }
    }

    /**
     * 兼容旧API：房主进入下一层（现为返回地图探索）
     * @deprecated 使用 returnToMap 替代
     */
    @Deprecated
    public Map<String, Object> nextFloor(String roomCode, String requesterId) {
        return returnToMap(roomCode, requesterId);
    }

    /** 战斗状态转 Map（保持原公开签名，委托组装器） */
    public Map<String, Object> toBattleInfo(MultiplayerBattleState state) {
        return infoAssembler.toBattleInfo(state);
    }

    // ===== 内部模板 =====

    /** 保存状态并广播战斗更新（锁内调用） */
    private MultiplayerBattleState saveAndBroadcast(String roomCode, MultiplayerBattleState state) {
        battleStore.save(state);
        broadcaster.broadcastBattleUpdate(roomCode, infoAssembler.toBattleInfo(state));
        return state;
    }

    /** 在房间级分布式锁保护下执行状态变更。 */
    private <T> T withRoomLock(String roomCode, Supplier<T> action) {
        return lockService.executeWithLock(RoomLockKeys.forRoom(roomCode), 5, action);
    }

    private String commandKey(String operation, String roomCode, String userId, String idempotencyKey) {
        if (idempotencyStore == null || idempotencyKey == null || idempotencyKey.isBlank()) return null;
        return operation + ":" + roomCode + ":" + userId + ":" + idempotencyKey;
    }

    private IdempotencyStore.Entry begin(String key, String fingerprint) {
        return key == null ? null : CommandGuard.begin(idempotencyStore, key, fingerprint);
    }

    private void complete(String key, String fingerprint, String value) {
        if (key != null) idempotencyStore.complete(key, fingerprint, value, CommandGuard.TTL);
    }

    private void abort(String key) {
        if (key != null) idempotencyStore.remove(key);
    }

    /** 获取战斗状态，不存在则抛异常 */
    private MultiplayerBattleState getBattleOrThrow(String roomCode) {
        MultiplayerBattleState state = battleStore.get(roomCode);
        if (state == null) {
            throw new BusinessException("BATTLE_NOT_FOUND", "战斗不存在或已结束", 404);
        }
        return state;
    }
}