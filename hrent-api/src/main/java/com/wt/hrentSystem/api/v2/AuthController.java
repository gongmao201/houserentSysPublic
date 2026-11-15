package com.wt.hrentSystem.api.v2;

import com.wt.hrentSystem.common.entity.User;
import com.wt.hrentSystem.common.service.RedisService;
import com.wt.hrentSystem.common.service.TokenBlacklistService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证 v2 接口：退出登录（令牌写入 Redis 吊销名单）。
 * 令牌解析与 v2 其它接口一致：优先请求头 token，其次 Authorization: Bearer。
 */
@RestController
@RequestMapping("/api/v2/auth")
public class AuthController {

    @Resource
    private V2Context v2Context;

    @Resource
    private TokenBlacklistService tokenBlacklistService;

    @Resource
    private RedisService redisService;

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout(HttpServletRequest request) {
        User user = v2Context.current(request);
        String token = resolveToken(request);
        boolean revoked = tokenBlacklistService.revoke(token);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId());
        data.put("revoked", revoked);
        data.put("store", revoked ? "redis" : "degraded");
        if (revoked) {
            return ApiResponse.ok(data, "已退出登录");
        }
        // Redis 不可用时不能谎报成功吊销：令牌仍可用，客户端需自行丢弃
        return ApiResponse.ok(data, "Redis 不可用，令牌未被吊销（客户端请丢弃本地 token）");
    }

    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader("token");
        if (token == null || token.trim().isEmpty()) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                token = auth.substring(7).trim();
            }
        }
        return token;
    }
}
