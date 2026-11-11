package com.wt.hrentSystem.lease.dao;

import com.wt.hrentSystem.lease.entity.LeaseLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 租约流转日志 lease_log（V2）。只追加。
 */
@Mapper
public interface LeaseLogDao {

    int insert(LeaseLog entity);

    List<LeaseLog> findByLeaseId(String leaseId);

    List<LeaseLog> findList(LeaseLog entity);
}
