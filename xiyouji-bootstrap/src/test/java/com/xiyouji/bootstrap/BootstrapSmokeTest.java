package com.xiyouji.bootstrap;

import com.xiyouji.config.OpenApiConfig;
import com.xiyouji.config.WebConfig;
import com.xiyouji.controller.InstanceInfoController;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapSmokeTest {

    @Test
    void instanceInfoAndHealthExposeDeploymentIdentity() {
        InstanceInfoController controller = new InstanceInfoController();
        ReflectionTestUtils.setField(controller, "instanceId", "instance-test");
        ReflectionTestUtils.setField(controller, "serverPort", "8081");
        ReflectionTestUtils.setField(controller, "activeProfile", "distributed");

        Map<String, String> health = controller.health();
        assertEquals("UP", health.get("status"));
        assertEquals("instance-test", health.get("instance"));
        assertEquals("8081", health.get("port"));

        Map<String, Object> info = controller.getInstanceInfo();
        assertEquals("instance-test", info.get("instanceId"));
        assertEquals("8081", info.get("serverPort"));
        assertEquals("distributed", info.get("activeProfile"));
        assertTrue(info.containsKey("jvmName"));
        assertTrue(info.containsKey("uptimeSeconds"));
    }

    @Test
    void openApiConfigurationContainsBearerScheme() {
        var openApi = new OpenApiConfig().openAPI();
        assertEquals("1.0.0", openApi.getInfo().getVersion());
        assertTrue(openApi.getComponents().getSecuritySchemes().containsKey("bearerAuth"));
        assertEquals("JWT", openApi.getComponents().getSecuritySchemes().get("bearerAuth").getBearerFormat());
    }

    @Test
    void webConfigRegistersCorsPolicy() {
        WebConfig config = new WebConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", new String[]{"http://localhost:8080"});
        config.addCorsMappings(new CorsRegistry());
    }
}
