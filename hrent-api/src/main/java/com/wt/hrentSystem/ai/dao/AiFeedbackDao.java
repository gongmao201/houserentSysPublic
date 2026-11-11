package com.wt.hrentSystem.ai.dao;

import com.wt.hrentSystem.ai.entity.AiFeedback;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * AI 推荐反馈埋点 ai_feedback（V6）。只追加。
 */
@Mapper
public interface AiFeedbackDao {

    int insert(AiFeedback entity);

    List<AiFeedback> findList(AiFeedback entity);
}
