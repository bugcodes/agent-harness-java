package com.bugwiki.harness.tools;

import com.bugwiki.harness.schema.ToolDefinition;
import com.bugwiki.harness.schema.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 对现有文件进行局部字符串替换，支持多层模糊匹配以降低编辑失败率。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class EditFileTool implements BaseTool {
    private final Path workDir;

    public EditFileTool(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String name() {
        return "edit_file";
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode schema = JsonSchemas.objectSchema();
        ObjectNode properties = (ObjectNode) schema.get("properties");
        JsonSchemas.stringProperty(properties, "path", "要修改的文件路径");
        JsonSchemas.stringProperty(properties, "old_text", "文件中原有的文本。必须包含足够的上下文，以确保在文件中的唯一性。");
        JsonSchemas.stringProperty(properties, "new_text", "要替换成的新文本");
        schema.withArray("required").add("path").add("old_text").add("new_text");
        return new ToolDefinition(
                name(), "对现有文件进行局部的字符串替换。这比重写整个文件更安全、更快速。请提供足够的 old_text 上下文以确保匹配的唯一性。", schema);
    }

    @Override
    public ToolResult execute(String toolCallId, JsonNode arguments) throws Exception {
        String inputPath = arguments.path("path").asText();
        Path fullPath = joinWorkDir(inputPath);
        String originalContent;
        try {
            originalContent = Files.readString(fullPath);
        } catch (IOException ex) {
            throw new IOException("读取文件失败，请确认路径是否正确: " + ex.getMessage(), ex);
        }

        String newContent =
                fuzzyReplace(
                        originalContent,
                        arguments.path("old_text").asText(),
                        arguments.path("new_text").asText());
        try {
            Files.writeString(fullPath, newContent);
        } catch (IOException ex) {
            throw new IOException("写回文件失败: " + ex.getMessage(), ex);
        }

        return new ToolResult(toolCallId, "✅ 成功修改文件: " + inputPath, false);
    }

    private String fuzzyReplace(String originalContent, String oldText, String newText) {
        int count = countOccurrences(originalContent, oldText);
        if (count == 1) {
            return replaceFirstLiteral(originalContent, oldText, newText);
        }
        if (count > 1) {
            throw new IllegalArgumentException("old_text 匹配到了 " + count + " 处，请提供更多的上下文代码以确保唯一性");
        }

        String normalizedContent = originalContent.replace("\r\n", "\n");
        String normalizedOld = oldText.replace("\r\n", "\n");
        count = countOccurrences(normalizedContent, normalizedOld);
        if (count == 1) {
            return replaceFirstLiteral(normalizedContent, normalizedOld, newText);
        }

        String trimmedOld = normalizedOld.trim();
        if (!trimmedOld.isEmpty()) {
            count = countOccurrences(normalizedContent, trimmedOld);
            if (count == 1) {
                return replaceFirstLiteral(normalizedContent, trimmedOld, newText);
            }
        }

        return lineByLineReplace(normalizedContent, normalizedOld, newText);
    }

    private String lineByLineReplace(String content, String oldText, String newText) {
        List<String> contentLines = new ArrayList<>(Arrays.asList(content.split("\n", -1)));
        List<String> oldLines = new ArrayList<>(Arrays.asList(oldText.trim().split("\n", -1)));

        if (oldLines.isEmpty() || contentLines.size() < oldLines.size()) {
            throw new IllegalArgumentException("找不到该代码片段");
        }

        for (int i = 0; i < oldLines.size(); i++) {
            oldLines.set(i, oldLines.get(i).trim());
        }

        int matchCount = 0;
        int matchStartIndex = -1;
        int matchEndIndex = -1;

        for (int i = 0; i <= contentLines.size() - oldLines.size(); i++) {
            boolean match = true;
            for (int j = 0; j < oldLines.size(); j++) {
                if (!contentLines.get(i + j).trim().equals(oldLines.get(j))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                matchCount++;
                matchStartIndex = i;
                matchEndIndex = i + oldLines.size();
            }
        }

        if (matchCount == 0) {
            throw new IllegalArgumentException("在文件中未找到 old_text，请检查内容和缩进");
        }
        if (matchCount > 1) {
            throw new IllegalArgumentException("模糊匹配到了 " + matchCount + " 处代码，请提供更多上下文以定位");
        }

        List<String> newContentLines = new ArrayList<>();
        newContentLines.addAll(contentLines.subList(0, matchStartIndex));
        newContentLines.add(newText);
        newContentLines.addAll(contentLines.subList(matchEndIndex, contentLines.size()));
        return String.join("\n", newContentLines);
    }

    private int countOccurrences(String content, String target) {
        if (target == null || target.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }

    private String replaceFirstLiteral(String content, String oldText, String newText) {
        int index = content.indexOf(oldText);
        if (index < 0) {
            return content;
        }
        return content.substring(0, index) + newText + content.substring(index + oldText.length());
    }

    private Path joinWorkDir(String inputPath) {
        String path = inputPath == null ? "" : inputPath;
        while (path.startsWith("/") || path.startsWith("\\")) {
            path = path.substring(1);
        }
        return workDir.resolve(path).normalize();
    }
}
