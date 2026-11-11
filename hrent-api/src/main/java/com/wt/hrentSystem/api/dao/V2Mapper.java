package com.wt.hrentSystem.api.dao;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * v2 专用 SQL：只查/写 V2–V7 迁移引入的列与联表关系。
 * 放在 api.dao 下是为了被启动类已有的 @MapperScan("com.wt.hrentSystem.*.dao") 扫到，
 * 不需要改动任何存量配置。返回 Map 时列名一律起驼峰别名，供 v2 响应直接序列化。
 */
public interface V2Mapper {

    List<String> findRolesByUserId(@Param("userId") String userId);

    String findLandlordIdByHouseId(@Param("houseId") String houseId);

    Map<String, Object> getLeaseById(@Param("id") String id);

    List<Map<String, Object>> findMyLeases(@Param("userId") String userId,
                                          @Param("statusCode") Integer statusCode,
                                          @Param("isAdmin") boolean isAdmin);

    int terminateLease(@Param("id") String id,
                       @Param("actualEndDate") String actualEndDate,
                       @Param("version") int version);

    List<Map<String, Object>> findMyRepairs(@Param("userId") String userId,
                                           @Param("status") String status,
                                           @Param("isAdmin") boolean isAdmin,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    int countMyRepairs(@Param("userId") String userId,
                       @Param("status") String status,
                       @Param("isAdmin") boolean isAdmin);

    /**一条 SQL 完成状态流转 + 乐观锁 + 时间字段写入，避免「先查后改」竞态 */
    int transitionRepair(@Param("id") String id,
                         @Param("fromStatus") String fromStatus,
                         @Param("toStatus") String toStatus,
                         @Param("version") int version,
                         @Param("acceptedBy") String acceptedBy,
                         @Param("acceptedAt") String acceptedAt,
                         @Param("arrivedAt") String arrivedAt,
                         @Param("completedAt") String completedAt,
                         @Param("confirmedAt") String confirmedAt,
                         @Param("cancelReason") String cancelReason,
                         @Param("tenantScore") Integer tenantScore,
                         @Param("tenantComment") String tenantComment);

    Map<String, Object> getBillById(@Param("id") String id);

    List<Map<String, Object>> findMyBills(@Param("userId") String userId,
                                         @Param("period") String period,
                                         @Param("status") String status,
                                         @Param("isAdmin") boolean isAdmin,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    int countMyBills(@Param("userId") String userId,
                     @Param("period") String period,
                     @Param("status") String status,
                     @Param("isAdmin") boolean isAdmin);

    List<Map<String, Object>> findPendingBills(@Param("userId") String userId,
                                              @Param("isAdmin") boolean isAdmin,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    int countPendingBills(@Param("userId") String userId, @Param("isAdmin") boolean isAdmin);

    String findBillIdByRentingPeriod(@Param("rentingId") String rentingId, @Param("period") String period);

    Map<String, Object> getRentingBrief(@Param("rentingId") String rentingId);

    int insertBill(@Param("id") String id,
                   @Param("rentingId") String rentingId,
                   @Param("period") String period,
                   @Param("amountFen") long amountFen,
                   @Param("payableDate") String payableDate,
                   @Param("tenantId") String tenantId);

    int updateBillPaid(@Param("id") String id,
                       @Param("paidFen") long paidFen,
                       @Param("channel") String channel,
                       @Param("voucherPic") String voucherPic,
                       @Param("version") int version);

    int confirmBill(@Param("id") String id, @Param("version") int version);

    List<Map<String, Object>> findHousesForRecommend(@Param("region") String region,
                                                    @Param("budgetMaxFen") Long budgetMaxFen,
                                                    @Param("rooms") Integer rooms,
                                                    @Param("tagKeywords") List<String> tagKeywords,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    int countHousesForRecommend(@Param("region") String region,
                                @Param("budgetMaxFen") Long budgetMaxFen,
                                @Param("rooms") Integer rooms,
                                @Param("tagKeywords") List<String> tagKeywords);

    Map<String, Object> getHouseBrief(@Param("houseId") String houseId);
}
