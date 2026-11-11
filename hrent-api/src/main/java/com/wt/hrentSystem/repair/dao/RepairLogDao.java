package com.wt.hrentSystem.repair.dao;

import com.wt.hrentSystem.repair.entity.RepairLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 工单流转日志 repair_log（V4）。只追加。
 */
@Mapper
public interface RepairLogDao {

    int insert(RepairLog entity);

    List<RepairLog> findByRepairId(String repairId);

    List<RepairLog> findList(RepairLog entity);
}
