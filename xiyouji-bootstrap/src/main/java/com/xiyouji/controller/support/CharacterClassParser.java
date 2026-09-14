package com.xiyouji.controller.support;

import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.enums.CharacterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 角色职业解析器
 *
 * 统一解析 HTTP 入参中的角色职业字符串，支持四种匹配策略：
 *   1. 枚举名精确匹配（如 "SUN_WUKONG"）
 *   2. 大小写不敏感 + 空格/连字符归一化（如 "sun wukong"、"sun-wukong"）
 *   3. 去分隔符匹配（如 "sunwukong"）
 *   4. 中文显示名匹配（如 "孙悟空"）
 */
@Component
public class CharacterClassParser {

    private static final Logger log = LoggerFactory.getLogger(CharacterClassParser.class);

    public CharacterClass parse(String input) {
        if (input == null || input.isBlank()) {
            throw new InvalidActionException("角色职业不能为空");
        }
        // 1. 尝试直接枚举匹配（大小写敏感）
        try {
            return CharacterClass.valueOf(input);
        } catch (IllegalArgumentException ignored) {
        }
        // 2. 大小写不敏感匹配：转大写并替换空格/连字符为下划线
        String normalized = input.trim().toUpperCase().replace(" ", "_").replace("-", "_");
        try {
            return CharacterClass.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
        }
        // 3. 去除下划线后匹配（如 "sunwukong" → "SUNWUKONG" vs "SUN_WUKONG"）
        String noSeparator = normalized.replace("_", "");
        for (CharacterClass cc : CharacterClass.values()) {
            if (cc.name().replace("_", "").equals(noSeparator)) {
                return cc;
            }
        }
        // 4. 中文名称匹配
        for (CharacterClass cc : CharacterClass.values()) {
            if (cc.getDisplayName().equals(input.trim())) {
                return cc;
            }
        }
        log.warn("Invalid character class provided: {}", input);
        throw new InvalidActionException("无效的角色职业: " + input);
    }
}