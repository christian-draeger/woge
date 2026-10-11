package dev.woge.gradle;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.gradle.api.GradleException;

final class ComponentJson {
    private ComponentJson() {}

    static Map<String, Object> parse(String text) {
        try {
            return object(new JsonSlurper().parseText(text));
        } catch (RuntimeException exception) {
            throw new GradleException("Invalid component JSON", exception);
        }
    }

    static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new GradleException("Expected a JSON object");
        Map<String, Object> result = new TreeMap<>();
        map.forEach((key, child) -> result.put(key.toString(), child));
        return result;
    }

    static List<Object> array(Object value) {
        if (!(value instanceof List<?> list)) throw new GradleException("Expected a JSON array");
        return new ArrayList<>(list);
    }

    static String string(Object value) {
        if (!(value instanceof String text)) throw new GradleException("Expected a JSON string");
        return text;
    }

    static String write(Object value) {
        return JsonOutput.prettyPrint(JsonOutput.toJson(value)) + "\n";
    }

    // The bundled schema is authoritative, including when an offline mirror supplies the index.
    static void validate(Object value, Map<String, Object> schema, String location) {
        Object constant = schema.get("const");
        if (constant != null && !constant.equals(value)) invalid(location, "unsupported schema version");
        if (schema.containsKey("enum") && !array(schema.get("enum")).contains(value)) {
            invalid(location, "unknown value");
        }
        String type = (String) schema.get("type");
        if ("object".equals(type)) {
            Map<String, Object> map = object(value);
            Map<String, Object> properties = object(schema.get("properties"));
            for (Object required : array(schema.getOrDefault("required", List.of()))) {
                if (!map.containsKey(required.toString())) invalid(location, "missing " + required);
            }
            if (map.size() < ((Number) schema.getOrDefault("minProperties", 0)).intValue()) {
                invalid(location, "empty object");
            }
            map.forEach((key, child) -> {
                if (!properties.containsKey(key)) invalid(location, "unknown field " + key);
                validate(child, object(properties.get(key)), location + "." + key);
            });
        } else if ("array".equals(type)) {
            List<Object> list = array(value);
            if (list.size() < ((Number) schema.getOrDefault("minItems", 0)).intValue()) {
                invalid(location, "empty array");
            }
            if (Boolean.TRUE.equals(schema.get("uniqueItems")) && list.stream().distinct().count() != list.size()) {
                invalid(location, "duplicate values");
            }
            for (Object child : list) validate(child, object(schema.get("items")), location + "[]");
        } else if ("string".equals(type)) {
            String text = string(value);
            if (text.length() < ((Number) schema.getOrDefault("minLength", 0)).intValue()
                    || schema.containsKey("pattern") && !java.util.regex.Pattern.compile(string(schema.get("pattern"))).matcher(text).find()) {
                invalid(location, "invalid string");
            }
        }
    }

    private static void invalid(String location, String reason) {
        throw new GradleException("Invalid registry " + location + ": " + reason);
    }
}
