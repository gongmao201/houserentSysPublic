package com.wt.hrentSystem.ai.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 房源向量 house_embedding（V6）。
 * 二期启用；本期禁止读写（V6 文件头红线）。
 */
@Data
public class HouseEmbedding implements Serializable {
    private static final long serialVersionUID = 1L;

    private String houseId;
    private String model;
    private Integer dim;
    /** float32 数组序列化 */
    private byte[] vec;
    /** 描述哈希，变了才重算 */
    private String contentHash;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
