package com.wt.hrentSystem.rating.dao;

import com.wt.hrentSystem.rating.entity.RatingSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评分汇总 rating_summary（V3）。复合主键，不能用 BaseDao.get(String)。
 */
@Mapper
public interface RatingSummaryDao {

    RatingSummary getById(@Param("targetType") String targetType, @Param("targetId") String targetId);

    int upsert(RatingSummary entity);

    int deleteById(@Param("targetType") String targetType, @Param("targetId") String targetId);

    List<RatingSummary> findTopByType(@Param("targetType") String targetType, @Param("limit") int limit);
}
