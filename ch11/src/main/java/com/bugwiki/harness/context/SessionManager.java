package com.bugwiki.harness.context;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches sessions by identifier for repeatable command-line runs.
 *
 * @author zhaobinjie
 * @date 2026-06-25
 */
public class SessionManager {
    public static final SessionManager GLOBAL = new SessionManager();

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public Session getOrCreate(String id, Path workDir) {
        return sessions.computeIfAbsent(id, key -> new Session(key, workDir));
    }
}
