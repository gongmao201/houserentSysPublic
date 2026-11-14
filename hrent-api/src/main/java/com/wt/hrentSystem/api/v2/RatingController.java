package com.wt.hrentSystem.api.v2;

import com.wt.hrentSystem.common.entity.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 评分 v2 接口（docs/13 §4.2）。全部走 /api/v2，与存量接口物理隔离。
 */
@RestController
@RequestMapping("/api/v2/rating")
public class RatingController {

    @Resource
    private RatingService ratingService;
    @Resource
    private V2Context v2Context;

    /**提交评价：租约结束后 30 天内、同租约同对象唯一 */
    @PostMapping("/submit")
    public ApiResponse<Map<String, Object>> submit(@RequestBody Map<String, Object> body,
                                                   HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(ratingService.submit(body, user));
    }

    @GetMapping("/house/{houseId}")
    public ApiResponse<Map<String, Object>> house(@PathVariable("houseId") String houseId,
                                                  HttpServletRequest request) {
        v2Context.current(request);
        return ApiResponse.ok(ratingService.summary("HOUSE", houseId));
    }

    @GetMapping("/landlord/{landlordId}")
    public ApiResponse<Map<String, Object>> landlord(@PathVariable("landlordId") String landlordId,
                                                     HttpServletRequest request) {
        v2Context.current(request);
        return ApiResponse.ok(ratingService.summary("LANDLORD", landlordId));
    }

    @PostMapping("/{id}/reply")
    public ApiResponse<Map<String, Object>> reply(@PathVariable("id") String id,
                                                  @RequestBody Map<String, Object> body,
                                                  HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(ratingService.reply(id, body, user));
    }

    @PostMapping("/{id}/appeal")
    public ApiResponse<Map<String, Object>> appeal(@PathVariable("id") String id,
                                                   @RequestBody Map<String, Object> body,
                                                   HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(ratingService.appeal(id, body, user));
    }

    /**平台核验：仅管理员 */
    @PostMapping("/audit")
    public ApiResponse<Map<String, Object>> audit(@RequestBody Map<String, Object> body,
                                                  HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(ratingService.audit(body, user));
    }

    /**核验结果回读：房东本人或管理员 */
    @GetMapping("/house/{houseId}/audit")
    public ApiResponse<Map<String, Object>> auditDetail(@PathVariable("houseId") String houseId,
                                                        HttpServletRequest request) {
        User user = v2Context.current(request);
        return ApiResponse.ok(ratingService.auditDetail(houseId, user));
    }
}
