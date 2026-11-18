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
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 账单 v2 接口（docs/13 §4.5）。不含在线支付通道，仅线下转账标记 + 房东确认。
 */
@RestController
@RequestMapping("/api/v2/bill")
public class BillController {

    @Resource
    private BillService billService;
    @Resource
    private V2Context v2Context;

    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generate(@RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(billService.generate(body, user));
    }

    @GetMapping("/mine")
    public ApiResponse<PageResult<Map<String, Object>>> mine(
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(billService.mine(user, period, status,
                Req.pageNo(pageNo), Req.pageSize(pageSize)));
    }

    @GetMapping("/pending")
    public ApiResponse<PageResult<Map<String, Object>>> pending(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(billService.pending(user, Req.pageNo(pageNo), Req.pageSize(pageSize)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable("id") String id,
                                                HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(billService.get(id, user));
    }

    @PostMapping("/{id}/mark-paid")
    public ApiResponse<Map<String, Object>> markPaid(@PathVariable("id") String id,
                                                     @RequestBody Map<String, Object> body,
                                                     HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(billService.markPaid(id, body, user));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<Map<String, Object>> confirm(@PathVariable("id") String id,
                                                    HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(billService.confirm(id, user));
    }

    /**追加费用明细（WATER/ELECTRIC/PROPERTY/OTHER），仅房东在待缴/逾期阶段可用 */
    @PostMapping("/{id}/items")
    public ApiResponse<Map<String, Object>> addItem(@PathVariable("id") String id,
                                                    @RequestBody Map<String, Object> body,
                                                    HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(billService.addItem(id, body, user));
    }

    /**
     * 管理员手动触发月结 + 逾期扫描（定时任务同一套逻辑，便于验收/补跑）。
     * body 可选：{ "period":"2026-11", "overdueLimit":200 }
     */
    @PostMapping("/scheduler/run")
    public ApiResponse<Map<String, Object>> runScheduler(@RequestBody(required = false) Map<String, Object> body,
                                                         HttpServletRequest request) {
        User user = v2Context.current(request);
        if (!v2Context.isAdmin(user)) {
            throw ApiException.forbidden("仅管理员可手动触发账单任务");
        }
        String period = body == null ? null : Req.str(body, "period", false, 7);
        if (period == null || period.isEmpty()) {
            period = YearMonth.now(ZoneId.of("Asia/Shanghai")).toString();
        } else {
            try {
                YearMonth.parse(period);
            } catch (Exception e) {
                throw ApiException.bad("period 必须是 YYYY-MM");
            }
        }
        int limit = 200;
        if (body != null) {
            Integer requested = Req.integer(body, "overdueLimit", false);
            if (requested != null) {
                limit = requested;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("monthly", billService.monthlyGenerate(YearMonth.parse(period), user.getId()));
        result.put("overdue", billService.scanOverdue(limit));
        return ApiResponse.ok(result);
    }
}
