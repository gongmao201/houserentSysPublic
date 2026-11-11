package com.wt.hrentSystem.api.v2;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * v2 入参校验工具：把 Map 请求体安全地转成强类型值，任何类型不符一律 400，绝不静默转换。
 * 设计原因：v2 接口契约以 JSON 为主，手写十几个 Request DTO 会产生大量样板；
 * 统一在这一层做「必填 / 类型 / 长度 / 枚举 / 范围」校验，Service 只处理业务。
 */
public final class Req {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_PAGE_SIZE = 50;

    private Req() {
    }

    public static Map<String, Object> body(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw ApiException.bad("请求体不能为空");
        }
        return body;
    }

    public static String str(Map<String, Object> b, String key, boolean required, int maxLen) {
        String s = asString(b.get(key), key);
        if (s == null || s.trim().isEmpty()) {
            if (required) {
                throw ApiException.bad("缺少必填参数：" + key);
            }
            return null;
        }
        s = s.trim();
        if (maxLen > 0 && s.length() > maxLen) {
            throw ApiException.bad(key + " 超长（最多 " + maxLen + " 字）");
        }
        return s;
    }

    public static String oneOf(Map<String, Object> b, String key, boolean required, String... allowed) {
        String s = str(b, key, required, 32);
        if (s == null) {
            return null;
        }
        for (String a : allowed) {
            if (a.equals(s)) {
                return s;
            }
        }
        throw ApiException.bad(key + " 取值不合法，允许：" + String.join("/", allowed));
    }

    public static Long fen(Map<String, Object> b, String key, boolean required) {
        Object v = b.get(key);
        if (v == null) {
            if (required) {
                throw ApiException.bad("缺少必填参数：" + key);
            }
            return null;
        }
        BigDecimal d = asDecimal(v, key);
        if (d.stripTrailingZeros().scale() > 0) {
            throw ApiException.bad(key + " 必须是整数（单位：分）");
        }
        long value;
        try {
            value = d.longValueExact();
        } catch (ArithmeticException e) {
            throw ApiException.bad(key + " 数值超出范围");
        }
        if (value < 0) {
            throw ApiException.bad(key + " 不能为负数");
        }
        return value;
    }

    public static Integer integer(Map<String, Object> b, String key, boolean required) {
        Object v = b.get(key);
        if (v == null) {
            if (required) {
                throw ApiException.bad("缺少必填参数：" + key);
            }
            return null;
        }
        BigDecimal d = asDecimal(v, key);
        try {
            return d.intValueExact();
        } catch (ArithmeticException e) {
            throw ApiException.bad(key + " 必须是整数");
        }
    }

    public static BigDecimal decimal(Map<String, Object> b, String key, boolean required) {
        Object v = b.get(key);
        if (v == null) {
            if (required) {
                throw ApiException.bad("缺少必填参数：" + key);
            }
            return null;
        }
        return asDecimal(v, key);
    }

    public static Boolean bool(Map<String, Object> b, String key) {
        Object v = b.get(key);
        if (v == null) {
            return Boolean.FALSE;
        }
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof Number) {
            int n = ((Number) v).intValue();
            if (n == 0 || n == 1) {
                return n == 1;
            }
        }
        throw ApiException.bad(key + " 必须是布尔值");
    }

    public static List<String> strList(Map<String, Object> b, String key, int maxItems) {
        Object v = b.get(key);
        if (v == null) {
            return Collections.emptyList();
        }
        if (!(v instanceof List)) {
            throw ApiException.bad(key + " 必须是数组");
        }
        List<?> raw = (List<?>) v;
        if (raw.size() > maxItems) {
            throw ApiException.bad(key + " 最多 " + maxItems + " 项");
        }
        List<String> result = new ArrayList<>();
        for (Object item : raw) {
            String s = asString(item, key);
            if (s != null && !s.trim().isEmpty()) {
                result.add(s.trim());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Map<String, Object> b, String key) {
        Object v = b.get(key);
        if (v == null) {
            return Collections.emptyMap();
        }
        if (!(v instanceof Map)) {
            throw ApiException.bad(key + " 必须是对象");
        }
        return (Map<String, Object>) v;
    }

    /**yyyy-MM-dd 或 ISO-8601（带不带时区均可），统一按 Asia/Shanghai 解析 */
    public static Date date(Map<String, Object> b, String key, boolean required) {
        String s = str(b, key, required, 64);
        if (s == null) {
            return null;
        }
        try {
            if (s.length() == 10) {
                return Date.from(LocalDate.parse(s).atStartOfDay(ZONE).toInstant());
            }
            if (s.contains("T")) {
                try {
                    return Date.from(OffsetDateTime.parse(s).toInstant());
                } catch (DateTimeParseException ignored) {
                    return Date.from(LocalDateTime.parse(s).atZone(ZONE).toInstant());
                }
            }
            return Date.from(LocalDateTime.parse(s.replace(' ', 'T')).atZone(ZONE).toInstant());
        } catch (DateTimeException e) {
            throw ApiException.bad(key + " 不是合法日期（yyyy-MM-dd 或 ISO-8601）");
        }
    }

    public static int pageNo(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }

    /**pageSize 上限 50，防止拖库（docs/13 §5 第 7 条） */
    public static int pageSize(Integer value) {
        if (value == null || value < 1) {
            return 10;
        }
        return Math.min(value, MAX_PAGE_SIZE);
    }

    private static String asString(Object v, String key) {
        if (v == null) {
            return null;
        }
        if (v instanceof String) {
            return (String) v;
        }
        if (v instanceof Number || v instanceof Boolean || v instanceof Character) {
            return String.valueOf(v);
        }
        throw ApiException.bad(key + " 类型不合法（应为字符串或数字）");
    }

    private static BigDecimal asDecimal(Object v, String key) {
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        if (v instanceof Number) {
            return new BigDecimal(v.toString());
        }
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.isEmpty()) {
                throw ApiException.bad(key + " 不能为空字符串");
            }
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException e) {
                throw ApiException.bad(key + " 不是合法数字");
            }
        }
        throw ApiException.bad(key + " 必须是数字");
    }
}
