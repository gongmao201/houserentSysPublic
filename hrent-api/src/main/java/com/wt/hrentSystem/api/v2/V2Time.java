package com.wt.hrentSystem.api.v2;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * v2 出参时间格式化：统一 ISO-8601 带时区（docs/13 §2.7）。
 * 存量实体用的是 yyyy-MM-dd HH:mm:ss 字符串，v2 不复用该格式。
 */
public final class V2Time {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private V2Time() {
    }

    public static String iso(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(Req.ZONE).format(ISO);
    }

    public static String day(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(Req.ZONE).toLocalDate().format(DAY);
    }

    public static String day(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Date) {
            return day((Date) raw);
        }
        if (raw instanceof LocalDate) {
            return ((LocalDate) raw).format(DAY);
        }
        return String.valueOf(raw);
    }

    public static String today() {
        return LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DAY);
    }
}
