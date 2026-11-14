package com.wt.hrentSystem.common.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.wt.hrentSystem.common.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 登录令牌工具（jjwt 0.11.5）。
 *
 * 密钥来源：Spring 启动时由 TokenSettings 注入 token.secret（已由 Environment 解析占位符），
 * 不再经过 YmlUtil —— 后者读的是原始 YAML，会把 `${JWT_SECRET:}` 当成字面量。
 * HS256 要求密钥 ≥ 256 bit；配置过短时用 SHA-256 派生为 32 字节，避免 WeakKeyException。
 *
 * 双验期（docs/13 §2.3）：换签发库当天老 token 不能全量失效。
 *   - 新 token 带 claim `vt=2`，按 token.expire-days 严格校验，过期即失效；
 *   - 老 token（无 `vt` claim）先用当前密钥验签，验不过再回退 token.legacy-secret 验签；
 *   - 老 token 过期后仍可在 token.legacy-grace-days（默认 7 天）内使用，给前端留出换 token 窗口。
 */
public class TokenUtils {

    /** 未注入配置时的开发兜底（不安全，仅用于工具类脱离 Spring 的单测/离线调用） */
    public static final String FALLBACK_SECRET = "<SET_VIA_ENV_JWT_SECRET>";

    /** 新签发 token 的版本标记；老 token 没有该 claim */
    private static final String CLAIM_VERSION = "vt";
    private static final int TOKEN_VERSION = 2;

    private static final long DEFAULT_EXPIRE_DAYS = 7L;
    private static final long DEFAULT_LEGACY_GRACE_DAYS = 7L;

    private static volatile String secret;
    private static volatile String legacySecret;

    private static volatile long expireDays = DEFAULT_EXPIRE_DAYS;
    private static volatile long legacyGraceDays = DEFAULT_LEGACY_GRACE_DAYS;

    private TokenUtils() {
    }

    /**
     * 由 Spring 容器调用，注入解析后的配置（兼容旧调用方）。
     *
     * @param resolvedSecret     token.secret 的最终值（可能为空，表示未配置）
     * @param resolvedExpireDays token.expire-days，非法值回退 7 天
     */
    public static void configure(String resolvedSecret, long resolvedExpireDays) {
        configure(resolvedSecret, resolvedExpireDays, null, DEFAULT_LEGACY_GRACE_DAYS);
    }

    /**
     * 双验期配置。
     *
     * @param resolvedSecret     新 token 签发/验签密钥
     * @param resolvedExpireDays 新 token 有效期（天）
     * @param resolvedLegacy     老 token 的兜底密钥，未配置时回退为新密钥
     * @param graceDays          老 token 过期后的宽限天数（默认 7）
     */
    public static void configure(String resolvedSecret, long resolvedExpireDays,
                                 String resolvedLegacy, long graceDays) {
        secret = blankToNull(resolvedSecret);
        legacySecret = blankToNull(resolvedLegacy);
        if (resolvedExpireDays > 0) {
            expireDays = resolvedExpireDays;
        }
        if (graceDays >= 0) {
            legacyGraceDays = graceDays;
        }
    }

    /** 当前生效的密钥原文（不含派生） */
    public static String secret() {
        String s = secret;
        return (s == null || s.isEmpty()) ? FALLBACK_SECRET : s;
    }

    /** 当前生效的兜底密钥原文；未配置时等于主密钥 */
    public static String legacySecret() {
        String s = legacySecret;
        return (s == null || s.isEmpty()) ? secret() : s;
    }

    /** 老 token 过期宽限天数 */
    public static long legacyGraceDays() {
        return legacyGraceDays;
    }

    /** 有效期（天） */
    public static long expireDays() {
        return expireDays;
    }

    /**
     * 为登录用户签发 token（subject 为用户 id，带 vt=2 标记）
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
                .claim(CLAIM_VERSION, TOKEN_VERSION)
                .signWith(key(secretKeySource()), SignatureAlgorithm.HS256)
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
     * token 剩余有效秒数（用于写 Redis 吊销名单的 TTL）；
     * 老 token 宽限期内返回剩余宽限秒数，无法解析时回退配置的有效期
     */
    public static long remainingSeconds(String token) {
        Claims claims = parse(token);
        if (claims == null || claims.getExpiration() == null) {
            return expireMillis() / 1000L;
        }
        long remaining = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000L;
        if (remaining > 0) {
            return remaining;
        }
        // 老 token 已过期但仍在宽限期：吊销名单 TTL 取剩余宽限时间，避免提前失效
        long graceEnd = claims.getExpiration().getTime() + graceMillis();
        long left = (graceEnd - System.currentTimeMillis()) / 1000L;
        return Math.max(left, 0L);
    }

    /**是否为老 token（无 vt claim）；解析失败返回 false */
    public static boolean isLegacy(String token) {
        JSONObject payload = payloadOf(token);
        return payload != null && payload.get(CLAIM_VERSION) == null;
    }

    private static Claims parse(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        String t = token.trim();
        JSONObject payload = payloadOf(t);
        if (payload == null) {
            return null;
        }
        boolean legacy = payload.get(CLAIM_VERSION) == null;
        Claims expiredClaims = null;

        // 1) 主密钥验签（换库后新 token 走这条）
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key(secretKeySource()))
                    .build()
                    .parseClaimsJws(t)
                    .getBody();
        } catch (ExpiredJwtException e) {
            // 签名已通过，仅时间过期：老 token 才有宽限
            expiredClaims = e.getClaims();
        } catch (Exception e) {
            // 签名不符 / 格式错误，继续尝试老密钥
        }

        // 2) 老密钥兜底验签（老库签发的 token）
        if (!sameKey(legacySecret(), secret()) || expiredClaims == null) {
            try {
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(key(legacySecret()))
                        .build()
                        .parseClaimsJws(t)
                        .getBody();
                //老密钥只服务老 token；新 token 必须由主密钥签发，防止“降级签名”绕过轮换
                return legacy ? claims : null;
            } catch (ExpiredJwtException e) {
                if (expiredClaims == null) {
                    expiredClaims = e.getClaims();
                }
            } catch (Exception e) {
                // 两种密钥都验不过：无效令牌
                if (expiredClaims == null) {
                    return null;
                }
            }
        }

        // 3) 过期判定：老 token 允许 exp + 宽限期，新 token 一秒都不放宽
        if (expiredClaims == null) {
            return null;
        }
        if (!legacy) {
            return null;
        }
        Date expiration = expiredClaims.getExpiration();
        if (expiration == null) {
            return null;
        }
        return System.currentTimeMillis() <= expiration.getTime() + graceMillis() ? expiredClaims : null;
    }

    /**拆 payload 读取 claims（未验签，仅用于判断 token 版本与是否存在 exp） */
    private static JSONObject payloadOf(String token) {
        if (token == null) {
            return null;
        }
        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            byte[] raw = Base64.getUrlDecoder().decode(parts[1]);
            return JSON.parseObject(new String(raw, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private static String secretKeySource() {
        return secret();
    }

    private static boolean sameKey(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String blankToNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    private static long expireMillis() {
        return expireDays * 24L * 60L * 60L * 1000L;
    }

    private static long graceMillis() {
        return legacyGraceDays * 24L * 60L * 60L * 1000L;
    }

    /** HS256 密钥：原文不足 32 字节时用 SHA-256 派生，保证密钥强度 */
    private static SecretKey key(String source) {
        byte[] raw = source.getBytes(StandardCharsets.UTF_8);
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
