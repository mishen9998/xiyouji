package com.xiyouji.service.room;

import com.xiyouji.dto.response.room.RoomDTO;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.enums.CharacterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 房间成员管理组件
 *
 * 负责：创建、加入、退出、准备切换与角色选择。
 * 所有写操作经 RoomAccess 按房间粒度持锁并以状态版本保护并发。
 */
@Component
public class RoomMembershipService {

    private static final Logger log = LoggerFactory.getLogger(RoomMembershipService.class);

    private final RoomAccess access;
    private final RoomDTOAssembler assembler;
    private final RoomCodeGenerator codeGenerator;

    public RoomMembershipService(RoomAccess access, RoomDTOAssembler assembler,
                                 RoomCodeGenerator codeGenerator) {
        this.access = access;
        this.assembler = assembler;
        this.codeGenerator = codeGenerator;
    }

    /** 创建房间（全局创建锁保证房间码查重与写入原子） */
    public RoomDTO createRoom(String hostUserId, String hostUsername) {
        return access.withCreateLock(() -> {
            String code = codeGenerator.generate();
            Room room = new Room(code, hostUserId);

            RoomPlayer host = new RoomPlayer(hostUserId, hostUsername);
            host.setHost(true);
            host.setReady(false);
            room.getPlayers().add(host);

            access.save(room);
            log.info("Room created: code={}, host={}", code, hostUsername);
            return assembler.toDTO(room);
        });
    }

    /** 加入房间（房间锁防止两玩家同时加入突破 5 人上限） */
    public RoomDTO joinRoom(String code, String userId, String username, long expectedVersion) {
        return access.withRoomLock(code, () -> {
            Room room = access.getRoomOrThrow(code);
            // Re-entering after a refresh is a read, even when the room is full or already playing.
            if (room.hasPlayer(userId)) return assembler.toDTO(room);
            access.checkVersion(code, room, expectedVersion);

            if (room.getStatus() != RoomStatus.WAITING) {
                throw new InvalidActionException("房间已开始游戏，无法加入");
            }
            if (room.isFull()) {
                throw new InvalidActionException("房间已满（5人）");
            }

            RoomPlayer player = new RoomPlayer(userId, username);
            room.getPlayers().add(player);
            access.save(room);
            log.info("Player {} joined room {}", username, code);
            return assembler.toDTO(room);
        });
    }

    /** 退出房间（房主退出则解散整个房间） */
    public void leaveRoom(String code, String userId, long expectedVersion) {
        access.withRoomLock(code, () -> {
            Room room = access.getRoomOrThrow(code);
            access.checkVersion(code, room, expectedVersion);

            if (!room.hasPlayer(userId)) {
                throw new InvalidActionException("你不在该房间内");
            }

            if (room.getHostUserId().equals(userId)) {
                // 房主退出，解散房间
                access.remove(code);
                log.info("Host {} left, room {} dissolved", userId, code);
            } else {
                room.getPlayers().removeIf(p -> p.getUserId().equals(userId));
                access.save(room);
                log.info("Player {} left room {}", userId, code);
            }
        });
    }

    /** 切换准备状态（房间锁避免房主检查"全员已准备"与玩家取消准备的竞态） */
    public RoomDTO toggleReady(String code, String userId, long expectedVersion) {
        return access.withRoomLock(code, () -> {
            Room room = access.getRoomOrThrow(code);
            access.checkVersion(code, room, expectedVersion);

            if (room.getStatus() != RoomStatus.WAITING) {
                throw new InvalidActionException("游戏已开始，无法切换准备状态");
            }

            RoomPlayer player = room.getPlayers().stream()
                    .filter(p -> p.getUserId().equals(userId))
                    .findFirst()
                    .orElseThrow(() -> new InvalidActionException("你不在该房间内"));

            player.setReady(!player.isReady());
            access.save(room);
            log.info("Player {} toggled ready={} in room {}", userId, player.isReady(), code);
            return assembler.toDTO(room);
        });
    }

    /** 选择角色（房间锁防止两玩家同时选同一角色） */
    public RoomDTO selectCharacter(String code, String userId, CharacterClass characterClass,
                                   long expectedVersion) {
        return access.withRoomLock(code, () -> {
            Room room = access.getRoomOrThrow(code);
            access.checkVersion(code, room, expectedVersion);

            if (room.getStatus() != RoomStatus.WAITING) {
                throw new InvalidActionException("游戏已开始，无法选择角色");
            }

            RoomPlayer player = room.getPlayers().stream()
                    .filter(p -> p.getUserId().equals(userId))
                    .findFirst()
                    .orElseThrow(() -> new InvalidActionException("你不在该房间内"));

            boolean occupied = room.getPlayers().stream()
                    .filter(p -> !p.getUserId().equals(userId))
                    .anyMatch(p -> characterClass == p.getCharacterClass());
            if (occupied) {
                throw new InvalidActionException("该角色已被其他玩家选择");
            }

            player.setCharacterClass(characterClass);
            access.save(room);
            log.info("Player {} selected {} in room {}", userId, characterClass, code);
            return assembler.toDTO(room);
        });
    }
}