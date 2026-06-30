package com.bugwiki.harness.context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Loads optional skill-like workspace instructions for prompt composition.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class SkillLoader {
    public Optional<String> loadIfExists(Path path) {
        try {
            if (Files.exists(path)) {
                return Optional.of(Files.readString(path));
            }
            return Optional.empty();
        } catch (Exception ex) {
            return Optional.of("Failed to load " + path + ": " + ex.getMessage());
        }
    }
}
