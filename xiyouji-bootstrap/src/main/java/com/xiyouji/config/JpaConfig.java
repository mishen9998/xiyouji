package com.xiyouji.config;

import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * JPA 实体注册边界（bootstrap）。
 *
 * 领域五个实体不带 JPA 注解，映射契约收敛于 xiyouji-domain 模块的
 * META-INF/orm.xml（配合 META-INF/persistence.xml）。此处显式声明
 * mappingResources，避免依赖 Spring Boot 对 fat jar 内嵌套 jar 的
 * persistence.xml 自动发现——该路径在打产物后不可靠，曾导致
 * "Not a managed type" 启动失败。
 *
 * spring.jpa.*（ddl-auto / 方言等）与既有配置语义保持一致。
 */
@Configuration
public class JpaConfig {

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource,
            JpaVendorAdapter jpaVendorAdapter,
            JpaProperties jpaProperties,
            Environment environment) {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setJpaVendorAdapter(jpaVendorAdapter);
        emf.setMappingResources("META-INF/orm.xml");

        // 自定义 EMF 会绕开 Spring Boot 的 schema 管理工具，
        // 将 spring.jpa.hibernate.ddl-auto 显式传递为 hibernate.hbm2ddl.auto，
        // 保持既有配置语义（如 distributed 下 validate、dev 下 update）。
        Map<String, Object> props = new HashMap<>(jpaProperties.getProperties());
        String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto");
        if (ddlAuto != null && !"none".equalsIgnoreCase(ddlAuto)) {
            props.put("hibernate.hbm2ddl.auto", ddlAuto);
        }
        // 自定义 EMF 同样会绕开 Boot 的默认命名策略装配，
        // 显式恢复 camelCase -> snake_case 物理命名（与 Flyway 脚本一致）。
        props.put("hibernate.physical_naming_strategy",
                org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy.class.getName());
        emf.setJpaPropertyMap(props);
        return emf;
    }
}