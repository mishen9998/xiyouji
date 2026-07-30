package com.xiyouji;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableCaching
@EnableTransactionManagement
public class XiyoujiApplication {

    private static final Logger log = LoggerFactory.getLogger(XiyoujiApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(XiyoujiApplication.class, args);
        log.info("============================================");
        log.info("  西游记Roguelike卡牌游戏 后端启动成功！");
        log.info("  访问地址: http://localhost:8080");
        log.info("  H2控制台: http://localhost:8080/h2-console");
        log.info("  前端页面: http://localhost:8080/index.html");
        log.info("============================================");
    }
}
