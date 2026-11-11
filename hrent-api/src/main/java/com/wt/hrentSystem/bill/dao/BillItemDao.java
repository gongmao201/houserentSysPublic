package com.wt.hrentSystem.bill.dao;

import com.wt.hrentSystem.bill.entity.BillItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 账单明细 bill_item（V5）。只追加；按账单物理删除。
 */
@Mapper
public interface BillItemDao {

    int insert(BillItem entity);

    int batchInsert(@Param("list") List<BillItem> list);

    List<BillItem> findByRentId(String rentId);

    int deleteByRentId(String rentId);
}
