package com.wt.hrentSystem.common.util;

import com.wt.hrentSystem.common.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Date;

/**
 * JWT 登录令牌工具
 * 说明：原始发布包缺失该类，登录签发/解析均依赖，此为重建实现（基于 jjwt 0.9.1）。
 */
public class TokenUtils {

    private TokenUtils() {
    }

    /** 签名密钥（可通过 application.yml 的 token.secret 覆盖） */
    private static String secret() {
        Object v = YmlUtil.getValue("token.secret");
        return v == null ? "<SET_VIA_ENV_JWT_SECRET>" : String.valueOf(v);
    }

    /** 有效期（天），可通过 application.yml 的 token.expire-days 覆盖 */
    private static long expireMillis() {
        Object v = YmlUtil.getValue("token.expire-days");
        long days = 7L;
        if (v != null) {
            try {
                days = Long.parseLong(String.valueOf(v).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return days * 24L * 60L * 60L * 1000L;
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
                .signWith(SignatureAlgorithm.HS256, secret())
                .compact();
    }

    /**
     * 从 token 中解析用户 id；解析失败返回 null
     */
    public static String getUserId(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .setSigningKey(secret())
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}
