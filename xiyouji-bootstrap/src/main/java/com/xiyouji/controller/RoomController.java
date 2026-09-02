package com.xiyouji.controller;

import com.xiyouji.dto.request.room.JoinRoomRequest;
import com.xiyouji.dto.request.room.SelectCharacterRequest;
import com.xiyouji.dto.response.room.RoomDTO;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.service.room.RoomEventPublisher;
import com.xiyouji.service.room.RoomService;
import com.xiyouji.service.CommandGuard;
import com.xiyouji.service.CommandIdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;

/**
 * 房间控制器 - 多人协作房间管理API
 * 提供创建/加入/退出房间、选择角色、切换准备等接口。
 *
 * 当前用户身份从 JWT 的 subject（username）获取，
 * 同时兼容注册用户和游客（guest_xxxx）。
 */
@RestController
@RequestMapping("/api/room")
@Validated
@Tag(name = "多人房间系统", description = "房间创建、加入、角色选择与准备相关API")
public class RoomController {

    private static final Logger log = LoggerFactory.getLogger(RoomController.class);

    private final RoomService roomService;
    private final RoomEventPublisher broadcaster;
    private final CommandIdempotencyService idempotency;

    public RoomController(RoomService roomService, RoomEventPublisher broadcaster,
                          CommandIdempotencyService idempotency) {
        this.roomService = roomService;
        this.broadcaster = broadcaster;
        this.idempotency = idempotency;
    }

    @PostMapping("/create")
    @Operation(summary = "创建房间", description = "生成8位房间码，创建者自动成为房主")
    public RoomDTO createRoom(@RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        String username = currentUsername();
        log.info("Create room request from {}", username);
        String fingerprint = CommandGuard.fingerprint("POST", "/api/room/create", username);
        String scope = "room:create:" + username;
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        RoomDTO cachedResponse = idempotency.replay(previous, RoomDTO.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed() && !previous.value().isBlank()) {
            return roomService.getRoom(previous.value());
        }
        try {
            RoomDTO room = roomService.createRoom(username, username);
            idempotency.completeResponse(scope, idempotencyKey, fingerprint, room);
            return room;
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
    }

    @PostMapping("/join")
    @Operation(summary = "加入房间", description = "凭8位房间码加入房间")
    public RoomDTO joinRoom(@Valid @RequestBody JoinRoomRequest request,
                            @RequestHeader("X-Expected-State-Version") long expectedVersion,
                            @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        String username = currentUsername();
        log.info("Join room request from {}, code={}", username, request.getCode());
        String fingerprint = CommandGuard.fingerprint("POST", "/api/room/join", request.getCode());
        String scope = "room:join:" + username + ":" + request.getCode();
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        RoomDTO cachedResponse = idempotency.replay(previous, RoomDTO.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) return roomService.getRoom(request.getCode());
        RoomDTO room;
        try {
            room = roomService.joinRoom(request.getCode(), username, username, expectedVersion);
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
        // 广播房间状态变化给所有订阅者
        broadcaster.broadcastRoomUpdate(request.getCode(), room);
        broadcaster.broadcastSystemMessage(request.getCode(), username + " 加入了房间");
        idempotency.completeResponse(scope, idempotencyKey, fingerprint, room);
        return room;
    }

    @PostMapping("/{code}/leave")
    @Operation(summary = "退出房间", description = "退出房间；房主退出则解散整个房间")
    public Map<String, Object> leaveRoom(@PathVariable String code,
                                         @RequestHeader("X-Expected-State-Version") long expectedVersion,
                                         @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        String username = currentUsername();
        log.info("Leave room request from {}, code={}", username, code);
        String fingerprint = CommandGuard.fingerprint("POST", "/api/room/" + code + "/leave", "");
        String scope = "room:leave:" + username + ":" + code;
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        Map<String, Object> cachedResponse = idempotency.replay(previous, Map.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) return Map.of("dissolved", true);
        boolean wasHost = username.equals(roomService.getRoomEntity(code).getHostUserId());
        try {
            roomService.leaveRoom(code, username, expectedVersion);
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
        if (wasHost) {
            // 房主退出后房间已解散，广播解散通知
            broadcaster.broadcastSystemMessage(code, "房主已退出，房间已解散");
            Map<String, Object> result = Map.of("dissolved", true);
            idempotency.completeResponse(scope, idempotencyKey, fingerprint, result);
            return result;
        }
        // 非房主退出，广播剩余玩家
        RoomDTO room = roomService.getRoom(code);
        broadcaster.broadcastRoomUpdate(code, room);
        broadcaster.broadcastSystemMessage(code, username + " 退出了房间");
        Map<String, Object> result = Map.of("dissolved", false, "room", room);
        idempotency.completeResponse(scope, idempotencyKey, fingerprint, result);
        return result;
    }

    @PostMapping("/{code}/ready")
    @Operation(summary = "切换准备状态", description = "切换当前玩家的准备状态")
    public RoomDTO toggleReady(@PathVariable String code,
                               @RequestHeader("X-Expected-State-Version") long expectedVersion,
                               @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        String username = currentUsername();
        String fingerprint = CommandGuard.fingerprint("POST", "/api/room/" + code + "/ready", "");
        String scope = "room:ready:" + username + ":" + code;
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        RoomDTO cachedResponse = idempotency.replay(previous, RoomDTO.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) return roomService.getRoom(code);
        RoomDTO room;
        try {
            room = roomService.toggleReady(code, username, expectedVersion);
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
        broadcaster.broadcastRoomUpdate(code, room);
        idempotency.completeResponse(scope, idempotencyKey, fingerprint, room);
        return room;
    }

    @PostMapping("/{code}/character")
    @Operation(summary = "选择角色", description = "选择本局角色职业")
    public RoomDTO selectCharacter(@PathVariable String code,
                                   @Valid @RequestBody SelectCharacterRequest request,
                                   @RequestHeader("X-Expected-State-Version") long expectedVersion,
                                   @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        String username = currentUsername();
        CharacterClass cc = parseCharacterClass(request.getCharacterClass());
        String fingerprint = CommandGuard.fingerprint("POST", "/api/room/" + code + "/character", cc.name());
        String scope = "room:character:" + username + ":" + code;
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        RoomDTO cachedResponse = idempotency.replay(previous, RoomDTO.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) return roomService.getRoom(code);
        RoomDTO room;
        try {
            room = roomService.selectCharacter(code, username, cc, expectedVersion);
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
        broadcaster.broadcastRoomUpdate(code, room);
        idempotency.completeResponse(scope, idempotencyKey, fingerprint, room);
        return room;
    }

    @GetMapping("/{code}")
    @Operation(summary = "获取房间信息", description = "查询房间当前状态与玩家列表")
    public RoomDTO getRoom(@PathVariable String code) {
        return roomService.getRoom(code);
    }

    @GetMapping("/characters")
    @Operation(summary = "获取可选角色列表", description = "返回所有可选角色职业")
    public CharacterClass[] listCharacters() {
        return CharacterClass.values();
    }

    @GetMapping("/{code}/canStart")
    @Operation(summary = "检查是否可开始游戏", description = "所有玩家已准备且选了角色时返回true")
    public boolean canStart(@PathVariable String code) {
        return roomService.canStart(code);
    }

    // ===== 地图探索 API =====

    @PostMapping("/{code}/start-game")
    @Operation(summary = "开始游戏", description = "房主开始游戏，生成第一层地图并初始化所有玩家角色")
    public RoomDTO startGame(@PathVariable String code,
                             @RequestHeader("X-Expected-State-Version") long expectedVersion,
                             @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        String username = currentUsername();
        log.info("Start game request from {}, code={}", username, code);
        String fingerprint = CommandGuard.fingerprint("POST", "/api/room/" + code + "/start-game", "");
        String scope = "room:start:" + username + ":" + code;
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        RoomDTO cachedResponse = idempotency.replay(previous, RoomDTO.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) return roomService.getRoom(code);
        RoomDTO room;
        try {
            room = roomService.startGame(code, username, expectedVersion);
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
        broadcaster.broadcastRoomUpdate(code, room);
        broadcaster.broadcastSystemMessage(code, "游戏开始！探索第1层地图");
        idempotency.completeResponse(scope, idempotencyKey, fingerprint, room);
        return room;
    }

    @PostMapping("/{code}/move")
    @Operation(summary = "移动到节点", description = "房主选择移动到指定地图节点")
    public Map<String, Object> moveToNode(@PathVariable String code,
                                           @RequestBody Map<String, String> request,
                                           @RequestHeader("X-Expected-State-Version") long expectedVersion,
                                           @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        String username = currentUsername();
        String nodeId = request.get("nodeId");
        log.info("Move request from {}, code={}, nodeId={}", username, code, nodeId);
        String fingerprint = CommandGuard.fingerprint("POST", "/api/room/" + code + "/move", nodeId);
        String scope = "room:move:" + username + ":" + code;
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        Map<String, Object> cachedResponse = idempotency.replay(previous, Map.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) {
            RoomDTO existing = roomService.getRoom(code);
            Map<String, Object> replay = new java.util.LinkedHashMap<>();
            replay.put("room", existing);
            replay.put("node", existing.getCurrentNode());
            replay.put("eventType", existing.getCurrentNode() == null ? "unknown"
                    : existing.getCurrentNode().getType().toLowerCase());
            replay.put("stateVersion", existing.getStateVersion());
            return replay;
        }
        Map<String, Object> result;
        try {
            result = roomService.moveToNode(code, nodeId, expectedVersion, username);
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
        broadcaster.broadcastRoomUpdate(code, (RoomDTO) result.get("room"));
        idempotency.completeResponse(scope, idempotencyKey, fingerprint, result);
        return result;
    }

    @PostMapping("/{code}/event")
    @Operation(summary = "处理节点事件", description = "处理休息、篝火升级、宝箱、商店、随机事件等")
    public Map<String, Object> handleEvent(@PathVariable String code,
                                             @RequestBody Map<String, Object> request,
                                             @RequestHeader("X-Expected-State-Version") long expectedVersion,
                                             @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        String username = currentUsername();
        String action = (String) request.getOrDefault("action", "none");
        Long cardId = request.get("cardId") != null ? Long.valueOf(request.get("cardId").toString()) : null;
        Integer cardIndex = request.get("cardIndex") != null ? Integer.valueOf(request.get("cardIndex").toString()) : null;
        log.info("Event request from {}, code={}, action={}", username, code, action);
        String fingerprint = CommandGuard.fingerprint("POST", "/api/room/" + code + "/event",
                action + ":" + cardId + ":" + cardIndex);
        String scope = "room:event:" + username + ":" + code;
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        Map<String, Object> cachedResponse = idempotency.replay(previous, Map.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) return Map.of("room", roomService.getRoom(code));
        Map<String, Object> result;
        try {
            result = roomService.handleEvent(code, username, action, cardId, cardIndex, expectedVersion);
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
        broadcaster.broadcastRoomUpdate(code, roomService.getRoom(code));
        idempotency.completeResponse(scope, idempotencyKey, fingerprint, result);
        return result;
    }

    @PostMapping("/{code}/next-layer")
    @Operation(summary = "进入下一层", description = "Boss击败后房主进入下一层地图")
    public Map<String, Object> nextLayer(@PathVariable String code,
                                         @RequestHeader("X-Expected-State-Version") long expectedVersion,
                                         @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        String username = currentUsername();
        log.info("Next layer request from {}, code={}", username, code);
        String fingerprint = CommandGuard.fingerprint("POST", "/api/room/" + code + "/next-layer", "");
        String scope = "room:next-layer:" + username + ":" + code;
        var previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        Map<String, Object> cachedResponse = idempotency.replay(previous, Map.class);
        if (cachedResponse != null) return cachedResponse;
        if (previous != null && previous.completed()) return Map.of("room", roomService.getRoom(code));
        Map<String, Object> result;
        try {
            result = roomService.nextLayer(code, username, expectedVersion);
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
        broadcaster.broadcastSystemMessage(code, "进入第 " + roomService.getRoom(code).getFloor() + " 层");
        broadcaster.broadcastRoomUpdate(code, roomService.getRoom(code));
        idempotency.completeResponse(scope, idempotencyKey, fingerprint, result);
        return result;
    }

    // ===== 内部方法 =====

    /** 从 SecurityContext 获取当前用户名（JWT subject） */
    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new InvalidActionException("未登录，请先获取游客token");
        }
        return auth.getName();
    }

    /** 解析角色职业字符串，无效则抛出 InvalidActionException */
    private CharacterClass parseCharacterClass(String input) {
        try {
            return CharacterClass.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidActionException(
                    "无效的角色职业: " + input + "，可选: " + Arrays.toString(CharacterClass.values()));
        }
    }
}
