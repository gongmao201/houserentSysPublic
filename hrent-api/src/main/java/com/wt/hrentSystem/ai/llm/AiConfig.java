package com.wt.hrentSystem.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 客户端装配：两个端点同一套 OpenAI 兼容实现，仅参数不同；
 * SSE 使用独立守护线程池，避免占用 Tomcat 请求线程。
 */
@Configuration
public class AiConfig {

    @Bean(name = "localAiClient", destroyMethod = "close")
    public OpenAiCompatibleClient localAiClient(AiProperties properties, ObjectMapper objectMapper) {
        AiProperties.Local local = properties.getLocal();
        return new OpenAiCompatibleClient("local", local.getEndpoint(), "", local.getModel(),
                local.getTimeoutMs(), local.isEnabled(), false, objectMapper);
    }

    @Bean(name = "deepSeekClient", destroyMethod = "close")
    public OpenAiCompatibleClient deepSeekClient(AiProperties properties, ObjectMapper objectMapper) {
        AiProperties.Deepseek deepseek = properties.getDeepseek();
        return new OpenAiCompatibleClient("deepseek", deepseek.getEndpoint(), deepseek.getApiKey(),
                deepseek.getModel(), deepseek.getTimeoutMs(), deepseek.isEnabled(), true, objectMapper);
    }

    @Bean(name = "aiStreamExecutor", destroyMethod = "shutdown")
    public ExecutorService aiStreamExecutor() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ai-sse-" + seq.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
        return Executors.newFixedThreadPool(4, factory);
    }
}
