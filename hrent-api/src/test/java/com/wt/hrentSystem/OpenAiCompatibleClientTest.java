package com.wt.hrentSystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.wt.hrentSystem.ai.llm.OpenAiCompatibleClient;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI 兼容客户端：路径拼接、Bearer、choices[0].message.content 解析、非 200 抛异常。
 * 用 JDK 自带 HttpServer，不依赖外网/Ollama。
 */
class OpenAiCompatibleClientTest {

    @Test
    void parsesContentAndSendsAuth() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        HttpServer server = server(200, "{\"choices\":[{\"message\":{\"content\":\"天河两房，2400 元\"}}]}",
                body, auth);
        try {
            OpenAiCompatibleClient client = new OpenAiCompatibleClient("deepseek",
                    "http://127.0.0.1:" + server.getAddress().getPort(), "sk-test",
                    "deepseek-chat", 3000, true, true, new ObjectMapper());
            String content = client.chat("你是顾问", "推荐天河两房");
            assertEquals("天河两房，2400 元", content);
            assertEquals("Bearer sk-test", auth.get());
            assertTrue(body.get().contains("\"model\":\"deepseek-chat\""));
            client.close();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void non200ThrowsForFallback() throws Exception {
        HttpServer server = server(500, "{\"error\":\"boom\"}", new AtomicReference<>(), new AtomicReference<>());
        try {
            OpenAiCompatibleClient client = new OpenAiCompatibleClient("local",
                    "http://127.0.0.1:" + server.getAddress().getPort(), "", "qwen", 3000,
                    true, false, new ObjectMapper());
            RuntimeException e = assertThrows(RuntimeException.class, () -> client.chat("s", "u"));
            assertTrue(e.getMessage().contains("HTTP 500"));
            client.close();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void socketTimeoutHonorsConfiguredMillis() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = "{\"choices\":[{\"message\":{\"content\":\"late\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        try {
            OpenAiCompatibleClient client = new OpenAiCompatibleClient("local",
                    "http://127.0.0.1:" + server.getAddress().getPort(), "", "qwen", 300,
                    true, false, new ObjectMapper());
            long start = System.currentTimeMillis();
            assertThrows(RuntimeException.class, () -> client.chat("s", "u"));
            assertTrue(System.currentTimeMillis() - start < 1200, "300ms 超时不应等到 mock 的 1500ms");
            client.close();
        } finally {
            server.stop(0);
        }
    }

    private HttpServer server(int status, String response, AtomicReference<String> body,
                              AtomicReference<String> auth) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            body.set(sb.toString());
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return server;
    }
}
