package com.bugwiki.harness.tools;

import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Edits an existing file by replacing one uniquely matched text fragment.
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public class EditFileTool implements BaseTool {
    private final Path workDir;

    public EditFileTool(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "edit_file";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        stringProperty(properties, "path", "要修改的文件路径");
        stringProperty(properties, "old_text", "文件中原有的文本。必须包含足够上下文，以确保唯一性。");
        stringProperty(properties, "new_text", "要替换成的新文本");
        schema.putArray("required").add("path").add("old_text").add("new_text");
        return new ToolDefinition(
                name(),
                "对现有文件进行局部的字符串替换。这比重写整个文件更安全、更快速。请提供足够的 old_text 上下文以确保匹配的唯一性。",
                schema);
    }

    @Override
    public ToolResult execute(String toolCallId, JsonNode arguments) throws Exception {
        String path = arguments.path("path").asText();
        String oldText = arguments.path("old_text").asText();
        String newText = arguments.path("new_text").asText();
        Path fullPath = resolve(path);

        String originalContent = Files.readString(fullPath);
        String newContent = fuzzyReplace(originalContent, oldText, newText);
        Files.writeString(fullPath, newContent);
        return new ToolResult(toolCallId, "✅ 成功修改文件: " + path, false);
    }

    private String fuzzyReplace(String originalContent, String oldText, String newText) {
        // L1: 精确匹配
        int count = count(originalContent, oldText);
        if (count == 1) {
            return originalContent.replaceFirst(java.util.regex.Pattern.quote(oldText), java.util.regex.Matcher.quoteReplacement(newText));
        }
        if (count > 1) {
            throw new IllegalArgumentException("old_text 匹配到了 " + count + " 处，请提供更多的上下文代码以确保唯一性");
        }
        // L2: 换行符归一化 (统一将 \r\n 转换为 \n)
        String normalizedContent = originalContent.replace("\r\n", "\n");
        String normalizedOld = oldText.replace("\r\n", "\n");
        count = count(normalizedContent, normalizedOld);
        if (count == 1) {
            return normalizedContent.replaceFirst(java.util.regex.Pattern.quote(normalizedOld), java.util.regex.Matcher.quoteReplacement(newText));
        }
        // L3: Trim Space 匹配 (忽略首尾的空行和空格)
        String trimmedOld = normalizedOld.trim();
        if (!trimmedOld.isEmpty()) {
            count = count(normalizedContent, trimmedOld);
            if (count == 1) {
                // 注意：这里替换时，我们只能替换被 Trim 后的部分，不能直接用 newText 破坏原本的缩进
                // 为了保持本专栏代码不过于冗长复杂，当触发 L3/L4 时，如果 newText 没有带有正确的缩进，
                // 可能会导致替换后代码格式不美观。但这总比直接报错让 Agent 死循环要好。
                return normalizedContent.replaceFirst(java.util.regex.Pattern.quote(trimmedOld), java.util.regex.Matcher.quoteReplacement(newText));
            }
        }
        // L4: 逐行去缩进匹配 (最强力的容错：消除大模型遗漏缩进的幻觉)
        return lineByLineReplace(normalizedContent, normalizedOld, newText);
    }

    private String lineByLineReplace(String content, String oldText, String newText) {
        String[] contentLines = content.split("\n", -1);
        String[] oldLines = oldText.trim().split("\n", -1);
        if (oldLines.length == 0 || contentLines.length < oldLines.length) {
            throw new IllegalArgumentException("找不到该代码片段");
        }

        for (int i = 0; i < oldLines.length; i++) {
            oldLines[i] = oldLines[i].trim();
        }

        int matchCount = 0;
        int matchStartIndex = -1;
        int matchEndIndex = -1;
        for (int i = 0; i <= contentLines.length - oldLines.length; i++) {
            boolean matched = true;
            for (int j = 0; j < oldLines.length; j++) {
                if (!contentLines[i + j].trim().equals(oldLines[j])) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                matchCount++;
                matchStartIndex = i;
                matchEndIndex = i + oldLines.length;
            }
        }

        if (matchCount == 0) {
            throw new IllegalArgumentException("在文件中未找到 old_text，请检查内容和缩进");
        }
        if (matchCount > 1) {
            throw new IllegalArgumentException("模糊匹配到了 " + matchCount + " 处代码，请提供更多上下文以定位");
        }

        List<String> newContentLines = new ArrayList<>();
        for (int i = 0; i < matchStartIndex; i++) {
            newContentLines.add(contentLines[i]);
        }
        newContentLines.add(newText);
        for (int i = matchEndIndex; i < contentLines.length; i++) {
            newContentLines.add(contentLines[i]);
        }
        return String.join("\n", newContentLines);
    }

    private int count(String source, String target) {
        if (target == null || target.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }

    private ObjectNode stringProperty(ObjectNode properties, String name, String description) {
        ObjectNode property = properties.putObject(name);
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private Path resolve(String path) {
        Path target = workDir.resolve(path).normalize();
        if (!target.startsWith(workDir)) {
            throw new IllegalArgumentException("Path escapes workspace: " + path);
        }
        return target;
    }
}
