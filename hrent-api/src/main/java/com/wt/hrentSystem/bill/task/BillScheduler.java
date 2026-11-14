package com.wt.hrentSystem.bill.task;

import com.wt.hrentSystem.api.v2.BillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;

/**
 * 账单定时任务（docs/03 W7「单条 cron」简化口径）。
 * 1) 每月 1 日 00:05 生成当月账单；
 * 2) 每天 00:10 扫描逾期并给租客发站内信。
 * 时间统一 Asia/Shanghai；可通过 application.yml / 环境变量改 cron 或关闭。
 */
@Component
public class BillScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillScheduler.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private BillService billService;

    @Value("${bill.monthly.enabled:true}")
    private boolean monthlyEnabled;
    @Value("${bill.overdue.enabled:true}")
    private boolean overdueEnabled;
    @Value("${bill.overdue.limit:200}")
    private int overdueLimit;

    @Scheduled(cron = "${bill.monthly.cron:0 5 0 1 * ?}", zone = "Asia/Shanghai")
    public void generateMonthlyBills() {
        if (!monthlyEnabled) {
            log.info("账单月结任务已关闭（bill.monthly.enabled=false）");
            return;
        }
        try {
            Map<String, Object> result = billService.monthlyGenerate(YearMonth.now(ZONE), "system");
            log.info("账单月结完成 " + result);
        } catch (Exception e) {
            log.error("账单月结任务失败", e);
        }
    }

    @Scheduled(cron = "${bill.overdue.cron:0 10 0 * * ?}", zone = "Asia/Shanghai")
    public void notifyOverdueBills() {
        if (!overdueEnabled) {
            log.info("逾期提醒任务已关闭（bill.overdue.enabled=false）");
            return;
        }
        try {
            Map<String, Object> result = billService.scanOverdue(overdueLimit);
            log.info("逾期站内信完成 " + result);
        } catch (Exception e) {
            log.error("逾期站内信任务失败", e);
        }
    }
}
