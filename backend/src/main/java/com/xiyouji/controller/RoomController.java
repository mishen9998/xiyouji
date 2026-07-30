package com.xiyouji.controller;

import com.xiyouji.dto.request.room.JoinRoomRequest;
import com.xiyouji.dto.request.room.SelectCharacterRequest;
import com.xiyouji.dto.response.room.RoomDTO;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.enums.CharacterClass;
import com.xiyouji.service.room.RoomEventBroadcaster;
import com.xiyouji.service.room.RoomService;
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
    private final RoomEventBroadcaster broadcaster;

    public RoomController(RoomService roomService, RoomEventBroadcaster broadcaster) {
        this.roomService = roomService;
        this.broadcaster = broadcaster;
    }

    @PostMapping("/create")
    @Operation(summary = "创建房间", description = "生成8位房间码，创建者自动成为房主")
    public RoomDTO createRoom() {
        String username = currentUsername();
        log.info("Create room request from {}", username);
        RoomDTO room = roomService.createRoom(username, username);
        return room;
    }

    @PostMapping("/join")
    @Operation(summary = "加入房间", description = "凭8位房间码加入房间")
    public RoomDTO joinRoom(@Valid @RequestBody JoinRoomRequest request) {
        String username = currentUsername();
        log.info("Join room request from {}, code={}", username, request.getCode());
        RoomDTO room = roomService.joinRoom(request.getCode(), username, username);
        // 广播房间状态变化给所有订阅者
        broadcaster.broadcastRoomUpdate(request.getCode(), room);
        broadcaster.broadcastSystemMessage(request.getCode(), username + " 加入了房间");
        return room;
    }

    @PostMapping("/{code}/leave")
    @Operation(summary = "退出房间", description = "退出房间；房主退出则解散整个房间")
    public Map<String, Object> leaveRoom(@PathVariable String code) {
        String username = currentUsername();
        log.info("Leave room request from {}, code={}", username, code);
        boolean wasHost = username.equals(roomService.getRoomEntity(code).getHostUserId());
        roomService.leaveRoom(code, username);
        if (wasHost) {
            // 房主退出后房间已解散，广播解散通知
            broadcaster.broadcastSystemMessage(code, "房主已退出，房间已解散");
            return Map.of("dissolved", true);
        }
        // 非房主退出，广播剩余玩家
        RoomDTO room = roomService.getRoom(code);
        broadcaster.broadcastRoomUpdate(code, room);
        broadcaster.broadcastSystemMessage(code, username + " 退出了房间");
        return Map.of("dissolved", false, "room", room);
    }

    @PostMapping("/{code}/ready")
    @Operation(summary = "切换准备状态", description = "切换当前玩家的准备状态")
    public RoomDTO toggleReady(@PathVariable String code) {
        String username = currentUsername();
        RoomDTO room = roomService.toggleReady(code, username);
        broadcaster.broadcastRoomUpdate(code, room);
        return room;
    }

    @PostMapping("/{code}/character")
    @Operation(summary = "选择角色", description = "选择本局角色职业")
    public RoomDTO selectCharacter(@PathVariable String code,
                                   @Valid @RequestBody SelectCharacterRequest request) {
        String username = currentUsername();
        CharacterClass cc = parseCharacterClass(request.getCharacterClass());
        RoomDTO room = roomService.selectCharacter(code, username, cc);
        broadcaster.broadcastRoomUpdate(code, room);
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
    public RoomDTO startGame(@PathVariable String code) {
        String username = currentUsername();
        log.info("Start game request from {}, code={}", username, code);
        RoomDTO room = roomService.startGame(code, username);
        broadcaster.broadcastRoomUpdate(code, room);
        broadcaster.broadcastSystemMessage(code, "游戏开始！探索第1层地图");
        return room;
    }

    @PostMapping("/{code}/move")
    @Operation(summary = "移动到节点", description = "房主选择移动到指定地图节点")
    public Map<String, Object> moveToNode(@PathVariable String code,
                                           @RequestBody Map<String, String> request) {
        String username = currentUsername();
        String nodeId = request.get("nodeId");
        log.info("Move request from {}, code={}, nodeId={}", username, code, nodeId);
        Map<String, Object> result = roomService.moveToNode(code, nodeId);
        broadcaster.broadcastRoomUpdate(code, (RoomDTO) result.get("room"));
        return result;
    }

    @PostMapping("/{code}/event")
    @Operation(summary = "处理节点事件", description = "处理休息、篝火升级、宝箱、商店、随机事件等")
    public Map<String, Object> handleEvent(@PathVariable String code,
                                             @RequestBody Map<String, Object> request) {
        String username = currentUsername();
        String action = (String) request.getOrDefault("action", "none");
        Long cardId = request.get("cardId") != null ? Long.valueOf(request.get("cardId").toString()) : null;
        Integer cardIndex = request.get("cardIndex") != null ? Integer.valueOf(request.get("cardIndex").toString()) : null;
        log.info("Event request from {}, code={}, action={}", username, code, action);
        Map<String, Object> result = roomService.handleEvent(code, username, action, cardId, cardIndex);
        broadcaster.broadcastRoomUpdate(code, roomService.getRoom(code));
        return result;
    }

    @PostMapping("/{code}/next-layer")
    @Operation(summary = "进入下一层", description = "Boss击败后房主进入下一层地图")
    public Map<String, Object> nextLayer(@PathVariable String code) {
        String username = currentUsername();
        log.info("Next layer request from {}, code={}", username, code);
        Map<String, Object> result = roomService.nextLayer(code, username);
        broadcaster.broadcastSystemMessage(code, "进入第 " + roomService.getRoom(code).getFloor() + " 层");
        broadcaster.broadcastRoomUpdate(code, roomService.getRoom(code));
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
