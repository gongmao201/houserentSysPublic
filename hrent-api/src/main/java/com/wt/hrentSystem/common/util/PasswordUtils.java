package com.wt.hrentSystem.common.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码双哈希工具。
 *
 * <p>存量数据是 MD5（无盐），不能直接覆盖，否则全员登录失败。
 * 策略：md5 兼容读 → 校验通过后用 BCrypt 重写并置 pwd_algo=bcrypt（静默升级）。</p>
 */
public final class PasswordUtils {

    public static final String ALGO_MD5 = "md5";
    public static final String ALGO_BCRYPT = "bcrypt";

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtils() {
    }

    /** 用 BCrypt 加密明文（每次结果不同，含随机盐）。 */
    public static String encodeBcrypt(String raw) {
        return ENCODER.encode(raw);
    }

    /** BCrypt 校验；入参为空或格式非法时返回 false，不抛异常。 */
    public static boolean matchesBcrypt(String raw, String stored) {
        if (raw == null || stored == null || stored.isEmpty()) {
            return false;
        }
        try {
            return ENCODER.matches(raw, stored);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 判断存量值是否已经是 BCrypt 串（$2a$/$2b$/$2y$）。 */
    public static boolean looksLikeBcrypt(String stored) {
        return stored != null && stored.length() >= 4
                && stored.charAt(0) == '$' && stored.charAt(1) == '2'
                && (stored.charAt(2) == 'a' || stored.charAt(2) == 'b' || stored.charAt(2) == 'y');
    }
}
