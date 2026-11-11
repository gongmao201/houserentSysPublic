package com.wt.hrentSystem.common.util;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5 加密工具
 * 说明：原始发布包缺失该类，UserService/UserController 登录注册均依赖，
 * 此为重建实现。输出为 32 位小写十六进制（与数据库种子数据口径一致）。
 */
public class MD5Utils {

    private MD5Utils() {
    }

    /**
     * 对字符串进行 MD5 加密
     *
     * @param str 明文字符串
     * @return 32 位小写十六进制密文
     */
    public static String EncoderByMd5(String str)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        if (str == null) {
            return null;
        }
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] digest = md5.digest(str.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder(32);
        for (byte b : digest) {
            int v = b & 0xFF;
            if (v < 0x10) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }

    /**
     * 校验明文与密文是否匹配
     */
    public static boolean matches(String raw, String encoded)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        if (raw == null || encoded == null) {
            return false;
        }
        return EncoderByMd5(raw).equalsIgnoreCase(encoded);
    }
}
