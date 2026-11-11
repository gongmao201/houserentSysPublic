package com.wt.hrentSystem.api.v2;

import com.wt.hrentSystem.common.entity.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 报修 v2 接口（docs/13 §4.4）。
 * 5 个状态动作用统一路由 /{id}/{action} 分发，action 由 Service 白名单校验，未知动作返回 400。
 */
@RestController
@RequestMapping("/api/v2/repair")
public class RepairController {

    @Resource
    private RepairService repairService;
    @Resource
    private V2Context v2Context;

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
            HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(repairService.create(body, user, idemKey));
    }

    @GetMapping("/mine")
    public ApiResponse<PageResult<Map<String, Object>>> mine(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(repairService.mine(user, status,
                Req.pageNo(pageNo), Req.pageSize(pageSize)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable("id") String id,
                                                HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(repairService.get(id, user));
    }

    @PostMapping("/{id}/{action}")
    public ApiResponse<Map<String, Object>> transition(
            @PathVariable("id") String id,
            @PathVariable("action") String action,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(repairService.transition(id, action,
                body == null ? new java.util.LinkedHashMap<String, Object>() : body, user));
    }
}
