package com.bugwiki.harness.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads local MiniMax settings from JVM properties, environment variables, or application-local.yml.
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public class AppConfig {
    public static final String PLACEHOLDER_API_KEY = "YOUR_MINIMAX_API_KEY";
    private static final String DEFAULT_MODEL = "MiniMax-M3";

    private final String minimaxApiKey;
    private final String minimaxModel;

    private AppConfig(String minimaxApiKey, String minimaxModel) {
        this.minimaxApiKey = valueOrDefault(minimaxApiKey, PLACEHOLDER_API_KEY);
        this.minimaxModel = valueOrDefault(minimaxModel, DEFAULT_MODEL);
    }

    public static AppConfig load() {
        FileConfig fileConfig = readApplicationLocal();
        String apiKey =
                firstNonBlank(
                        System.getProperty("minimax.api-key"),
                        System.getenv("MINIMAX_API_KEY"),
                        fileConfig.apiKey(),
                        PLACEHOLDER_API_KEY);
        String model =
                firstNonBlank(
                        System.getProperty("minimax.model"),
                        System.getenv("MINIMAX_MODEL"),
                        fileConfig.model(),
                        DEFAULT_MODEL);
        return new AppConfig(apiKey, model);
    }

    public String getMinimaxApiKey() {
        return minimaxApiKey;
    }

    public String getMinimaxModel() {
        return minimaxModel;
    }

    public boolean hasRealMinimaxKey() {
        return !minimaxApiKey.isBlank() && !PLACEHOLDER_API_KEY.equals(minimaxApiKey);
    }

    private static FileConfig readApplicationLocal() {
        Path file = findApplicationLocal();
        if (file == null || !Files.isRegularFile(file)) {
            return new FileConfig("", "");
        }

        String section = "";
        String apiKey = "";
        String model = "";
        try {
            List<String> lines = Files.readAllLines(file);
            for (String rawLine : lines) {
                String line = stripInlineComment(rawLine).trim();
                if (line.isBlank()) {
                    continue;
                }
                if (!rawLine.startsWith(" ") && line.endsWith(":")) {
                    section = line.substring(0, line.length() - 1).trim();
                    continue;
                }

                String key = keyOf(line);
                String value = valueOf(line);
                if (key.isBlank()) {
                    continue;
                }
                if ("minimax.api-key".equals(key)
                        || ("minimax".equals(section) && ("api-key".equals(key) || "apiKey".equals(key) || "api_key".equals(key)))) {
                    apiKey = value;
                } else if ("minimax.model".equals(key) || ("minimax".equals(section) && "model".equals(key))) {
                    model = value;
                }
            }
        } catch (IOException ignored) {
            return new FileConfig("", "");
        }
        return new FileConfig(apiKey, model);
    }

    private static Path findApplicationLocal() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("application-local.yml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    private static String keyOf(String line) {
        int colon = line.indexOf(':');
        return colon < 0 ? "" : line.substring(0, colon).trim();
    }

    private static String valueOf(String line) {
        int colon = line.indexOf(':');
        if (colon < 0) {
            return "";
        }
        return unquote(line.substring(colon + 1).trim());
    }

    private static String stripInlineComment(String line) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (ch == '#' && !inSingleQuote && !inDoubleQuote) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String unquote(String value) {
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private record FileConfig(String apiKey, String model) {}
}
