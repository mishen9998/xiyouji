package com.xiyouji.service.room;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.dto.response.room.RoomDTO;
import com.xiyouji.exception.BusinessException;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.Card;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.MapNode;
import com.xiyouji.port.CardRepositoryPort;
import com.xiyouji.port.CharacterRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 房间游戏进程组件
 *
 * 负责：开局生成第一层地图并初始化玩家角色、房主移动到节点、Boss 击败后进入下一层。
 */
@Component
public class RoomProgressionService {

    private static final Logger log = LoggerFactory.getLogger(RoomProgressionService.class);

    private final RoomAccess access;
    private final MultiplayerMapService mapService;
    private final CharacterRepositoryPort characterRepo;
    private final CardRepositoryPort cardRepo;
    private final RoomDTOAssembler assembler;

    public RoomProgressionService(RoomAccess access, MultiplayerMapService mapService,
                                  CharacterRepositoryPort characterRepo, CardRepositoryPort cardRepo,
                                  RoomDTOAssembler assembler) {
        this.access = access;
        this.mapService = mapService;
        this.characterRepo = characterRepo;
        this.cardRepo = cardRepo;
        this.assembler = assembler;
    }

    /** 房主开始游戏 - 生成第一层地图并初始化所有玩家角色 */
    public RoomDTO startGame(String code, String requesterId, long expectedVersion) {
        // 房间锁防止房主双击"开始游戏"触发两次地图生成
        return access.withRoomLock(code, () -> {
            Room room = access.getRoomOrThrow(code);
            access.checkVersion(code, room, expectedVersion);

            if (!room.getHostUserId().equals(requesterId)) {
                throw new InvalidActionException("只有房主才能开始游戏");
            }
            if (room.getStatus() != RoomStatus.WAITING) {
                // 幂等保护：已开始则直接返回当前状态，不重复生成地图
                log.info("startGame called but room {} already in status {}", code, room.getStatus());
                return assembler.toDTO(room);
            }
            if (!canStart(room)) {
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

            access.save(room);
            log.info("Game started: room={}, players={}, floor=1", code, room.getPlayerCount());
            return assembler.toDTO(room);
        });
    }

    private boolean canStart(Room room) {
        return room.getStatus() == RoomStatus.WAITING
                && room.allReady()
                && room.getPlayers().stream().allMatch(p -> p.getCharacterClass() != null);
    }

    /** 房主移动到指定节点，返回 eventType / node / room */
    public Map<String, Object> moveToNode(String code, String nodeId, long expectedVersion,
                                          String requesterId) {
        return access.withRoomLock(code, () -> {
            Room room = access.getRoomOrThrow(code);
            access.checkVersion(code, room, expectedVersion);

            if (requesterId != null && !room.getHostUserId().equals(requesterId)) {
                throw new InvalidActionException("只有房主才能移动地图");
            }

            if (room.getStatus() != RoomStatus.IN_MAP) {
                throw new InvalidActionException("当前不在地图探索阶段");
            }

            MapNode node = mapService.moveToNode(room, nodeId);
            String eventType = node.domainEventType();

            access.save(room);
            log.info("Room {} moved to node: {}, event: {}", code, nodeId, eventType);

            Map<String, Object> result = new HashMap<>();
            result.put("node", node);
            result.put("eventType", eventType);
            result.put("room", assembler.toDTO(room));
            return result;
        });
    }

    /** 战斗结束后同步玩家状态并归图；整个过程只保存同一个权威房间对象。 */
    public RoomDTO returnFromBattle(String code, String requesterId, List<MultiplayerPlayer> players) {
        return access.withRoomLock(code, () -> {
            Room room = access.getRoomOrThrow(code);
            if (!room.getHostUserId().equals(requesterId)) {
                throw new InvalidActionException("只有房主才能继续");
            }
            for (MultiplayerPlayer player : players) {
                room.getPlayers().stream()
                        .filter(member -> member.getUserId().equals(player.getUserId()))
                        .findFirst()
                        .ifPresent(member -> member.syncFromCharacter(player.getCharacter()));
            }

            boolean completed = mapService.isAtBoss(room) && !mapService.advanceToNextLayer(room);
            room.setStatus(completed ? RoomStatus.FINISHED : RoomStatus.IN_MAP);
            access.save(room);
            return assembler.toDTO(room);
        });
    }

    /** Boss 击败后进入下一层 */
    public Map<String, Object> nextLayer(String code, String requesterId, long expectedVersion) {
        return access.withRoomLock(code, () -> {
            Room room = access.getRoomOrThrow(code);
            access.checkVersion(code, room, expectedVersion);

            if (!room.getHostUserId().equals(requesterId)) {
                throw new InvalidActionException("只有房主才能进入下一层");
            }

            boolean success = mapService.advanceToNextLayer(room);
            Map<String, Object> result = new HashMap<>();

            if (success) {
                room.setStatus(RoomStatus.IN_MAP);
                access.save(room);
                result.put("room", assembler.toDTO(room));
                result.put("message", "进入第 " + room.getFloor() + " 层");
            } else {
                room.setStatus(RoomStatus.FINISHED);
                access.save(room);
                result.put("message", "恭喜通关！西天取经圆满！");
            }
            result.put("stateVersion", room.getStateVersion());
            result.put("room", assembler.toDTO(room));
            return result;
        });
    }
}
