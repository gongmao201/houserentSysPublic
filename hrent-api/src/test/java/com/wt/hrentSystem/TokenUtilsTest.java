package com.wt.hrentSystem;

import com.wt.hrentSystem.common.entity.User;
import com.wt.hrentSystem.common.util.TokenUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT（jjwt 0.11.5）签发/解析回归：不依赖 Spring 上下文。
 * 覆盖：正常往返、被篡改令牌、过期配置、短密钥派生、空密钥兜底、双验期（老 token 兼容 7 天）。
 */
class TokenUtilsTest {

    private static final String SECRET_32 = "0123456789abcdef0123456789abcdef";
    private static final String LEGACY_32 = "fedcba9876543210fedcba9876543210";
    private static final long DAY = 24L * 60L * 60L * 1000L;

    @AfterEach
    void reset() {
        TokenUtils.configure(null, 7, null, 7);
    }

    @Test
    void signAndParseRoundTrip() {
        TokenUtils.configure(SECRET_32, 7);
        String token = TokenUtils.sign(user("42"));
        assertNotNull(token);
        assertEquals("42", TokenUtils.getUserId(token));
        assertTrue(TokenUtils.remainingSeconds(token) > 0);
        //新 token 带 vt=2，不再走老 token 宽限
        assertFalse(TokenUtils.isLegacy(token));
    }

    @Test
    void tamperedTokenIsRejected() {
        TokenUtils.configure(SECRET_32, 7);
        String token = TokenUtils.sign(user("7"));
        // 只改签名末两位可能解码出完全相同的字节（Base64 末位是填充位），测试会偶发假通过；
        // 这里改 payload 首字符，claims 必然变化，签名必然对不上。
        String[] parts = token.split("\\.");
        char[] payload = parts[1].toCharArray();
        payload[0] = payload[0] == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + new String(payload) + "." + parts[2];
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

    @Test
    void legacyTokenExpiredTwoDaysIsAcceptedWithinGrace() {
        TokenUtils.configure(SECRET_32, 7, null, 7);
        // 老 token：没有 vt claim，7 天有效期，2 天前过期
        String legacy = legacyToken("88", SECRET_32, -2);
        assertTrue(TokenUtils.isLegacy(legacy));
        assertEquals("88", TokenUtils.getUserId(legacy));
    }

    @Test
    void legacyTokenExpiredTenDaysIsRejected() {
        TokenUtils.configure(SECRET_32, 7, null, 7);
        String legacy = legacyToken("89", SECRET_32, -10);
        assertTrue(TokenUtils.isLegacy(legacy));
        assertNull(TokenUtils.getUserId(legacy));
    }

    @Test
    void legacyTokenSignedWithLegacySecretIsAccepted() {
        TokenUtils.configure(SECRET_32, 7, LEGACY_32, 7);
        String legacy = legacyToken("90", LEGACY_32, 1);
        assertEquals("90", TokenUtils.getUserId(legacy));
    }

    @Test
    void newTokenSignedWithLegacySecretIsRejected() {
        // 防降级：带 vt=2 的 token 只能用主密钥签，老密钥不能兜底
        TokenUtils.configure(SECRET_32, 7, LEGACY_32, 7);
        String forged = Jwts.builder()
                .setSubject("91")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + DAY))
                .claim("vt", 2)
                .signWith(Keys.hmacShaKeyFor(LEGACY_32.getBytes()), SignatureAlgorithm.HS256)
                .compact();
        assertNull(TokenUtils.getUserId(forged));
    }

    @Test
    void expiredNewTokenIsRejectedEvenWithinGrace() {
        TokenUtils.configure(SECRET_32, 7, null, 7);
        String expired = Jwts.builder()
                .setSubject("92")
                .setIssuedAt(new Date(System.currentTimeMillis() - 2 * DAY))
                .setExpiration(new Date(System.currentTimeMillis() - DAY))
                .claim("vt", 2)
                .signWith(Keys.hmacShaKeyFor(SECRET_32.getBytes()), SignatureAlgorithm.HS256)
                .compact();
        assertNull(TokenUtils.getUserId(expired));
    }

    /**手工构造老 token（无 vt claim），offsetDays 为负数表示已过期 */
    private String legacyToken(String userId, String secret, long offsetDays) {
        long exp = System.currentTimeMillis() + offsetDays * DAY;
        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(new Date(exp - 7 * DAY))
                .setExpiration(new Date(exp))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    private User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
