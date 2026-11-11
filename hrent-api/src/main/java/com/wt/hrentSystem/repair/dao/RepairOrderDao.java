package com.wt.hrentSystem.repair.dao;

import com.wt.hrentSystem.common.dao.BaseDao;
import com.wt.hrentSystem.repair.entity.RepairOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 报修工单 repair_order（V4）。表有 del_flag，走 BaseDao 全套。
 */
@Mapper
public interface RepairOrderDao extends BaseDao<RepairOrder> {

    RepairOrder findByIdemKey(String idemKey);

    /**
     * 乐观锁更新状态：where id=? and version=?；返回 0 表示抢单失败。
     */
    int updateStatusWithVersion(@Param("id") String id,
                                @Param("status") String status,
                                @Param("version") Integer version,
                                @Param("acceptedBy") String acceptedBy);
}
