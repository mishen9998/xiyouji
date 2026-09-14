package com.xiyouji.controller.support;

import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.enums.CharacterClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CharacterClassParser 单元测试
 * 验证四种匹配策略与两类非法输入的异常契约。
 */
@DisplayName("角色职业解析器")
class CharacterClassParserTest {

    private final CharacterClassParser parser = new CharacterClassParser();

    @Test
    @DisplayName("枚举名精确匹配")
    void parses_exactEnumName() {
        assertEquals(CharacterClass.SUN_WUKONG, parser.parse("SUN_WUKONG"));
    }

    @Test
    @DisplayName("大小写不敏感且归一化空格/连字符")
    void parses_caseInsensitiveWithSeparators() {
        assertEquals(CharacterClass.SUN_WUKONG, parser.parse("sun wukong"));
        assertEquals(CharacterClass.SUN_WUKONG, parser.parse("sun-wukong"));
        assertEquals(CharacterClass.SUN_WUKONG, parser.parse("Sun_Wukong"));
    }

    @Test
    @DisplayName("去分隔符全小写匹配")
    void parses_flatLowerCase() {
        assertEquals(CharacterClass.SUN_WUKONG, parser.parse("sunwukong"));
    }

    @Test
    @DisplayName("中文明示名称匹配")
    void parses_chineseDisplayName() {
        assertEquals(CharacterClass.SUN_WUKONG, parser.parse("孙悟空"));
    }

    @Test
    @DisplayName("空值或空白抛出异常")
    void blanks_throw() {
        assertThrows(InvalidActionException.class, () -> parser.parse(null));
        assertThrows(InvalidActionException.class, () -> parser.parse("  "));
    }

    @Test
    @DisplayName("未知职业抛出异常")
    void unknown_throws() {
        assertThrows(InvalidActionException.class, () -> parser.parse("牛魔王"));
    }
}