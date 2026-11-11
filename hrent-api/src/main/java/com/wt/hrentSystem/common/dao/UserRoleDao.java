package com.wt.hrentSystem.common.dao;

import com.wt.hrentSystem.common.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户多角色 user_role（V2）。只追加，不提供更新/软删。
 */
@Mapper
public interface UserRoleDao {

    int insert(UserRole entity);

    List<UserRole> findByUserId(String userId);

    List<UserRole> findList(UserRole entity);

    int deleteByUserAndRole(@Param("userId") String userId, @Param("role") String role);
}
