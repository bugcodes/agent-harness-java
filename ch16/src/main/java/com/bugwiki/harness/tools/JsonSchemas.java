package com.bugwiki.harness.tools;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builds small JSON schemas used by local tool definitions.
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
final class JsonSchemas {
    private JsonSchemas() {}

    static ObjectNode objectSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        schema.putArray("required");
        return schema;
    }

    static ObjectNode stringProperty(ObjectNode properties, String name, String description) {
        ObjectNode property = properties.putObject(name);
        property.put("type", "string");
        property.put("description", description);
        return property;
    }
}
