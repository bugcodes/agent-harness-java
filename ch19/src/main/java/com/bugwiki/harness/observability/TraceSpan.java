package com.bugwiki.harness.observability;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表示链路追踪中的一个时间跨度，记录操作名称、耗时、属性与子跨度。
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class TraceSpan {
    private final String name;
    private final Instant startTime;
    private Instant endTime;
    private long durationMs;
    private final Map<String, Object> attributes = Collections.synchronizedMap(new LinkedHashMap<>());
    private final List<TraceSpan> children = Collections.synchronizedList(new ArrayList<>());

    public TraceSpan(String name) {
        this.name = name;
        this.startTime = Instant.now();
    }

    public TraceSpan child(String childName) {
        TraceSpan child = new TraceSpan(childName);
        children.add(child);
        return child;
    }

    public void addAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public void endSpan() {
        this.endTime = Instant.now();
        this.durationMs = Duration.between(startTime, endTime).toMillis();
    }

    public String getName() {
        return name;
    }

    @JsonProperty("start_time")
    public String getStartTime() {
        return startTime.toString();
    }

    @JsonProperty("end_time")
    public String getEndTime() {
        return endTime == null ? null : endTime.toString();
    }

    @JsonProperty("duration_ms")
    public long getDurationMs() {
        return durationMs;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public List<TraceSpan> getChildren() {
        return children;
    }
}
