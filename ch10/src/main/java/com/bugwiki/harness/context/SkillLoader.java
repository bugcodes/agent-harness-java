package com.bugwiki.harness.context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Loads SKILL.md files from the workspace skill directory for prompt composition.
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public class SkillLoader {
    private final Path workDir;

    public SkillLoader(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
    }

    public String loadAll() {
        Path skillBaseDir = workDir.resolve(".claw").resolve("skills");
        if (!Files.exists(skillBaseDir)) {
            return "";
        }

        StringBuilder skills = new StringBuilder();
        skills.append("\n### 可用专业技能 (Agent Skills)\n");
        skills.append("以下是你拥有的标准化外挂技能，请在符合 description 描述的场景下严格遵循其正文指令：\n\n");

        try {
            try (Stream<Path> paths = Files.walk(skillBaseDir)) {
                paths.filter(path -> Files.isRegularFile(path) && "SKILL.md".equals(path.getFileName().toString()))
                        .sorted()
                        .forEach(
                                path -> {
                                    try {
                                        Skill skill = parseSkillMd(Files.readString(path));
                                        skills.append("#### 技能名称: ").append(skill.name()).append("\n");
                                        skills.append("**触发条件**: ").append(skill.description()).append("\n\n");
                                        skills.append("**执行指南**:\n");
                                        skills.append(skill.body()).append("\n\n---\n");
                                    } catch (Exception ignored) {
                                        // Go version also silently skips unreadable skill files.
                                    }
                                });
            }
        } catch (Exception ex) {
            return "";
        }

        return skills.length() < 50 ? "" : skills.toString();
    }

    private Skill parseSkillMd(String content) {
        String name = "Unknown Skill";
        String description = "No description provided.";
        String body = content;

        if (content.startsWith("---\n") || content.startsWith("---\r\n")) {
            String[] parts = content.split("---", 3);
            if (parts.length == 3) {
                body = parts[2].trim();
                for (String line : parts[1].split("\\R")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("name:")) {
                        name = trimmed.substring("name:".length()).trim();
                    } else if (trimmed.startsWith("description:")) {
                        description = trimmed.substring("description:".length()).trim();
                    }
                }
            }
        }
        return new Skill(name, description, body);
    }

    private record Skill(String name, String description, String body) {}
}
