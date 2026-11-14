package com.wt.hrentSystem.common.util;

import com.wt.hrentSystem.common.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

/**
 * JWT 登录令牌工具（jjwt 0.11.5）。
 *
 * 密钥来源：Spring 启动时由 TokenSettings 注入 token.secret（已由 Environment 解析占位符），
 * 不再经过 YmlUtil —— 后者读的是原始 YAML，会把 `${JWT_SECRET:}` 当成字面量。
 * HS256 要求密钥 ≥ 256 bit；配置过短时用 SHA-256 派生为 32 字节，避免 WeakKeyException。
 */
public class TokenUtils {

    /** 未注入配置时的开发兜底（不安全，仅用于工具类脱离 Spring 的单测/离线调用） */
    public static final String FALLBACK_SECRET = "<SET_VIA_ENV_JWT_SECRET>";

    private static final long DEFAULT_EXPIRE_DAYS = 7L;

    private static volatile String secret;

    private static volatile long expireDays = DEFAULT_EXPIRE_DAYS;

    private TokenUtils() {
    }

    /**
     * 由 Spring 容器调用，注入解析后的配置。
     *
     * @param resolvedSecret     token.secret 的最终值（可能为空，表示未配置）
     * @param resolvedExpireDays token.expire-days，非法值回退 7 天
     */
    public static void configure(String resolvedSecret, long resolvedExpireDays) {
        secret = (resolvedSecret == null || resolvedSecret.trim().isEmpty())
                ? null
                : resolvedSecret.trim();
        if (resolvedExpireDays > 0) {
            expireDays = resolvedExpireDays;
        }
    }

    /** 当前生效的密钥原文（不含派生） */
    public static String secret() {
        String s = secret;
        return (s == null || s.isEmpty()) ? FALLBACK_SECRET : s;
    }

    /** 有效期（天） */
    public static long expireDays() {
        return expireDays;
    }

    /**
     * 为登录用户签发 token（subject 为用户 id）
     */
    public static String sign(User user) {
        if (user == null || user.getId() == null || user.getId().isEmpty()) {
            return null;
        }
        Date now = new Date();
        return Jwts.builder()
                .setSubject(user.getId())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireMillis()))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 从 token 中解析用户 id；为空、过期、签名不符等任何异常均返回 null
     */
    public static String getUserId(String token) {
        Claims claims = parse(token);
        return claims == null ? null : claims.getSubject();
    }

    /**
     * token 剩余有效秒数（用于写 Redis 吊销名单的 TTL）；无法解析时回退配置的有效期
     */
    public static long remainingSeconds(String token) {
        Claims claims = parse(token);
        if (claims == null || claims.getExpiration() == null) {
            return expireMillis() / 1000L;
        }
        long remaining = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000L;
        return remaining > 0 ? remaining : 0L;
    }

    private static Claims parse(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key())
                    .build()
                    .parseClaimsJws(token.trim())
                    .getBody();
        } catch (Exception e) {
            // 签名不符 / 过期 / 格式错误统一视为无效令牌
            return null;
        }
    }

    private static long expireMillis() {
        return expireDays * 24L * 60L * 60L * 1000L;
    }

    /** HS256 密钥：原文不足 32 字节时用 SHA-256 派生，保证密钥强度 */
    private static SecretKey key() {
        byte[] raw = secret().getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            raw = sha256(raw);
        }
        return Keys.hmacShaKeyFor(raw);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256 实现", e);
        }
    }
}
