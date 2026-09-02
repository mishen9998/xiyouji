package com.xiyouji.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Web 配置
 * - CORS 跨域: 从配置文件读取白名单, 替代 @CrossOrigin("*")
 * - SPA 路由回退: 非 API 请求统一返回 index.html
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:8080,http://localhost:8081,http://localhost:8082,http://localhost:80,http://127.0.0.1:8080,http://127.0.0.1:80}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * SPA 路由回退：将所有未匹配静态资源且非 /api 的请求转发到 index.html，
     * 让 Vue Router 在客户端处理路由。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource resource = location.createRelative(resourcePath);
                        // 如果文件存在且可读，直接返回
                        if (resource.exists() && resource.isReadable()) {
                            return resource;
                        }
                        // 否则返回 index.html（SPA 回退）
                        return location.createRelative("index.html");
                    }
                });
    }
}
