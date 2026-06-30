package com.bugwiki.harness.cmd;

import com.bugwiki.harness.config.AppConfig;
import com.bugwiki.harness.engine.AgentEngine;
import com.bugwiki.harness.provider.LlmProvider;
import com.bugwiki.harness.provider.MiniMaxProvider;
import com.bugwiki.harness.schema.Message;
import com.bugwiki.harness.schema.Role;
import com.bugwiki.harness.schema.ToolCall;
import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.tools.BashTool;
import com.bugwiki.harness.tools.EditFileTool;
import com.bugwiki.harness.tools.ReadFileTool;
import com.bugwiki.harness.tools.ToolRegistry;
import com.bugwiki.harness.tools.WriteFileTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Starts chapter seven by adding the edit_file tool to the local registry.
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public class ClawApplication {
    private static final AppConfig CONFIG = AppConfig.load();

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        Path workDir = Path.of(".").toAbsolutePath().normalize();
        prepareWorkspace(workDir);

        LlmProvider provider = newLlmProvider();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.register(new EditFileTool(workDir));

        AgentEngine engine = new AgentEngine(provider, registry, workDir, false);
        engine.run(
                """
                我当前目录下有一个 server.go 文件。
                请帮我把里面 "TODO: 增加鉴权逻辑" 下面的那个 if 语句，整个替换为：
                if user == nil {
                    fmt.Println("Forbidden!")
                    return
                }
                """);
    }

    private static void prepareWorkspace(Path workDir) throws Exception {
        Files.writeString(
                workDir.resolve("server.go"),
                """
                package main

                import "fmt"

                func main() {
                    // 启动服务器
                    fmt.Println("Server is starting on port 8080...")

                    // TODO: 增加鉴权逻辑
                    if true {
                        fmt.Println("No auth, everyone can access.")
                    }
                }
                """);
    }

    private static LlmProvider newLlmProvider() {
        if (!CONFIG.hasRealMinimaxKey()) {
            System.out.println("[Provider] MiniMax key 还是占位，使用本地 DemoProvider。");
            return new DemoProvider();
        }
        return new MiniMaxProvider(CONFIG.getMinimaxApiKey(), CONFIG.getMinimaxModel());
    }

    /**
     * Drives a deterministic local demo of the edit_file tool.
     *
     * @author zhaobinjie
     * @date 2026-06-25
     */
    private static class DemoProvider implements LlmProvider {
        private final ObjectMapper mapper = new ObjectMapper();
        private int turn;

        @Override
        public Message generate(List<Message> messages, List<ToolDefinition> availableTools)
                throws Exception {
            turn++;
            if (turn == 1) {
                Message message = new Message(Role.ASSISTANT, "我会使用 edit_file 精确替换 server.go 中的鉴权逻辑。");
                message.setToolCalls(
                        List.of(
                                new ToolCall(
                                        "call_edit_server",
                                        "edit_file",
                                        mapper.readTree(
                                                """
                                                {
                                                  "path": "server.go",
                                                  "old_text": "if true {\\n        fmt.Println(\\"No auth, everyone can access.\\")\\n    }",
                                                  "new_text": "if user == nil {\\n        fmt.Println(\\"Forbidden!\\")\\n        return\\n    }"
                                                }
                                                """))));
                return message;
            }
            return new Message(Role.ASSISTANT, "server.go 已经把无鉴权分支替换成 Forbidden 分支。");
        }
    }
}
