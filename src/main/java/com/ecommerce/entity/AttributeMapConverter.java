package com.ecommerce.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Converter
public class AttributeMapConverter implements AttributeConverter<Map<String, List<String>>, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, List<String>> attribute) {
        if (attribute == null || attribute.isEmpty()) return null;
        try {
            return mapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Override
    public Map<String, List<String>> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return new HashMap<>();
        try {
            return mapper.readValue(dbData, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }
}
