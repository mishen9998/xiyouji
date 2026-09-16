package com.xiyouji.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyouji.combat.EnemyActionDefinition;
import com.xiyouji.exception.EnemyActionDataException;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;

/** Database JSON is deliberately independent from Redis polymorphic snapshot metadata. */
@Converter
public class EnemyActionDefinitionsConverter implements AttributeConverter<List<EnemyActionDefinition>, String> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<EnemyActionDefinition>> TYPE = new TypeReference<>() {};

    @Override public String convertToDatabaseColumn(List<EnemyActionDefinition> actions) {
        if (actions == null) return null;
        try { return JSON.writeValueAsString(actions); }
        catch (Exception e) { throw new IllegalArgumentException("Cannot persist enemy action definitions", e); }
    }

    @Override public List<EnemyActionDefinition> convertToEntityAttribute(String data) {
        if (data == null) return null;
        try {
            List<EnemyActionDefinition> actions = JSON.readValue(data, TYPE);
            if (actions == null || actions.isEmpty() || actions.contains(null)) {
                throw new IllegalArgumentException("Enemy action cycle must not be empty");
            }
            return new ArrayList<>(actions);
        } catch (Exception e) {
            throw new EnemyActionDataException("Invalid enemy action definitions: " + e.getMessage(), e);
        }
    }
}
