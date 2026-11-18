package com.wt.hrentSystem.ai.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 三级降级配置（docs/00 §3.1.0 D18）。
 * mode：auto / local / deepseek / rule；auto 按 local → deepseek → rule 顺序降级。
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String mode = "auto";
    private Local local = new Local();
    private Deepseek deepseek = new Deepseek();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Local getLocal() {
        return local;
    }

    public void setLocal(Local local) {
        this.local = local;
    }

    public Deepseek getDeepseek() {
        return deepseek;
    }

    public void setDeepseek(Deepseek deepseek) {
        this.deepseek = deepseek;
    }

    /**第 1 级：云托管 → 花生壳(vicp.fun) → 本机 Ollama（OpenAI 兼容） */
    public static class Local {
        private boolean enabled = true;
        private String endpoint = "";
        private int timeoutMs = 3000;
        private String model = "qwen2.5:3b-instruct-q4";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    /**第 2 级：DeepSeek API（OpenAI 兼容） */
    public static class Deepseek {
        private boolean enabled = true;
        private String endpoint = "https://api.deepseek.com/v1/chat/completions";
        private String apiKey = "";
        private int timeoutMs = 8000;
        private String model = "deepseek-chat";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
