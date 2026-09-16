package com.xiyouji.controller;

import com.xiyouji.controller.support.CurrentUserResolver;
import com.xiyouji.exception.BusinessException;
import com.xiyouji.exception.ResultUnknownException;
import com.xiyouji.service.CommandIdempotencyService;
import com.xiyouji.service.CommandGuard;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Read-only recovery: never reserve, renew, execute, or infer success from a version. */
@RestController
@RequestMapping("/api/commands")
public class CommandReceiptController {
    private final CommandIdempotencyService commands;
    private final CurrentUserResolver users;
    private final com.xiyouji.service.room.RoomService rooms;
    public CommandReceiptController(CommandIdempotencyService commands, CurrentUserResolver users,
                                    com.xiyouji.service.room.RoomService rooms) {
        this.commands = commands;
        this.users = users;
        this.rooms = rooms;
    }
    @GetMapping("/receipt")
    public Map<String, Object> receipt(@RequestParam String path, @RequestParam String commandId,
                                       @RequestParam(defaultValue = "") String resource) {
        String user = users.username();
        String scope = scope(path, resource, user);
        var entry = commands.receipt(scope, commandId);
        if (!entry.completed()) throw new ResultUnknownException();
        if (path.equals("/api/room/join")) {
            // scope already binds user + original room; also verify the stored request fingerprint.
            if (!CommandGuard.fingerprint("POST", path, resource).equals(entry.fingerprint()))
                throw new BusinessException("INVALID_COMMAND", "回执与原加入房间不匹配", 400);
            rooms.assertMember(resource, user);
        }
        if (entry.resourceRef() != null && entry.resourceRef().startsWith("room:")) rooms.assertMember(entry.resourceRef().substring(5), user);
        String[] segments = path.split("/");
        if (segments.length == 5 && segments[2].equals("room") && !segments[4].equals("leave")) rooms.assertMember(segments[3], user);
        if (segments.length == 6 && segments[2].equals("multiplayer")) rooms.assertMember(segments[4], user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("commandId", commandId);
        result.put("status", "COMPLETED");
        result.put("response", commands.replay(entry, Map.class));
        result.put("resourceRef", entry.resourceRef());
        return result;
    }
    private String scope(String path, String resource, String user) {
        if (path.equals("/api/room/create")) return "room:create:" + user;
        if (path.equals("/api/room/join") && resource.matches("[A-Z0-9]{8}")) return "room:join:" + user + ":" + resource;
        if (path.equals("/api/game/new")) return "game:new:" + user;
        String[] parts = path.split("/");
        if (parts.length == 5 && parts[1].equals("api") && parts[2].equals("room")) {
            String operation = parts[4].equals("start-game") ? "start" : parts[4];
            if (Set.of("leave", "ready", "character", "start", "move", "event", "next-layer").contains(operation))
                return "room:" + operation + ":" + user + ":" + parts[3];
        }
        if (parts.length == 6 && parts[1].equals("api") && parts[2].equals("multiplayer") && parts[3].equals("battle")
                && Set.of("start", "play", "endturn", "claim-reward", "skip-reward", "next-floor").contains(parts[5]))
            return parts[5] + ":" + parts[4] + ":" + user;
        if (parts.length >= 5 && parts[1].equals("api") && parts[2].equals("game")) {
            String operation = parts[3];
            if (parts.length == 6 && Set.of("battle", "reward", "deck").contains(operation))
                operation += operation.equals("deck") ? "-" + parts[4] : ":" + parts[4];
            if (operation.equals("sessions")) operation = "delete";
            if (Set.of("delete", "move", "event", "next-layer", "deck-remove", "battle:start", "battle:play", "battle:endturn", "reward:choose", "reward:skip").contains(operation))
                return "game:" + operation + ":" + user + ":" + parts[parts.length - 1];
        }
        throw new BusinessException("INVALID_COMMAND", "不支持的命令回执", 400);
    }
}
