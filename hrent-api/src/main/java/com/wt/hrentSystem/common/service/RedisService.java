package com.wt.hrentSystem.common.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis 访问薄封装。
 *
 * 设计约束：Redis 是「增强项」而非启动前置条件 —— 连不上时不得阻塞启动，
 * 也不能让每个请求都等一次连接超时。这里用 30 秒熔断窗口：
 * 一次失败后窗口内所有调用直接降级返回，窗口结束再尝试恢复。
 */
@Service
public class RedisService {

    private static final Logger log = LoggerFactory.getLogger(RedisService.class);

    /** 失败后的降级窗口，避免每个请求重复吃连接超时 */
    private static final long DEGRADE_WINDOW_MS = 30_000L;

    private final StringRedisTemplate template;

    private volatile long retryAfterMillis = 0L;

    private volatile String lastError = "";

    public RedisService(StringRedisTemplate template) {
        this.template = template;
    }

    /** 是否处于可用窗口（不代表实际连通，仅表示可以发起一次尝试） */
    public boolean inService() {
        return System.currentTimeMillis() >= retryAfterMillis;
    }

    public boolean ping() {
        if (!inService()) {
            return false;
        }
        try {
            String pong = template.execute((RedisCallback<String>) connection -> connection.ping());
            return pong != null && "PONG".equalsIgnoreCase(pong.trim());
        } catch (Exception e) {
            degrade("PING", e);
            return false;
        }
    }

    /** 写入字符串并设置过期秒数（ttl<=0 表示不过期） */
    public boolean set(String key, String value, long ttlSeconds) {
        if (key == null || key.isEmpty() || !inService()) {
            return false;
        }
        try {
            if (ttlSeconds > 0) {
                template.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
            } else {
                template.opsForValue().set(key, value);
            }
            return true;
        } catch (Exception e) {
            degrade("SET " + key, e);
            return false;
        }
    }

    public String get(String key) {
        if (key == null || key.isEmpty() || !inService()) {
            return null;
        }
        try {
            return template.opsForValue().get(key);
        } catch (Exception e) {
            degrade("GET " + key, e);
            return null;
        }
    }

    public boolean exists(String key) {
        if (key == null || key.isEmpty() || !inService()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(template.hasKey(key));
        } catch (Exception e) {
            degrade("EXISTS " + key, e);
            return false;
        }
    }

    public boolean delete(String key) {
        if (key == null || key.isEmpty() || !inService()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(template.delete(key));
        } catch (Exception e) {
            degrade("DEL " + key, e);
            return false;
        }
    }

    /** 最近一次错误摘要（排查用，不含敏感信息） */
    public String lastError() {
        return lastError;
    }

    private void degrade(String op, Exception e) {
        long now = System.currentTimeMillis();
        if (now >= retryAfterMillis) {
            log.warn("Redis 操作失败（{}），未来 {} 秒内降级为本地逻辑：{}",
                    op, DEGRADE_WINDOW_MS / 1000, e.getMessage());
        }
        retryAfterMillis = now + DEGRADE_WINDOW_MS;
        lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
