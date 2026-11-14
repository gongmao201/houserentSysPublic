package com.wt.hrentSystem.common.config;

import com.wt.hrentSystem.common.util.TokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 把 Spring Environment 解析后的 JWT 配置注入 TokenUtils。
 *
 * 为什么需要这个类：TokenUtils 是静态工具，历史实现用 YmlUtil 读原始 YAML，
 * 读到的是 `${JWT_SECRET:}` 字面量而不是真实密钥 —— 既导致密钥不可控，也掩盖了未配置问题。
 *
 * 双验期：token.legacy-secret 用于给老库签发的 token 兜底验签；
 * token.legacy-grace-days 控制老 token 过期后的宽限天数（默认 7 天）。
 */
@Component
public class TokenSettings {

    private static final Logger log = LoggerFactory.getLogger(TokenSettings.class);

    public TokenSettings(@Value("${token.secret:}") String secret,
                         @Value("${token.expire-days:7}") long expireDays,
                         @Value("${token.legacy-secret:}") String legacySecret,
                         @Value("${token.legacy-grace-days:7}") long legacyGraceDays) {
        TokenUtils.configure(secret, expireDays, legacySecret, legacyGraceDays);
        if (secret == null || secret.trim().isEmpty()) {
            log.warn("token.secret 未配置，JWT 使用内置开发密钥；生产环境请在 .env 设置 TOKEN_SECRET（≥32 字节）");
        } else if (secret.trim().length() < 32) {
            log.warn("token.secret 长度不足 32 字节，已用 SHA-256 派生为 HS256 密钥；建议改为 32 字节以上随机值");
        }
        log.info("JWT 配置已加载：expireDays={}，老 token 宽限 {} 天，legacySecret={}",
                TokenUtils.expireDays(), TokenUtils.legacyGraceDays(),
                legacySecret == null || legacySecret.trim().isEmpty() ? "未配置（回退主密钥）" : "已配置");
    }
}
