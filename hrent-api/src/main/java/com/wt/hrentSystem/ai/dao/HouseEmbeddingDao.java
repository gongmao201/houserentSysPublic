package com.wt.hrentSystem.ai.dao;

import com.wt.hrentSystem.ai.entity.HouseEmbedding;
import org.apache.ibatis.annotations.Mapper;

/**
 * 房源向量 house_embedding（V6）。二期启用，本期禁止读写。
 */
@Mapper
public interface HouseEmbeddingDao {

    HouseEmbedding getByHouseId(String houseId);

    int upsert(HouseEmbedding entity);

    int deleteByHouseId(String houseId);
}
