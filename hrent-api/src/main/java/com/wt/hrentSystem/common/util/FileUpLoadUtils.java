package com.wt.hrentSystem.common.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 文件上传工具
 * 说明：原始发布包缺失该类，UserController/PicController/HouseResourceController
 * 的图片上传均依赖，此为重建实现。
 * 存储目录取 application.yml 的 file.local（默认 ./static/images），
 * 返回的访问路径与 file.visitLocal 对应，经 WebMvcConfig 映射 /images/** 对外暴露。
 */
public class FileUpLoadUtils {

    /**
     * 保存上传文件
     *
     * @param file SpringMVC 上传文件
     * @param dir  业务子目录（以 / 开头），如 /upload/img/{userId}/head/
     * @return 可访问的相对路径，如 /upload/img/{userId}/head/xxx.png
     */
    public String uploadFile(MultipartFile file, String dir) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String localDir = resolveLocalDir();
        String subDir = (dir == null || dir.isEmpty()) ? "/" : dir;
        if (!subDir.startsWith("/")) {
            subDir = "/" + subDir;
        }
        File folder = new File(localDir + subDir);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new RuntimeException("创建上传目录失败: " + folder.getAbsolutePath());
        }
        String originalName = file.getOriginalFilename();
        String suffix = "";
        if (originalName != null && originalName.contains(".")) {
            suffix = originalName.substring(originalName.lastIndexOf('.'));
        }
        String fileName = UUID.randomUUID().toString().replace("-", "") + suffix;
        File target = new File(folder, fileName);
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败: " + e.getMessage(), e);
        }
        return subDir + fileName;
    }

    private String resolveLocalDir() {
        Object local = YmlUtil.getValue("file.local");
        if (local == null || "null".equals(String.valueOf(local).trim())
                || String.valueOf(local).trim().isEmpty()) {
            return System.getProperty("user.dir") + "/static/images";
        }
        return String.valueOf(local).trim();
    }
}
