package com.xiyouji.dto.response;

import java.util.List;

/** Read-only presentation: skipping scenes never sends a shared-state command. */
public record StoryEvent(List<Scene> scenes, EventPreview event) {
    public record Scene(String id, String trigger, String title, String text, boolean skippable) { }
}
