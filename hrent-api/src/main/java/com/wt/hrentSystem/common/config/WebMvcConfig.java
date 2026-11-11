package com.wt.hrentSystem.common.config;

import com.wt.hrentSystem.common.util.YmlUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静态资源映射配置
 * 说明：将上传目录（file.local）与 classpath 静态目录映射为对外的 /images/**，
 * 使 file.visitLocal（如 localhost:8888/hrent/images）访问的图片真实可达。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        YmlUtil.loadYml("application.yml");
        Object local = YmlUtil.getValue("file.local");
        String dir;
        if (local == null || "null".equals(String.valueOf(local).trim())
                || String.valueOf(local).trim().isEmpty()) {
            dir = System.getProperty("user.dir") + "/static/images";
        } else {
            dir = String.valueOf(local).trim();
        }
        // Windows 路径统一为正斜杠，避免 file: 协议解析异常
        dir = dir.replace("\\", "/");
        if (!dir.endsWith("/")) {
            dir = dir + "/";
        }
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + dir, "classpath:/static/images/");

        // 前端构建产物（hrent-web/dist）映射到 /web/**，让单个 Java 进程同时提供页面
        // 与接口，部署时不必再单独装 nginx。前端为 hash 路由 + assetsPublicPath './'，
        // 因此放在 /hrent/web/ 子路径下可直接工作，无需服务端 fallback。
        // 目录优先取环境变量 HRENT_WEB_DIR（启动脚本注入绝对路径），否则回退到
        // 运行目录上一级的 hrent-web/dist（脚本以 hrent-api 为工作目录启动）。
        String webDir = System.getenv("HRENT_WEB_DIR");
        if (webDir == null || webDir.trim().isEmpty()) {
            webDir = System.getProperty("user.dir") + "/../hrent-web/dist";
        }
        // 规范化：去掉 .. 与多余分隔符，避免 file: 协议解析出越界路径
        webDir = java.nio.file.Paths.get(webDir).toAbsolutePath().normalize().toString().replace("\\", "/");
        if (!webDir.endsWith("/")) {
            webDir = webDir + "/";
        }
        registry.addResourceHandler("/web/**")
                .addResourceLocations("file:" + webDir);
    }

    /**
     * 全局跨域配置：允许开发环境前端（localhost:8080 / 127.0.0.1:8080）直接访问后端接口。
     * 生产环境应把 allowedOriginPatterns 收紧为具体域名。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
