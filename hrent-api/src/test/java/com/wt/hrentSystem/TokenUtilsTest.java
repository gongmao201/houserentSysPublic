package com.wt.hrentSystem;

import com.wt.hrentSystem.common.entity.User;
import com.wt.hrentSystem.common.util.TokenUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT（jjwt 0.11.5）签发/解析回归：不依赖 Spring 上下文。
 * 覆盖：正常往返、被篡改令牌、过期配置、短密钥派生、空密钥兜底。
 */
class TokenUtilsTest {

    private static final String SECRET_32 = "0123456789abcdef0123456789abcdef";

    @AfterEach
    void reset() {
        TokenUtils.configure(null, 7);
    }

    @Test
    void signAndParseRoundTrip() {
        TokenUtils.configure(SECRET_32, 7);
        String token = TokenUtils.sign(user("42"));
        assertNotNull(token);
        assertEquals("42", TokenUtils.getUserId(token));
        assertTrue(TokenUtils.remainingSeconds(token) > 0);
    }

    @Test
    void tamperedTokenIsRejected() {
        TokenUtils.configure(SECRET_32, 7);
        String token = TokenUtils.sign(user("7"));
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertNull(TokenUtils.getUserId(tampered));
        assertNull(TokenUtils.getUserId(null));
        assertNull(TokenUtils.getUserId(""));
        assertNull(TokenUtils.getUserId("not-a-jwt"));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        TokenUtils.configure(SECRET_32, 7);
        String token = TokenUtils.sign(user("9"));
        TokenUtils.configure("another-secret-another-secret-1234", 7);
        assertNull(TokenUtils.getUserId(token));
    }

    @Test
    void shortSecretIsDerivedToHs256Key() {
        // < 32 字节：内部 SHA-256 派生，仍能正常签发/解析（不会抛 WeakKeyException）
        TokenUtils.configure("short-secret", 7);
        String token = TokenUtils.sign(user("11"));
        assertNotNull(token);
        assertEquals("11", TokenUtils.getUserId(token));
    }

    @Test
    void emptySecretFallsBackAndStillWorks() {
        TokenUtils.configure("", 7);
        assertEquals(TokenUtils.FALLBACK_SECRET, TokenUtils.secret());
        String token = TokenUtils.sign(user("12"));
        assertNotNull(token);
        assertEquals("12", TokenUtils.getUserId(token));
    }

    @Test
    void missingUserIdProducesNoToken() {
        TokenUtils.configure(SECRET_32, 7);
        assertNull(TokenUtils.sign(null));
        assertNull(TokenUtils.sign(new User()));
        assertNotEquals("", TokenUtils.FALLBACK_SECRET);
    }

    private User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
