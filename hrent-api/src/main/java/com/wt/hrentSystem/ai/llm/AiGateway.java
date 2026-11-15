package com.wt.hrentSystem.ai.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 三级降级网关（docs/00 §3.1.0 D18）：
 * ① local（云托管 → 花生壳 → 本机 Ollama 3b，3s）
 * ② deepseek（OpenAI 兼容，8s）
 * ③ rule（本类不实现规则，返回空结果，由 AiService 的规则/模板兜底）
 *
 * 只有「成功解析出可用结果」才算命中；超时、5xx、空内容、JSON 不合法都继续降级。
 */
@Service
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    private static final Set<String> TAGS =
            new LinkedHashSet<>(Arrays.asList("pet", "metro", "elevator", "sunny", "new"));

    private static final String INTENT_SYSTEM =
            "你是房屋租赁系统的选房意图解析器。只输出一个 JSON 对象，不要解释、不要 Markdown。"
                    + "字段：region（城市区名，字符串或 null）、budgetMaxYuan（月租上限，整数元或 null）、"
                    + "rooms（卧室数 1-6，整数或 null）、tags（数组，只能取 pet/metro/elevator/sunny/new）。"
                    + "没有提到的字段填 null，不要编造。";

    private static final String REASON_SYSTEM =
            "你是房屋租赁顾问。根据房源信息为每套房子写一句中文推荐理由，"
                    + "只输出 JSON 数组，元素为 {\"houseId\":\"...\",\"reason\":\"...\"}，"
                    + "reason 不超过 60 字，必须提到价格与命中标签，不要编造房源没有的信息。";

    private final AiProperties properties;
    private final LlmClient local;
    private final LlmClient deepseek;
    private final ObjectMapper objectMapper;

    public AiGateway(AiProperties properties,
                     @Qualifier("localAiClient") LlmClient local,
                     @Qualifier("deepSeekClient") LlmClient deepseek,
                     ObjectMapper objectMapper) {
        this.properties = properties;
        this.local = local;
        this.deepseek = deepseek;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void logConfiguration() {
        log.info("AI 降级链 mode={} local={} deepseek={}", mode(),
                local.isConfigured() ? "configured" : "skip",
                deepseek.isConfigured() ? "configured" : "skip");
    }

    public String mode() {
        String raw = properties.getMode() == null ? "auto" : properties.getMode().trim().toLowerCase();
        if ("auto".equals(raw) || "local".equals(raw) || "deepseek".equals(raw) || "rule".equals(raw)) {
            return raw;
        }
        throw new IllegalStateException("ai.mode 只能是 auto/local/deepseek/rule，当前：" + raw);
    }

    /**规则抽取命中不足时调用；返回 provider=rule 表示模型都不可用 */
    public IntentOutcome extractIntent(String query) {
        List<LlmClient> chain = chain();
        if (chain.isEmpty()) {
            return IntentOutcome.rule();
        }
        for (LlmClient client : chain) {
            if (!client.isConfigured()) {
                continue;
            }
            try {
                String raw = client.chat(INTENT_SYSTEM, query);
                Map<String, Object> intent = parseIntent(raw);
                if (!intent.isEmpty()) {
                    log.debug("AI 意图抽取命中 provider={}", client.name());
                    return new IntentOutcome(intent, client.name());
                }
            } catch (Exception e) {
                log.warn("AI 意图抽取降级 provider={} reason={}", client.name(), e.getMessage());
            }
        }
        return IntentOutcome.rule();
    }

    /**批量理由增强；失败返回 provider=rule + 空 map，由模板兜底 */
    public ReasonOutcome reasons(List<Map<String, Object>> briefs) {
        List<LlmClient> chain = chain();
        if (chain.isEmpty() || briefs == null || briefs.isEmpty()) {
            return ReasonOutcome.rule();
        }
        for (LlmClient client : chain) {
            if (!client.isConfigured()) {
                continue;
            }
            try {
                String raw = client.chat(REASON_SYSTEM, reasonPrompt(briefs));
                Map<String, String> reasons = parseReasons(raw, briefs);
                if (!reasons.isEmpty()) {
                    log.debug("AI 理由增强命中 provider={} count={}", client.name(), reasons.size());
                    return new ReasonOutcome(reasons, client.name());
                }
            } catch (Exception e) {
                log.warn("AI 理由增强降级 provider={} reason={}", client.name(), e.getMessage());
            }
        }
        return ReasonOutcome.rule();
    }

    private List<LlmClient> chain() {
        String mode = mode();
        if ("rule".equals(mode)) {
            return Collections.emptyList();
        }
        if ("local".equals(mode)) {
            return Collections.singletonList(local);
        }
        if ("deepseek".equals(mode)) {
            return Collections.singletonList(deepseek);
        }
        List<LlmClient> both = new ArrayList<>();
        both.add(local);
        both.add(deepseek);
        return both;
    }

    private String reasonPrompt(List<Map<String, Object>> briefs) {
        try {
            return "房源列表：" + objectMapper.writeValueAsString(briefs);
        } catch (Exception e) {
            throw new IllegalArgumentException("房源列表序列化失败", e);
        }
    }

    private Map<String, Object> parseIntent(String raw) throws Exception {
        String json = extractJson(raw, '{', '}');
        Map<String, Object> node = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
        });
        Map<String, Object> intent = new LinkedHashMap<>();
        String region = stringValue(node.get("region"));
        if (region != null && region.length() <= 64) {
            intent.put("region", region);
        }
        Long budgetFen = yuanToFen(node.get("budgetMaxYuan"));
        if (budgetFen != null && budgetFen > 0) {
            intent.put("budgetMax", budgetFen);
        }
        Integer rooms = intValue(node.get("rooms"));
        if (rooms != null && rooms >= 1 && rooms <= 6) {
            intent.put("rooms", rooms);
        }
        List<String> tags = tags(node.get("tags"));
        if (!tags.isEmpty()) {
            intent.put("tags", tags);
        }
        if (intent.isEmpty()) {
            throw new IllegalArgumentException("空意图");
        }
        return intent;
    }

    private Map<String, String> parseReasons(String raw, List<Map<String, Object>> briefs) throws Exception {
        String json = extractJson(raw, '[', ']');
        List<Map<String, Object>> items;
        if (json.startsWith("{")) {
            Map<String, Object> wrapper = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {
                    });
            Object inner = wrapper.get("items");
            if (!(inner instanceof List)) {
                throw new IllegalArgumentException("理由 JSON 缺少 items");
            }
            items = castList(inner);
        } else {
            items = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        }
        Set<String> allowed = new LinkedHashSet<>();
        for (Map<String, Object> brief : briefs) {
            allowed.add(String.valueOf(brief.get("houseId")));
        }
        Map<String, String> reasons = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String id = stringValue(item.get("houseId"));
            String reason = stringValue(item.get("reason"));
            if (id != null && allowed.contains(id) && reason != null) {
                reason = reason.replaceAll("^[\"“]|[\"”]$", "").trim();
                reasons.put(id, reason.length() <= 120 ? reason : reason.substring(0, 120));
            }
        }
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("理由为空或不含请求的 houseId");
        }
        return reasons;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private String extractJson(String raw, char open, char close) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            text = firstLine > 0 ? text.substring(firstLine + 1) : text;
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
        }
        int start = text.indexOf(open);
        int end = text.lastIndexOf(close);
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("模型输出不含 JSON");
        }
        return text.substring(start, end + 1);
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String s = ((String) value).trim();
            return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
        }
        return null;
    }

    private Long yuanToFen(Object value) {
        if (value == null) {
            return null;
        }
        try {
            BigDecimal yuan = new BigDecimal(String.valueOf(value).trim());
            if (yuan.compareTo(BigDecimal.ZERO) <= 0 || yuan.compareTo(new BigDecimal("200000")) > 0) {
                return null;
            }
            return yuan.multiply(new BigDecimal("100")).longValueExact();
        } catch (Exception e) {
            return null;
        }
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            BigDecimal d = new BigDecimal(String.valueOf(value).trim());
            return d.intValueExact();
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> tags(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<String> tags = new ArrayList<>();
        for (Object item : (List<?>) value) {
            String tag = stringValue(item);
            if (tag != null && TAGS.contains(tag) && !tags.contains(tag)) {
                tags.add(tag);
            }
        }
        return tags;
    }

    public static class IntentOutcome {
        private final Map<String, Object> intent;
        private final String provider;

        IntentOutcome(Map<String, Object> intent, String provider) {
            this.intent = intent;
            this.provider = provider;
        }

        static IntentOutcome rule() {
            return new IntentOutcome(Collections.emptyMap(), "rule");
        }

        public Map<String, Object> getIntent() {
            return intent;
        }

        public String getProvider() {
            return provider;
        }

        public boolean fromModel() {
            return !"rule".equals(provider);
        }
    }

    public static class ReasonOutcome {
        private final Map<String, String> reasons;
        private final String provider;

        public ReasonOutcome(Map<String, String> reasons, String provider) {
            this.reasons = reasons;
            this.provider = provider;
        }

        static ReasonOutcome rule() {
            return new ReasonOutcome(Collections.emptyMap(), "rule");
        }

        public Map<String, String> getReasons() {
            return reasons;
        }

        public String getProvider() {
            return provider;
        }
    }
}
