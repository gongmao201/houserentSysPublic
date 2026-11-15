package com.wt.hrentSystem.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 chat/completions 客户端，JDK8 可运行（Apache HttpClient，不引新依赖）。
 * 花生壳的 Ollama 与 DeepSeek 都是同一协议，差异只在 endpoint / api-key / 超时。
 */
public class OpenAiCompatibleClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final String name;
    private final String url;
    private final String apiKey;
    private final String model;
    private final int timeoutMs;
    private final boolean configured;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    public OpenAiCompatibleClient(String name, String endpoint, String apiKey, String model,
                                  int timeoutMs, boolean enabled, boolean requireKey,
                                  ObjectMapper objectMapper) {
        this.name = name;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.timeoutMs = Math.max(1, timeoutMs);
        this.url = normalize(endpoint);
        this.configured = enabled && !url.isEmpty() && (!requireKey || !this.apiKey.isEmpty());
        this.objectMapper = objectMapper;
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(this.timeoutMs)
                .setConnectionRequestTimeout(this.timeoutMs)
                .setSocketTimeout(this.timeoutMs)
                .build();
        this.httpClient = HttpClients.custom().setDefaultRequestConfig(config).build();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (!configured) {
            throw new IllegalStateException(name + " 未配置");
        }
        HttpPost post = new HttpPost(url);
        post.setHeader("Content-Type", "application/json; charset=UTF-8");
        if (!apiKey.isEmpty()) {
            post.setHeader("Authorization", "Bearer " + apiKey);
        }
        try {
            post.setEntity(new StringEntity(requestBody(systemPrompt, userPrompt), ContentType.APPLICATION_JSON));
        } catch (Exception e) {
            throw new RuntimeException(name + " 请求体构造失败", e);
        }
        try (CloseableHttpResponse response = httpClient.execute(post)) {
            int code = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            String body = entity == null ? "" : EntityUtils.toString(entity, StandardCharsets.UTF_8);
            if (code != 200) {
                throw new RuntimeException(name + " HTTP " + code + "：" + brief(body));
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().trim().isEmpty()) {
                throw new RuntimeException(name + " 返回空 content");
            }
            return content.asText().trim();
        } catch (IOException e) {
            throw new RuntimeException(name + " 连接失败/超时：" + e.getClass().getSimpleName(), e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(name + " 响应解析失败：" + e.getClass().getSimpleName(), e);
        }
    }

    private String requestBody(String systemPrompt, String userPrompt) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("temperature", 0);
        body.put("max_tokens", 512);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        body.put("messages", messages);
        return objectMapper.writeValueAsString(body);
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    /**支持传基址或已带 /v1/chat/completions 的完整地址 */
    static String normalize(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return "";
        }
        String value = endpoint.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/chat/completions")) {
            return value;
        }
        if (value.endsWith("/v1")) {
            return value + "/chat/completions";
        }
        return value + "/v1/chat/completions";
    }

    private String brief(String body) {
        if (body == null) {
            return "";
        }
        String oneLine = body.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= 160 ? oneLine : oneLine.substring(0, 160) + "...";
    }

    public void close() {
        try {
            httpClient.close();
        } catch (IOException e) {
            log.warn("关闭 AI HTTP 客户端失败 provider=" + name, e);
        }
    }
}
