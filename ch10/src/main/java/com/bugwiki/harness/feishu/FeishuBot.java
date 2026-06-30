package com.bugwiki.harness.feishu;

import com.bugwiki.harness.engine.AgentEngine;
import com.bugwiki.harness.engine.Reporter;

/**
 * Provides a lightweight Feishu bot skeleton for tutorial chapters.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class FeishuBot {
    private final AgentEngine engine;

    public FeishuBot(AgentEngine engine) {
        this.engine = engine;
    }

    public AgentEngine getEngine() {
        return engine;
    }

    public Reporter reporter(String chatId) {
        return new FeishuReporter(chatId);
    }
}
