package com.wt.hrentSystem;

import com.wt.hrentSystem.common.util.MD5Utils;
import com.wt.hrentSystem.common.util.PasswordUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密码双哈希回归：存量 MD5 可读、BCrypt 可写、登录升级路径可用。
 */
class PasswordDualHashTest {

    @Test
    void md5LegacyValueStaysVerifiable() throws Exception {
        // 老系统就是 MD5 无盐；这条断言等于锁死"存量还能登录"
        assertEquals("e10adc3949ba59abbe56e057f20f883e", MD5Utils.EncoderByMd5("123456"));
    }

    @Test
    void bcryptRoundTrip() {
        String hash = PasswordUtils.encodeBcrypt("123456");
        assertTrue(PasswordUtils.looksLikeBcrypt(hash));
        assertTrue(PasswordUtils.matchesBcrypt("123456", hash));
        assertFalse(PasswordUtils.matchesBcrypt("1234567", hash));
        assertFalse(PasswordUtils.matchesBcrypt("123456", null));
        assertFalse(PasswordUtils.matchesBcrypt("123456", "not-a-hash"));
    }

    @Test
    void upgradePathFromMd5ToBcrypt() throws Exception {
        // 模拟：库里是 MD5 → 校验通过 → 写入 BCrypt → 再次校验走 BCrypt 分支
        String legacy = MD5Utils.EncoderByMd5("hrent@2026");
        assertFalse(PasswordUtils.looksLikeBcrypt(legacy));
        assertEquals(legacy, MD5Utils.EncoderByMd5("hrent@2026"));

        String upgraded = PasswordUtils.encodeBcrypt("hrent@2026");
        assertTrue(PasswordUtils.looksLikeBcrypt(upgraded));
        assertTrue(PasswordUtils.matchesBcrypt("hrent@2026", upgraded));
        // 同一明文两次 BCrypt 结果不同（随机盐），但都能校验
        assertFalse(upgraded.equals(PasswordUtils.encodeBcrypt("hrent@2026")));
        assertTrue(PasswordUtils.matchesBcrypt("hrent@2026", PasswordUtils.encodeBcrypt("hrent@2026")));
    }
}
