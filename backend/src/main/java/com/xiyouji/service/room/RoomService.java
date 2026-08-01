package com.xiyouji.service.room;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.dto.response.room.RoomDTO;
import com.xiyouji.exception.BusinessException;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.Card;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.MapNode;
import com.xiyouji.model.Relic;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.repository.CardRepository;
import com.xiyouji.repository.CharacterRepository;
import com.xiyouji.repository.RelicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 房间服务 - 管理多人协作房间的创建、加入、退出、准备、地图探索等业务逻辑
 */
@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private static final char[] CODE_CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 8;

    private final RoomStore roomStore;
    private final MultiplayerMapService mapService;
    private final CharacterRepository characterRepo;
    private final CardRepository cardRepo;
    private final RelicRepository relicRepo;
    private final DistributedLockService lockService;
    private final SecureRandom random = new SecureRandom();

    public RoomService(RoomStore roomStore,
                       MultiplayerMapService mapService,
                       CharacterRepository characterRepo,
                       CardRepository cardRepo,
                       RelicRepository relicRepo,
                       DistributedLockService lockService) {
        this.roomStore = roomStore;
        this.mapService = mapService;
        this.characterRepo = characterRepo;
        this.cardRepo = cardRepo;
        this.relicRepo = relicRepo;
        this.lockService = lockService;
    }

    /**
     * 创建房间
     *
     * @param hostUserId   房主用户ID
     * @param hostUsername 房主用户名
     * @return 房间信息
     */
    public RoomDTO createRoom(String hostUserId, String hostUsername) {
        String code = generateUniqueCode();
        Room room = new Room(code, hostUserId);

        RoomPlayer host = new RoomPlayer(hostUserId, hostUsername);
        host.setHost(true);
        host.setReady(false);
        room.getPlayers().add(host);

        roomStore.save(room);
        log.info("Room created: code={}, host={}", code, hostUsername);
        return toDTO(room);
    }

    /**
     * 加入房间
     *
     * @param code     房间码
     * @param userId   用户ID
     * @param username 用户名
     * @return 房间信息
     * @throws BusinessException 房间不存在/已满/已在房间/已开始
     */
    public RoomDTO joinRoom(String code, String userId, String username) {
        // 加分布式锁：防止两玩家同时加入突破 5 人上限（检查 isFull 与 add 必须原子）
        return lockService.executeWithLock("room:join:" + code, 5, () -> {
            Room room = getRoomOrThrow(code);

            if (room.getStatus() != RoomStatus.WAITING) {
                throw new InvalidActionException("房间已开始游戏，无法加入");
            }
            if (room.isFull()) {
                throw new InvalidActionException("房间已满（5人）");
            }
            if (room.hasPlayer(userId)) {
                throw new InvalidActionException("你已在该房间内");
            }

            RoomPlayer player = new RoomPlayer(userId, username);
            room.getPlayers().add(player);
            roomStore.save(room);
            log.info("Player {} joined room {}", username, code);
            return toDTO(room);
        });
    }

    /**
     * 退出房间
     * 房主退出则解散整个房间（简化处理，避免房主转移的复杂逻辑）。
     *
     * @param code   房间码
     * @param userId 用户ID
     */
    public void leaveRoom(String code, String userId) {
        Room room = getRoomOrThrow(code);

        if (!room.hasPlayer(userId)) {
            throw new InvalidActionException("你不在该房间内");
        }

        if (room.getHostUserId().equals(userId)) {
            // 房主退出，解散房间
            roomStore.remove(code);
            log.info("Host {} left, room {} dissolved", userId, code);
        } else {
            room.getPlayers().removeIf(p -> p.getUserId().equals(userId));
            roomStore.save(room);
            log.info("Player {} left room {}", userId, code);
        }
    }

    /**
     * 切换准备状态
     *
     * @param code   房间码
     * @param userId 用户ID
     * @return 更新后的房间信息
     */
    public RoomDTO toggleReady(String code, String userId) {
        // 加分布式锁：避免房主检查"全员已准备"与某玩家同时取消准备导致状态不一致
        return lockService.executeWithLock("room:ready:" + code, 5, () -> {
            Room room = getRoomOrThrow(code);

            if (room.getStatus() != RoomStatus.WAITING) {
                throw new InvalidActionException("游戏已开始，无法切换准备状态");
            }

            RoomPlayer player = room.getPlayers().stream()
                    .filter(p -> p.getUserId().equals(userId))
                    .findFirst()
                    .orElseThrow(() -> new InvalidActionException("你不在该房间内"));

            player.setReady(!player.isReady());
            roomStore.save(room);
            log.info("Player {} toggled ready={} in room {}", userId, player.isReady(), code);
            return toDTO(room);
        });
    }

    /**
     * 选择角色
     *
     * @param code           房间码
     * @param userId         用户ID
     * @param characterClass 角色职业
     * @return 更新后的房间信息
     */
    public RoomDTO selectCharacter(String code, String userId, CharacterClass characterClass) {
        // 加分布式锁：防止两玩家同时选同一角色（检查角色占用与 setCharacterClass 必须原子）
        return lockService.executeWithLock("room:char:" + code, 5, () -> {
            Room room = getRoomOrThrow(code);

            if (room.getStatus() != RoomStatus.WAITING) {
                throw new InvalidActionException("游戏已开始，无法选择角色");
            }

            RoomPlayer player = room.getPlayers().stream()
                    .filter(p -> p.getUserId().equals(userId))
                    .findFirst()
                    .orElseThrow(() -> new InvalidActionException("你不在该房间内"));

            player.setCharacterClass(characterClass);
            roomStore.save(room);
            log.info("Player {} selected {} in room {}", userId, characterClass, code);
            return toDTO(room);
        });
    }

    /**
     * 获取房间信息
     */
    public RoomDTO getRoom(String code) {
        return toDTO(getRoomOrThrow(code));
    }

    /**
     * 获取房间领域对象（供战斗系统等内部使用）
     */
    public Room getRoomEntity(String code) {
        return getRoomOrThrow(code);
    }

    /**
     * 房间是否存在
     */
    public boolean roomExists(String code) {
        return roomStore.exists(code);
    }

    /**
     * 检查是否所有玩家都已准备且选了角色，可否开始游戏
     */
    public boolean canStart(String code) {
        Room room = getRoomOrThrow(code);
        return room.getStatus() == RoomStatus.WAITING
                && room.allReady()
                && room.getPlayers().stream().allMatch(p -> p.getCharacterClass() != null);
    }

    /**
     * 将房间状态切换为战斗中（供战斗系统调用）
     */
    public void markInBattle(String code) {
        Room room = getRoomOrThrow(code);
        room.setStatus(RoomStatus.IN_BATTLE);
        roomStore.save(room);
    }

    // ===== 地图探索相关 =====

    /**
     * 房主开始游戏 - 生成第一层地图并初始化所有玩家角色
     *
     * @param code       房间码
     * @param requesterId 发起者用户ID（必须是房主）
     * @return 更新后的房间信息
     */
    @Transactional
    public RoomDTO startGame(String code, String requesterId) {
        // 加分布式锁：防止房主双击"开始游戏"按钮触发两次地图生成
        return lockService.executeWithLock("room:start:" + code, 5, () -> {
            Room room = getRoomOrThrow(code);

            if (!room.getHostUserId().equals(requesterId)) {
                throw new InvalidActionException("只有房主才能开始游戏");
            }
            if (room.getStatus() != RoomStatus.WAITING) {
                // 幂等保护：已开始则直接返回当前状态，不重复生成地图
                log.info("startGame called but room {} already in status {}", code, room.getStatus());
                return toDTO(room);
            }
            if (!canStart(code)) {
                throw new InvalidActionException("需要所有玩家准备并选择角色后才能开始");
            }

            // 生成第一层地图
            room.setMap(mapService.generateLayer(1));
            room.setCurrentNode(null);
            room.setMapOpen(true);
            room.setStatus(RoomStatus.IN_MAP);
            room.setFloor(1);

            // 为每个玩家初始化角色状态（HP、金币、初始牌组）
            for (RoomPlayer rp : room.getPlayers()) {
                GameCharacter template = characterRepo.findByCharacterClass(rp.getCharacterClass())
                        .orElseThrow(() -> new BusinessException("CHARACTER_NOT_FOUND",
                                "角色不存在: " + rp.getCharacterClass(), 404));
                rp.setMaxHp(template.getMaxHp());
                rp.setHp(template.getMaxHp());
                rp.setGold(template.getStartingGold());
                rp.getDeck().clear();
                rp.getRelics().clear();

                // 构建初始牌组：5张挥棒 + 5张格挡
                List<Card> basicAttack = cardRepo.findByName("挥棒");
                List<Card> basicDefend = cardRepo.findByName("格挡");
                for (int i = 0; i < GameConstants.INITIAL_HAND_SIZE; i++) {
                    if (!basicAttack.isEmpty()) rp.getDeck().add(basicAttack.get(0).copy());
                    if (!basicDefend.isEmpty()) rp.getDeck().add(basicDefend.get(0).copy());
                }
            }

            roomStore.save(room);
            log.info("Game started: room={}, players={}, floor=1", code, room.getPlayerCount());
            return toDTO(room);
        });
    }

    /**
     * 房主移动到指定节点
     *
     * @param code   房间码
     * @param nodeId 目标节点ID
     * @return 包含 eventType 和 node 的 Map
     */
    @Transactional
    public Map<String, Object> moveToNode(String code, String nodeId) {
        Room room = getRoomOrThrow(code);

        if (room.getStatus() != RoomStatus.IN_MAP) {
            throw new InvalidActionException("当前不在地图探索阶段");
        }

        MapNode node = mapService.moveToNode(room, nodeId);
        String eventType = mapService.interpretNode(node);

        roomStore.save(room);
        log.info("Room {} moved to node: {}, event: {}", code, nodeId, eventType);

        Map<String, Object> result = new HashMap<>();
        result.put("node", node);
        result.put("eventType", eventType);
        result.put("room", toDTO(room));
        return result;
    }

    /**
     * 处理节点事件（休息/篝火/宝箱/商店/随机）
     *
     * @param code       房间码
     * @param userId     操作玩家ID
     * @param action     动作（rest/upgrade/buy/browse/trigger）
     * @param cardId     购买卡牌ID（商店用）
     * @param cardIndex  升级卡牌索引（篝火用）
     * @return 事件结果
     */
    @Transactional
    public Map<String, Object> handleEvent(String code, String userId,
                                           String action, Long cardId, Integer cardIndex) {
        Room room = getRoomOrThrow(code);
        MapNode node = room.getCurrentNode();

        if (node == null) {
            throw new InvalidActionException("不在任何节点");
        }
        if (room.getStatus() != RoomStatus.IN_MAP) {
            throw new InvalidActionException("当前不在地图探索阶段");
        }

        RoomPlayer player = room.getPlayers().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new InvalidActionException("你不在该房间内"));

        Map<String, Object> result = new HashMap<>();

        switch (node.getType()) {
            case "REST" -> {
                if ("rest".equals(action)) {
                    int healAmount = player.getMaxHp() / 3;
                    player.setHp(Math.min(player.getMaxHp(), player.getHp() + healAmount));
                    result.put("message", "休息完毕，全队生命值已恢复");
                    result.put("healAmount", healAmount);
                }
                result.put("players", room.getPlayers());
            }
            case "BONFIRE" -> {
                if ("upgrade".equals(action) && cardIndex != null && cardIndex >= 0) {
                    if (room.getBonfireUpgradesLeft() <= 0) {
                        result.put("error", "升级次数已用完");
                    } else {
                        List<Card> deck = player.getDeck();
                        if (cardIndex < deck.size()) {
                            deck.get(cardIndex).upgrade();
                            room.setBonfireUpgradesLeft(room.getBonfireUpgradesLeft() - 1);
                            result.put("message", "卡牌已升级");
                        }
                    }
                }
                result.put("bonfireUpgradesLeft", room.getBonfireUpgradesLeft());
                result.put("players", room.getPlayers());
            }
            case "TREASURE" -> {
                Relic relic = getRandomRelic();
                if (relic != null) {
                    player.getRelics().add(relic);
                    result.put("relic", relic);
                    result.put("message", player.getUsername() + " 获得遗物: " + relic.getName());
                }
                result.put("players", room.getPlayers());
            }
            case "SHOP" -> {
                if ("buy".equals(action) && cardId != null) {
                    int price = 50;
                    // 通关文牒折扣
                    boolean hasDiscount = player.getRelics().stream()
                            .anyMatch(r -> GameConstants.RELIC_TONGGUANWENDIE.equals(r.getName()));
                    if (hasDiscount) price = price * 80 / 100;

                    if (player.getGold() >= price) {
                        player.setGold(player.getGold() - price);
                        cardRepo.findById(cardId).ifPresent(card ->
                                player.getDeck().add(card.copy()));
                        result.put("bought", true);
                        result.put("message", "购买成功，花费 " + price + " 金币");
                    } else {
                        result.put("bought", false);
                        result.put("error", "金币不足");
                    }
                } else {
                    // 浏览商店
                    List<Card> shopCards = getShopCards(player);
                    result.put("shopCards", shopCards);
                }
                result.put("players", room.getPlayers());
            }
            case "RANDOM" -> {
                String event = doRandomEvent(player);
                result.put("message", event);
                result.put("players", room.getPlayers());
            }
            default -> result.put("error", "未知节点类型: " + node.getType());
        }

        roomStore.save(room);
        return result;
    }

    /**
     * Boss击败后进入下一层
     *
     * @param code       房间码
     * @param requesterId 发起者ID
     * @return 结果（含是否通关）
     */
    @Transactional
    public Map<String, Object> nextLayer(String code, String requesterId) {
        Room room = getRoomOrThrow(code);

        if (!room.getHostUserId().equals(requesterId)) {
            throw new InvalidActionException("只有房主才能进入下一层");
        }

        boolean success = mapService.advanceToNextLayer(room);
        Map<String, Object> result = new HashMap<>();

        if (success) {
            room.setStatus(RoomStatus.IN_MAP);
            roomStore.save(room);
            result.put("room", toDTO(room));
            result.put("message", "进入第 " + room.getFloor() + " 层");
        } else {
            room.setStatus(RoomStatus.FINISHED);
            roomStore.save(room);
            result.put("message", "恭喜通关！西天取经圆满！");
        }
        return result;
    }

    /**
     * 将房间状态从战斗中恢复为地图探索（战斗结束后调用）
     */
    @Transactional
    public void markInMap(String code) {
        Room room = getRoomOrThrow(code);
        room.setStatus(RoomStatus.IN_MAP);
        roomStore.save(room);
    }

    // ===== 内部辅助方法 =====

    /**
     * 获取全部宝物（缓存）
     * 使用 @Cacheable 避免每次随机都查库，与 GameService 共享 "relics" 缓存
     */
    @Cacheable(value = "relics", key = "'all'")
    public List<Relic> getAllRelics() {
        return relicRepo.findAll();
    }

    private Relic getRandomRelic() {
        List<Relic> relics = new ArrayList<>(getAllRelics());
        Collections.shuffle(relics);
        return relics.isEmpty() ? null : relics.get(0);
    }

    private List<Card> getShopCards(RoomPlayer player) {
        List<Card> available = cardRepo.findByCharacterClassOrCharacterClassIsNull(
                player.getCharacterClass());
        Collections.shuffle(available);
        return available.subList(0, Math.min(GameConstants.CARD_REWARD_COUNT, available.size()));
    }

    private String doRandomEvent(RoomPlayer player) {
        String[] events = {
            "你遇到了一位老神仙，他给了你一些指引。获得10金币。",
            "路边有棵人参果树，摘了一颗吃。回复8点生命值。",
            "遇到小妖怪打劫！失去10金币。",
            "发现了太上老君的丹炉遗迹，获得了一件遗物。",
            "山间的温泉让你神清气爽。回复5点生命值。"
        };
        String event = events[random.nextInt(events.length)];

        if (event.contains("获得10金币")) {
            player.setGold(player.getGold() + 10);
        }
        if (event.contains("8点生命")) {
            player.setHp(Math.min(player.getMaxHp(), player.getHp() + 8));
        }
        if (event.contains("失去")) {
            player.setGold(Math.max(0, player.getGold() - 10));
        }
        if (event.contains("5点生命")) {
            player.setHp(Math.min(player.getMaxHp(), player.getHp() + 5));
        }
        if (event.contains("遗物")) {
            Relic relic = getRandomRelic();
            if (relic != null) player.getRelics().add(relic);
        }
        return event;
    }

    /**
     * 更新房间楼层（供多人战斗系统调用）
     */
    public void setFloor(String code, int floor) {
        Room room = getRoomOrThrow(code);
        room.setFloor(floor);
        roomStore.save(room);
    }

    // ===== 内部方法 =====

    private Room getRoomOrThrow(String code) {
        Room room = roomStore.get(code);
        if (room == null) {
            throw new BusinessException("ROOM_NOT_FOUND", "房间不存在或已解散", 404);
        }
        return room;
    }

    /**
     * 生成唯一8位房间码（冲突时重试，最多10次）
     */
    private String generateUniqueCode() {
        for (int i = 0; i < 10; i++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int j = 0; j < CODE_LENGTH; j++) {
                sb.append(CODE_CHARS[random.nextInt(CODE_CHARS.length)]);
            }
            String code = sb.toString();
            if (!roomStore.codeExists(code)) {
                return code;
            }
            log.warn("Room code collision, retrying: {}", code);
        }
        // 极低概率（约 23^8 ≈ 180亿分之一单次，10次冲突后几乎不可能）
        throw new BusinessException("ROOM_CODE_GENERATION_FAILED",
                "房间码生成失败，请重试", 500);
    }

    private RoomDTO toDTO(Room room) {
        RoomDTO dto = new RoomDTO();
        dto.setCode(room.getCode());
        dto.setHostUserId(room.getHostUserId());
        dto.setPlayers(room.getPlayers());
        dto.setPlayerCount(room.getPlayerCount());
        dto.setStatus(room.getStatus());
        dto.setCreatedAt(room.getCreatedAt());
        dto.setFloor(room.getFloor());
        dto.setMaxLayer(room.getMaxLayer());
        dto.setMap(room.getMap());
        dto.setCurrentNode(room.getCurrentNode());
        dto.setBonfireUpgradesLeft(room.getBonfireUpgradesLeft());
        return dto;
    }
}
