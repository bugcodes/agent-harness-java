package com.bugwiki.harness.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the role used by model chat messages.
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public enum Role {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static Role fromValue(String value) {
        for (Role role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}
