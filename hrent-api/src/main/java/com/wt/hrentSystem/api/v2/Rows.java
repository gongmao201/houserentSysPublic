package com.wt.hrentSystem.api.v2;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V2Mapper 返回 Map 行时的安全取值与时间转换。
 * MySQL TINYINT/BIGINT 在 Map 里可能是 Byte/Integer/Long/BigInteger，统一在这里收敛。
 */
public final class Rows {

    private Rows() {
    }

    public static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    public static Long lng(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return new BigDecimal(String.valueOf(v)).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw ApiException.bad("数值字段格式异常");
        }
    }

    public static Integer integer(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return new BigDecimal(String.valueOf(v)).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw ApiException.bad("状态字段格式异常");
        }
    }

    public static int intOr(Object v, int defaultValue) {
        Integer i = integer(v);
        return i == null ? defaultValue : i;
    }

    public static long longOr(Object v, long defaultValue) {
        Long l = lng(v);
        return l == null ? defaultValue : l;
    }

    public static String iso(Object v) {
        if (v instanceof Date) {
            return V2Time.iso((Date) v);
        }
        return v == null ? null : String.valueOf(v);
    }

    public static String day(Object v) {
        if (v instanceof Date) {
            return V2Time.day((Date) v);
        }
        return v == null ? null : String.valueOf(v);
    }

    /**保留驼峰 key，把 Date 统一转 ISO-8601，供响应体直接序列化 */
    public static Map<String, Object> copy(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (row == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            Object v = e.getValue();
            out.put(e.getKey(), v instanceof Date ? V2Time.iso((Date) v) : v);
        }
        return out;
    }
}
