package com.xiyouji.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 实例信息接口
 * 在分布式部署中, 用于确认当前请求被哪个实例处理
 * 访问 /api/instance/info 即可看到实例ID、端口、JVM启动时间等
 */
@RestController
@RequestMapping("/api/instance")
@Tag(name = "实例信息", description = "分布式实例标识与健康检查")
public class InstanceInfoController {

    @Value("${instance.id:unknown}")
    private String instanceId;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @GetMapping("/info")
    @Operation(summary = "获取当前实例信息", description = "返回实例ID、端口、JVM信息，用于验证负载均衡是否生效")
    public Map<String, Object> getInstanceInfo() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        Runtime r = Runtime.getRuntime();

        Map<String, Object> info = new HashMap<>();
        info.put("instanceId", instanceId);
        info.put("serverPort", serverPort);
        info.put("activeProfile", activeProfile);
        info.put("jvmName", runtime.getVmName());
        info.put("jvmVersion", runtime.getVmVersion());
        info.put("startTime", Instant.ofEpochMilli(runtime.getStartTime()).toString());
        info.put("uptimeSeconds", runtime.getUptime() / 1000);
        info.put("availableProcessors", r.availableProcessors());
        info.put("maxMemoryMB", r.maxMemory() / 1024 / 1024);
        info.put("usedMemoryMB", (r.totalMemory() - r.freeMemory()) / 1024 / 1024);
        info.put("timestamp", Instant.now().toString());

        return info;
    }

    @GetMapping("/health")
    @Operation(summary = "轻量级健康检查", description = "返回实例ID和OK状态，用于负载均衡器健康探测")
    public Map<String, String> health() {
        Map<String, String> result = new HashMap<>();
        result.put("status", "UP");
        result.put("instance", instanceId);
        result.put("port", serverPort);
        return result;
    }
}
