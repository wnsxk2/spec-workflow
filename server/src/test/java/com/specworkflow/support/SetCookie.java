package com.specworkflow.support;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 응답의 Set-Cookie 헤더 하나를 이름, 값, 속성으로 나눈 것. */
public record SetCookie(String name, String value, Map<String, String> attributes) {

    public static SetCookie parse(String header) {
        String[] parts = header.split(";");
        String[] nameValue = parts[0].split("=", 2);
        Map<String, String> attributes = new HashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String[] attribute = parts[i].trim().split("=", 2);
            attributes.put(attribute[0].toLowerCase(Locale.ROOT), attribute.length > 1 ? attribute[1] : "");
        }
        return new SetCookie(nameValue[0].trim(), nameValue[1].trim(), attributes);
    }

    public boolean has(String attribute) {
        return attributes.containsKey(attribute.toLowerCase(Locale.ROOT));
    }

    public Optional<String> attribute(String attribute) {
        return Optional.ofNullable(attributes.get(attribute.toLowerCase(Locale.ROOT)));
    }
}
