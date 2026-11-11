package com.wt.hrentSystem.common.config;

import com.wt.hrentSystem.common.util.YmlUtil;

/**
 * 服务端基础地址配置
 * 说明：原始发布包缺失该类，SysCommonController.getBaseUrl 依赖，此为重建实现。
 * 直接 new 使用，内部从 application.yml 读取 ip / server.port / context-path。
 */
public class ServerConfig {

    private String ip;
    private String port;
    private String contextPath;

    public ServerConfig() {
        YmlUtil.loadYml("application.yml");
        this.ip = normalize(YmlUtil.getValue("ip"), "localhost");
        this.port = normalize(YmlUtil.getValue("server.port"), "8888");
        Object cp = YmlUtil.getValue("server.servlet.context-path");
        this.contextPath = cp == null ? "" : String.valueOf(cp).trim();
    }

    /** 后端基础访问地址，如 http://127.0.0.1:8888/hrent */
    public String getBaseUrl() {
        return "http://" + ip + ":" + port + contextPath;
    }

    private String normalize(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() || "null".equals(s) ? defaultValue : s;
    }

    public String getIp() {
        return ip;
    }

    public String getPort() {
        return port;
    }

    public String getContextPath() {
        return contextPath;
    }
}
