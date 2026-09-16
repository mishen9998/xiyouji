package com.xiyouji.event;

import com.xiyouji.model.Card;
import com.xiyouji.model.Relic;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persisted inside the map node, in the same aggregate write as the rewards. */
public class EventState {
    public String eventInstanceId;
    public String definitionId;
    public boolean resolved;
    public String selectedOption;
    public Map<String, List<Outcome>> members = new LinkedHashMap<>();

    public static class Outcome {
        public int hpCost;
        public int goldCost;
        public int amount;
        public int goldReward;
        public EventDefinition.Reward reward;
        public int cardIndex = -1;
        public String cardFingerprint;
        public String targetName;
        public Card card;
        public Relic relic;
        public String unavailableReason;
    }
}
