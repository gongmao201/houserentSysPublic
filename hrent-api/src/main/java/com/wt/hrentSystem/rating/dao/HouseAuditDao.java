package com.wt.hrentSystem.rating.dao;

import com.wt.hrentSystem.rating.entity.HouseAudit;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 房源核验单 house_audit（V3）。只追加，不更新。
 */
@Mapper
public interface HouseAuditDao {

    int insert(HouseAudit entity);

    HouseAudit findLatestByHouseId(String houseId);

    List<HouseAudit> findByHouseId(String houseId);

    List<HouseAudit> findList(HouseAudit entity);
}
