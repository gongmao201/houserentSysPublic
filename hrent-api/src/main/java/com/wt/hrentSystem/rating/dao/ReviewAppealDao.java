package com.wt.hrentSystem.rating.dao;

import com.wt.hrentSystem.rating.entity.ReviewAppeal;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 评价申诉 review_appeal（V3）。无 del_flag，物理删除或归档。
 */
@Mapper
public interface ReviewAppealDao {

    int insert(ReviewAppeal entity);

    List<ReviewAppeal> findByReviewId(String reviewId);

    List<ReviewAppeal> findList(ReviewAppeal entity);

    int updateHandled(ReviewAppeal entity);
}
