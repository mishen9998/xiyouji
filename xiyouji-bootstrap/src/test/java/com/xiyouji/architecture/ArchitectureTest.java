package com.xiyouji.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Executable guardrails for the layered modular-monolith boundaries. */
@AnalyzeClasses(packages = "com.xiyouji")
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllersMustNotAccessRepositories = noClasses()
            .that().resideInAnyPackage("com.xiyouji.controller..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.xiyouji.repository..");

    @ArchTest
    static final ArchRule servicesMustNotDependOnControllers = noClasses()
            .that().resideInAnyPackage("com.xiyouji.service..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.xiyouji.controller..");

    @ArchTest
    static final ArchRule applicationServicesMustUsePorts = noClasses()
            .that().resideInAnyPackage("com.xiyouji.service..", "com.xiyouji.port..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.xiyouji.repository..", "org.springframework.data.jpa..");

    @ArchTest
    static final ArchRule gameModelsMustNotDependOnRedis = noClasses()
            .that().resideInAnyPackage("com.xiyouji.model..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.data.redis..", "org.redisson..");
}
