package com.wt.hrentSystem.repair.dao;

import com.wt.hrentSystem.repair.entity.RepairImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工单图片 repair_image（V4）。只追加；按工单物理删除。
 */
@Mapper
public interface RepairImageDao {

    int insert(RepairImage entity);

    int batchInsert(@Param("list") List<RepairImage> list);

    List<RepairImage> findByRepairId(@Param("repairId") String repairId, @Param("stage") String stage);

    int deleteByRepairId(String repairId);
}
