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
