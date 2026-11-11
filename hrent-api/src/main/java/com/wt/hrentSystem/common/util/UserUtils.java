package com.wt.hrentSystem.common.util;

import com.wt.hrentSystem.common.dao.UserDao;
import com.wt.hrentSystem.common.entity.User;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 当前登录用户工具（Spring Bean）
 * 说明：原始发布包缺失该类，各 Controller 均依赖，此为重建实现。
 * 行为：从请求头 token 解析用户 id 并查询用户；
 * 未携带/解析失败时回退为系统内置管理员（id=1），
 * 与 BaseEntity.preInsert() 对 currentUser 的非空要求保持一致。
 */
@Component
public class UserUtils {

    /** 未登录时的默认用户 id（种子数据中的超级管理员） */
    public static final String DEFAULT_USER_ID = "1";

    @Resource
    private UserDao userDao;

    /**
     * 获取当前请求的登录用户
     */
    public User getUser(HttpServletRequest request) {
        String userId = null;
        if (request != null) {
            userId = TokenUtils.getUserId(request.getHeader("token"));
        }
        if (userId == null || userId.isEmpty()) {
            userId = DEFAULT_USER_ID;
        }
        User user = userDao.get(userId);
        if (user == null) {
            // 数据库尚未初始化时的兜底，保证 createBy/updateBy 链路不断
            user = new User();
            user.setId(DEFAULT_USER_ID);
        }
        return user;
    }
}
