package com.xiyouji.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyouji.config.RoomAuthorizationConfig;
import com.xiyouji.controller.support.CharacterClassParser;
import com.xiyouji.controller.support.CurrentUserResolver;
import com.xiyouji.exception.GlobalExceptionHandler;
import com.xiyouji.service.*;
import com.xiyouji.service.room.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real admission, receipt serialization and membership revocation, with only publishing mocked. */
class JoinReplayAuthorizationTest {
    private static final String CODE = "JOIN0001";
    private static final String OTHER = "OTHER001";
    private static final String KEY = "original-join";
    private static final String PATH = "/api/room/join";
    private InMemoryRoomStore roomStore;
    private LocalIdempotencyStore commands;
    private RoomService rooms;
    private RoomEventPublisher publisher;
    private MockMvc mvc;

    @BeforeEach void setup() {
        roomStore = new InMemoryRoomStore();
        Room room = new Room(CODE, "host");
        room.getPlayers().add(new RoomPlayer("host", "host"));
        roomStore.save(room);
        var access = new RoomAccess(roomStore, new LocalDistributedLockService());
        var assembler = new RoomDTOAssembler();
        rooms = new RoomService(access, assembler,
                new RoomMembershipService(access, assembler, mock(RoomCodeGenerator.class)), null, null);
        commands = new LocalIdempotencyStore();
        var service = new CommandIdempotencyService(commands, new ObjectMapper().findAndRegisterModules());
        var user = new CurrentUserResolver();
        publisher = mock(RoomEventPublisher.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new RoomController(rooms, publisher, new IdempotentCommandRunner(service), user, new CharacterClassParser()),
                new CommandReceiptController(service, user, rooms))
                .addMappedInterceptors(new String[]{"/api/room/**"}, new RoomAuthorizationConfig(rooms, user).interceptor())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        as("friend");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void as(String user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
    private MockHttpServletRequestBuilder join() {
        return post(PATH).header("X-Idempotency-Key", KEY).contentType("application/json")
                .content("{\"code\":\"" + CODE + "\"}");
    }
    private MockHttpServletRequestBuilder receipt(String resource) {
        return get("/api/commands/receipt").param("path", PATH).param("commandId", KEY).param("resource", resource);
    }
    private String storedKey(String code) { return "room:join:friend:" + code + ":" + KEY; }
    private void leave() throws Exception {
        mvc.perform(post("/api/room/" + CODE + "/leave")
                        .header("X-Idempotency-Key", "leave-once")
                        .header("X-Expected-State-Version", rooms.getRoomEntity(CODE).getStateVersion()))
                .andExpect(status().isOk());
        clearInvocations(publisher);
    }
    private void legacyCompleted() {
        rooms.joinRoom(CODE, "friend", "friend", -1);
        commands.reserve(storedKey(CODE), new IdempotencyStore.Entry(
                CommandGuard.fingerprint("POST", PATH, CODE), "done", true), CommandGuard.TTL);
    }

    @Test void firstAdmissionWorksAndCurrentMemberCanRecoverWithoutRebroadcast() throws Exception {
        assertFalse(rooms.getRoomEntity(CODE).hasPlayer("friend"));
        mvc.perform(join()).andExpect(status().isOk()).andExpect(jsonPath("$.playerCount").value(2));
        verify(publisher).broadcastRoomUpdate(eq(CODE), any());
        verify(publisher).broadcastSystemMessage(CODE, "friend 加入了房间");
        long version = rooms.getRoomEntity(CODE).getStateVersion();
        clearInvocations(publisher);
        mvc.perform(join()).andExpect(status().isOk()).andExpect(jsonPath("$.playerCount").value(2));
        mvc.perform(receipt(CODE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.response.code").value(CODE));
        assertEquals(version, rooms.getRoomEntity(CODE).getStateVersion());
        verifyNoInteractions(publisher);
    }

    @Test void departedMemberCannotReadCachedJoinOrReceiptAndIsNotReadmitted() throws Exception {
        mvc.perform(join()).andExpect(status().isOk());
        leave();
        long version = rooms.getRoomEntity(CODE).getStateVersion();
        mvc.perform(get("/api/room/" + CODE)).andExpect(status().isForbidden());
        mvc.perform(receipt(CODE)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
        mvc.perform(join()).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
        assertFalse(rooms.getRoomEntity(CODE).hasPlayer("friend"));
        assertEquals(version, rooms.getRoomEntity(CODE).getStateVersion());
        assertTrue(commands.find(storedKey(CODE)).orElseThrow().completed());
        verifyNoInteractions(publisher);
    }

    @Test void currentMemberCanReplayLegacyCompletionWithoutRebroadcast() throws Exception {
        legacyCompleted();
        long version = rooms.getRoomEntity(CODE).getStateVersion();
        mvc.perform(join()).andExpect(status().isOk()).andExpect(jsonPath("$.playerCount").value(2));
        assertEquals(version, rooms.getRoomEntity(CODE).getStateVersion());
        verifyNoInteractions(publisher);
    }

    @Test void departedMemberCannotReplayLegacyCompletionOrSeeNewTeamState() throws Exception {
        legacyCompleted();
        leave();
        rooms.getRoomEntity(CODE).getPlayers().get(0).setGold(777);
        mvc.perform(join()).andExpect(status().isForbidden()).andExpect(jsonPath("$.players").doesNotExist());
        assertEquals(1, rooms.getRoomEntity(CODE).getPlayers().size());
        assertEquals("done", commands.find(storedKey(CODE)).orElseThrow().value());
        verifyNoInteractions(publisher);
    }

    @Test void changingReceiptResourceCannotAuthorizeAnUnrelatedOriginalRoom() throws Exception {
        mvc.perform(join()).andExpect(status().isOk());
        var original = commands.find(storedKey(CODE)).orElseThrow();
        leave();
        Room other = new Room(OTHER, "friend");
        other.getPlayers().add(new RoomPlayer("friend", "friend"));
        roomStore.save(other);
        mvc.perform(receipt(OTHER)).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("RESULT_UNKNOWN"));
        mvc.perform(receipt("../" + CODE)).andExpect(status().isBadRequest());
        // Even a wrongly associated stored receipt must match the original join fingerprint.
        commands.reserve(storedKey(OTHER), original, CommandGuard.TTL);
        mvc.perform(receipt(OTHER)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.response").doesNotExist());
        mvc.perform(receipt(CODE)).andExpect(status().isForbidden());
        verifyNoInteractions(publisher);
    }
}
