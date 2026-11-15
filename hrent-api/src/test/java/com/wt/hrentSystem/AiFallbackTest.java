package com.wt.hrentSystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wt.hrentSystem.ai.llm.AiGateway;
import com.wt.hrentSystem.ai.llm.AiProperties;
import com.wt.hrentSystem.ai.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 三级降级顺序：local 失败 → deepseek 成功；两级都挂 → rule，绝不抛给接口层。
 */
class AiFallbackTest {

    private LlmClient local;
    private LlmClient deepseek;
    private AiProperties properties;
    private AiGateway gateway;

    @BeforeEach
    void setUp() {
        local = mock(LlmClient.class);
        deepseek = mock(LlmClient.class);
        lenient().when(local.name()).thenReturn("local");
        lenient().when(deepseek.name()).thenReturn("deepseek");
        properties = new AiProperties();
        properties.setMode("auto");
        gateway = new AiGateway(properties, local, deepseek, new ObjectMapper());
    }

    @Test
    void localTimeoutFallsBackToDeepSeek() {
        when(local.isConfigured()).thenReturn(true);
        when(local.chat(anyString(), anyString())).thenThrow(new RuntimeException("local 连接失败/超时"));
        when(deepseek.isConfigured()).thenReturn(true);
        when(deepseek.chat(anyString(), anyString())).thenReturn(
                "```json\n{\"region\":\"天河区\",\"budgetMaxYuan\":2500,\"rooms\":2,"
                        + "\"tags\":[\"pet\",\"metro\",\"unknown\"]}\n```");

        AiGateway.IntentOutcome outcome = gateway.extractIntent("想找个安静的两居");

        assertEquals("deepseek", outcome.getProvider());
        assertEquals("天河区", outcome.getIntent().get("region"));
        assertEquals(250000L, outcome.getIntent().get("budgetMax"));
        assertEquals(2, outcome.getIntent().get("rooms"));
        assertEquals(2, ((java.util.List<?>) outcome.getIntent().get("tags")).size());
        verify(local).chat(anyString(), anyString());
    }

    @Test
    void bothProvidersFailReturnsRule() {
        when(local.isConfigured()).thenReturn(true);
        when(local.chat(anyString(), anyString())).thenThrow(new RuntimeException("timeout"));
        when(deepseek.isConfigured()).thenReturn(true);
        when(deepseek.chat(anyString(), anyString())).thenThrow(new RuntimeException("HTTP 500"));

        AiGateway.IntentOutcome outcome = gateway.extractIntent("随便看看");

        assertEquals("rule", outcome.getProvider());
        assertTrue(outcome.getIntent().isEmpty());
    }

    @Test
    void ruleModeNeverCallsProviders() {
        properties.setMode("rule");

        AiGateway.IntentOutcome outcome = gateway.extractIntent("天河 2500 两房");

        assertEquals("rule", outcome.getProvider());
        verify(local, never()).chat(anyString(), anyString());
        verify(deepseek, never()).chat(anyString(), anyString());
    }

    @Test
    void reasonsFallbackAndFilterUnknownHouseIds() {
        when(local.isConfigured()).thenReturn(true);
        when(local.chat(anyString(), anyString())).thenThrow(new RuntimeException("timeout"));
        when(deepseek.isConfigured()).thenReturn(true);
        when(deepseek.chat(anyString(), anyString())).thenReturn(
                "[{\"houseId\":\"h1\",\"reason\":\"天河两房，2400 元，近地铁\"},"
                        + "{\"houseId\":\"h2\",\"reason\":\"不存在的房源\"}]");

        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("houseId", "h1");
        brief.put("priceFen", 240000);
        AiGateway.ReasonOutcome outcome = gateway.reasons(Collections.singletonList(brief));

        assertEquals("deepseek", outcome.getProvider());
        assertEquals(1, outcome.getReasons().size());
        assertTrue(outcome.getReasons().get("h1").contains("近地铁"));
    }

    @Test
    void invalidJsonFallsBackToRule() {
        when(local.isConfigured()).thenReturn(true);
        when(local.chat(anyString(), anyString())).thenReturn("我觉得天河不错，但没有 JSON");

        AiGateway.IntentOutcome outcome = gateway.extractIntent("天河");

        assertEquals("rule", outcome.getProvider());
    }
}
