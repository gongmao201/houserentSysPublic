package com.wt.hrentSystem.api.v2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * v2 文件上传（docs/13 §4.7）。
 * 已实现：扩展名白名单、Content-Type 白名单、5MB 上限、UUID 重命名（防路径穿越）、目录配置化。
 * 待定 T-05：压缩到 200KB + 400px 缩略图需图像库，本期 thumbUrl 与 url 同值并显式返回 compressed=false。
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final Set<String> EXTS = new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "webp"));
    private static final Set<String> TYPES = new HashSet<>(
            Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp"));

    @Value("${file.local:./static/images}")
    private String localDir;

    public Map<String, Object> upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.bad("上传文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw ApiException.bad("文件不能超过 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw ApiException.bad("仅支持 jpg / jpeg / png / webp 图片");
        }
        String original = file.getOriginalFilename();
        String ext = extensionOf(original);
        if (ext == null || !EXTS.contains(ext)) {
            throw ApiException.bad("文件扩展名不合法，仅支持 jpg / jpeg / png / webp");
        }

        String id = UUID.randomUUID().toString().replace("-", "");
        String name = id + "." + ext;
        Path dir = Paths.get(localDir).toAbsolutePath().normalize();
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(dir)) {
            throw ApiException.bad("非法文件路径");
        }
        try {
            Files.createDirectories(dir);
            file.transferTo(target.toFile());
        } catch (IOException e) {
            log.error("v2 上传落盘失败 traceId=" + TraceIds.get() + " dir=" + dir, e);
            throw new ApiException(ApiCodes.SERVER_ERROR, "文件保存失败，请稍后重试");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        // 与存量 WebMvcConfig 的 /images/** 静态映射对齐；契约里的 /uploads/... 见待定 T-12
        data.put("url", "/images/" + name);
        data.put("thumbUrl", "/images/" + name);
        data.put("size", file.getSize());
        data.put("width", null);
        data.put("height", null);
        data.put("compressed", Boolean.FALSE);
        return data;
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
