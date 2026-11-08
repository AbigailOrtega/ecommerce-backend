package com.ecommerce.entity;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Converter
public class AttributeMapConverter implements AttributeConverter<Map<String, List<String>>, String> {

    private static final ObjectMapper mapper = JsonMapper.builder().build();

    @Override
    public String convertToDatabaseColumn(Map<String, List<String>> attribute) {
        if (attribute == null || attribute.isEmpty()) return null;
        try {
            return mapper.writeValueAsString(attribute);
        } catch (JacksonException e) {
            return null;
        }
    }

    @Override
    public Map<String, List<String>> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return new HashMap<>();
        try {
            return mapper.readValue(dbData, new TypeReference<>() {});
        } catch (JacksonException e) {
            return new HashMap<>();
        }
    }
}
