package com.wt.hrentSystem.api.v2;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 文件 v2 接口（docs/13 §4.7）。上传必须登录，避免匿名写盘。
 */
@RestController
@RequestMapping("/api/v2/file")
public class FileController {

    @Resource
    private FileService fileService;
    @Resource
    private V2Context v2Context;

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                   HttpServletRequest request) {
        v2Context.current(request);
        return ApiResponse.ok(fileService.upload(file));
    }
}
