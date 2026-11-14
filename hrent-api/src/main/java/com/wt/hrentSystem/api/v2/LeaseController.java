package com.wt.hrentSystem.api.v2;

import com.wt.hrentSystem.common.entity.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 租约 v2 接口（docs/13 §4.3）。terminate 是评价窗口的触发器。
 */
@RestController
@RequestMapping("/api/v2/lease")
public class LeaseController {

    @Resource
    private LeaseService leaseService;
    @Resource
    private V2Context v2Context;

    @GetMapping("/mine")
    public ApiResponse<PageResult<Map<String, Object>>> mine(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(leaseService.mine(user, status, Req.pageNo(pageNo), Req.pageSize(pageSize)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable("id") String id,
                                                HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(leaseService.get(id, user));
    }

    @PostMapping("/{id}/terminate")
    public ApiResponse<Map<String, Object>> terminate(@PathVariable("id") String id,
                                                      @RequestBody Map<String, Object> body,
                                                      HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(leaseService.terminate(id, body, user));
    }

    /**到期：生效中 → 已到期（房东/管理员） */
    @PostMapping("/{id}/expire")
    public ApiResponse<Map<String, Object>> expire(@PathVariable("id") String id,
                                                   @RequestBody(required = false) Map<String, Object> body,
                                                   HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(leaseService.expire(id, body, user));
    }

    /**解约：生效中 → 已解约（租客/房东/管理员），必须写原因 */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable("id") String id,
                                                   @RequestBody Map<String, Object> body,
                                                   HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(leaseService.cancel(id, body, user));
    }
}
