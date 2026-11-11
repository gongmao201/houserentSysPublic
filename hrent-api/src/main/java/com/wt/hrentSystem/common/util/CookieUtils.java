package com.wt.hrentSystem.common.util;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Cookie 工具
 * 说明：原始发布包缺失该类，Page 分页参数记忆依赖，此为重建实现。
 */
public class CookieUtils {

    private CookieUtils() {
    }

    /** 写入 Cookie（有效期一年，全站路径） */
    public static void setCookie(HttpServletResponse response, String name, String value) {
        if (response == null || name == null) {
            return;
        }
        Cookie cookie = new Cookie(name, value == null ? "" : value);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24 * 365);
        response.addCookie(cookie);
    }

    /** 读取 Cookie 值，不存在返回 null */
    public static String getCookie(HttpServletRequest request, String name) {
        if (request == null || name == null) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** 删除 Cookie */
    public static void removeCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
