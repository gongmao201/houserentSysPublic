package com.wt.hrentSystem.rating.dao;

import com.wt.hrentSystem.common.dao.BaseDao;
import com.wt.hrentSystem.rating.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评价 review（V3）。表有 del_flag，走 BaseDao 全套逻辑删。
 */
@Mapper
public interface ReviewDao extends BaseDao<Review> {

    List<Review> findByLeaseId(String leaseId);

    List<Review> findByTarget(@Param("targetType") String targetType, @Param("targetId") String targetId);
}
