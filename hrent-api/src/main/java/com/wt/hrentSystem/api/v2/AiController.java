package com.wt.hrentSystem.api.v2;

import com.wt.hrentSystem.common.entity.User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * AI v2 接口（docs/13 §4.6）。
 * recommend / reason 允许匿名（演示体验，待定 T-11）；feedback 必须登录以便归属埋点。
 * 不抛异常的「可选用户」解析只读取 token，不吞掉任何业务异常。
 */
@RestController
@RequestMapping("/api/v2/ai")
public class AiController {

    @Resource
    private AiService aiService;
    @Resource
    private V2Context v2Context;

    @PostMapping("/recommend")
    public ApiResponse<Map<String, Object>> recommend(@RequestBody Map<String, Object> body) {
        Map<String, Object> data = aiService.recommend(body);
        return ApiResponse.ok(data, AiService.takeMessage(data));
    }

    @PostMapping("/reason")
    public ApiResponse<Map<String, Object>> reason(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(aiService.reason(body));
    }

    @PostMapping("/feedback")
    public ApiResponse<Map<String, Object>> feedback(@RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(aiService.feedback(body, user));
    }

}
