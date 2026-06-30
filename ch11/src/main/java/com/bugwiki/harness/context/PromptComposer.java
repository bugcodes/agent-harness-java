package com.bugwiki.harness.context;

import com.bugwiki.harness.schema.Message;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds the system prompt from harness rules and workspace instructions.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class PromptComposer {
    private final Path workDir;

    public PromptComposer(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
    }

    public Message build() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are tiny-claw, a local agent harness. ");
        prompt.append("Use tools to inspect and change only the configured workspace.\n");
        Path agents = workDir.resolve("AGENTS.md");
        if (Files.exists(agents)) {
            try {
                prompt.append("\nWorkspace instructions:\n");
                prompt.append(Files.readString(agents));
                prompt.append("\n");
            } catch (Exception ex) {
                prompt.append("\nAGENTS.md could not be read: ").append(ex.getMessage()).append("\n");
            }
        }
        return Message.system(prompt.toString());
    }
}
