package com.wt.hrentSystem.common.service;

import com.wt.hrentSystem.common.util.TokenUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * JWT 吊销名单（Redis）。
 *
 * 键为 token 的 SHA-256 摘要，不落库原始令牌；TTL 取令牌剩余有效期，
 * 过期后键自动消失，不需要额外清理任务。
 */
@Service
public class TokenBlacklistService {

    /** 键前缀：hrent:jwt:blacklist:<sha256> */
    private static final String KEY_PREFIX = "hrent:jwt:blacklist:";

    private final RedisService redisService;

    public TokenBlacklistService(RedisService redisService) {
        this.redisService = redisService;
    }

    /**
     * 吊销 token。
     *
     * @return true 表示已写入 Redis；false 表示 Redis 不可用或 token 无效（调用方需如实反馈）
     */
    public boolean revoke(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        long ttl = TokenUtils.remainingSeconds(token);
        return redisService.set(key(token), "1", ttl > 0 ? ttl : 60L);
    }

    public boolean isRevoked(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        return redisService.exists(key(token));
    }

    public static String key(String token) {
        return KEY_PREFIX + sha256Hex(token.trim());
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256 实现", e);
        }
    }
}
