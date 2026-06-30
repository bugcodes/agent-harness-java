package com.bugwiki.harness.observability;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * 创建追踪跨度，并把根跨度导出为工作区内的 JSON 链路文件。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class Tracer {
    private static final ObjectMapper MAPPER =
            new ObjectMapper()
                    .findAndRegisterModules()
                    .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                    .enable(SerializationFeature.INDENT_OUTPUT);

    private Tracer() {}

    public static TraceSpan startSpan(String name) {
        return new TraceSpan(name);
    }

    public static TraceSpan startSpan(TraceSpan parent, String name) {
        return parent == null ? startSpan(name) : parent.child(name);
    }

    public static Path exportTraceToFile(TraceSpan root, Path workDir, String sessionId) throws Exception {
        Path traceDir = workDir.resolve(".claw").resolve("traces");
        Files.createDirectories(traceDir);
        Path file = traceDir.resolve("trace_" + sessionId + "_" + unixNano() + ".json");
        MAPPER.writeValue(file.toFile(), root);
        return file;
    }

    private static long unixNano() {
        Instant now = Instant.now();
        return now.getEpochSecond() * 1_000_000_000L + now.getNano();
    }
}
