package com.xiyouji.dto.response;

import java.util.List;
import java.util.Map;

public record EventPreview(String eventInstanceId, String definitionId, String title, String text,
                           boolean resolved, String selectedOption, List<Option> options) {
    public record Option(String id, String label, boolean enabled, String disabledReason,
                         Map<String, MemberOutcome> members) { }
    public record MemberOutcome(int hpCost, int goldCost, String reward, int amount,
                                int goldReward, String targetName) { }
}
