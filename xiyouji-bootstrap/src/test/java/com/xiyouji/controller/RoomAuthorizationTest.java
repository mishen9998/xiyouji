package com.xiyouji.controller;

import com.xiyouji.config.*;
import com.xiyouji.controller.support.*;
import com.xiyouji.exception.*;
import com.xiyouji.service.*;
import com.xiyouji.service.room.*;
import org.junit.jupiter.api.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoomAuthorizationTest {
    RoomService rooms;
    MultiplayerBattleService battles;
    MockMvc mvc;
    @BeforeEach void setup() {
        var store = new InMemoryRoomStore();
        Room room = new Room("ROOM0001", "host");
        room.getPlayers().add(new RoomPlayer("host", "host"));
        room.getPlayers().add(new RoomPlayer("friend", "friend")); store.save(room);
        rooms = new RoomService(new RoomAccess(store, new LocalDistributedLockService()), new RoomDTOAssembler(), null, null, null);
        battles = mock(MultiplayerBattleService.class);
        when(battles.getBattleInfo("ROOM0001")).thenReturn(Map.of("players", List.of(Map.of("userId", "friend", "hand", List.of("shared-card")))));
        var user = new CurrentUserResolver();
        mvc = MockMvcBuilders.standaloneSetup(
                new RoomController(rooms, mock(RoomEventPublisher.class), mock(IdempotentCommandRunner.class), user, new CharacterClassParser()),
                new MultiplayerBattleController(battles, rooms, user))
                .addInterceptors(new RoomAuthorizationConfig(rooms, user).interceptor())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void as(String user) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of())); }
    @Test void outsidersCannotReadRoomBattleExistenceOrStartState() throws Exception {
        as("outsider");
        for (String path : List.of("/api/room/ROOM0001", "/api/room/ROOM0001/canStart", "/api/multiplayer/battle/ROOM0001/state", "/api/multiplayer/battle/ROOM0001/exists"))
            mvc.perform(get(path)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
        verifyNoInteractions(battles);
    }
    @Test void membersRetainSharedTeamHandAndRoomInformation() throws Exception {
        as("host");
        mvc.perform(get("/api/room/ROOM0001")).andExpect(status().isOk()).andExpect(jsonPath("$.players.length()").value(2));
        mvc.perform(get("/api/multiplayer/battle/ROOM0001/state")).andExpect(status().isOk()).andExpect(jsonPath("$.players[0].hand[0]").value("shared-card"));
    }
    @Test void stompSubscriptionRequiresMembershipAndRejectsWildcardAndClientPublishing() {
        var interceptor = new RoomSubscriptionInterceptor(rooms);
        assertDoesNotThrow(() -> interceptor.preSend(message("host", "/topic/room/ROOM0001/battle", StompCommand.SUBSCRIBE), null));
        assertThrows(BusinessException.class, () -> interceptor.preSend(message("outsider", "/topic/room/ROOM0001", StompCommand.SUBSCRIBE), null));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> interceptor.preSend(message("host", "/topic/room/*", StompCommand.SUBSCRIBE), null));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> interceptor.preSend(message("host", "/topic/room/ROOM0001", StompCommand.SEND), null));
    }
    org.springframework.messaging.Message<byte[]> message(String user, String destination, StompCommand command) {
        var headers = StompHeaderAccessor.create(command);
        headers.setDestination(destination); headers.setSessionAttributes(Map.of("username", user));
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
