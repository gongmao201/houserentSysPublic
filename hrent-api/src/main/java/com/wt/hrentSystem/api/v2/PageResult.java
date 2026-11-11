package com.wt.hrentSystem.api.v2;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * v2 分页结构：沿用存量字段名 pageNo / pageSize / count / list。
 */
@Data
public class PageResult<T> {

    private int pageNo;
    private int pageSize;
    private int count;
    private List<T> list;

    public static <T> PageResult<T> of(int pageNo, int pageSize, int count, List<T> list) {
        PageResult<T> p = new PageResult<>();
        p.setPageNo(pageNo);
        p.setPageSize(pageSize);
        p.setCount(count);
        p.setList(list == null ? Collections.<T>emptyList() : list);
        return p;
    }
}
