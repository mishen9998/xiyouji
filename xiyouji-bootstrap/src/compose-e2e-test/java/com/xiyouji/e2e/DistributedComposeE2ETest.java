package com.xiyouji.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box verification for the two Spring Boot instances started by
 * docker-compose.e2e.yml. It is intentionally outside the normal test source
 * tree and runs only with the compose-e2e Maven profile.
 */
class DistributedComposeE2ETest {

    private static final String APP_1 = System.getProperty("xiyouji.e2e.app1", "http://localhost:18081");
    private static final String APP_2 = System.getProperty("xiyouji.e2e.app2", "http://localhost:18082");
    // The browser is served by Nginx on :8080 in the production-like stack;
    // only the test transport connects to each app's temporary published port.
    private static final String WS_ORIGIN = System.getProperty("xiyouji.e2e.origin", "http://localhost:8080");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<WebSocket> sockets = new ArrayList<>();

    @AfterEach
    void closeSockets() {
        sockets.forEach(socket -> socket.sendClose(WebSocket.NORMAL_CLOSURE, "test complete"));
    }

    @Test
    void sharedRoomStateAndCrossInstanceWebSocketNotification() throws Exception {
        Guest host = guest(APP_1);
        Guest player = guest(APP_2);
        String roomCode = null;
        try {
            JsonNode created = request(APP_1, "POST", "/api/room/create", host.token(), null).json();
            roomCode = created.get("code").asText();
            assertThat(created.get("playerCount").asInt()).isEqualTo(1);

            JsonNode joined = request(APP_2, "POST", "/api/room/join", player.token(),
                    "{\"code\":\"" + roomCode + "\"}", created.get("stateVersion").asLong()).json();
            assertThat(joined.get("code").asText()).isEqualTo(roomCode);
            assertThat(joined.get("playerCount").asInt()).isEqualTo(2);

            StompClient app1Socket = connect(APP_1, host.token(), roomCode);
            StompClient app2Socket = connect(APP_2, player.token(), roomCode);

            // This mutation is sent to instance 2. The event must cross Redis
            // Pub/Sub and arrive at the client connected to instance 1.
            JsonNode ready = request(APP_2, "POST", "/api/room/" + roomCode + "/ready",
                    player.token(), null, joined.get("stateVersion").asLong()).json();
            assertThat(ready.get("stateVersion").asLong()).isGreaterThan(2);

            JsonNode event = mapper.readTree(app1Socket.roomUpdate().get(10, TimeUnit.SECONDS));
            assertThat(event.get("code").asText()).isEqualTo(roomCode);
            assertThat(event.get("players").size()).isEqualTo(2);
            assertThat(event.get("stateVersion").asLong()).isEqualTo(ready.get("stateVersion").asLong());
            assertThat(app2Socket).isNotNull();
        } finally {
            if (roomCode != null) {
                JsonNode latest = request(APP_1, "GET", "/api/room/" + roomCode, host.token(), null).json();
                request(APP_1, "POST", "/api/room/" + roomCode + "/leave", host.token(), null,
                        latest.get("stateVersion").asLong());
            }
        }
    }

    @Test
    void concurrentJoinRequestsNeverExceedFivePlayers() throws Exception {
        Guest host = guest(APP_1);
        String roomCode = null;
        try {
            JsonNode created = request(APP_1, "POST", "/api/room/create", host.token(), null).json();
            roomCode = created.get("code").asText();
            String code = roomCode;
            long initialVersion = created.get("stateVersion").asLong();
            List<CompletableFuture<HttpResponse<String>>> requests = IntStream.range(0, 20)
                    .mapToObj(index -> CompletableFuture.supplyAsync(() -> {
                                try {
                                    return guest(index % 2 == 0 ? APP_1 : APP_2);
                                } catch (Exception error) {
                                    throw new CompletionException(error);
                                }
                            })
                            .thenCompose(guest -> sendAsync(index % 2 == 0 ? APP_1 : APP_2,
                                    "POST", "/api/room/join", guest.token(),
                                    "{\"code\":\"" + code + "\"}", initialVersion)))
                    .toList();

            long successfulJoins = requests.stream()
                    .map(CompletableFuture::join)
                    .filter(response -> response.statusCode() == 200)
                    .count();

            JsonNode finalRoom = request(APP_2, "GET", "/api/room/" + roomCode, host.token(), null).json();
            assertThat(successfulJoins).isLessThanOrEqualTo(4);
            assertThat(finalRoom.get("playerCount").asInt()).isLessThanOrEqualTo(5);
        } finally {
            if (roomCode != null) {
                JsonNode latest = request(APP_1, "GET", "/api/room/" + roomCode, host.token(), null).json();
                request(APP_1, "POST", "/api/room/" + roomCode + "/leave", host.token(), null,
                        latest.get("stateVersion").asLong());
            }
        }
    }

    private Guest guest(String baseUrl) throws Exception {
        JsonNode response = request(baseUrl, "POST", "/api/auth/guest", null, null).json();
        return new Guest(response.get("username").asText(), response.get("token").asText());
    }

    private StompClient connect(String baseUrl, String token, String roomCode) throws Exception {
        StompClient listener = new StompClient(roomCode);
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        WebSocket socket = http.newWebSocketBuilder()
                .header("Origin", WS_ORIGIN)
                .connectTimeout(REQUEST_TIMEOUT)
                .buildAsync(URI.create(baseUrl.replace("http://", "ws://") + "/ws?token=" + encodedToken), listener)
                .get(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        sockets.add(socket);
        listener.connected().get(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        listener.subscribe();
        return listener;
    }

    private Response request(String baseUrl, String method, String path, String token, String body) throws Exception {
        return request(baseUrl, method, path, token, body, null);
    }

    private Response request(String baseUrl, String method, String path, String token, String body,
                             Long expectedVersion) throws Exception {
        HttpResponse<String> response = sendAsync(baseUrl, method, path, token, body, expectedVersion)
                .get(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(response.statusCode())
                .as(method + " " + path + " response: " + response.body())
                .isBetween(200, 299);
        return new Response(response.statusCode(), response.body());
    }

    private CompletableFuture<HttpResponse<String>> sendAsync(String baseUrl, String method, String path,
                                                               String token, String body) {
        return sendAsync(baseUrl, method, path, token, body, null);
    }

    private CompletableFuture<HttpResponse<String>> sendAsync(String baseUrl, String method, String path,
                                                               String token, String body, Long expectedVersion) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
        if (!("GET".equals(method) || ("POST".equals(method) && path.equals("/api/auth/login")))) {
            builder.header("X-Idempotency-Key", UUID.randomUUID().toString());
            if (expectedVersion != null) {
                builder.header("X-Expected-State-Version", String.valueOf(expectedVersion));
            }
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private record Guest(String username, String token) {}

    private record Response(int statusCode, String body) {
        JsonNode json() throws Exception {
            return new ObjectMapper().readTree(body);
        }
    }

    private static final class StompClient implements WebSocket.Listener {
        private final String destination;
        private final StringBuilder frameBuffer = new StringBuilder();
        private final CompletableFuture<WebSocket> connected = new CompletableFuture<>();
        private final CompletableFuture<String> roomUpdate = new CompletableFuture<>();
        private volatile WebSocket socket;

        private StompClient(String roomCode) {
            this.destination = "/topic/room/" + roomCode;
        }

        CompletableFuture<WebSocket> connected() {
            return connected;
        }

        CompletableFuture<String> roomUpdate() {
            return roomUpdate;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            this.socket = webSocket;
            webSocket.request(1);
            webSocket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\n\n\0", true);
        }

        void subscribe() {
            socket.sendText("SUBSCRIBE\nid:room-update\ndestination:" + destination + "\nack:auto\n\n\0", true);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            frameBuffer.append(data);
            if (last) {
                processFrames();
            }
            webSocket.request(1);
            return null;
        }

        private void processFrames() {
            int end;
            while ((end = frameBuffer.indexOf("\0")) >= 0) {
                String frame = frameBuffer.substring(0, end);
                frameBuffer.delete(0, end + 1);
                if (frame.startsWith("CONNECTED")) {
                    connected.complete(socket);
                } else if (frame.startsWith("MESSAGE") && frame.contains("destination:" + destination)) {
                    int bodyStart = frame.indexOf("\n\n");
                    if (bodyStart >= 0) {
                        roomUpdate.complete(frame.substring(bodyStart + 2));
                    }
                }
            }
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connected.completeExceptionally(error);
            roomUpdate.completeExceptionally(error);
        }
    }
}
