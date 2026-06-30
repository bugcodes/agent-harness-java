package com.bugwiki.harness.provider;

/**
 * Preconfigures the OpenAI-compatible provider for the MiniMax endpoint.
 *
 * @author zhaobinjie
 * @date 2026-06-30
 */
public class MiniMaxProvider extends OpenAICompatibleProvider {
    public MiniMaxProvider(String apiKey, String model) {
        super(apiKey, "https://api.minimaxi.com/v1/", model);
    }
}
