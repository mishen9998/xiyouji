package com.xiyouji.dto.request.room;

import jakarta.validation.constraints.NotBlank;

/**
 * 选择角色请求
 */
public class SelectCharacterRequest {

    @NotBlank(message = "角色职业不能为空")
    private String characterClass;

    public String getCharacterClass() { return characterClass; }
    public void setCharacterClass(String characterClass) { this.characterClass = characterClass; }
}
