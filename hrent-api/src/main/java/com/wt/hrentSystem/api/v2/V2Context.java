package com.wt.hrentSystem.api.v2;

import com.wt.hrentSystem.api.dao.V2Mapper;
import com.wt.hrentSystem.common.dao.UserDao;
import com.wt.hrentSystem.common.entity.User;
import com.wt.hrentSystem.common.service.TokenBlacklistService;
import com.wt.hrentSystem.common.util.TokenUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;

/**
 * v2 当前登录用户 + 越权校验。
 * 与存量 UserUtils 的关键差异：存量「无 token 回退管理员」，v2 严格拒绝（600）。
 * 原因：v2 涉及评价、账单、报修，静默提权会造成真实越权。
 */
@Component
public class V2Context {

    @Resource
    private UserDao userDao;

    @Resource
    private V2Mapper v2Mapper;

    @Resource
    private TokenBlacklistService tokenBlacklistService;

    public User current(HttpServletRequest request) {
        String token = request.getHeader("token");
        if (token == null || token.trim().isEmpty()) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                token = auth.substring(7).trim();
            }
        }
        String userId = TokenUtils.getUserId(token);
        if (userId == null || userId.isEmpty()) {
            throw new ApiException(ApiCodes.UNAUTHORIZED, "未登录或登录已过期");
        }
        // 已退出登录的令牌：Redis 写入吊销名单；Redis 不可用时降级为不拦截
        if (tokenBlacklistService.isRevoked(token)) {
            throw new ApiException(ApiCodes.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        User user = userDao.get(userId);
        if (user == null) {
            throw new ApiException(ApiCodes.UNAUTHORIZED, "登录用户不存在");
        }
        return user;
    }

    public boolean isAdmin(User user) {
        return user.getRole() != null && user.getRole() == 2;
    }

    /**docs/13 §5 第 2 条：接口第一行校验 owner，越权返回 403 而不是 404 */
    public void assertOwner(User user, String ownerId) {
        if (isAdmin(user)) {
            return;
        }
        if (ownerId == null || !ownerId.equals(user.getId())) {
            throw ApiException.forbidden("无权操作该资源");
        }
    }

    /**多角色来源 user_role；空表时回退存量 sys_user.role 单值 */
    public List<String> roles(User user) {
        List<String> roles = v2Mapper.findRolesByUserId(user.getId());
        if (roles != null && !roles.isEmpty()) {
            return roles;
        }
        Integer r = user.getRole();
        if (r == null) {
            return Collections.emptyList();
        }
        String single = r == 2 ? "ADMIN" : (r == 1 ? "LANDLORD" : "TENANT");
        return Collections.singletonList(single);
    }

    public boolean hasRole(User user, String role) {
        return isAdmin(user) || roles(user).contains(role);
    }
}
