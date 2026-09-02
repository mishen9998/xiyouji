package com.xiyouji.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 新游戏请求DTO
 */
public class NewGameRequest {

    @NotBlank(message = "角色职业不能为空")
    private String characterClass;

    public NewGameRequest() {
    }

    public NewGameRequest(String characterClass) {
        this.characterClass = characterClass;
    }

    public String getCharacterClass() {
        return characterClass;
    }

    public void setCharacterClass(String characterClass) {
        this.characterClass = characterClass;
    }
}
