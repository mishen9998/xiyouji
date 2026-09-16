package com.xiyouji.config;

import com.xiyouji.service.room.RoomService;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Component
public class RoomSubscriptionInterceptor implements ChannelInterceptor {
    private static final Pattern TOPIC = Pattern.compile("^/topic/room/([A-Z0-9]{8})(/battle)?$");
    private final RoomService rooms;
    private final java.util.concurrent.ConcurrentHashMap<String, String> members = new java.util.concurrent.ConcurrentHashMap<>();
    public RoomSubscriptionInterceptor(RoomService rooms) { this.rooms = rooms; }
    @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) return message;
        if (headers.getCommand() == StompCommand.SEND)
            throw new AccessDeniedException("ACCESS_DENIED: writes require authenticated REST commands");
        if (headers.getCommand() == StompCommand.SUBSCRIBE) {
            String destination = headers.getDestination();
            var match = TOPIC.matcher(destination == null ? "" : destination);
            var attributes = headers.getSessionAttributes();
            Object username = attributes == null ? null : attributes.get("username");
            if (!match.matches() || username == null)
                throw new AccessDeniedException("ACCESS_DENIED: invalid room subscription");
            rooms.assertMember(match.group(1), username.toString());
            if (headers.getSessionId() != null) members.put(headers.getSessionId(), username.toString());
        }
        return message;
    }
    /** A member who leaves cannot keep receiving team hands on an old subscription. */
    public Message<?> authorizeOutbound(Message<?> message) {
        String destination = org.springframework.messaging.simp.SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        if (destination == null) return message;
        var match = TOPIC.matcher(destination);
        if (!match.matches()) return message;
        String session = org.springframework.messaging.simp.SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        String user = session == null ? null : members.get(session);
        if (user == null) return null;
        try { rooms.assertMember(match.group(1), user); return message; }
        catch (RuntimeException deniedOrUnavailable) { return null; }
    }
    @org.springframework.context.event.EventListener
    public void disconnected(org.springframework.web.socket.messaging.SessionDisconnectEvent event) {
        members.remove(event.getSessionId());
    }
}
