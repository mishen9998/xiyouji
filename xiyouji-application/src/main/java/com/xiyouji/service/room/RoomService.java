package com.xiyouji.service.room;

import com.xiyouji.dto.response.room.RoomDTO;
import com.xiyouji.model.enums.CharacterClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 房间服务（门面）- 管理多人协作房间的创建、加入、退出、准备、地图探索等业务逻辑
 *
 * 统一对外 API 与事务边界，并委托给内聚组件：
 *   - RoomMembershipService  创建/加入/退出/准备/选角色
 *   - RoomProgressionService 开局生成地图/移动到节点/进入下一层
 *   - RoomEventProcessor     节点事件（休息/篝火/宝箱/商店/随机）
 *   - RoomAccess             按房间持锁 + 取房间 + 版本校验模板
 *   - RoomCodeGenerator      房间码生成
 *   - RoomDTOAssembler       Room -> RoomDTO
 *
 * 公开 API 契约不变（Controller 与战斗系统依赖），前端零改动。
 */
@Service
public class RoomService {

    private final RoomAccess access;
    private final RoomDTOAssembler assembler;
    private final RoomMembershipService membershipService;
    private final RoomProgressionService progressionService;
    private final RoomEventProcessor eventProcessor;

    public RoomService(RoomAccess access,
                       RoomDTOAssembler assembler,
                       RoomMembershipService membershipService,
                       RoomProgressionService progressionService,
                       RoomEventProcessor eventProcessor) {
        this.access = access;
        this.assembler = assembler;
        this.membershipService = membershipService;
        this.progressionService = progressionService;
        this.eventProcessor = eventProcessor;
    }

    // ===== 成员管理 =====

    public RoomDTO createRoom(String hostUserId, String hostUsername) {
        return membershipService.createRoom(hostUserId, hostUsername);
    }

    public RoomDTO joinRoom(String code, String userId, String username) {
        return joinRoom(code, userId, username, -1);
    }

    public RoomDTO joinRoom(String code, String userId, String username, long expectedVersion) {
        return membershipService.joinRoom(code, userId, username, expectedVersion);
    }

    public void leaveRoom(String code, String userId) {
        leaveRoom(code, userId, -1);
    }

    public void leaveRoom(String code, String userId, long expectedVersion) {
        membershipService.leaveRoom(code, userId, expectedVersion);
    }

    public RoomDTO toggleReady(String code, String userId) {
        return toggleReady(code, userId, -1);
    }

    public RoomDTO toggleReady(String code, String userId, long expectedVersion) {
        return membershipService.toggleReady(code, userId, expectedVersion);
    }

    public RoomDTO selectCharacter(String code, String userId, CharacterClass characterClass) {
        return selectCharacter(code, userId, characterClass, -1);
    }

    public RoomDTO selectCharacter(String code, String userId, CharacterClass characterClass,
                                   long expectedVersion) {
        return membershipService.selectCharacter(code, userId, characterClass, expectedVersion);
    }

    // ===== 查询 =====

    /** 获取房间信息 */
    public RoomDTO getRoom(String code) {
        return assembler.toDTO(access.getRoomOrThrow(code));
    }

    /** 获取房间领域对象（供战斗系统等内部使用） */
    public Room getRoomEntity(String code) {
        return access.getRoomOrThrow(code);
    }

    /** 房间是否存在 */
    public boolean roomExists(String code) {
        return access.roomExists(code);
    }

    /** 检查是否所有玩家都已准备且选了角色，可否开始游戏 */
    public boolean canStart(String code) {
        Room room = access.getRoomOrThrow(code);
        return room.getStatus() == RoomStatus.WAITING
                && room.allReady()
                && room.getPlayers().stream().allMatch(p -> p.getCharacterClass() != null);
    }

    // ===== 状态切换 =====

    /** 将房间状态切换为战斗中（供战斗系统调用） */
    public void markInBattle(String code) {
        access.withRoomLock(code, () -> {
            Room room = access.getRoomOrThrow(code);
            room.setStatus(RoomStatus.IN_BATTLE);
            access.save(room);
        });
    }

    /** 将房间状态从战斗中恢复为地图探索（战斗结束后调用） */
    @Transactional
    public void markInMap(String code) {
        access.withRoomLock(code, () -> {
            Room room = access.getRoomOrThrow(code);
            room.setStatus(RoomStatus.IN_MAP);
            access.save(room);
        });
    }

    /** 同步战斗中的玩家状态并返回保存后的地图或通关房间。 */
    @Transactional
    public RoomDTO returnFromBattle(String code, String requesterId, List<MultiplayerPlayer> players) {
        return progressionService.returnFromBattle(code, requesterId, players);
    }

    /** 更新房间楼层（供多人战斗系统调用） */
    public void setFloor(String code, int floor) {
        access.withRoomLock(code, () -> {
            Room room = access.getRoomOrThrow(code);
            room.setFloor(floor);
            access.save(room);
        });
    }

    // ===== 地图探索 =====

    @Transactional
    public RoomDTO startGame(String code, String requesterId) {
        return startGame(code, requesterId, -1);
    }

    @Transactional
    public RoomDTO startGame(String code, String requesterId, long expectedVersion) {
        return progressionService.startGame(code, requesterId, expectedVersion);
    }

    @Transactional
    public Map<String, Object> moveToNode(String code, String nodeId) {
        return moveToNode(code, nodeId, -1);
    }

    @Transactional
    public Map<String, Object> moveToNode(String code, String nodeId, long expectedVersion) {
        return moveToNode(code, nodeId, expectedVersion, null);
    }

    @Transactional
    public Map<String, Object> moveToNode(String code, String nodeId, long expectedVersion,
                                          String requesterId) {
        return progressionService.moveToNode(code, nodeId, expectedVersion, requesterId);
    }

    @Transactional
    public Map<String, Object> handleEvent(String code, String userId,
                                           String action, Long cardId, Integer cardIndex) {
        return handleEvent(code, userId, action, cardId, cardIndex, -1);
    }

    @Transactional
    public Map<String, Object> handleEvent(String code, String userId,
                                           String action, Long cardId, Integer cardIndex,
                                           long expectedVersion) {
        return eventProcessor.handleEvent(code, userId, action, cardId, cardIndex, expectedVersion);
    }

    @Transactional
    public Map<String, Object> nextLayer(String code, String requesterId) {
        return nextLayer(code, requesterId, -1);
    }

    @Transactional
    public Map<String, Object> nextLayer(String code, String requesterId, long expectedVersion) {
        return progressionService.nextLayer(code, requesterId, expectedVersion);
    }
}
