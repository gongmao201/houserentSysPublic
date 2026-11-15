package com.wt.hrentSystem.api.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wt.hrentSystem.ai.llm.AiGateway;
import com.wt.hrentSystem.common.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * AI v2 接口（docs/13 §4.6）。
 * recommend / reason 允许匿名（演示体验，待定 T-11）；feedback 必须登录以便归属埋点。
 * 不抛异常的「可选用户」解析只读取 token，不吞掉任何业务异常。
 */
@RestController
@RequestMapping("/api/v2/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    @Resource
    private AiService aiService;
    @Resource
    private V2Context v2Context;
    @Resource
    private ObjectMapper objectMapper;
    @Resource(name = "aiStreamExecutor")
    private ExecutorService aiStreamExecutor;

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

    /**
     * SSE 理由增强（docs/00 §3.1 D11）：先推模板 reason 帧，再推模型增强帧。
     * 事件：meta → template* → reason*（模型成功时）→ done；模型全挂仍正常 done。
     */
    @PostMapping(value = "/reason/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter reasonStream(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        v2Context.current(request);
        Req.body(body);
        List<String> ids = Req.strList(body, "houseIds", 10);
        if (ids.isEmpty()) {
            throw ApiException.bad("houseIds 不能为空");
        }
        SseEmitter emitter = new SseEmitter(60_000L);
        aiStreamExecutor.execute(() -> streamReasons(emitter, ids));
        return emitter;
    }

    private void streamReasons(SseEmitter emitter, List<String> ids) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("total", ids.size());
            meta.put("mode", "stream");
            send(emitter, "meta", meta);

            List<Map<String, Object>> templates = aiService.templateReasonItems(ids);
            for (Map<String, Object> item : templates) {
                send(emitter, "template", item);
            }

            AiGateway.ReasonOutcome outcome = aiService.reasonOutcome(ids);
            int enhanced = 0;
            for (Map<String, Object> item : templates) {
                String id = String.valueOf(item.get("houseId"));
                String reason = outcome.getReasons().get(id);
                if (reason != null) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("houseId", id);
                    data.put("reason", reason);
                    data.put("source", outcome.getProvider());
                    send(emitter, "reason", data);
                    enhanced++;
                }
            }
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("source", outcome.getProvider());
            done.put("mode", enhanced > 0 ? "llm" : "rule");
            done.put("total", ids.size());
            done.put("enhanced", enhanced);
            send(emitter, "done", done);
            emitter.complete();
        } catch (Exception e) {
            log.warn("SSE 理由增强中断：" + e.getMessage());
            try {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("source", "rule");
                error.put("message", "增强中断，前端保留模板文案");
                send(emitter, "done", error);
                emitter.complete();
            } catch (Exception ignored) {
                emitter.completeWithError(e);
            }
        }
    }

    private void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(objectMapper.writeValueAsString(data)));
    }

}
