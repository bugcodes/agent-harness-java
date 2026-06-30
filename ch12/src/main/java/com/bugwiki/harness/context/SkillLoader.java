package com.bugwiki.harness.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 从工作区 .claw/skills 目录加载 SKILL.md，并拼接为系统提示中的技能说明。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class SkillLoader {
    private final Path workDir;

    public SkillLoader(Path workDir) {
        this.workDir = workDir;
    }

    public String loadAll() {
        Path skillBaseDir = workDir.resolve(".claw").resolve("skills");
        if (!Files.isDirectory(skillBaseDir)) {
            return "";
        }

        StringBuilder skillsBuilder = new StringBuilder();
        skillsBuilder.append("\n### 可用专业技能 (Agent Skills)\n");
        skillsBuilder.append("以下是你拥有的标准化外挂技能，请在符合 description 描述的场景下严格遵循其正文指令：\n\n");

        try {
            try (Stream<Path> paths = Files.walk(skillBaseDir)) {
                List<Path> skillFiles =
                        paths
                            .filter(path -> Files.isRegularFile(path) && "SKILL.md".equals(path.getFileName().toString()))
                            .sorted(Comparator.comparing(Path::toString))
                            .toList();
                for (Path skillFile : skillFiles) {
                    String content = Files.readString(skillFile);
                    Skill skill = parseSkillMd(content);
                    skillsBuilder.append("#### 技能名称: ").append(skill.name()).append("\n");
                    skillsBuilder.append("**触发条件**: ").append(skill.description()).append("\n\n");
                    skillsBuilder.append("**执行指南**:\n");
                    skillsBuilder.append(skill.body());
                    skillsBuilder.append("\n\n---\n");
                }
            }
        } catch (IOException ex) {
            return "";
        }

        return skillsBuilder.length() < 50 ? "" : skillsBuilder.toString();
    }

    private Skill parseSkillMd(String content) {
        String name = "Unknown Skill";
        String description = "No description provided.";
        String body = content;

        if (content.startsWith("---\n") || content.startsWith("---\r\n")) {
            String[] parts = content.split("---", 3);
            if (parts.length == 3) {
                String frontmatter = parts[1];
                body = parts[2].trim();
                for (String rawLine : frontmatter.split("\n")) {
                    String line = rawLine.trim();
                    if (line.startsWith("name:")) {
                        name = line.substring("name:".length()).trim();
                    } else if (line.startsWith("description:")) {
                        description = line.substring("description:".length()).trim();
                    }
                }
            }
        }

        return new Skill(name, description, body);
    }

    private record Skill(String name, String description, String body) {}
}
