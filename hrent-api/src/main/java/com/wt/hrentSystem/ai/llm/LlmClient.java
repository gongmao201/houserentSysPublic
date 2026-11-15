package com.wt.hrentSystem.ai.llm;

/**
 * 单个模型端点的最小契约：一次同步 chat 调用，失败抛 RuntimeException。
 * 降级顺序由 {@link AiGateway} 负责，端点自身只处理 HTTP/解析。
 */
public interface LlmClient {

    /**local / deepseek，用于日志与响应 source */
    String name();

    /**未配置 endpoint / api-key 时返回 false，调用方直接跳到下一级 */
    boolean isConfigured();

    /**同步调用；超时、HTTP 非 200、空 content 一律抛异常，由网关降级 */
    String chat(String systemPrompt, String userPrompt);
}
